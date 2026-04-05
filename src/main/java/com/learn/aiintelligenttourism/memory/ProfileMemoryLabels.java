package com.learn.aiintelligenttourism.memory;

import java.util.Map;

/**
 * 统一维护 profile key 和中文展示名，避免 prompt 拼装处散落硬编码。
 */
public final class ProfileMemoryLabels {

    private static final Map<String, String> LABELS = Map.of(
            "travel_pace", "出行节奏",
            "budget_range", "预算偏好",
            "hotel_preference", "住宿偏好",
            "interest_tags", "兴趣主题",
            "transport_preference", "交通偏好"
    );

    private ProfileMemoryLabels() {
    }

    public static String labelOf(String key) {
        return LABELS.getOrDefault(key, key);
    }
}
