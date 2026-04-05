package com.learn.aiintelligenttourism.memory.event;

import com.learn.aiintelligenttourism.config.KafkaConfig;
import com.learn.aiintelligenttourism.memory.LongTermMemoryService;
import com.learn.aiintelligenttourism.memory.ProfileMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MemoryKafkaConsumer {

    private final ProfileMemoryService profileMemoryService;
    private final LongTermMemoryService longTermMemoryService;

    public MemoryKafkaConsumer(ProfileMemoryService profileMemoryService, LongTermMemoryService longTermMemoryService) {
        this.profileMemoryService = profileMemoryService;
        this.longTermMemoryService = longTermMemoryService;
    }

    // 【核心改造】去掉了内部吃掉异常的 try-catch，如果大模型报错，任其往外抛。
    // 让 Spring 底层捕获它，并触发我们在 KafkaConfig 内配置的 ErrorHandler（重试 + 抛入死信队列）
    @KafkaListener(topics = KafkaConfig.TOPIC_TEXT_TURN, groupId = "memory-consumer-group")
    public void handleTextTurnEvent(TextTurnMemoryEvent event) {
        if (event == null || event.getIdentity() == null) {
            return;
        }
        log.info("Kafka Consumer 收到文本记忆事件: visitorId={}", event.getIdentity().visitorId());

        profileMemoryService.rememberVisitorPreferences(event.getIdentity().visitorId(), event.getUserMessage());
        longTermMemoryService.rememberTextTurn(event.getIdentity(), event.getUserMessage(), event.getAssistantMessage());
    }

    @KafkaListener(topics = KafkaConfig.TOPIC_ITINERARY, groupId = "memory-consumer-group")
    public void handleItineraryEvent(ItineraryMemoryEvent event) {
        if (event == null || event.getIdentity() == null) {
            return;
        }
        log.info("Kafka Consumer 收到行程存档事件: visitorId={}", event.getIdentity().visitorId());

        profileMemoryService.rememberVisitorPreferences(event.getIdentity().visitorId(), event.getUserMessage());
        longTermMemoryService.rememberItinerary(event.getIdentity(), event.getUserMessage(), event.getItinerary());
    }
}
