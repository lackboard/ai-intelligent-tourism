package com.learn.aiintelligenttourism.Model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TravelRequirements(
    String destination, // 目的地
    String travelDate,  // 出行时间
    String budget,      // 预算 (可选)
    String preference   // 偏好 (可选，如特种兵、亲子)
) {
    // 辅助方法：判断关键信息是否缺失
    public boolean isMissingCriticalInfo() {
        return destination == null || destination.isBlank() || 
               travelDate == null || travelDate.isBlank();
    }
}