package com.learn.aiintelligenttourism.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
import com.alibaba.cloud.ai.graph.action.InterruptableAction;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.learn.aiintelligenttourism.Model.TravelRequirements;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class CircularInformationExtractorNode implements AsyncNodeActionWithConfig, InterruptableAction {

    private final ChatClient chatClient;
    private final String nodeId;
    private final ChatOptions chatOptions;
    private TravelRequirements requirements;
    private List<Message> newMessageList = new ArrayList<>();
    // 获取当前日期
    String today = LocalDate.now().toString();

    public CircularInformationExtractorNode(ChatClient chatClient, String nodeId) {
        this.chatClient = chatClient;
        this.nodeId = nodeId;
        this.chatOptions = DashScopeChatOptions.builder()
                .withModel("qwen3-max")
                .build();
    }


    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state, RunnableConfig config) {
        List<Message> resMessageList = new ArrayList<>(newMessageList);
        newMessageList.clear();
        return CompletableFuture.completedFuture(Map.of(
                "travelRequirements", requirements,
                "messages", resMessageList));
    }

    @Override
    public Optional<InterruptionMetadata> interrupt(String nodeId, OverAllState state, RunnableConfig config) {
        log.info(">>> 进入节点: CircularInformationExtractorNode (信息提取)");
        String message = state.value("userMessage")
                .map(v -> (String) v)
                .orElseThrow(() -> new IllegalStateException("用户输入信息为空"));
        List<Message> messages = (List<Message>) state.value("messages")
                .orElseThrow(() -> new IllegalStateException("用户输入信息为空"));
        // 中断过来的话要更新
        if(!newMessageList.isEmpty()) {
            Message messageLast = newMessageList.getLast();
            if (messageLast instanceof AssistantMessage) {
                newMessageList.add(new UserMessage(message));
            }
        }

        //String chatId = state.value("chatId")
        //        .map(v -> (String) v)
        //        .orElseThrow(() -> new IllegalStateException("会话ID 异常"));
        Prompt prompt = new Prompt(messages, this.chatOptions);
        // 1. 构造提取信息的 Prompt
        // 重点：我们需要 AI 根据"对话历史"来提取，而不仅仅是当前这一句话
        // 因为用户可能上一句说了"去日本"，这一句说"下周出发"
        String extractPrompt = """
    你是一个专业的旅游需求分析师。你的任务是从用户的输入中提取关键信息。
    
    # 当前参考时间
    今天是：{current_date}。
    注意：此日期仅用于计算相对时间（如“明天”、“下周一”），绝不要将其作为默认的出行时间。

    # 待提取字段
    1. destination (目的地)
    2. travelDate (出行时间)
    3. budget (预算)
    4. preference (游玩偏好)

    # 提取规则 (非常重要)
    1. **严格忠于用户输入**：只有当用户明确提到了时间（如“明天去”、“10月1号去”、“这周末去”），才能提取 travelDate。
    2. **禁止默认**：如果用户只说了“想去大理”而未提及时间，travelDate 必须为 null。不要因为今天是 {current_date} 就自动填入该日期。
    3. **处理相对时间**：如果用户说“明天”，请基于 {current_date} 计算出具体日期 (yyyy-MM-dd)。
    4. 不要编造任何未提及的信息。

    """;

        // 2. 调用 AI 进行提取 (带有记忆功能)
        requirements = this.chatClient.prompt(prompt)
                .system(extractPrompt)
                .system(s -> s.param("current_date", today) )
                .user(message)
                //.advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId)) // 关键：带上记忆！
                .call()
                .entity(TravelRequirements.class); // 自动转为 TravelRequirements 对象

        log.info("AI 提取到的需求: {}", requirements);

        assert requirements != null;

        // 4. 路由逻辑判断
        if (requirements.isMissingCriticalInfo()) {
            log.info("关键信息缺失 (目的地或时间)");
            String userMessage = "用户想去旅游，但信息不全。当前已知信息：" + requirements.toString() +
                    "。请生成一句自然的话术，礼貌地追问用户缺少的关键信息（目的地或时间）。只输出追问话术。";

            String question = chatClient.prompt()
                    .options(this.chatOptions)
                    //.advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId)) // 关键：带上记忆！
                    .user(userMessage).call().content();
            assert question != null;
            newMessageList.add(new AssistantMessage(question));

            // 返回 InterruptionMetadata 来中断执行
            InterruptionMetadata interruption = InterruptionMetadata.builder(nodeId, state)
                    .addMetadata("finalResponse", question)
                    .addMetadata("node", nodeId)
                    .addMetadata("travelRequirements", requirements)
                    // 如果要做工具确认的话，可以在这里添加 toolFeedbacks，具体可参考 HumanInTheLoopHook 实现
                    //.toolFeedbacks(List.of(InterruptionMetadata.ToolFeedback.builder().description("").build()))
                    .build();

            return Optional.of(interruption);
        }

        return Optional.empty();
    }
}