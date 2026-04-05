package com.learn.aiintelligenttourism.memory;

/**
 * 给调试接口使用的 L1 明细视图。
 */
public record WorkingMemoryMessageView(
        String type,
        String text
) {
}
