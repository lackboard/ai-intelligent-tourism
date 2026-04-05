package com.learn.aiintelligenttourism.memory;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 记忆相关表只依赖 PostgreSQL，自行初始化即可，不和业务迁移工具强耦合。
 */
@Component
public class MemorySchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public MemorySchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initialize() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS visitor_profile_memory (
                    visitor_id VARCHAR(128) NOT NULL,
                    memory_key VARCHAR(64) NOT NULL,
                    memory_value TEXT NOT NULL,
                    confidence DOUBLE PRECISION NOT NULL,
                    source VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (visitor_id, memory_key)
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS visitor_long_term_memory (
                    memory_id VARCHAR(64) PRIMARY KEY,
                    visitor_id VARCHAR(128) NOT NULL,
                    thread_id VARCHAR(128) NOT NULL,
                    memory_type VARCHAR(32) NOT NULL,
                    event_type VARCHAR(64) NOT NULL DEFAULT 'legacy',
                    title VARCHAR(255) NOT NULL,
                    summary TEXT NOT NULL,
                    tags TEXT,
                    importance DOUBLE PRECISION NOT NULL,
                    source VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);

        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_visitor_profile_memory_updated_at
                ON visitor_profile_memory (visitor_id, updated_at DESC)
                """);

        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_visitor_long_term_memory_created_at
                ON visitor_long_term_memory (visitor_id, created_at DESC)
                """);

        // 兼容已经存在的旧表。
        // 旧版本只有 source=text_turn / itinerary，没有独立 event_type 字段。
        // 这里用 add-if-not-exists 做平滑升级，避免引入额外迁移工具。
        jdbcTemplate.execute("""
                ALTER TABLE visitor_long_term_memory
                ADD COLUMN IF NOT EXISTS event_type VARCHAR(64) NOT NULL DEFAULT 'legacy'
                """);

        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_visitor_long_term_memory_thread_event
                ON visitor_long_term_memory (visitor_id, thread_id, event_type, created_at DESC)
                """);
    }
}
