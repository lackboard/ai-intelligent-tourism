package com.learn.aiintelligenttourism.config;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.postgresql.PostgresSaver;
import com.alibaba.cloud.ai.graph.serializer.StateSerializer;
import com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SpringAIJacksonStateSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.sql.SQLException;

@Configuration
public class GraphCheckpointConfig {

    /**
     * Graph 工作流状态使用 PostgreSQL 持久化，和进程生命周期解耦。
     * 这里显式指定 Jackson 序列化器，保证 Spring AI Message、record 等对象都能稳定落盘。
     */
    @Bean
    public BaseCheckpointSaver graphCheckpointSaver(
            DataSourceProperties dataSourceProperties,
            ObjectMapper objectMapper
    ) throws SQLException {
        ParsedPostgresJdbcUrl jdbcUrl = parsePostgresJdbcUrl(dataSourceProperties.determineUrl());
        StateSerializer stateSerializer = new SpringAIJacksonStateSerializer(OverAllState::new, objectMapper.copy());

        return PostgresSaver.builder()
                .host(jdbcUrl.host())
                .port(jdbcUrl.port())
                .database(jdbcUrl.database())
                .user(dataSourceProperties.determineUsername())
                .password(dataSourceProperties.determinePassword())
                .createTables(false)
                .dropTablesFirst(false)
                .stateSerializer(stateSerializer)
                .build();
    }

    private ParsedPostgresJdbcUrl parsePostgresJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalStateException("spring.datasource.url 不能为空，无法初始化 Graph checkpoint 持久化");
        }
        if (!jdbcUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException("当前 Graph checkpoint 仅支持 PostgreSQL，实际 url: " + jdbcUrl);
        }

        URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        String database = uri.getPath();
        if (database == null || database.isBlank() || "/".equals(database)) {
            throw new IllegalStateException("无法从 JDBC URL 中解析数据库名: " + jdbcUrl);
        }

        return new ParsedPostgresJdbcUrl(
                uri.getHost(),
                uri.getPort() > 0 ? uri.getPort() : 5432,
                database.startsWith("/") ? database.substring(1) : database
        );
    }

    private record ParsedPostgresJdbcUrl(String host, int port, String database) {
    }
}
