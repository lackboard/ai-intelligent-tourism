package com.learn.aiintelligenttourism.agent;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SimpleChatNode implements NodeActionWithConfig {

    private final Resource systemResource;
    private final ChatClient chatClient;

    @Autowired
    private ToolCallback[] allTools;

    @Autowired
    private Advisor tourismAppRagCustomAdvisor ;

    private final ChatOptions chatOptions;

    @Autowired
    private JdbcChatMemoryRepository chatMemoryRepository;


    public SimpleChatNode(ChatClient chatClient, Resource systemResource) {
        this.chatClient = chatClient;
        this.systemResource = systemResource;
        this.chatOptions = DashScopeChatOptions.builder()
                .withModel("qwen3-max")
                .build();
    }

    @Autowired
    public SimpleChatNode(ChatClient chatClient) {
        this.chatClient = chatClient;
        this.systemResource = new ClassPathResource("/prompts/system-message.st");
        this.chatOptions = DashScopeChatOptions.builder()
                .withModel("qwen3-max")
                .build();
    }


    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.info(">>> 进入节点: SimpleChatNode (仅会话)");

        List<Message> messages = (List<Message>) state.value("messages")
                .orElseThrow(() -> new IllegalStateException("用户输入信息为空"));
        String message = state.value("userMessage")
                .map(v -> (String) v)
                .orElseThrow(() -> new IllegalStateException("用户输入信息为空"));
        String chatId = state.value("chatId")
                .map(v -> (String) v)
                .orElseThrow(() -> new IllegalStateException("会话ID 异常"));
        Prompt prompt = new Prompt(messages, this.chatOptions);
        ChatResponse chatResponse = this.chatClient
                .prompt(prompt)
                //.advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                //.advisors(loveAppRagCloudAdvisor)
                .system(systemResource)
                .user(message)
                .advisors(tourismAppRagCustomAdvisor)
                .toolCallbacks(allTools)
                .call()
                .chatResponse();


        assert chatResponse != null;
        String content = chatResponse.getResult().getOutput().getText();
        // 返回给前端：类型是 "text"
        assert content != null;
        return Map.of(
                "finalResponse", content,
                "next_node","end",
                "messages",new AssistantMessage(content));

    }
}
