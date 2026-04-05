package com.learn.aiintelligenttourism.memory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * L3 落库后的原始视图，方便测试时核对“写入内容”和“召回内容”是否一致。
 */
public record StoredLongTermMemoryView(
        String memoryId,
        String threadId,
        String memoryType,
        String eventType,
        String title,
        String summary,
        List<String> tags,
        double importance,
        String source,
        LocalDateTime createdAt
) {
}
