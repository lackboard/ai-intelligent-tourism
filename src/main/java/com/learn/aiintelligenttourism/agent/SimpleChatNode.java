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
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SimpleChatNode implements NodeActionWithConfig {

    private final Resource systemResource;
    private final ChatClient chatClient;

    @jakarta.annotation.Resource(name = "simpleChatTools")
    private ToolCallback[] simpleChatTools;

    @jakarta.annotation.Resource(name = "allTools")
    private ToolCallback[] allTools;

    @Autowired
    private Advisor tourismAppRagCustomAdvisor;

    private final ChatOptions chatOptions;

    public SimpleChatNode(ChatClient chatClient, Resource systemResource) {
        this.chatClient = chatClient;
        this.systemResource = systemResource;
        this.chatOptions = DashScopeChatOptions.builder()
                .withModel("qwen3-max")
                .build();
    }

    @Autowired
    public SimpleChatNode(ChatClient chatClient) {
        this(chatClient, new ClassPathResource("/prompts/system-message.st"));
    }

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) {
        log.info(">>> 进入节点: SimpleChatNode (普通对话)");

        List<Message> history = getConversationHistory(state);
        String memoryContextPrompt = MemoryPromptSupport.fromState(state);
        String userMessage = state.value("userMessage")
                .map(String.class::cast)
                .orElseThrow(() -> new IllegalStateException("用户输入信息为空"));

        Prompt prompt = new Prompt(history, this.chatOptions);
        ChatResponse chatResponse = applyMemoryContext(this.chatClient
                .prompt(prompt)
                .system(systemResource)
                .user(userMessage), memoryContextPrompt)
                //.advisors(tourismAppRagCustomAdvisor)
                .toolCallbacks(allTools)
                .call()
                .chatResponse();

        if (chatResponse == null || chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
            throw new IllegalStateException("普通对话未返回有效结果");
        }

        String content = chatResponse.getResult().getOutput().getText();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("普通对话返回空内容");
        }

        // Graph 模式下不依赖 ChatMemoryAdvisor，而是显式把当前轮对话写回 checkpoint。
        return Map.of(
                "finalResponse", content,
                "next_node", "end",
                // 只有在本轮用户消息尚未持久化时，才把用户输入和助手回复一并落进 checkpoint。
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
