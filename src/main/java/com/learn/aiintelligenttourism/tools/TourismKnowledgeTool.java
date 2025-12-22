package com.learn.aiintelligenttourism.tools;

import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Component;
import java.util.stream.Collectors;

@Component
public class TourismKnowledgeTool {

    private final VectorStore vectorStore;

    public TourismKnowledgeTool(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Tool(description = "查询目的地的旅游攻略、避坑指南、景点介绍或当地政策。")
    public String searchTravelGuide(@ToolParam(description = "查询关键词，如'京都避坑'、'长滩岛签证'") String query) {
        // 执行向量检索
        SearchRequest requestQuery = SearchRequest.builder()
                .query(query)
                .topK(3)                  // 返回最相似的5个结果
                .similarityThreshold(0.7) // 相似度阈值，0.0-1.0之间
                .build();
        var results = vectorStore.similaritySearch(requestQuery);

        assert results != null;
        if (results.isEmpty()) {
            return "未找到相关攻略信息。";
        }
        
        // 将检索到的文档拼接成字符串返回给 Agent
        return results.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));
    }
}