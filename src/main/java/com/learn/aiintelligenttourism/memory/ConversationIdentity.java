package com.learn.aiintelligenttourism.memory;

/**
 * 对话身份最小集合。
 * threadId 负责隔离单次会话，visitorId 负责聚合同一访客的长期记忆。
 */
public record ConversationIdentity(String visitorId, String threadId) {

    public ConversationIdentity {
        if (visitorId == null || visitorId.isBlank()) {
            throw new IllegalArgumentException("visitorId 不能为空");
        }
        if (threadId == null || threadId.isBlank()) {
            throw new IllegalArgumentException("threadId 不能为空");
        }
        visitorId = visitorId.trim();
        threadId = threadId.trim();
    }
}
