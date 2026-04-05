package com.learn.aiintelligenttourism.agent;

import com.alibaba.cloud.ai.graph.KeyStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话消息只保留最近 N 条，避免 Graph checkpoint 随着会话增长无限膨胀。
 * 这里同时兼容单条 Message 和 List<Message> 两种写法，便于节点按需返回。
 */
public class BoundedMessageAppendStrategy implements KeyStrategy {

    private final int maxMessages;

    public BoundedMessageAppendStrategy(int maxMessages) {
        this.maxMessages = maxMessages;
    }

    @Override
    public Object apply(Object oldValue, Object newValue) {
        List<Object> merged = new ArrayList<>();
        append(merged, oldValue);
        append(merged, newValue);

        if (merged.size() <= maxMessages) {
            return merged;
        }
        return new ArrayList<>(merged.subList(merged.size() - maxMessages, merged.size()));
    }

    private void append(List<Object> target, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof List<?> list) {
            target.addAll(list);
            return;
        }
        target.add(value);
    }
}
