package com.learn.aiintelligenttourism.memory;

/**
 * L2 用户记忆的最小存储单元。
 */
public record ProfileMemoryFact(
        String key,
        String value,
        double confidence,
        String source
) {
}
