package com.learn.aiintelligenttourism.memory;

import com.learn.aiintelligenttourism.Model.ItineraryResponse;
import com.learn.aiintelligenttourism.memory.event.ItineraryMemoryEvent;
import com.learn.aiintelligenttourism.memory.event.TextTurnMemoryEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 统一编排三层记忆的读写，避免业务链路直接依赖多个存储细节。
 */
@Slf4j
@Service
public class MemoryOrchestrator {

    private static final int PROFILE_FACT_LIMIT = 5;
    private static final int LONG_TERM_MEMORY_LIMIT = 4;

    private final WorkingMemoryService workingMemoryService;
    private final ProfileMemoryService profileMemoryService;
    private final LongTermMemoryService longTermMemoryService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public MemoryOrchestrator(
            WorkingMemoryService workingMemoryService,
            ProfileMemoryService profileMemoryService,
            LongTermMemoryService longTermMemoryService,
            KafkaTemplate<String, Object> kafkaTemplate
    ) {
        this.workingMemoryService = workingMemoryService;
        this.profileMemoryService = profileMemoryService;
        this.longTermMemoryService = longTermMemoryService;
        this.kafkaTemplate = kafkaTemplate;
    }

    public MemoryContext loadContext(ConversationIdentity identity, String userMessage) {
        return loadContext(identity, userMessage, null);
    }

    /**
     * workingMemorySummaryOverride 用于 Graph 链路。
     * Graph 的 L1 在 checkpoint 里，不在 JDBC ChatMemory 里，因此必须允许调用方显式传入。
     */
    public MemoryContext loadContext(ConversationIdentity identity, String userMessage, String workingMemorySummaryOverride) {
        String workingSummary = workingMemoryService.summarizeRecentConversation(
                identity.threadId(),
                MemoryConstants.WORKING_MEMORY_WINDOW_SIZE
        );
        if (workingMemorySummaryOverride != null && !workingMemorySummaryOverride.isBlank()) {
            workingSummary = workingMemorySummaryOverride;
        }
        List<ProfileMemoryFact> profileFacts = profileMemoryService.findTopFacts(
                identity.visitorId(),
                PROFILE_FACT_LIMIT
        );
        List<LongTermMemoryItem> recalledMemories = longTermMemoryService.recall(
                identity.visitorId(),
                buildRecallQuery(userMessage, profileFacts),
                LONG_TERM_MEMORY_LIMIT
        );

        return new MemoryContext(workingSummary, profileFacts, recalledMemories);
    }

    public String buildPrompt(ConversationIdentity identity, String userMessage) {
        return loadContext(identity, userMessage).toSystemPrompt();
    }

    public String buildPrompt(ConversationIdentity identity, String userMessage, String workingMemorySummaryOverride) {
        return loadContext(identity, userMessage, workingMemorySummaryOverride).toSystemPrompt();
    }

    /**
     * 将解析任务丢给 Kafka，配合 CompletableFuture 回调实现【生产端异步确认】
     */
    public void rememberTextTurn(ConversationIdentity identity, String userMessage, String assistantMessage) {
        TextTurnMemoryEvent event = new TextTurnMemoryEvent(identity, userMessage, assistantMessage);
        kafkaTemplate.send(com.learn.aiintelligenttourism.config.KafkaConfig.TOPIC_TEXT_TURN, identity.visitorId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("【极其严重】写入 Kafka TextTurn 失败，用户记忆可能丢失。visitorId={}", identity.visitorId(), ex);
                    }
                });
    }

    /**
     * 将生成行程存档任务抛到 Kafka，生产端安全无阻塞设计
     */
    public void rememberItinerary(ConversationIdentity identity, String userMessage, ItineraryResponse itinerary) {
        ItineraryMemoryEvent event = new ItineraryMemoryEvent(identity, userMessage, itinerary);
        kafkaTemplate.send(com.learn.aiintelligenttourism.config.KafkaConfig.TOPIC_ITINERARY, identity.visitorId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("【极其严重】写入 Kafka Itinerary 失败，用户卡片流可能丢失。visitorId={}", identity.visitorId(), ex);
                    }
                });
    }

    private String buildRecallQuery(String userMessage, List<ProfileMemoryFact> profileFacts) {
        StringBuilder query = new StringBuilder();
        if (userMessage != null) {
            query.append(userMessage);
        }
        for (ProfileMemoryFact fact : profileFacts) {
            query.append(' ').append(fact.value());
        }
        return query.toString().trim();
    }
}
