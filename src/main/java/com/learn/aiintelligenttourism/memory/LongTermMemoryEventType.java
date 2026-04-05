package com.learn.aiintelligenttourism.memory;

/**
 * L3 长期记忆的最小事件集合。
 *
 * <p>这版只保留四类高价值事件：
 * 1. itinerary_generated: 系统刚刚生成了一份行程
 * 2. itinerary_accepted: 用户明确接受了一份行程
 * 3. itinerary_rejected: 用户明确否定了一份行程，并给出调整方向
 * 4. session_closed: 一段有信息量的会话结束了
 *
 * <p>后续如果要扩展成更完整的记忆系统，可以继续补充 major_correction、
 * preference_change 等事件类型，但当前 MVP 先保持收敛。
 */
public enum LongTermMemoryEventType {

    ITINERARY_GENERATED("itinerary_generated"),
    ITINERARY_ACCEPTED("itinerary_accepted"),
    ITINERARY_REJECTED("itinerary_rejected"),
    SESSION_CLOSED("session_closed");

    private final String code;

    LongTermMemoryEventType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
