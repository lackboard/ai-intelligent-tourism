package com.learn.aiintelligenttourism.memory;

import com.alibaba.cloud.ai.graph.OverAllState;

/**
 * Graph 节点共享的记忆 prompt key。
 */
public final class MemoryPromptSupport {

    public static final String MEMORY_CONTEXT_PROMPT_KEY = "memoryContextPrompt";
    public static final String CURRENT_TURN_USER_MESSAGE_PERSISTED_KEY = "currentTurnUserMessagePersisted";

    private MemoryPromptSupport() {
    }

    public static String fromState(OverAllState state) {
        if (state == null) {
            return "";
        }
        Object raw = state.data().get(MEMORY_CONTEXT_PROMPT_KEY);
        return raw == null ? "" : raw.toString();
    }

    /**
     * 新会话通过 stream(initialInput, config) 启动时，当前用户消息只保证本轮执行可见，
     * 不保证会自动进入最终 checkpoint。恢复中断时则会先用 updateState(...) 持久化用户消息。
     * 这里统一把该差异抽成一个布尔标记，供各节点决定是否需要把用户输入和助手回复一起写回 L1。
     */
    public static boolean isCurrentTurnUserMessagePersisted(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text.trim());
        }
        return false;
    }
}
