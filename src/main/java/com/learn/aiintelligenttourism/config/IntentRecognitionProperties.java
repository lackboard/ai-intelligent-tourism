package com.learn.aiintelligenttourism.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "intent-recognition")
/**
 * 意图识别配置：
 * - 控制模型、召回参数、兜底开关；
 * - 通过 yml 热调整，避免频繁改代码。
 */
public class IntentRecognitionProperties {

    /**
     * 意图识别模型，建议使用低成本模型配合前置 RAG。
     */
    private String model = "qwen-flash";

    /**
     * 是否启用前置意图 RAG 召回。
     */
    private boolean ragEnabled = true;

    /**
     * 意图知识库召回数量。
     */
    private int topK = 6;

    /**
     * 意图知识库召回相似度阈值。
     */
    private double similarityThreshold = 0.55;

    /**
     * 单条召回样例最大长度，避免提示词膨胀。
     */
    private int maxCaseLength = 80;

    /**
     * 意图识别失败时是否启用关键词兜底。
     */
    private boolean keywordFallbackEnabled = true;

    /**
     * PLAN 兜底关键词。
     */
    private List<String> planKeywords = new ArrayList<>(List.of("规划", "行程", "安排", "路线", "几日游", "攻略计划"));

    /**
     * POLICY 兜底关键词。
     */
    private List<String> policyKeywords = new ArrayList<>(List.of("政策", "预约", "开放", "闭园", "限流", "入园", "通行", "退改", "门票规则"));
}

