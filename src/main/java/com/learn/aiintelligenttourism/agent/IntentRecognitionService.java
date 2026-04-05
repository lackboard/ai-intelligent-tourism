package com.learn.aiintelligenttourism.agent;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.learn.aiintelligenttourism.config.IntentRecognitionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
/**
 * 统一意图识别入口：
 * 1) 先做前置 RAG 召回，拿到相似 query 样例；
 * 2) 将用户原句 + 召回样例拼入分类提示词；
 * 3) 调用低成本模型输出意图；
 * 4) 模型异常时走关键词兜底，保证链路可用。
 */
public class IntentRecognitionService {

    private final ChatClient defaultChatClient;
    private final IntentRagRecallService intentRagRecallService;
    private final IntentRecognitionProperties properties;
    private final Resource systemResource;

    public IntentRecognitionService(
            ChatClient defaultChatClient,
            IntentRagRecallService intentRagRecallService,
            IntentRecognitionProperties properties,
            @Value("classpath:/prompts/system-message-intention-judgment.st") Resource systemResource
    ) {
        this.defaultChatClient = defaultChatClient;
        this.intentRagRecallService = intentRagRecallService;
        this.properties = properties;
        this.systemResource = systemResource;
    }

    public IntentRecognitionResult recognize(String userMessage) {
        // Step 1: 先召回语义相近的历史/知识库样例，降低方言、反问、口语化表达带来的分类漂移。
        List<String> recalledCases = intentRagRecallService.recallIntentCases(userMessage);
        // Step 2: 将“原始问题 + 召回证据”合并为一个分类请求。
        String intentPrompt = buildIntentPrompt(userMessage, recalledCases);

        try {
            // Step 3: 使用配置化模型执行分类；模型可通过 yml 切换 qwen-turbo / qwen-plus。
            DashScopeChatOptions options = DashScopeChatOptions.builder()
                    .withModel(properties.getModel())
                    .build();

            String content = defaultChatClient.prompt()
                    .options(options)
                    .system(systemResource)
                    .user(intentPrompt)
                    .call()
                    .content();

            // Step 4: 对模型输出做强约束归一化，只保留 PLAN/POLICY/CHAT。
            String intent = extractIntent(content);
            return IntentRecognitionResult.of(intent, content, recalledCases);
        } catch (Exception e) {
            // Step 5: 模型不可用时降级，避免路由节点直接失败。
            log.error("Intent recognition failed, fallback to keyword rules", e);
            return fallbackByKeywords(userMessage, recalledCases, "fallback-keyword");
        }
    }

    private String buildIntentPrompt(String userMessage, List<String> recalledCases) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("用户原始输入:\n")
                .append(userMessage == null ? "" : userMessage)
                .append("\n\n");

        if (recalledCases == null || recalledCases.isEmpty()) {
            prompt.append("前置意图知识库召回样例: 无\n");
        } else {
            // 召回样例以编号列表传给分类模型，便于模型做“少样本比对”。
            prompt.append("前置意图知识库召回样例(仅供分类参考):\n");
            for (int i = 0; i < recalledCases.size(); i++) {
                prompt.append(i + 1).append(". ").append(recalledCases.get(i)).append("\n");
            }
        }

        prompt.append("\n请你结合用户输入与召回样例，严格输出 PLAN、POLICY、CHAT 之一。");
        return prompt.toString();
    }

    private String extractIntent(String content) {
        if (content == null || content.isBlank()) {
            return "CHAT";
        }

        // 兼容模型偶发返回解释性文本，只要包含目标标签子串就认定对应意图。
        String cleanResult = content.trim().toUpperCase().replaceAll("[^A-Z]", "");
        if (cleanResult.contains("PLAN")) {
            return "PLAN";
        }
        if (cleanResult.contains("POLICY")) {
            return "POLICY";
        }
        if (cleanResult.contains("CHAT")) {
            return "CHAT";
        }
        return "CHAT";
    }

    private IntentRecognitionResult fallbackByKeywords(String message, List<String> recalledCases, String rawModelOutput) {
        if (message == null || message.isBlank() || !properties.isKeywordFallbackEnabled()) {
            return IntentRecognitionResult.of("CHAT", rawModelOutput, recalledCases);
        }

        // 兜底顺序：先 POLICY 再 PLAN，尽量拦截强规则词（预约、开放、限流等）。
        if (containsAny(message, properties.getPolicyKeywords())) {
            return IntentRecognitionResult.of("POLICY", rawModelOutput, recalledCases);
        }
        if (containsAny(message, properties.getPlanKeywords())) {
            return IntentRecognitionResult.of("PLAN", rawModelOutput, recalledCases);
        }
        return IntentRecognitionResult.of("CHAT", rawModelOutput, recalledCases);
    }

    private boolean containsAny(String text, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}

