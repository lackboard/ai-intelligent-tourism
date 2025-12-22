package com.learn.aiintelligenttourism.app;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.convert.Convert;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.action.*;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.learn.aiintelligenttourism.Model.ChatRequest;
import com.learn.aiintelligenttourism.Model.ItineraryResponse;
import com.learn.aiintelligenttourism.agent.*;
import com.learn.aiintelligenttourism.config.ChatClientConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

import org.springframework.ai.chat.messages.UserMessage;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;

@Slf4j
@Service
public class TourismGraphService {

    @Autowired
    private ChatClient defaultChatClient;
    private CompiledGraph compiledGraph; // 单例 Graph

    private final ConcurrentMap<String, ChatClient> userChatClientCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {

        // 2. 编译 Graph (只编译一次)
        try {
            this.compiledGraph = createGraphWithInterruptableAction();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compile graph", e);
        }
    }

    /**
     * 处理聊天请求（核心业务逻辑）
     */
    public Map<String, Object> handleChat(ChatRequest request) throws Exception {
        String threadId = request.getThreadId();
        String userInput = request.getMessage();
        // 配置线程ID
        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId)
                .build();

        // 1. 尝试获取当前状态
        StateSnapshot currentState = null;
        try {
            // 如果是第一次运行，这里会抛出 "Missing Checkpoint" 异常
            currentState = compiledGraph.getState(config);
        } catch (Exception e) {
            // 捕获异常，说明没有历史状态，视为新对话
            log.info("Thread [{}] - No existing state found (New Conversation)", threadId);
        }
        boolean isResuming = currentState != null && !currentState.next().isEmpty() && !currentState.next().equals(END);

        
        if (isResuming) {
            log.info("Thread [{}] - Resuming from interruption with input: {}", threadId, userInput);
            // 获取当前挂起的节点（通常是 InterruptableAction 的节点）
            //String pendingNode = currentState.next().iterator().next();
            
            // 更新状态（模拟用户填充了缺失信息）
            Map<String, Object> stateUpdate = Map.of(
                    "userMessage", userInput
            );
            
            // updateState 会返回一个新的 config
            config = compiledGraph.updateState(config, stateUpdate, currentState.next());
            
            // 添加恢复标记 (HUMAN_FEEDBACK_METADATA_KEY)
            config = RunnableConfig.builder(config)
                    .addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, "true")
                    .build();
            
            // 恢复执行时，input 为 null (因为状态已通过 updateState 更新)
            return executeGraph(null, config, threadId);

        } else {
            log.info("Thread [{}] - Starting new conversation: {}", threadId, userInput);
            // 新对话初始输入
            Map<String, Object> initialInput = Map.of(
                    "messages", new UserMessage(userInput),
                    "chatId", request.getChatId() != null ? request.getChatId() : "default",
                    "userMessage", userInput
            );
            return executeGraph(initialInput, config, threadId);
        }
    }

    /**
     * 执行 Graph 并封装结果
     */
    private Map<String, Object> executeGraph(Map<String, Object> input, RunnableConfig config, String threadId) {
        AtomicReference<NodeOutput> lastOutputRef = new AtomicReference<>();

        try {
            // 执行流
            compiledGraph.stream(input, config)
                    .doOnNext(event -> {
                        if(event instanceof StreamingOutput<?> streamingOutput){
                            // 流式输出块
                            String chunk = streamingOutput.chunk();
                            if (chunk != null && !chunk.isEmpty()) {
                                System.out.print(chunk); // 实时打印流式内容
                            }
                        }

                        lastOutputRef.set(event);
                        // 这里可以根据实际输出结构收集文本，这里简单假设最后会有文本输出
                        // 如果需要流式推送到前端，这里需要改为 SSE 逻辑，本例先做同步返回
                    })
                    .blockLast(); 
        } catch (Exception e) {
            log.error("Graph execution error", e);
            return Map.of("type", "error", "data","服务器出现问题");
        }

        NodeOutput lastOutput = lastOutputRef.get();

        // 1. 判断是否中断
        if (lastOutput instanceof InterruptionMetadata interruption) {
            Optional<Object> finalResponse = interruption.metadata("finalResponse");
            return Map.of("type", "text", "data", finalResponse.isPresent() ? finalResponse.get().toString() : "");
        }

        // 2. 正常结束
        // 尝试从状态中获取最后信息

        return getResultMap(lastOutput);


    }

    // 辅助方法：从 Graph 状态中提取最后一条 AI 回复
    private Map<String,Object> getResultMap(NodeOutput lastOutput) {
        Map<String, Object> stateData = lastOutput.state().data(); // 注意：StateSnapshot.values() 返回的是 Map
        String intent = (String)stateData.get("intent");
        if("CHAT".equals(intent)){
            // 2. 安全获取文本
            if (stateData.containsKey("finalResponse")) {
                String finalResponse = String.valueOf(stateData.get("finalResponse"));
                return Map.of("type", "text", "data", finalResponse);
            }
        }else{
            // 3. 安全转换 ItineraryResponse
            if (stateData.containsKey("itinerary")) {
                Object rawItinerary = stateData.get("itinerary");
                if (rawItinerary != null) {
                    try {
                        // 【核心修复】使用 Jackson 将 Map 转换为 Record 对象
                        Object itineraryObj = BeanUtil.toBean(rawItinerary, ItineraryResponse.class);
                        return Map.of("type", "card", "data", itineraryObj);
                    } catch (IllegalArgumentException e) {
                        log.error("Failed to convert itinerary data", e);
                        // 如果转换失败，降级为返回原始 Map，或者 null
                        return Map.of("type", "error", "data","服务器出现问题");
                    }
                }
            }
        }
        return Map.of("type", "error", "data","服务器出现问题");

    }

    @Autowired
    private ResearchNode researchNode;

    @Autowired
    private SimpleChatNode simpleChatNode;

    /**
     * 这里复用你原有的 Graph 构建逻辑
     * 注意：researchNodeAction 需要包含在方法内或者作为类成员
     */
    private CompiledGraph createGraphWithInterruptableAction() throws GraphStateException {

        // 意图路由节点
        var intentRouterNodeAsync = AsyncNodeActionWithConfig.node_async(new IntentRouterNode(defaultChatClient));
        // 仅聊天节点
        var simpleChatNodeAsync = AsyncNodeActionWithConfig.node_async(this.simpleChatNode);

        // 循环检查旅游要素节点（实现 InterruptableAction）
        var circularInformationExtractor = new CircularInformationExtractorNode(defaultChatClient,"circular_information_extractor");

        // 规划生成智能体
        var planGeneratorNodeAsync = AsyncNodeActionWithConfig.node_async(new PlanGeneratorNode(defaultChatClient));

        // 配置 KeyStrategyFactory
        KeyStrategyFactory keyStrategyFactory = TourismAppKeyStrategyFactory.createKeyStrategyFactory();

        // 构建 Graph
        StateGraph workflow = new StateGraph(keyStrategyFactory)
                .addNode("intent_router", intentRouterNodeAsync)
                .addNode("simple_chat", simpleChatNodeAsync)
                .addNode("circular_information_extractor", circularInformationExtractor)  // 使用可中断节点
                .addNode("research_agent",AsyncNodeActionWithConfig.node_async(researchNode))
                .addNode("plan_generator", planGeneratorNodeAsync)
                .addEdge(START, "intent_router")
                .addEdge("simple_chat", END)
                .addEdge("circular_information_extractor", "research_agent")
                .addEdge("research_agent", "plan_generator")
                .addEdge("plan_generator", END);

        // 添加条件边（基于节点返回的 next_node）
        workflow.addConditionalEdges("intent_router",
                edge_async(state -> {
                    return (String) state.value("next_node").orElse("simple_chat");
                }),
                Map.of(
                        "simple_chat", "simple_chat",
                        "circular_information_extractor", "circular_information_extractor"
                ));

        // 配置内存保存器（用于状态持久化）
        var saver = new MemorySaver();

        var compileConfig = CompileConfig.builder()
                .saverConfig(SaverConfig.builder()
                        .register(saver)
                        .build())
                // 不再需要 interruptBefore 配置，中断由 InterruptableAction 控制
                .build();

        return workflow.compile(compileConfig);
    }

}