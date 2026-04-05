package com.learn.aiintelligenttourism.memory;

import java.util.List;

/**
 * L1 调试视图。
 * source 用来说明这份工作记忆来自 JDBC ChatMemory 还是 Graph checkpoint。
 */
public record WorkingMemorySnapshot(
        String source,
        String summary,
        List<WorkingMemoryMessageView> recentMessages
) {

    public boolean hasContent() {
        return (summary != null && !summary.isBlank())
                || (recentMessages != null && !recentMessages.isEmpty());
    }

    public static WorkingMemorySnapshot empty(String source) {
        return new WorkingMemorySnapshot(source, "", List.of());
    }
}
