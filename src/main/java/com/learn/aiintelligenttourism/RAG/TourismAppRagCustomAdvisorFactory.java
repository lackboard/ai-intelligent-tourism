package com.learn.aiintelligenttourism.RAG;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.TranslationQueryTransformer;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TourismAppRagCustomAdvisorFactory {


    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private DashScopeChatModel dashscopeChatModel;

    public static Advisor createTourismAppRagCustomAdvisor(VectorStore vectorStore,ChatModel chatModel) {
        DocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                //.filterExpression(expression) // 过滤条件
                .similarityThreshold(0.5) // 相似度阈值
                .topK(4) // 返回文档数量
                .build();
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(true)
                        .build())
                .queryTransformers(TranslationQueryTransformer.builder()
                        .chatClientBuilder(ChatClient.builder(chatModel))
                        .targetLanguage("chinese")
                        .build())
                .build();
    }

    @Bean
    public Advisor tourismAppRagCustomAdvisor() {
        return createTourismAppRagCustomAdvisor(vectorStore, dashscopeChatModel);
    }





}
