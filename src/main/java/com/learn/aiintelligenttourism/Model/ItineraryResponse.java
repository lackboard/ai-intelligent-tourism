package com.learn.aiintelligenttourism.Model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItineraryResponse {

    private String title;
    private List<DailyPlan> days;
    private double totalBudget;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyPlan {
        private int day;
        private String city;
        private List<ActivityItem> activities;
        private String note;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityItem {
        private String time;
        private String location;     // 前端展示用，例如："文殊院周边「洞子口张老二凉粉」"
        private String poiName;      // 新增：专门给地图API搜索用的纯净地名，例如："洞子口张老二凉粉" 或 "文殊院"
        private String description;
        private String type;
        private double cost;
    }
}
