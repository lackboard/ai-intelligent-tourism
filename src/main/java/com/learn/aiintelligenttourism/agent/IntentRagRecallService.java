package com.learn.aiintelligenttourism.agent;

import com.learn.aiintelligenttourism.config.IntentRecognitionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
/**
 * 意图前置召回服务：
 * - 从向量库召回与用户 query 最相似的意图样例；
 * - 将样例压缩成短文本，作为后续分类模型的“判定证据”。
 */
public class IntentRagRecallService {

    private final VectorStore intentVectorStore;
    private final IntentRecognitionProperties properties;

    public IntentRagRecallService(
            @Qualifier("intentVectorStore") VectorStore intentVectorStore,
            IntentRecognitionProperties properties
    ) {
        this.intentVectorStore = intentVectorStore;
        this.properties = properties;
    }

    public List<String> recallIntentCases(String userMessage) {
        // 允许通过配置快速关闭召回链路，便于线上排障/AB 实验。
        if (!properties.isRagEnabled() || userMessage == null || userMessage.isBlank()) {
            return List.of();
        }

        try {
            // topK + threshold 全部走配置，避免硬编码影响不同业务域调参。
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(userMessage)
                    .topK(properties.getTopK())
                    .similarityThreshold(properties.getSimilarityThreshold())
                    .build();

            List<Document> documents = intentVectorStore.similaritySearch(searchRequest);
            if (documents == null || documents.isEmpty()) {
                return List.of();
            }

            // LinkedHashSet: 去重且保留召回顺序，尽量维持“最相关在前”。
            Set<String> deduplicatedCases = new LinkedHashSet<>();
            for (Document document : documents) {
                // 控制单条样例长度，防止 prompt 过长稀释用户问题。
                String normalizedText = normalizeText(document.getText(), properties.getMaxCaseLength());
                if (normalizedText.isBlank()) {
                    continue;
                }

                // 支持两种常见 metadata 字段名，兼容不同入库脚本。
                Object intent = document.getMetadata().get("intent");
                if (intent == null) {
                    intent = document.getMetadata().get("intent_label");
                }

                if (intent != null && !String.valueOf(intent).isBlank()) {
                    deduplicatedCases.add("[intent=" + intent + "] " + normalizedText);
                } else {
                    deduplicatedCases.add(normalizedText);
                }

                if (deduplicatedCases.size() >= properties.getTopK()) {
                    break;
                }
            }

            return new ArrayList<>(deduplicatedCases);
        } catch (Exception e) {
            // 召回失败不阻断主流程，让上层走“纯 LLM 分类 + 规则兜底”。
            log.warn("Intent RAG recall failed, fallback to pure LLM classification", e);
            return List.of();
        }
    }

    private String normalizeText(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }
}

