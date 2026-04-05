package com.learn.aiintelligenttourism.memory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * L2 用户记忆：稳定偏好走结构化表，便于后续做画像筛选和迁移。
 */
@Service
public class ProfileMemoryService {

    private static final String PROFILE_TABLE = "visitor_profile_memory";

    private final JdbcTemplate jdbcTemplate;
    private final MemorySignalExtractor memorySignalExtractor;

    public ProfileMemoryService(JdbcTemplate jdbcTemplate, MemorySignalExtractor memorySignalExtractor) {
        this.jdbcTemplate = jdbcTemplate;
        this.memorySignalExtractor = memorySignalExtractor;
    }

    public List<ProfileMemoryFact> rememberVisitorPreferences(String visitorId, String userMessage) {
        if (visitorId == null || visitorId.isBlank() || userMessage == null || userMessage.isBlank()) {
            return List.of();
        }

        List<ProfileMemoryFact> extractedFacts = memorySignalExtractor.extractProfileFacts(userMessage);
        if (extractedFacts.isEmpty()) {
            return List.of();
        }

        Map<String, ProfileMemoryFact> mergedFacts = new LinkedHashMap<>();
        for (ProfileMemoryFact fact : extractedFacts) {
            // L2 现在不再是“抽到什么写什么”。
            // 这里先用一层策略过滤掉明显的单次变量，只保留可跨会话复用的稳定偏好。
            if (!memorySignalExtractor.shouldStoreAsProfileFact(fact, userMessage)) {
                continue;
            }
            mergedFacts.merge(fact.key(), fact, this::mergeSameKeyFact);
        }

        if (mergedFacts.isEmpty()) {
            return List.of();
        }

        for (ProfileMemoryFact fact : mergedFacts.values()) {
            upsertFact(visitorId.trim(), fact);
        }
        return List.copyOf(mergedFacts.values());
    }

    public List<ProfileMemoryFact> findTopFacts(String visitorId, int limit) {
        if (visitorId == null || visitorId.isBlank()) {
            return List.of();
        }
        return jdbcTemplate.query(
                "SELECT memory_key, memory_value, confidence, source FROM " + PROFILE_TABLE +
                        " WHERE visitor_id = ? ORDER BY confidence DESC, updated_at DESC LIMIT ?",
                new ProfileMemoryFactRowMapper(),
                visitorId.trim(),
                limit
        );
    }

    /**
     * 调试时查看该访客已经沉淀下来的全部画像字段。
     */
    public List<ProfileMemoryFact> findAllFacts(String visitorId) {
        if (visitorId == null || visitorId.isBlank()) {
            return List.of();
        }
        return jdbcTemplate.query(
                "SELECT memory_key, memory_value, confidence, source FROM " + PROFILE_TABLE +
                        " WHERE visitor_id = ? ORDER BY updated_at DESC, memory_key ASC",
                new ProfileMemoryFactRowMapper(),
                visitorId.trim()
        );
    }

    public int clearVisitorFacts(String visitorId) {
        if (visitorId == null || visitorId.isBlank()) {
            return 0;
        }
        return jdbcTemplate.update(
                "DELETE FROM " + PROFILE_TABLE + " WHERE visitor_id = ?",
                visitorId.trim()
        );
    }

    private void upsertFact(String visitorId, ProfileMemoryFact fact) {
        String valueToStore = fact.value();
        if ("interest_tags".equals(fact.key())) {
            valueToStore = mergeCsvValues(findCurrentValue(visitorId, fact.key()), fact.value());
        }

        jdbcTemplate.update(
                "INSERT INTO " + PROFILE_TABLE + " (visitor_id, memory_key, memory_value, confidence, source) " +
                        "VALUES (?, ?, ?, ?, ?) " +
                        "ON CONFLICT (visitor_id, memory_key) DO UPDATE SET " +
                        "memory_value = EXCLUDED.memory_value, " +
                        "confidence = GREATEST(" + PROFILE_TABLE + ".confidence, EXCLUDED.confidence), " +
                        "source = EXCLUDED.source, " +
                        "updated_at = CURRENT_TIMESTAMP",
                visitorId,
                fact.key(),
                valueToStore,
                fact.confidence(),
                fact.source()
        );
    }

    private String findCurrentValue(String visitorId, String key) {
        List<String> values = jdbcTemplate.query(
                "SELECT memory_value FROM " + PROFILE_TABLE + " WHERE visitor_id = ? AND memory_key = ?",
                (rs, rowNum) -> rs.getString("memory_value"),
                visitorId,
                key
        );
        return values.isEmpty() ? "" : values.getFirst();
    }

    private ProfileMemoryFact mergeSameKeyFact(ProfileMemoryFact current, ProfileMemoryFact incoming) {
        if (!"interest_tags".equals(current.key())) {
            // 非 tags 字段只保留当前更可信的那个值。
            // 这版 MVP 还没有完整的 candidate/active 多值画像，因此先保持“一个 key 一个主值”。
            return incoming.confidence() >= current.confidence() ? incoming : current;
        }
        String mergedValue = mergeCsvValues(current.value(), incoming.value());
        return new ProfileMemoryFact(
                current.key(),
                mergedValue,
                Math.max(current.confidence(), incoming.confidence()),
                incoming.source()
        );
    }

    private String mergeCsvValues(String left, String right) {
        Set<String> values = new TreeSet<>();
        addCsvValues(values, left);
        addCsvValues(values, right);
        return String.join(",", values);
    }

    private void addCsvValues(Set<String> target, String csv) {
        if (csv == null || csv.isBlank()) {
            return;
        }
        for (String value : csv.split(",")) {
            String trimmed = value.trim();
            if (!trimmed.isBlank()) {
                target.add(trimmed);
            }
        }
    }

    private static class ProfileMemoryFactRowMapper implements RowMapper<ProfileMemoryFact> {
        @Override
        public ProfileMemoryFact mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ProfileMemoryFact(
                    rs.getString("memory_key"),
                    rs.getString("memory_value"),
                    rs.getDouble("confidence"),
                    rs.getString("source")
            );
        }
    }
}
