package com.learn.aiintelligenttourism.RAG;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class KnowledgeIngestionService {

    // 当前 DashScope embedding 单次最多 10 条文本，批量入库必须严格不超过该上限。
    private static final int VECTOR_STORE_ADD_BATCH_SIZE = 10;
    // 逻辑作用域：用于区分不同知识库任务（锁、状态记录都会用到）。
    private static final String INGESTION_SCOPE = "tourism_markdown_v1";
    // 记录已入库批次的状态表，防止重启后重复写入。
    private static final String INGESTION_STATE_TABLE = "knowledge_ingestion_batch_state";

    @Resource
    private TourismAppDocumentReader tourismAppDocumentReader;

    @Resource(name = "intentVectorStore")
    private VectorStore vectorStore;

    @Resource
    private JdbcTemplate jdbcTemplate;

    /**
     * 手动触发知识库入库：按 10 条分批写入，并保证同一批次只会入库一次。
     */
    public Map<String, Object> ingestNow() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 通过 PG advisory lock 保证同一时刻只有一个入库任务执行。
        if (!tryAcquireLock()) {
            result.put("success", false);
            result.put("message", "已有入库任务在执行，请稍后重试");
            return result;
        }

        try {
            // 先确保幂等状态表存在。
            ensureIngestionStateTable();

            List<Document> documents = tourismAppDocumentReader.loadMarkdowns();
            if (documents == null || documents.isEmpty()) {
                // 空文档直接返回成功，避免误判为系统异常。
                result.put("success", true);
                result.put("message", "未发现可入库文档");
                result.put("totalDocuments", 0);
                result.put("addedBatches", 0);
                result.put("skippedBatches", 0);
                return result;
            }

            int addedBatches = 0;
            int skippedBatches = 0;
            int addedDocuments = 0;

            for (int start = 0; start < documents.size(); start += VECTOR_STORE_ADD_BATCH_SIZE) {
                int end = Math.min(start + VECTOR_STORE_ADD_BATCH_SIZE, documents.size());
                List<Document> batch = documents.subList(start, end);
                // 基于内容和 metadata 生成批次指纹，作为幂等 key。
                String batchKey = buildBatchFingerprint(batch);

                // 如果该批次已处理过，则直接跳过。
                if (isBatchAlreadyIngested(batchKey)) {
                    skippedBatches++;
                    continue;
                }

                vectorStore.add(batch);
                // 入库成功后再写状态，确保“状态已写入”一定表示“向量已写入”。
                markBatchIngested(batchKey, batch.size());
                addedBatches++;
                addedDocuments += batch.size();
            }

            result.put("success", true);
            result.put("message", "手动入库完成");
            result.put("totalDocuments", documents.size());
            result.put("addedDocuments", addedDocuments);
            result.put("addedBatches", addedBatches);
            result.put("skippedBatches", skippedBatches);
            return result;
        } finally {
            // 无论成功失败都要释放锁，避免后续任务被卡住。
            releaseLock();
        }
    }

    /**
     * 初始化幂等状态表。
     */
    private void ensureIngestionStateTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS knowledge_ingestion_batch_state (
                    scope VARCHAR(128) NOT NULL,
                    batch_key VARCHAR(64) NOT NULL,
                    document_count INTEGER NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (scope, batch_key)
                )
                """);
    }

    /**
     * 检查当前批次是否已入库。
     */
    private boolean isBatchAlreadyIngested(String batchKey) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM " + INGESTION_STATE_TABLE + " WHERE scope = ? AND batch_key = ?",
                Integer.class,
                INGESTION_SCOPE,
                batchKey
        );
        return count != null && count > 0;
    }

    /**
     * 记录批次已入库（使用 UPSERT 保证并发下幂等）。
     */
    private void markBatchIngested(String batchKey, int documentCount) {
        jdbcTemplate.update(
                "INSERT INTO " + INGESTION_STATE_TABLE + " (scope, batch_key, document_count) VALUES (?, ?, ?) " +
                        "ON CONFLICT (scope, batch_key) DO NOTHING",
                INGESTION_SCOPE,
                batchKey,
                documentCount
        );
    }

    /**
     * 生成批次指纹：文本 + metadata 排序后拼接，再做 SHA-256。
     */
    private String buildBatchFingerprint(List<Document> batch) {
        StringBuilder raw = new StringBuilder(4096);
        for (Document document : batch) {
            raw.append(document.getText()).append('\n');
            if (!document.getMetadata().isEmpty()) {
                document.getMetadata().entrySet().stream()
                        // key 排序，避免 metadata 顺序不一致导致同内容不同 hash。
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> raw.append(entry.getKey()).append('=').append(entry.getValue()).append(';'));
            }
            raw.append("\n---\n");
        }
        return sha256(raw.toString());
    }

    /**
     * 计算字符串的 SHA-256 十六进制摘要。
     */
    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    /**
     * 尝试获取 PostgreSQL advisory lock（非阻塞）。
     */
    private boolean tryAcquireLock() {
        Boolean locked = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_lock(hashtext(?))",
                Boolean.class,
                INGESTION_SCOPE
        );
        return Boolean.TRUE.equals(locked);
    }

    /**
     * 释放 PostgreSQL advisory lock。
     */
    private void releaseLock() {
        jdbcTemplate.queryForObject(
                "SELECT pg_advisory_unlock(hashtext(?))",
                Boolean.class,
                INGESTION_SCOPE
        );
    }
}
