package com.learn.aiintelligenttourism.memory;

/**
 * 统一维护三层记忆里的窗口类常量，避免不同链路各写一套数字。
 */
public final class MemoryConstants {

    /**
     * L1 工作记忆窗口统一固定为 20 条消息。
     * 这里的“条”指用户消息或助手消息各算一条。
     */
    public static final int WORKING_MEMORY_WINDOW_SIZE = 20;

    private MemoryConstants() {
    }
}
