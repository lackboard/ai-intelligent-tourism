package com.learn.aiintelligenttourism.memory;

import java.util.List;

/**
 * L3 长期记忆的读模型，专门给 prompt 组装层使用。
 */
public record LongTermMemoryItem(
        String memoryId,
        String eventType,
        String title,
        String summary,
        List<String> tags,
        double importance
) {
}
