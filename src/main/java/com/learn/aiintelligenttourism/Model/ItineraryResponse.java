package com.learn.aiintelligenttourism.Model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor // 关键：提供无参构造，方便反序列化框架实例化
@AllArgsConstructor
public class ItineraryResponse {

    private String title;
    private List<DailyPlan> days;
    private double totalBudget;

    /**
     * 内部类也同样改成 Class
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyPlan {
        private int day;
        private String city;
        private List<String> activities;
        private String note;
    }
}