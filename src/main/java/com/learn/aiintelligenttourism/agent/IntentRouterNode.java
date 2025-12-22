package com.learn.aiintelligenttourism.agent;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class IntentRouterNode implements NodeActionWithConfig {

    private Resource systemResource;
    private final ChatClient chatClient;

    public IntentRouterNode(ChatClient chatClient, Resource systemResource) {
        this.chatClient = chatClient;
        this.systemResource = systemResource;
    }


    public IntentRouterNode(ChatClient chatClient) {
        this.systemResource = new ClassPathResource("/prompts/system-message-intention-judgment.st");
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.info(">>> 进入节点: IntentRouterNode (意图判断)");

        String message = state.value("userMessage")
                .map(v -> (String) v)
                .orElseThrow(() -> new IllegalStateException("用户输入信息为空"));
        log.info("IntentionJudgmentNode 用户信息: {}",message);
        boolean intention = false;
        try {
            DashScopeChatOptions dashScopeChatOptions = DashScopeChatOptions.builder().withModel("qwen-flash").build();
            String content = this.chatClient.prompt()
                    .options(dashScopeChatOptions)
                    .system(systemResource)
                    .user(message)
                    .call()
                    .content();// 获取 AI 的回复
            // 清洗结果（防止 AI 偶尔输出 "TRUE." 或 "Result: TRUE"）
            assert content != null;
            String cleanResult = content.trim().toUpperCase().replaceAll("[^A-Z]", "");
            intention = "TRUE".equals(cleanResult);
        } catch (Exception e) {
            // 兜底逻辑：如果 AI 调用失败，回退到简单的关键词判断，保证系统不挂
            System.err.println("意图识别服务异常: " + e.getMessage());
            intention = message.contains("规划") || message.contains("行程") || message.contains("安排");
        }

        log.info("IntentionJudgmentNode 判断结果: {}",intention);
        if (intention) {
            return Map.of(
                    "intent", "PLAN",
                    "next_node","circular_information_extractor");
        }else{
            return Map.of(
                    "intent", "CHAT",
                    "next_node","simple_chat");
        }

    }
}
