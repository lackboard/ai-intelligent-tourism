package com.learn.aiintelligenttourism.memory;

import java.util.List;
import java.util.Locale;

/**
 * 三层记忆读取结果。
 * L1 是工作记忆摘要，L2 是稳定画像，L3 是召回到的历史经验。
 */
public record MemoryContext(
        String workingMemorySummary,
        List<ProfileMemoryFact> profileFacts,
        List<LongTermMemoryItem> longTermMemories
) {

    public boolean isEmpty() {
        return isBlank(workingMemorySummary)
                && (profileFacts == null || profileFacts.isEmpty())
                && (longTermMemories == null || longTermMemories.isEmpty());
    }

    public String toSystemPrompt() {
        if (isEmpty()) {
            return "";
        }

        StringBuilder prompt = new StringBuilder(512);
        prompt.append("""
                以下是系统为当前访客整理的三层记忆，只在与当前问题相关时参考：
                1. 当前用户这次消息的显式要求优先级最高。
                2. 历史偏好只能作为参考，不能覆盖当前用户的新要求。
                3. 如果记忆和当前输入冲突，以当前输入为准。

                """);

        if (!isBlank(workingMemorySummary)) {
            prompt.append("### L1 工作记忆\n");
            prompt.append(workingMemorySummary).append("\n\n");
        }

        if (profileFacts != null && !profileFacts.isEmpty()) {
            prompt.append("### L2 用户记忆\n");
            for (ProfileMemoryFact fact : profileFacts) {
                prompt.append("- ")
                        .append(ProfileMemoryLabels.labelOf(fact.key()))
                        .append(": ")
                        .append(fact.value())
                        .append(" (置信度 ")
                        .append(String.format(Locale.ROOT, "%.2f", fact.confidence()))
                        .append(")\n");
            }
            prompt.append('\n');
        }

        if (longTermMemories != null && !longTermMemories.isEmpty()) {
            prompt.append("### L3 长期记忆\n");
            for (LongTermMemoryItem item : longTermMemories) {
                prompt.append("- [")
                        .append(item.title())
                        .append("]");
                if (item.eventType() != null && !item.eventType().isBlank()) {
                    prompt.append(" (").append(item.eventType()).append(")");
                }
                prompt.append(' ')
                        .append(item.summary());
                if (item.tags() != null && !item.tags().isEmpty()) {
                    prompt.append(" | tags=").append(String.join("、", item.tags()));
                }
                prompt.append('\n');
            }
        }

        return prompt.toString().trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
