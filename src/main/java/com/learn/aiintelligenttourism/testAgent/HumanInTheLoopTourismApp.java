/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.learn.aiintelligenttourism.testAgent;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.action.*;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;

import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.learn.aiintelligenttourism.RAG.TourismAppRagCustomAdvisorFactory;
import com.learn.aiintelligenttourism.advisor.MyLoggerAdvisor;
import com.learn.aiintelligenttourism.agent.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;


/**
 * 人类反馈（Human-in-the-Loop）示例
 * 
 * 在实际业务场景中，经常会遇到人类介入的场景，人类的不同操作将影响工作流不同的走向。
 * Spring AI Alibaba Graph 提供了两种方式来实现人类反馈：
 * 
 * 1. InterruptionMetadata 模式：可以在任意节点随时中断，通过实现 InterruptableAction 接口来控制中断时机
 * 2. interruptBefore 模式：需要提前在编译配置中定义中断点，在指定节点执行前中断
 */
@Component
public class HumanInTheLoopTourismApp {

	// ==================== 模式一：InterruptionMetadata 模式 ====================

	private ChatClient chatClient = null;

	@Autowired
	JdbcChatMemoryRepository chatMemoryRepository; // 配置存储

	// 控制记忆长度
	ChatMemory messageWindowChatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(chatMemoryRepository)
			.maxMessages(15)
			.build();
	@Autowired
	private DashScopeChatModel dashscopeChatModel;
	// 获取当前日期
	String today = LocalDate.now().toString();

	@Autowired
	VectorStore vectorStore;
	private Advisor tourismAppRagCustomAdvisor ;


	@PostConstruct
	public void init() {
		// 在Spring完成所有依赖注入后执行
		this.tourismAppRagCustomAdvisor = TourismAppRagCustomAdvisorFactory.createTourismAppRagCustomAdvisor(
				vectorStore,
				dashscopeChatModel
		);
	}

	public HumanInTheLoopTourismApp(ChatModel dashscopeChatModel) {
		chatClient = ChatClient.builder(dashscopeChatModel)
				.defaultAdvisors(
						MessageChatMemoryAdvisor.builder(messageWindowChatMemory).build(),
						// 自定义拦截器
						new MyLoggerAdvisor()
				)
				.build();
	}

	// 查询资料智能体
	@Resource
  	private ReactAgent researchAgent;

	/**
	 * 创建测试 Graph
	 */
	public CompiledGraph createGraphWithInterruptableActionTest() throws GraphStateException {
		// 循环检查旅游要素节点（实现 InterruptableAction）
		var circularInformationExtractor = new CircularInformationExtractorNode(chatClient,"circular_information_extractor") ;

		// 配置 KeyStrategyFactory
		KeyStrategyFactory keyStrategyFactory = TourismAppKeyStrategyFactory.createKeyStrategyFactory();

		// 1. 定义一个手动调用 Agent 的节点行为，清空恢复中断行为
		NodeAction researchNodeAction = (state) -> {
			System.out.println(">>> 进入 Research Agent 节点 (手动隔离模式)");

			// 关键点：创建一个全新的 Config，不包含 resume 信号
			// 这样 Agent 就会把它当做一个全新的请求来处理，不会去查不存在的 Checkpoint
			RunnableConfig cleanConfig = RunnableConfig.builder()
					.build();

			// 执行 Agent
			// 注意：ReactAgent 的 invoke 入参和出参通常是 Map<String, Object>
			Optional<OverAllState> agentOutput = researchAgent.invoke(state.data().toString(), cleanConfig);

			// 处理输出：提取 Agent 的结果并放入主图需要的 key (searchResults)
			// ReactAgent 通常将最终结果放在 "output" 或 "payload" 中，也可能包含 "messages"

			String finalResult = "未获取到有效结果";

			if (agentOutput.isPresent()) {
				// 2. 获取内部的 OverAllState 对象
				OverAllState overallState = agentOutput.get();

				// 3. 获取 data 数据 (Map)
				// 注意：根据你的截图，OverAllState 应该有一个 getData() 方法返回 Map<String, Object>
				Map<String, Object> dataMap = overallState.data();

				if (dataMap != null && dataMap.containsKey("messages")) {
					Object messagesObj = dataMap.get("messages");

					// 4. 转换并获取消息列表
					if (messagesObj instanceof List) {
						List<Message> messages = (List<Message>) messagesObj;

						if (!messages.isEmpty()) {
							// 5. 获取最后一个消息 (根据截图，最后一条是总结性的 AssistantMessage)
							Message lastMsgObj = messages.getLast();

							// 6. 类型判断并提取文本
							if (lastMsgObj instanceof AssistantMessage) {
								finalResult = lastMsgObj.getText();
							} else  {
								// 兜底：如果是其他类型的 Message (如 UserMessage)，也尝试获取内容
								finalResult = lastMsgObj.getText();
							}
						}
					}
				}
			}

			// 7. 返回给主 Graph 的结果
            assert finalResult != null;
            return Map.of("searchResults", finalResult);
		};



		// 构建 Graph
		StateGraph workflow = new StateGraph(keyStrategyFactory)

				.addNode("circular_information_extractor", circularInformationExtractor)  // 使用可中断节点
				.addNode("research_agent",AsyncNodeAction.node_async(researchNodeAction))
				.addEdge(START, "circular_information_extractor")
				.addEdge("circular_information_extractor", "research_agent")
				.addEdge("research_agent", END);


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


	/**
	 * 创建智游 Graph
	 */
	public CompiledGraph createGraphWithInterruptableAction() throws GraphStateException {

		// 1. 定义一个手动调用 Agent 的节点行为，清空恢复中断行为
		NodeAction researchNodeAction = (state) -> {
			System.out.println(">>> 进入 Research Agent 节点 (手动隔离模式)");

			// 关键点：创建一个全新的 Config，不包含 resume 信号
			// 这样 Agent 就会把它当做一个全新的请求来处理，不会去查不存在的 Checkpoint
			RunnableConfig cleanConfig = RunnableConfig.builder()
					.build();

			// 执行 Agent
			// 注意：ReactAgent 的 invoke 入参和出参通常是 Map<String, Object>
			Optional<OverAllState> agentOutput = researchAgent.invoke(state.data().toString(), cleanConfig);

			// 处理输出：提取 Agent 的结果并放入主图需要的 key (searchResults)
			// ReactAgent 通常将最终结果放在 "output" 或 "payload" 中，也可能包含 "messages"

			String finalResult = "未获取到有效结果";

			if (agentOutput.isPresent()) {
				// 2. 获取内部的 OverAllState 对象
				OverAllState overallState = agentOutput.get();

				// 3. 获取 data 数据 (Map)
				// 注意：根据你的截图，OverAllState 应该有一个 getData() 方法返回 Map<String, Object>
				Map<String, Object> dataMap = overallState.data();

				if (dataMap != null && dataMap.containsKey("messages")) {
					Object messagesObj = dataMap.get("messages");

					// 4. 转换并获取消息列表
					if (messagesObj instanceof List) {
						List<Message> messages = (List<Message>) messagesObj;

						if (!messages.isEmpty()) {
							// 5. 获取最后一个消息 (根据截图，最后一条是总结性的 AssistantMessage)
							Message lastMsgObj = messages.getLast();

							// 6. 类型判断并提取文本
							if (lastMsgObj instanceof AssistantMessage) {
								finalResult = lastMsgObj.getText();
							} else  {
								// 兜底：如果是其他类型的 Message (如 UserMessage)，也尝试获取内容
								finalResult = lastMsgObj.getText();
							}
						}
					}
				}
			}

			// 7. 返回给主 Graph 的结果
			assert finalResult != null;
			return Map.of("searchResults", finalResult);
		};


		// 意图路由节点
		var intentRouterNode = AsyncNodeActionWithConfig.node_async(new IntentRouterNode(chatClient));
		// 仅聊天节点
		var simpleChatNode = AsyncNodeActionWithConfig.node_async(new SimpleChatNode(chatClient));

		// 循环检查旅游要素节点（实现 InterruptableAction）
		var circularInformationExtractor = new CircularInformationExtractorNode(chatClient,"circular_information_extractor");
		// 规划生成智能体
		var planGeneratorNode = AsyncNodeActionWithConfig.node_async(new PlanGeneratorNode(chatClient));

		// 配置 KeyStrategyFactory
		KeyStrategyFactory keyStrategyFactory = TourismAppKeyStrategyFactory.createKeyStrategyFactory();

		// 构建 Graph
		StateGraph workflow = new StateGraph(keyStrategyFactory)
				.addNode("intent_router", intentRouterNode)
				.addNode("simple_chat", simpleChatNode)
				.addNode("circular_information_extractor", circularInformationExtractor)  // 使用可中断节点
				.addNode("research_agent",AsyncNodeAction.node_async(researchNodeAction))
				.addNode("plan_generator", planGeneratorNode)
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

	/**
	 * 执行 Graph 直到中断（InterruptionMetadata 模式）
	 * 检查流式输出中的 InterruptionMetadata
	 */
	public InterruptionMetadata executeUntilInterruptWithMetadata(CompiledGraph graph) {
		String userText = "我想去西安旅游，请帮我规划一下旅程。";
		// 初始输入
		Map<String, Object> initialInput = Map.of(
				"messages", new UserMessage(userText),
				"chatId","2",
				"userMessage",userText);

		// 配置线程 ID
		var invokeConfig = RunnableConfig.builder()
				.threadId("Thread1")
				.build();

		// 用于保存最后一个输出
		AtomicReference<NodeOutput> lastOutputRef = new AtomicReference<>();

		// 运行 Graph 直到第一个中断点
		graph.stream(initialInput, invokeConfig)
				.doOnNext(event -> {
					System.out.println("节点输出: " + event);
					lastOutputRef.set(event);
				})
				.doOnError(error -> System.err.println("流错误: " + error.getMessage()))
				.doOnComplete(() -> System.out.println("流完成"))
				.blockLast();

		// 检查最后一个输出是否是 InterruptionMetadata
		NodeOutput lastOutput = lastOutputRef.get();
		if (lastOutput instanceof InterruptionMetadata) {
			System.out.println("\n检测到中断: " + lastOutput);
			return (InterruptionMetadata) lastOutput;
		}

		return null;
	}

	/**
	 * 等待用户输入并更新状态（InterruptionMetadata 模式）
	 */
	public RunnableConfig waitUserInputAndUpdateStateWithMetadata(CompiledGraph graph, InterruptionMetadata interruption) throws Exception {
		var invokeConfig = RunnableConfig.builder()
				.threadId("Thread1")
				.build();

		// 检查当前状态
		System.out.printf("\n--State before update--\n%s\n", graph.getState(invokeConfig));

		// 模拟用户输入
		var userInput = "我想后天去"; // "back" 表示返回上一个节点
		System.out.printf("\n--User Input--\n用户选择: '%s'\n\n", userInput);

		// 更新状态
		// 使用 updateState 更新状态，传入中断时的节点 ID
		var updatedConfig = graph.updateState(invokeConfig, Map.of(
				"messages", new UserMessage(userInput),
				"userMessage",userInput), interruption.node());


		// 检查更新后的状态
		System.out.printf("--State after update--\n%s\n", graph.getState(updatedConfig));
		StateSnapshot stateSnapshot = graph.getState(updatedConfig);

		return updatedConfig;
	}

	/**
	 * 继续执行 Graph（InterruptionMetadata 模式）
	 * 使用 HUMAN_FEEDBACK_METADATA_KEY 来恢复执行
	 */
	public void continueExecutionWithMetadata(CompiledGraph graph, RunnableConfig updatedConfig) {
		// 创建恢复配置，添加 HUMAN_FEEDBACK_METADATA_KEY
		RunnableConfig resumeConfig = RunnableConfig.builder(updatedConfig)
				.addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, "placeholder")
				.build();

		// 继续执行 Graph（input 为 null，使用之前的状态）
		graph.stream(null, resumeConfig)
				.doOnNext(event -> System.out.println("节点输出: " + event))
				.doOnError(error -> System.err.println("流错误: " + error.getMessage()))
				.doOnComplete(() -> System.out.println("流完成"))
				.blockLast();
	}

	// ==================== 主方法 ====================

	public void mainTest() throws Exception {
		System.out.println("========================================");
		System.out.println("人类反馈（Human-in-the-Loop）示例");
		System.out.println("========================================\n");

		// ========== 模式一：InterruptionMetadata 模式 ==========
		System.out.println("=== 模式一：InterruptionMetadata 模式 ===");
		System.out.println("演示如何在任意节点实现 InterruptableAction，通过返回 InterruptionMetadata 实现中断\n");

		CompiledGraph graph1 = createGraphWithInterruptableActionTest();

		// 执行直到中断
		InterruptionMetadata interruption = executeUntilInterruptWithMetadata(graph1);

		if (interruption != null) {
			// 等待用户输入并更新状态
			RunnableConfig updatedConfig = waitUserInputAndUpdateStateWithMetadata(graph1, interruption);

			// 继续执行
			continueExecutionWithMetadata(graph1, updatedConfig);
		}

		System.out.println("\n模式一示例执行完成\n");

		System.out.println("所有示例执行完成");
		System.out.println("========================================");
	}
}
