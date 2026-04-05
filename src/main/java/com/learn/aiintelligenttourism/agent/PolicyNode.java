package com.learn.aiintelligenttourism.agent;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import com.learn.aiintelligenttourism.memory.MemoryPromptSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class PolicyNode implements NodeActionWithConfig {

    private final ChatClient chatClient;
    private final ToolCallback[] policyTools;

    @Autowired
    private Advisor tourismAppRagCustomAdvisor;

    private final ChatOptions chatOptions;

    public PolicyNode(ChatClient chatClient, @Qualifier("policyTools") ToolCallback[] policyTools) {
        this.chatClient = chatClient;
        this.policyTools = policyTools;
        this.chatOptions = DashScopeChatOptions.builder()
                .withModel("qwen3-max")
                .build();
    }

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) {
        log.info(">>> 进入节点: PolicyNode (政策规则问答)");

        List<Message> history = getConversationHistory(state);
        String memoryContextPrompt = MemoryPromptSupport.fromState(state);
        String userMessage = state.value("userMessage")
                .map(String.class::cast)
                .orElseThrow(() -> new IllegalStateException("用户输入信息为空"));

        Prompt prompt = new Prompt(history, this.chatOptions);
        ChatResponse chatResponse = applyMemoryContext(this.chatClient
                .prompt(prompt)
                .system("你是国内旅游政策助手。优先调用政策工具查询：文旅局公告页、重点景区官方公告页、高德POI营业状态。\n" +
                        "回答格式要求：\n" +
                        "1) 先给结论，再给依据。\n" +
                        "2) 若工具结果中出现 official_url 或公告 url，必须输出“官方链接”小节并逐条附原始链接。\n" +
                        "3) 禁止编造官方来源；若无权威链接，明确说明“暂未检索到官方链接，请以景区/文旅局官网为准”。")
                .user(userMessage), memoryContextPrompt)
                //.advisors(tourismAppRagCustomAdvisor)
                .toolCallbacks(policyTools)
                .call()
                .chatResponse();

        if (chatResponse == null || chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
            throw new IllegalStateException("政策问答未返回有效结果");
        }

        String content = chatResponse.getResult().getOutput().getText();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("政策问答返回空内容");
        }

        return Map.of(
                "finalResponse", content,
                "next_node", "end",
                "messages", buildVisibleMessages(state, userMessage, content),
                MemoryPromptSupport.CURRENT_TURN_USER_MESSAGE_PERSISTED_KEY, true
        );
    }

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

    private ChatClient.ChatClientRequestSpec applyMemoryContext(ChatClient.ChatClientRequestSpec spec, String memoryContextPrompt) {
        if (memoryContextPrompt == null || memoryContextPrompt.isBlank()) {
            return spec;
        }
        return spec.system(memoryContextPrompt);
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
