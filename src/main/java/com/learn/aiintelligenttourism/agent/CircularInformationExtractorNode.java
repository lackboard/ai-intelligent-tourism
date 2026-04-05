package com.learn.aiintelligenttourism.agent;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
import com.alibaba.cloud.ai.graph.action.InterruptableAction;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.learn.aiintelligenttourism.Model.TravelRequirements;
import com.learn.aiintelligenttourism.memory.MemoryPromptSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class CircularInformationExtractorNode implements AsyncNodeActionWithConfig, InterruptableAction {

    private final ChatClient chatClient;
    private final String nodeId;
    private final ChatOptions chatOptions;

    public CircularInformationExtractorNode(ChatClient chatClient, String nodeId) {
        this.chatClient = chatClient;
        this.nodeId = nodeId;
        this.chatOptions = DashScopeChatOptions.builder()
                .withModel("qwen3-max")
                .build();
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state, RunnableConfig config) {
        String userMessage = state.value("userMessage")
                .map(String.class::cast)
                .orElseThrow(() -> new IllegalStateException("用户输入信息为空"));
        TravelRequirements requirements = extractRequirements(state, userMessage);

        // InterruptableAction 的 interrupt() 结果不会自动合并进后续 apply() 的 state。
        // 因此成功分支必须在 apply() 中显式返回结构化需求，交给框架持久化。
        Map<String, Object> outputs = Map.of(
                "travelRequirements", requirements,
                "pendingQuestion", ""
        );
        return CompletableFuture.completedFuture(outputs);
    }

    @Override
    public Optional<InterruptionMetadata> interrupt(String nodeId, OverAllState state, RunnableConfig config) {
        log.info(">>> 进入节点: CircularInformationExtractorNode (信息提取)");

        String userMessage = state.value("userMessage")
                .map(String.class::cast)
                .orElseThrow(() -> new IllegalStateException("用户输入信息为空"));
        TravelRequirements requirements = extractRequirements(state, userMessage);
        log.info("AI 提取到的需求: {}", requirements);

        if (requirements.isMissingCriticalInfo()) {
            log.info("关键信息缺失（目的地或时间）");

            String followUpPrompt = "用户想去旅游，但信息不全。当前已知信息：" + requirements +
                    "。请生成一句自然、礼貌的追问，只输出追问本身。";

            String question = chatClient.prompt()
                    .options(this.chatOptions)
                    .user(followUpPrompt)
                    .call()
                    .content();

            if (question == null || question.isBlank()) {
                question = "还差一点关键信息，请告诉我你的目的地和出行时间。";
            }

            // 中断节点内部不要依赖 state.updateState() 的副作用。
            // 这里把要持久化的数据挂到 metadata 上，由服务层用 graph.updateState(...) 正式写入 checkpoint。
            InterruptionMetadata interruption = InterruptionMetadata.builder(this.nodeId, state)
                    .addMetadata("finalResponse", question)
                    .addMetadata("node", this.nodeId)
                    .addMetadata("travelRequirements", requirements)
                    // 新会话场景下，当前用户消息可能尚未持久化，因此这里按标记决定是否一并写回。
                    .addMetadata("messages", buildVisibleMessages(state, userMessage, question))
                    .addMetadata("pendingQuestion", question)
                    .addMetadata(MemoryPromptSupport.CURRENT_TURN_USER_MESSAGE_PERSISTED_KEY, true)
                    .build();
            return Optional.of(interruption);
        }

        return Optional.empty();
    }

    private TravelRequirements extractRequirements(OverAllState state, String userMessage) {
        List<Message> messages = getConversationHistory(state);
        Prompt prompt = new Prompt(messages, this.chatOptions);
        String today = LocalDate.now().toString();

        String extractPrompt = """
            你是一个专业的旅游需求分析师。你的任务是从用户的输入中提取关键信息。

            # 当前参考时间
            今天是：{current_date}。
            注意：此日期仅用于计算相对时间（如“明天”“下周一”），绝不要将其作为默认的出行时间。

            # 待提取字段
            1. destination（目的地）
            2. travelDate（出行时间）
            3. budget（预算）
            4. preference（游玩偏好、人员构成等）

            # 提取规则（非常重要）
            1. 严格忠于用户输入：只有当用户明确提到时间时，才能提取 travelDate。
            2. 禁止默认：如果用户只说“想去大理”而未提及时间，travelDate 必须为 null。
            3. 处理相对时间：如果用户说“明天”，请基于 {current_date} 计算出具体日期（yyyy-MM-dd）。
            4. 不要编造任何未提及的信息。
            """;

        TravelRequirements requirements = this.chatClient.prompt(prompt)
                .system(extractPrompt)
                .system(spec -> spec.param("current_date", today))
                .user(userMessage)
                .call()
                .entity(TravelRequirements.class);

        if (requirements == null) {
            throw new IllegalStateException("需求提取失败");
        }
        return requirements;
    }

    @SuppressWarnings("unchecked")
    private List<Message> getConversationHistory(OverAllState state) {
        Object rawMessages = state.data().get("messages");
        if (!(rawMessages instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Message> messages = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Message message) {
                messages.add(message);
            }
        }
        return messages;
    }

    private List<Message> buildVisibleMessages(OverAllState state, String userMessage, String assistantMessage) {
        List<Message> messages = new ArrayList<>();
        boolean userMessagePersisted = MemoryPromptSupport.isCurrentTurnUserMessagePersisted(
                state.data().get(MemoryPromptSupport.CURRENT_TURN_USER_MESSAGE_PERSISTED_KEY)
        );
        if (!userMessagePersisted) {
            messages.add(new UserMessage(userMessage));
        }
        messages.add(new AssistantMessage(assistantMessage));
        return messages;
    }
}
