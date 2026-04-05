package com.learn.aiintelligenttourism.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.learn.aiintelligenttourism.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient defaultChatClient(DashScopeChatModel dashscopeChatModel) {
        // Graph 主链路不走 ChatMemoryAdvisor，避免和 Graph checkpoint 形成双写。
        return ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
    }
}
