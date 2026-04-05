package com.learn.aiintelligenttourism.memory;

import com.learn.aiintelligenttourism.Model.ItineraryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * L3 长期记忆服务。
 *
 * <p>这版最重要的变化是：L3 改成“事件驱动”而不是“每轮对话驱动”。
 * 也就是说，不再把普通文本轮次都写成 episodic memory，而只在高价值事件发生时写入：
 * - itinerary_generated
 * - itinerary_accepted
 * - itinerary_rejected
 * - session_closed
 *
 * <p>这样做的目标很明确：
 * 1. 降低噪声，避免 L3 很快被碎片对话塞满；
 * 2. 让召回出来的记忆更像“经历摘要”，而不是聊天流水账；
 * 3. 让后续引入 LLM 总结器时，接口边界保持稳定。
 */
@Slf4j
@Service
public class LongTermMemoryService {

    private static final String LONG_TERM_MEMORY_TABLE = "visitor_long_term_memory";
    private static final int THREAD_LOOKBACK_LIMIT = 6;

    private final JdbcTemplate jdbcTemplate;
    private final VectorStore memoryVectorStore;
    private final MemorySignalExtractor memorySignalExtractor;

    public LongTermMemoryService(
            JdbcTemplate jdbcTemplate,
            @Qualifier("memoryVectorStore") VectorStore memoryVectorStore,
            MemorySignalExtractor memorySignalExtractor
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.memoryVectorStore = memoryVectorStore;
        this.memorySignalExtractor = memorySignalExtractor;
    }

    /**
     * 普通文本轮次不再直接写 L3，只识别高价值事件。
     *
     * <p>这一步仍然接收 assistantMessage，是因为未来如果要引入 LLM 总结器，
     * 可以直接把“本轮问答”作为总结上下文；但当前 MVP 主要依赖 userMessage 做事件判定。
     */
    public void rememberTextTurn(ConversationIdentity identity, String userMessage, String assistantMessage) {
        if (identity == null || userMessage == null || userMessage.isBlank()) {
            return;
        }

        String normalizedUserMessage = userMessage.trim();

        if (memorySignalExtractor.isItineraryAcceptedMessage(normalizedUserMessage)) {
            rememberItineraryFeedback(identity, normalizedUserMessage, LongTermMemoryEventType.ITINERARY_ACCEPTED);
            return;
        }

        if (memorySignalExtractor.isItineraryRejectedMessage(normalizedUserMessage)) {
            rememberItineraryFeedback(identity, normalizedUserMessage, LongTermMemoryEventType.ITINERARY_REJECTED);
            return;
        }

        if (memorySignalExtractor.isSessionClosureMessage(normalizedUserMessage)) {
            rememberSessionClosed(identity, normalizedUserMessage, assistantMessage);
        }
    }

    public void rememberItinerary(ConversationIdentity identity, String userMessage, ItineraryResponse itinerary) {
        if (identity == null || itinerary == null) {
            return;
        }

        List<String> tags = memorySignalExtractor.buildLongTermTags(userMessage, itinerary);
        String title = itinerary.getTitle() == null || itinerary.getTitle().isBlank()
                ? "旅行行程单"
                : itinerary.getTitle().trim();
        String summary = memorySignalExtractor.buildItinerarySummary(itinerary);

        saveMemory(
                identity,
                "episodic",
                LongTermMemoryEventType.ITINERARY_GENERATED,
                title,
                summary,
                tags,
                0.82,
                "planner_flow"
        );
    }

    public List<LongTermMemoryItem> recall(String visitorId, String query, int topK) {
        if (visitorId == null || visitorId.isBlank() || query == null || query.isBlank()) {
            return List.of();
        }

        List<Document> documents = searchByVisitor(visitorId, query, topK);
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        List<LongTermMemoryItem> items = new ArrayList<>();
        for (Document document : documents) {
            Map<String, Object> metadata = document.getMetadata();
            items.add(new LongTermMemoryItem(
                    String.valueOf(metadata.getOrDefault("memoryId", document.getId())),
                    String.valueOf(metadata.getOrDefault("eventType", "")),
                    String.valueOf(metadata.getOrDefault("title", "历史记忆")),
                    document.getText(),
                    splitTags(String.valueOf(metadata.getOrDefault("tags", ""))),
                    parseDouble(metadata.get("importance"), 0.5)
            ));
        }
        return items;
    }

    /**
     * 读取已经落库的长期记忆原文，专门给调试接口和测试流程使用。
     */
    public List<StoredLongTermMemoryView> findRecentStoredMemories(String visitorId, int limit) {
        if (visitorId == null || visitorId.isBlank()) {
            return List.of();
        }

        return jdbcTemplate.query(
                "SELECT memory_id, thread_id, memory_type, event_type, title, summary, tags, importance, source, created_at " +
                        "FROM " + LONG_TERM_MEMORY_TABLE + " WHERE visitor_id = ? ORDER BY created_at DESC LIMIT ?",
                (rs, rowNum) -> new StoredLongTermMemoryView(
                        rs.getString("memory_id"),
                        rs.getString("thread_id"),
                        rs.getString("memory_type"),
                        rs.getString("event_type"),
                        rs.getString("title"),
                        rs.getString("summary"),
                        splitTags(rs.getString("tags")),
                        rs.getDouble("importance"),
                        rs.getString("source"),
                        toLocalDateTime(rs.getTimestamp("created_at"))
                ),
                visitorId.trim(),
                limit
        );
    }

    /**
     * 测试前清空某个访客在 L3 的结构化记录和向量记录，便于做干净基线对比。
     */
    public int clearVisitorMemories(String visitorId) {
        if (visitorId == null || visitorId.isBlank()) {
            return 0;
        }

        String normalizedVisitorId = visitorId.trim();
        int vectorRows = jdbcTemplate.update(
                "DELETE FROM memory_vector_store WHERE metadata->>'visitorId' = ?",
                normalizedVisitorId
        );
        int relationalRows = jdbcTemplate.update(
                "DELETE FROM " + LONG_TERM_MEMORY_TABLE + " WHERE visitor_id = ?",
                normalizedVisitorId
        );
        log.info("已清理 visitorId={} 的长期记忆，vectorRows={}, relationalRows={}",
                normalizedVisitorId, vectorRows, relationalRows);
        return relationalRows;
    }

    private void rememberItineraryFeedback(
            ConversationIdentity identity,
            String userMessage,
            LongTermMemoryEventType eventType
    ) {
        if (!hasThreadEvent(identity, LongTermMemoryEventType.ITINERARY_GENERATED)) {
            // 只有线程里确实生成过行程，accepted / rejected 才有语义基础。
            return;
        }

        StoredLongTermMemoryView latestGenerated = findLatestThreadEvent(identity, LongTermMemoryEventType.ITINERARY_GENERATED);
        if (latestGenerated == null) {
            return;
        }

        if (eventType == LongTermMemoryEventType.ITINERARY_ACCEPTED
                && hasThreadEvent(identity, LongTermMemoryEventType.ITINERARY_ACCEPTED)) {
            // 接受事件一个线程保留一条即可，避免“可以、好的、就这样”连续多轮重复写。
            return;
        }

        String titlePrefix = eventType == LongTermMemoryEventType.ITINERARY_ACCEPTED ? "已接受" : "已拒绝";
        String title = titlePrefix + "《" + latestGenerated.title() + "》";

        String summary;
        double importance;
        if (eventType == LongTermMemoryEventType.ITINERARY_ACCEPTED) {
            summary = "用户接受了行程《" + latestGenerated.title() + "》。用户的确认表达是“"
                    + trimForPrompt(userMessage, 80) + "”。该行程摘要：" + latestGenerated.summary();
            importance = 0.95;
        } else {
            summary = "用户否定了行程《" + latestGenerated.title() + "》。用户给出的原因或调整方向是“"
                    + trimForPrompt(userMessage, 100) + "”。该行程摘要：" + latestGenerated.summary();
            importance = 0.90;
        }

        saveMemory(
                identity,
                "episodic",
                eventType,
                title,
                summary,
                latestGenerated.tags(),
                importance,
                "feedback_signal"
        );
    }

    private void rememberSessionClosed(ConversationIdentity identity, String userMessage, String assistantMessage) {
        if (hasThreadEvent(identity, LongTermMemoryEventType.SESSION_CLOSED)) {
            return;
        }

        List<StoredLongTermMemoryView> recentThreadMemories = findRecentThreadMemories(
                identity.visitorId(),
                identity.threadId(),
                THREAD_LOOKBACK_LIMIT
        );
        if (recentThreadMemories.isEmpty()) {
            // session_closed 的目的是给“已经形成一定结果”的线程收尾。
            // 如果该线程里连一条高价值事件都没有，就不要凭一句“谢谢”硬写入 L3。
            return;
        }

        StoredLongTermMemoryView latestMemory = recentThreadMemories.getFirst();
        List<String> tags = latestMemory.tags();
        String title = "会话收尾：" + latestMemory.title();

        StringBuilder summary = new StringBuilder();
        summary.append("当前线程结束时，用户最后表示“")
                .append(trimForPrompt(userMessage, 80))
                .append("”。");
        summary.append("本线程最近形成的关键记忆是：")
                .append(latestMemory.summary());
        if (assistantMessage != null && !assistantMessage.isBlank()) {
            summary.append(" 收尾回复为“")
                    .append(trimForPrompt(assistantMessage, 100))
                    .append("”。");
        }

        saveMemory(
                identity,
                "episodic",
                LongTermMemoryEventType.SESSION_CLOSED,
                title,
                summary.toString(),
                tags,
                0.75,
                "session_signal"
        );
    }

    private boolean hasThreadEvent(ConversationIdentity identity, LongTermMemoryEventType eventType) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + LONG_TERM_MEMORY_TABLE +
                        " WHERE visitor_id = ? AND thread_id = ? AND event_type = ?",
                Integer.class,
                identity.visitorId(),
                identity.threadId(),
                eventType.code()
        );
        return count != null && count > 0;
    }

    private StoredLongTermMemoryView findLatestThreadEvent(ConversationIdentity identity, LongTermMemoryEventType eventType) {
        List<StoredLongTermMemoryView> rows = jdbcTemplate.query(
                "SELECT memory_id, thread_id, memory_type, event_type, title, summary, tags, importance, source, created_at " +
                        "FROM " + LONG_TERM_MEMORY_TABLE +
                        " WHERE visitor_id = ? AND thread_id = ? AND event_type = ? " +
                        " ORDER BY created_at DESC LIMIT 1",
                (rs, rowNum) -> new StoredLongTermMemoryView(
                        rs.getString("memory_id"),
                        rs.getString("thread_id"),
                        rs.getString("memory_type"),
                        rs.getString("event_type"),
                        rs.getString("title"),
                        rs.getString("summary"),
                        splitTags(rs.getString("tags")),
                        rs.getDouble("importance"),
                        rs.getString("source"),
                        toLocalDateTime(rs.getTimestamp("created_at"))
                ),
                identity.visitorId(),
                identity.threadId(),
                eventType.code()
        );
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private List<StoredLongTermMemoryView> findRecentThreadMemories(String visitorId, String threadId, int limit) {
        return jdbcTemplate.query(
                "SELECT memory_id, thread_id, memory_type, event_type, title, summary, tags, importance, source, created_at " +
                        "FROM " + LONG_TERM_MEMORY_TABLE +
                        " WHERE visitor_id = ? AND thread_id = ? ORDER BY created_at DESC LIMIT ?",
                (rs, rowNum) -> new StoredLongTermMemoryView(
                        rs.getString("memory_id"),
                        rs.getString("thread_id"),
                        rs.getString("memory_type"),
                        rs.getString("event_type"),
                        rs.getString("title"),
                        rs.getString("summary"),
                        splitTags(rs.getString("tags")),
                        rs.getDouble("importance"),
                        rs.getString("source"),
                        toLocalDateTime(rs.getTimestamp("created_at"))
                ),
                visitorId,
                threadId,
                limit
        );
    }

    private void saveMemory(
            ConversationIdentity identity,
            String memoryType,
            LongTermMemoryEventType eventType,
            String title,
            String summary,
            List<String> tags,
            double importance,
            String source
    ) {
        String memoryId = UUID.randomUUID().toString();
        String tagText = String.join(",", tags == null ? List.of() : tags);

        jdbcTemplate.update(
                "INSERT INTO " + LONG_TERM_MEMORY_TABLE +
                        " (memory_id, visitor_id, thread_id, memory_type, event_type, title, summary, tags, importance, source) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                memoryId,
                identity.visitorId(),
                identity.threadId(),
                memoryType,
                eventType.code(),
                title,
                summary,
                tagText,
                importance,
                source
        );

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("memoryId", memoryId);
        metadata.put("visitorId", identity.visitorId());
        metadata.put("threadId", identity.threadId());
        metadata.put("memoryType", memoryType);
        metadata.put("eventType", eventType.code());
        metadata.put("title", title);
        metadata.put("tags", tagText);
        metadata.put("importance", importance);
        metadata.put("source", source);

        try {
            // MVP 阶段直接用 summary 作为向量文本。
            // 这样调试时“存进数据库的摘要”和“向量召回的文本”保持一致，更容易排查问题。
            memoryVectorStore.add(List.of(new Document(memoryId, summary, metadata)));
        } catch (Exception e) {
            log.warn("长期记忆向量写入失败 memoryId={} error={}", memoryId, e.getMessage());
        }
    }

    private List<Document> searchByVisitor(String visitorId, String query, int topK) {
        SearchRequest strictRequest = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThresholdAll()
                .filterExpression("visitorId == '" + escapeFilterValue(visitorId) + "'")
                .build();

        try {
            List<Document> documents = memoryVectorStore.similaritySearch(strictRequest);
            if (documents != null && !documents.isEmpty()) {
                return documents;
            }
        } catch (Exception e) {
            log.warn("长期记忆过滤检索失败，将退化为客户端过滤: {}", e.getMessage());
        }

        List<Document> fallbackDocuments = memoryVectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK * 3)
                        .similarityThresholdAll()
                        .build()
        );

        if (fallbackDocuments == null) {
            return List.of();
        }

        return fallbackDocuments.stream()
                .filter(document -> visitorId.equals(String.valueOf(document.getMetadata().get("visitorId"))))
                .limit(topK)
                .collect(Collectors.toList());
    }

    private List<String> splitTags(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return List.of(csv.split(",")).stream()
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .toList();
    }

    private double parseDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String trimForPrompt(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private String escapeFilterValue(String value) {
        return value.replace("'", "\\'");
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
