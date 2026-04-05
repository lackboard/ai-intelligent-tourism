package com.learn.aiintelligenttourism.agent;

import java.util.List;

/**
 * 意图识别结果对象：
 * - intent: 归一化后的意图标签（PLAN/POLICY/CHAT）
 * - nextNode: Graph 下一跳节点
 * - rawModelOutput: 原始模型输出，便于线上排查 bad case
 * - recalledCases: 本次用于判定的召回样例
 */
public record IntentRecognitionResult(
        String intent,
        String nextNode,
        String rawModelOutput,
        List<String> recalledCases
) {

    public static IntentRecognitionResult of(String intent, String rawModelOutput, List<String> recalledCases) {
        // 统一在工厂方法里做收口，避免调用方遗漏标准化逻辑。
        String normalized = normalizeIntent(intent);
        return new IntentRecognitionResult(normalized, toNextNode(normalized), rawModelOutput, recalledCases == null ? List.of() : recalledCases);
    }

    public boolean isPlan() {
        return "PLAN".equals(intent);
    }

    private static String normalizeIntent(String intent) {
        if (intent == null || intent.isBlank()) {
            return "CHAT";
        }
        // 强制白名单标签，任何未知结果都收敛到 CHAT。
        String normalized = intent.trim().toUpperCase();
        return switch (normalized) {
            case "PLAN", "POLICY", "CHAT" -> normalized;
            default -> "CHAT";
        };
    }

    private static String toNextNode(String intent) {
        // 在这里定义“意图 -> 节点”的唯一映射，避免散落在多个节点里。
        return switch (intent) {
            case "PLAN" -> "circular_information_extractor";
            case "POLICY" -> "policy_node";
            default -> "simple_chat";
        };
    }
}

