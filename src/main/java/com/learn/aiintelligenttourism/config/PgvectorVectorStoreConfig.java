package com.learn.aiintelligenttourism.config;


import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;


@Configuration
public class PgvectorVectorStoreConfig {

    // 当前 DashScope embedding 接口单次最多 10 条文本。
    private static final int VECTOR_STORE_ADD_BATCH_SIZE = 10;

    @Bean
    @Primary
    VectorStore knowledgeVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel dashscopeEmbeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
                .dimensions(1536)                    // Optional: defaults to model dimensions or 1536
                .distanceType(COSINE_DISTANCE)       // Optional: defaults to COSINE_DISTANCE
                .indexType(HNSW)                     // Optional: defaults to HNSW
                .initializeSchema(true)              // Optional: defaults to false
                .schemaName("public")                // Optional: defaults to "public"
                .vectorTableName("vector_store")     // Optional: defaults to "vector_store"
                .maxDocumentBatchSize(VECTOR_STORE_ADD_BATCH_SIZE)
                .build();
    }

    /**
     * 用户长期记忆走独立向量表，避免和旅游知识库互相污染检索结果。
     */
    @Bean
    VectorStore memoryVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel dashscopeEmbeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
                .dimensions(1536)
                .distanceType(COSINE_DISTANCE)
                .indexType(HNSW)
                .initializeSchema(true)
                .schemaName("public")
                .vectorTableName("memory_vector_store")
                .maxDocumentBatchSize(VECTOR_STORE_ADD_BATCH_SIZE)
                .build();
    }

    /**
     * 意图前置召回使用独立向量表，避免和通用知识检索互相污染。
     */
    @Bean
    VectorStore intentVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel dashscopeEmbeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
                .dimensions(1536)
                .distanceType(COSINE_DISTANCE)
                .indexType(HNSW)
                .initializeSchema(true)
                .schemaName("public")
                .vectorTableName("intent_vector_store")
                .maxDocumentBatchSize(VECTOR_STORE_ADD_BATCH_SIZE)
                .build();
    }
}
