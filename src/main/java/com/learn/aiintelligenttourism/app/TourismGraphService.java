package com.learn.aiintelligenttourism.app;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.learn.aiintelligenttourism.Model.ChatRequest;
import com.learn.aiintelligenttourism.Model.ItineraryResponse;
import com.learn.aiintelligenttourism.agent.*;
import com.learn.aiintelligenttourism.memory.ConversationIdentity;
import com.learn.aiintelligenttourism.memory.MemoryConstants;
import com.learn.aiintelligenttourism.memory.MemoryOrchestrator;
import com.learn.aiintelligenttourism.memory.MemoryPromptSupport;
import com.learn.aiintelligenttourism.memory.WorkingMemoryMessageView;
import com.learn.aiintelligenttourism.memory.WorkingMemorySnapshot;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;

@Slf4j
@Service
public class TourismGraphService {

    public static final String SOURCE_GRAPH_CHECKPOINT = "graph_checkpoint";

    private final ChatClient defaultChatClient;
    private final ResearchNode researchNode;
    private final SimpleChatNode simpleChatNode;
    private final PolicyNode policyNode;
    private final PlanValidationNode planValidationNode;
    private final BaseCheckpointSaver graphCheckpointSaver;
    private final MemoryOrchestrator memoryOrchestrator;
    // Graph 主链路的统一意图识别服务（前置 RAG + LLM + fallback）。
    private final IntentRecognitionService intentRecognitionService;

    private CompiledGraph compiledGraph;

    @Autowired
    public TourismGraphService(
            ChatClient defaultChatClient,
            ResearchNode researchNode,
            SimpleChatNode simpleChatNode,
            PolicyNode policyNode,
            PlanValidationNode planValidationNode,
            BaseCheckpointSaver graphCheckpointSaver,
            MemoryOrchestrator memoryOrchestrator,
            IntentRecognitionService intentRecognitionService
    ) {
        this.defaultChatClient = defaultChatClient;
        this.researchNode = researchNode;
        this.simpleChatNode = simpleChatNode;
        this.policyNode = policyNode;
        this.planValidationNode = planValidationNode;
        this.graphCheckpointSaver = graphCheckpointSaver;
        this.memoryOrchestrator = memoryOrchestrator;
        this.intentRecognitionService = intentRecognitionService;
    }

    @PostConstruct
    public void init() {
        try {
            this.compiledGraph = createGraphWithInterruptableAction();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compile graph", e);
        }
    }

    /**
     * Graph 模式统一使用 threadId 作为会话主键。
     * - 已中断会话：恢复到上一个等待用户补充信息的节点
     * - 已结束会话：带着旧状态开启新一轮，从而保留历史上下文
     */
    public Map<String, Object> handleChat(ChatRequest request) throws Exception {
        String threadId = resolveThreadId(request);
        String visitorId = resolveVisitorId(request, threadId);
        String userInput = resolveUserInput(request);
        ConversationIdentity identity = new ConversationIdentity(visitorId, threadId);

        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId)
                .build();

        StateSnapshot currentState = getCurrentState(config, threadId);
        String graphWorkingMemorySummary = summarizeGraphMessages(currentState, MemoryConstants.WORKING_MEMORY_WINDOW_SIZE);
        String memoryContextPrompt = memoryOrchestrator.buildPrompt(identity, userInput, graphWorkingMemorySummary);
        boolean isResuming = isInterruptedState(currentState);
        Map<String, Object> newTurnInput = buildNewTurnInput(visitorId, threadId, userInput, memoryContextPrompt);

        if (isResuming) {
            log.info("Thread [{}] - Resuming from interruption with input: {}", threadId, userInput);

            try {
                Map<String, Object> stateUpdate = new LinkedHashMap<>();
                // 每轮开始前重置“最终输出槽位”，避免历史 finalResponse/itinerary 污染本轮返回类型。
                stateUpdate.put("finalResponse", "");
                stateUpdate.put("itinerary", null);
                stateUpdate.put("userMessage", userInput);
                stateUpdate.put("visitorId", visitorId);
                stateUpdate.put(MemoryPromptSupport.MEMORY_CONTEXT_PROMPT_KEY, memoryContextPrompt);
                // Graph 的 L1 统一在服务入口追加“当前用户这次真实输入”，避免各节点重复决定是否写用户消息。
                stateUpdate.put("messages", List.of(new UserMessage(userInput)));
                stateUpdate.put(MemoryPromptSupport.CURRENT_TURN_USER_MESSAGE_PERSISTED_KEY, true);
                config = compiledGraph.updateState(config, stateUpdate, currentState.next());
                config = RunnableConfig.builder(config)
                        .addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, "true")
                        .build();
                Map<String, Object> result = executeGraph(null, config);
                rememberGraphOutcome(identity, userInput, result);
                return result;
            } catch (IllegalStateException e) {
                if (!isMissingCheckpoint(e)) {
                    throw e;
                }
                log.warn("Thread [{}] - Resume failed because checkpoint is missing. Falling back to a fresh turn.", threadId, e);
            }
        }

        log.info("Thread [{}] - Starting a new graph turn: {}", threadId, userInput);
        // 新会话必须从 START 正常启动。
        // 之前改成 updateState(..., null) + stream(null, config) 后，图可能没有 next 节点可执行，导致接口卡住。
        Map<String, Object> result = executeGraph(newTurnInput, config);
        rememberGraphOutcome(identity, userInput, result);
        return result;
    }

    /**
     * Graph/manus 链路不会写 JDBC ChatMemory，L1 实际保存在 Graph checkpoint 的 state 里。
     * 调试接口需要读这里，才能看到 manus 模式下的工作记忆。
     */
    public WorkingMemorySnapshot getGraphWorkingMemorySnapshot(String threadId, int maxMessages) {
        if (threadId == null || threadId.isBlank()) {
            return WorkingMemorySnapshot.empty(SOURCE_GRAPH_CHECKPOINT);
        }

        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId.trim())
                .build();
        StateSnapshot snapshot = getCurrentState(config, threadId.trim());
        if (snapshot == null || snapshot.state() == null || snapshot.state().data() == null) {
            return WorkingMemorySnapshot.empty(SOURCE_GRAPH_CHECKPOINT);
        }

        List<WorkingMemoryMessageView> views = toWorkingMemoryViews(snapshot.state().data().get("messages"), maxMessages);
        if (views.isEmpty()) {
            return WorkingMemorySnapshot.empty(SOURCE_GRAPH_CHECKPOINT);
        }

        String summary = summarizeViews(views);
        return new WorkingMemorySnapshot(SOURCE_GRAPH_CHECKPOINT, summary, views);
    }

    private Map<String, Object> executeGraph(Map<String, Object> input, RunnableConfig config) {
        AtomicReference<NodeOutput> lastOutputRef = new AtomicReference<>();

        try {
            compiledGraph.stream(input, config)
                    .doOnNext(lastOutputRef::set)
                    .blockLast();
        } catch (Exception e) {
            log.error("Graph execution error", e);
            return Map.of("type", "error", "data", "服务器出现问题");
        }

        NodeOutput lastOutput = lastOutputRef.get();
        if (lastOutput == null) {
            return Map.of("type", "error", "data", "服务器出现问题");
        }

        if (lastOutput instanceof InterruptionMetadata interruption) {
            persistInterruptedState(config, interruption);
            Optional<Object> finalResponse = interruption.metadata("finalResponse");
            return Map.of("type", "text", "data", finalResponse.map(Object::toString).orElse(""));
        }

        return getResultMap(lastOutput);
    }

    private Map<String, Object> getResultMap(NodeOutput lastOutput) {
        Map<String, Object> stateData = lastOutput.state().data();

        // 对于 CHAT/POLICY 等文本节点，优先返回本轮 finalResponse，避免历史 itinerary 污染本轮输出。
        Object finalResponse = stateData.get("finalResponse");
        if (finalResponse instanceof String text && !text.isBlank()) {
            return Map.of("type", "text", "data", text);
        }

        if (stateData.containsKey("itinerary")) {
            Object rawItinerary = stateData.get("itinerary");
            if (rawItinerary instanceof ItineraryResponse itineraryResponse) {
                return Map.of("type", "card", "data", itineraryResponse);
            }
            try {
                Object itineraryObj = BeanUtil.toBean(rawItinerary, ItineraryResponse.class);
                return Map.of("type", "card", "data", itineraryObj);
            } catch (IllegalArgumentException e) {
                log.error("Failed to convert itinerary data", e);
            }
        }


        return Map.of("type", "error", "data", "服务器出现问题");
    }

    /**
     * InterruptableAction 返回的 metadata 不会自动写回 checkpoint。
     * 这里统一用 graph.updateState(...) 持久化追问阶段产生的结构化结果和消息历史。
     */
    private void persistInterruptedState(RunnableConfig config, InterruptionMetadata interruption) {
        Map<String, Object> stateUpdate = new LinkedHashMap<>();
        interruption.metadata("travelRequirements").ifPresent(value -> stateUpdate.put("travelRequirements", value));
        interruption.metadata("messages").ifPresent(value -> stateUpdate.put("messages", value));
        interruption.metadata("pendingQuestion").ifPresent(value -> stateUpdate.put("pendingQuestion", value));
        interruption.metadata(MemoryPromptSupport.CURRENT_TURN_USER_MESSAGE_PERSISTED_KEY)
                .ifPresent(value -> stateUpdate.put(MemoryPromptSupport.CURRENT_TURN_USER_MESSAGE_PERSISTED_KEY, value));

        if (stateUpdate.isEmpty()) {
            return;
        }

        try {
            compiledGraph.updateState(config, stateUpdate, interruption.node());
        } catch (Exception e) {
            log.error("Failed to persist interrupted graph state", e);
        }
    }

    private StateSnapshot getCurrentState(RunnableConfig config, String threadId) {
        try {
            return compiledGraph.getState(config);
        } catch (Exception e) {
            log.info("Thread [{}] - No existing state found", threadId);
            return null;
        }
    }

    private boolean isInterruptedState(StateSnapshot currentState) {
        return currentState != null
                && currentState.next() != null
                && !currentState.next().isBlank()
                && !END.equals(currentState.next());
    }

    /**
     * Graph 新会话统一通过 input 启动。
     * 不要在这里依赖 updateState 创建 checkpoint，否则在无 checkpoint 的 thread 上会直接抛 Missing Checkpoint。
     */
    private Map<String, Object> buildNewTurnInput(String visitorId, String threadId, String userInput, String memoryContextPrompt) {
        Map<String, Object> input = new HashMap<>();
        // 每轮开始前重置输出相关字段，确保返回值只来自本轮节点执行结果。
        input.put("finalResponse", "");
        input.put("itinerary", null);
        input.put("visitorId", visitorId);
        input.put("threadId", threadId);
        input.put("userMessage", userInput);
        input.put("retryCount", 0);
        input.put("validationFeedback", "");
        input.put(MemoryPromptSupport.MEMORY_CONTEXT_PROMPT_KEY, memoryContextPrompt);
        input.put(MemoryPromptSupport.CURRENT_TURN_USER_MESSAGE_PERSISTED_KEY, false);
        input.put("messages", List.of(new UserMessage(userInput)));
        return input;
    }

    private boolean isMissingCheckpoint(IllegalStateException e) {
        return e.getMessage() != null && e.getMessage().contains("Missing Checkpoint");
    }

    private String resolveThreadId(ChatRequest request) {
        if (request == null || request.getThreadId() == null || request.getThreadId().isBlank()) {
            throw new IllegalArgumentException("threadId 不能为空");
        }
        return request.getThreadId().trim();
    }

    private String resolveUserInput(ChatRequest request) {
        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            throw new IllegalArgumentException("message 不能为空");
        }
        return request.getMessage().trim();
    }

    private String resolveVisitorId(ChatRequest request, String threadId) {
        if (request == null || request.getVisitorId() == null || request.getVisitorId().isBlank()) {
            // 兼容旧请求：没有 visitorId 时退化到 threadId 作用域。
            return threadId;
        }
        return request.getVisitorId().trim();
    }

    private List<WorkingMemoryMessageView> toWorkingMemoryViews(Object rawMessages, int maxMessages) {
        if (!(rawMessages instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }

        List<WorkingMemoryMessageView> views = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Message message) {
                views.add(new WorkingMemoryMessageView(
                        message.getMessageType().name(),
                        trimForDebug(message.getText(), 300)
                ));
            }
        }

        if (views.isEmpty()) {
            return List.of();
        }

        int fromIndex = Math.max(0, views.size() - maxMessages);
        return views.subList(fromIndex, views.size());
    }

    private String summarizeGraphMessages(StateSnapshot snapshot, int maxMessages) {
        if (snapshot == null || snapshot.state() == null || snapshot.state().data() == null) {
            return "";
        }
        List<WorkingMemoryMessageView> views = toWorkingMemoryViews(snapshot.state().data().get("messages"), maxMessages);
        if (views.isEmpty()) {
            return "";
        }
        return summarizeViews(views);
    }

    private String summarizeViews(List<WorkingMemoryMessageView> views) {
        StringBuilder summary = new StringBuilder();
        for (WorkingMemoryMessageView view : views) {
            if (view.text() == null || view.text().isBlank()) {
                continue;
            }
            summary.append("- ")
                    .append(view.type())
                    .append(": ")
                    .append(trimForDebug(view.text(), 120))
                    .append('\n');
        }
        return summary.toString().trim();
    }

    private String trimForDebug(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private void rememberGraphOutcome(ConversationIdentity identity, String userInput, Map<String, Object> result) {
        if (result == null) {
            return;
        }
        Object type = result.get("type");
        Object data = result.get("data");
        
        // 1. 无论这轮大模型回复文本还是直接重新出卡片，先解析并存档用户的 feedback（如 isRejected, isAccepted）。
        // 提取时，如果没返回文本就传个空字符串给 assistant 占位即可。
        String assistantText = ("text".equals(type) && data instanceof String text) ? text : "";
        memoryOrchestrator.rememberTextTurn(identity, userInput, assistantText);

        // 2. 如果此轮确实产生了一版“全新的行程卡片”，独立向 L3 追加行程存底。
        if ("card".equals(type) && data instanceof ItineraryResponse itinerary) {
            memoryOrchestrator.rememberItinerary(identity, userInput, itinerary);
        }
    }

    private CompiledGraph createGraphWithInterruptableAction() throws GraphStateException {
        // 首节点使用增强意图路由：先判定 PLAN/POLICY/CHAT，再走条件边分流。
        var intentRouterNodeAsync = AsyncNodeActionWithConfig.node_async(new IntentRouterNode(intentRecognitionService));
        var simpleChatNodeAsync = AsyncNodeActionWithConfig.node_async(this.simpleChatNode);
        var policyNodeAsync = AsyncNodeActionWithConfig.node_async(this.policyNode);
        var circularInformationExtractor = new CircularInformationExtractorNode(defaultChatClient, "circular_information_extractor");
        var planGeneratorNodeAsync = AsyncNodeActionWithConfig.node_async(new PlanGeneratorNode(defaultChatClient));
        var planValidationNodeAsync = AsyncNodeActionWithConfig.node_async(this.planValidationNode);

        KeyStrategyFactory keyStrategyFactory = TourismAppKeyStrategyFactory.createKeyStrategyFactory();

        StateGraph workflow = new StateGraph(keyStrategyFactory)
                .addNode("intent_router", intentRouterNodeAsync)
                .addNode("simple_chat", simpleChatNodeAsync)
                .addNode("policy_node", policyNodeAsync)
                .addNode("circular_information_extractor", circularInformationExtractor)
                .addNode("research_agent", AsyncNodeActionWithConfig.node_async(researchNode))
                .addNode("plan_generator", planGeneratorNodeAsync)
                .addNode("plan_validation", planValidationNodeAsync)
                .addEdge(START, "intent_router")
                .addEdge("simple_chat", END)
                .addEdge("circular_information_extractor", "research_agent")
                .addEdge("research_agent", "plan_generator")
                .addEdge("plan_generator", "plan_validation")
                .addEdge("policy_node", END);

        workflow.addConditionalEdges("intent_router",
                edge_async(state -> (String) state.value("next_node").orElse("simple_chat")),
                Map.of(
                        "simple_chat", "simple_chat",
                        "policy_node", "policy_node",
                        "circular_information_extractor", "circular_information_extractor"
                ));

        workflow.addConditionalEdges("plan_validation",
                edge_async(state -> (String) state.value("next_node").orElse("end")),
                Map.of(
                        "plan_generator", "plan_generator",
                        "end", END
                ));

        CompileConfig compileConfig = CompileConfig.builder()
                .saverConfig(SaverConfig.builder()
                        .register(graphCheckpointSaver)
                        .build())
                .build();

        return workflow.compile(compileConfig);
    }
}
