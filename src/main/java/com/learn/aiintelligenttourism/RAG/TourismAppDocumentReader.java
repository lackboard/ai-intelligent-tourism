package com.learn.aiintelligenttourism.RAG;

import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.document.Document;

import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 恋爱大师应用文档加载器
 */
@Component
@Slf4j
public class TourismAppDocumentReader {


    private final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    /**
     * 读取多篇markdown文档
     * @return void
     */
    public List<Document> loadMarkdowns(){
        List<Document> documents = new ArrayList<Document>();

        try {
            Resource[] resources = resolver.getResources("classpath*:document/*.md");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                assert filename != null;
                // 1. 提取 Front Matter 元数据 (title, category, location, etc.)
                Map<String, Object> additionalMetadata = extractFrontMatter(resource);
                additionalMetadata.put("filename", filename);
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        // 不加载 代码块
                        .withIncludeCodeBlock(false)
                        // 不加载 引用块
                        .withIncludeBlockquote(false)
                        // 添加从文件头获取的元数据
                        .withAdditionalMetadata(additionalMetadata)
                        .build();
                MarkdownDocumentReader markdownDocumentReader = new MarkdownDocumentReader(resource, config);
                // 3. 获取文档片段
                List<Document> resourceDocuments = markdownDocumentReader.get();
                documents.addAll(resourceDocuments);


            }
        } catch (IOException e) {
            log.error("markdown 文档加载失败",e);
        }
        return documents;

    }

    /**
     * 手动解析 Markdown 顶部的 YAML Front Matter
     * 格式示例：
     * ---
     * title: xxx
     * category: xxx
     * ---
     */
    private Map<String, Object> extractFrontMatter(Resource resource) {
        Map<String, Object> metadata = new HashMap<>();



        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine();

            // 检查第一行是否以 --- 开头
            if (line == null || !line.trim().equals("---")) {
                return metadata; // 没有 Front Matter，直接返回空 Map
            }

            // 循环读取直到遇到下一个 ---
            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();

                // 遇到结束符 --- 停止解析
                if (trimmedLine.equals("---")) {
                    break;
                }

                // 简单的 key: value 解析
                int colonIndex = trimmedLine.indexOf(':');
                if (colonIndex > 0) {
                    String key = trimmedLine.substring(0, colonIndex).trim();
                    // 处理值可能包含冒号的情况，或者只是简单的字符串
                    String value = trimmedLine.substring(colonIndex + 1).trim();

                    // 这里不做复杂的类型转换，全部存为 String，AI 模型通常能理解
                    if (!key.isEmpty() && !value.isEmpty()) {
                        metadata.put(key, value);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析 Front Matter 失败: {}", resource.getFilename(), e);
        }

        return metadata;
    }
}
