package com.learn.aiintelligenttourism.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.learn.aiintelligenttourism.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;

@Component
public class ChatClientConfig {

    @Autowired
    private DashScopeChatModel dashscopeChatModel;

    @Autowired
    private JdbcChatMemoryRepository chatMemoryRepository;

    @Bean
    public ChatClient defaultChatClient() {
    // 1. 初始化 ChatClient
    //ChatMemory messageWindowChatMemory = MessageWindowChatMemory.builder()
    //        .chatMemoryRepository(chatMemoryRepository)
    //        .maxMessages(15)
    //        .build();

    return ChatClient.builder(dashscopeChatModel)
            .defaultAdvisors(
                    //MessageChatMemoryAdvisor.builder(messageWindowChatMemory).build(),
                    new MyLoggerAdvisor()
            )
            .build();
    }

}