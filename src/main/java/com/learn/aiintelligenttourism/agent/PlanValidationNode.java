package com.learn.aiintelligenttourism.agent;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.aiintelligenttourism.Model.ItineraryResponse;
import com.learn.aiintelligenttourism.Model.TravelRequirements;
import com.learn.aiintelligenttourism.tools.AmapTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class PlanValidationNode implements NodeActionWithConfig {

    private static final int MAX_RETRY = 1;
    private static final int GENERAL_MAX_SINGLE_DRIVE_MINUTES = 150;
    private static final int SPECIAL_MAX_SINGLE_DRIVE_MINUTES = 120;
    private static final int GENERAL_MAX_DAILY_DRIVE_MINUTES = 240;
    private static final int SPECIAL_MAX_DAILY_DRIVE_MINUTES = 180;
    private static final Pattern TIME_RANGE_PATTERN = Pattern.compile("(\\d{1,2}:\\d{2})\\s*-\\s*(\\d{1,2}:\\d{2})");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm");
    private static final Pattern NON_PHYSICAL_KEYWORD_PATTERN = Pattern.compile(".*(返程|出发|回程|酒店|休息|自由活动|睡觉|高铁|机场|火车站|打车|大巴).*");
    private static final Pattern MEAL_KEYWORD_PATTERN = Pattern.compile(".*(午餐|晚餐|早餐|早饭|中餐|晚饭|夜宵|小吃|咖啡|茶歇|甜品|餐厅|饭店|火锅|烧烤|面|菜|美食).*");

    private final AmapTool amapTool;
    private final ObjectMapper objectMapper;

    public PlanValidationNode(AmapTool amapTool, ObjectMapper objectMapper) {
        this.amapTool = amapTool;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) {
        log.info(">>> 进入节点: PlanValidationNode (基于地图 API 的物理逻辑校验)");

        ItineraryResponse itinerary = state.value("itinerary")
                .map(v -> (ItineraryResponse) v)
                .orElse(null);
        TravelRequirements req = state.value("travelRequirements")
                .map(v -> (TravelRequirements) v)
                .orElse(null);
        int retryCount = state.value("retryCount")
                .map(this::toInt)
                .orElse(0);

        if (itinerary == null || req == null) {
            return Map.of(
                    "validationPassed", false,
                    "validationFeedback", "未能获取有效行程或用户需求",
                    "next_node", "end",
                    "retryCount", retryCount
            );
        }

        boolean hasElderlyOrKids = req.preference() != null
                && (req.preference().contains("老人") || req.preference().contains("孩子") || req.preference().contains("亲子"));

        if (itinerary.getDays() == null || itinerary.getDays().isEmpty()) {
            return Map.of(
                    "validationPassed", false,
                    "validationFeedback", "模型未生成有效日程（days 为空），请重新生成。",
                    "next_node", retryCount < MAX_RETRY ? "plan_generator" : "end",
                    "retryCount", retryCount < MAX_RETRY ? retryCount + 1 : retryCount
            );
        }

        List<String> violations = new ArrayList<>();
        Map<String, GeoPoint> geocodeCache = new HashMap<>();

        for (ItineraryResponse.DailyPlan day : itinerary.getDays()) {
            List<ItineraryResponse.ActivityItem> activities = day.getActivities();
            if (activities == null || activities.isEmpty()) {
                continue;
            }

            int dailyDrivingMinutes = 0;
            int dailyDrivingLimit = hasElderlyOrKids ? SPECIAL_MAX_DAILY_DRIVE_MINUTES : GENERAL_MAX_DAILY_DRIVE_MINUTES;
            boolean dayHasTimeWarning = false;

            for (ItineraryResponse.ActivityItem activity : activities) {
                String searchKeyword = getSearchKeyword(activity);
                if (shouldSkipLocationValidation(activity, searchKeyword)) {
                    log.info("跳过非强校验地点: {}", searchKeyword);
                    continue;
                }

                GeoPoint point = resolveGeo(searchKeyword, day.getCity(), geocodeCache);
                if (point == null) {
                    violations.add(String.format(
                            "Day %d: 地点【%s】无法完成地理编码，请简化地点名称或提供更精确地址。",
                            day.getDay(), activity.getLocation()
                    ));
                }
            }

            for (int i = 0; i < activities.size() - 1; i++) {
                ItineraryResponse.ActivityItem current = activities.get(i);
                ItineraryResponse.ActivityItem next = activities.get(i + 1);
                String currKey = getSearchKeyword(current);
                String nextKey = getSearchKeyword(next);

                if (shouldSkipLocationValidation(current, currKey) || shouldSkipLocationValidation(next, nextKey)) {
                    continue;
                }

                GeoPoint currPoint = resolveGeo(currKey, day.getCity(), geocodeCache);
                GeoPoint nextPoint = resolveGeo(nextKey, day.getCity(), geocodeCache);
                if (currPoint == null || nextPoint == null) {
                    continue;
                }

                double straightDistance = calculateStraightDistance(currPoint.location, nextPoint.location);
                Integer drivingMinutes = queryDrivingMinutes(currPoint.location, nextPoint.location);
                if (drivingMinutes == null) {
                    if (straightDistance <= 3000) {
                        drivingMinutes = Math.max(1, (int) (straightDistance / 80));
                        log.info("Day {}: 无法计算【{}】到【{}】通勤耗时，但直线距离仅 {} 米，自动估算为 {} 分钟。",
                                day.getDay(), currKey, nextKey, Math.round(straightDistance), drivingMinutes);
                    } else {
                        violations.add(String.format(
                                "Day %d: 无法计算【%s】到【%s】通勤耗时，且距离偏远，请调整地点描述后重试。",
                                day.getDay(), currKey, nextKey
                        ));
                        continue;
                    }
                }

                dailyDrivingMinutes += drivingMinutes;

                int singleTripLimit = hasElderlyOrKids ? SPECIAL_MAX_SINGLE_DRIVE_MINUTES : GENERAL_MAX_SINGLE_DRIVE_MINUTES;
                if (drivingMinutes > singleTripLimit) {
                    violations.add(String.format(
                            "Day %d: 从【%s】到【%s】预计通勤 %d 分钟，超过单段上限 %d 分钟。",
                            day.getDay(), currKey, nextKey, drivingMinutes, singleTripLimit
                    ));
                }

                if (currPoint.city != null && nextPoint.city != null
                        && !currPoint.city.isBlank() && !nextPoint.city.isBlank()
                        && !currPoint.city.equals(nextPoint.city)) {
                    violations.add(String.format(
                            "Day %d: 同一天出现跨城跳点【%s -> %s】（%s -> %s），建议拆分到不同天。",
                            day.getDay(), currKey, nextKey, currPoint.city, nextPoint.city
                    ));
                }

                Integer currentEnd = parseEndMinutes(current.getTime());
                Integer nextStart = parseStartMinutes(next.getTime());
                if (currentEnd != null && nextStart != null && currentEnd + drivingMinutes > nextStart) {
                    dayHasTimeWarning = true;
                    log.warn("Day {}: 时间衔接偏紧【{} -> {}】。", day.getDay(), currKey, nextKey);
                }
            }

            if (dailyDrivingMinutes > dailyDrivingLimit) {
                violations.add(String.format(
                        "Day %d: 当日累计通勤 %d 分钟，超过上限 %d 分钟，请减少远距离景点。",
                        day.getDay(), dailyDrivingMinutes, dailyDrivingLimit
                ));
            }

            if (dayHasTimeWarning) {
                String existingNote = day.getNote() == null ? "" : day.getNote();
                String timeWarningTip = "温馨提示：受城市交通路况影响，部分通勤耗时可能偏长，建议根据实际情况灵活调整节奏，行程时间仅供参考。";
                day.setNote(existingNote + (existingNote.isBlank() ? "" : "\n") + timeWarningTip);
            }
        }

        Map<String, Object> outputs = new HashMap<>();
        boolean isPassed = violations.isEmpty();
        outputs.put("validationPassed", isPassed);
        outputs.put("retryCount", retryCount);

        if (isPassed) {
            outputs.put("next_node", "end");
            log.info("行程物理逻辑校验通过");
        } else {
            String feedback = String.join("\n", violations);
            outputs.put("validationFeedback", feedback);
            if (retryCount < MAX_RETRY) {
                outputs.put("retryCount", retryCount + 1);
                outputs.put("next_node", "plan_generator");
            } else {
                outputs.put("next_node", "end");
                outputs.put("validationFeedback", feedback + "\n已达到自动修正上限，返回当前最优版本。");
            }
            log.warn("行程校验未通过，打回意见:\n{}", outputs.get("validationFeedback"));
        }

        return outputs;
    }

    private String getSearchKeyword(ItineraryResponse.ActivityItem activity) {
        return activity.getPoiName() != null && !activity.getPoiName().isBlank()
                ? activity.getPoiName()
                : activity.getLocation();
    }

    private boolean shouldSkipLocationValidation(ItineraryResponse.ActivityItem activity, String searchKeyword) {
        if (searchKeyword == null || searchKeyword.isBlank()) {
            return true;
        }
        if ("NON_PHYSICAL".equalsIgnoreCase(searchKeyword)) {
            return true;
        }
        if (NON_PHYSICAL_KEYWORD_PATTERN.matcher(searchKeyword).matches()) {
            return true;
        }

        String type = activity.getType();
        if (type != null) {
            String normalizedType = type.trim().toLowerCase();
            if ("meal".equals(normalizedType) || "break".equals(normalizedType)) {
                return true;
            }
        }

        return MEAL_KEYWORD_PATTERN.matcher(searchKeyword).matches();
    }

    private GeoPoint resolveGeo(String location, String cityHint, Map<String, GeoPoint> cache) {
        if (location == null || location.isBlank()) {
            return null;
        }

        String searchKeyword = location;
        Matcher matcher = Pattern.compile("[《【\\(（](.*?)[》】\\)）]").matcher(location);
        if (matcher.find()) {
            searchKeyword = matcher.group(1);
        } else {
            searchKeyword = searchKeyword.replace("周边", "").replace("附近", "");
        }

        if (cache.containsKey(searchKeyword)) {
            return cache.get(searchKeyword);
        }

        try {
            String poiResponse = amapTool.searchPoi(searchKeyword, cityHint);
            if (poiResponse != null && !poiResponse.contains("\"error\":")) {
                JsonNode poiRoot = objectMapper.readTree(poiResponse);
                if (poiRoot.has("pois") && poiRoot.path("pois").isArray() && poiRoot.path("pois").size() > 0) {
                    JsonNode firstPoi = poiRoot.path("pois").get(0);
                    String geoLocation = firstPoi.path("location").asText("");
                    String city = firstPoi.path("cityname").asText("");
                    if (!geoLocation.isBlank()) {
                        GeoPoint point = new GeoPoint(geoLocation, city);
                        cache.put(searchKeyword, point);
                        cache.put(location, point);
                        log.info("POI 搜索成功 [{}] -> {}", searchKeyword, geoLocation);
                        return point;
                    }
                }
            }

            String response = amapTool.geocode(searchKeyword, cityHint);
            JsonNode root = objectMapper.readTree(response);
            if (root.has("error")) {
                return null;
            }

            String geoLocation = root.path("location").asText("");
            if (geoLocation.isBlank()) {
                return null;
            }

            String city = root.path("city").asText("");
            GeoPoint point = new GeoPoint(geoLocation, city);
            cache.put(searchKeyword, point);
            cache.put(location, point);
            log.info("Geocode 成功 [{}] -> {}", searchKeyword, geoLocation);
            return point;
        } catch (Exception e) {
            log.warn("geocode 失败 location={} error={}", location, e.getMessage());
            return null;
        }
    }

    private Integer queryDrivingMinutes(String origin, String destination) {
        try {
            String response = amapTool.distanceMeasure(origin, destination, "1");
            JsonNode root = objectMapper.readTree(response);
            if (root.has("error")) {
                return null;
            }
            String durationText = root.path("result").path("duration").asText("");
            if (durationText.isBlank()) {
                return null;
            }
            int durationSeconds = Integer.parseInt(durationText);
            return Math.max(1, (durationSeconds + 59) / 60);
        } catch (Exception e) {
            log.warn("distance 失败 origin={} destination={} error={}", origin, destination, e.getMessage());
            return null;
        }
    }

    private Integer parseStartMinutes(String timeRange) {
        return parseTimePart(timeRange, 1);
    }

    private Integer parseEndMinutes(String timeRange) {
        return parseTimePart(timeRange, 2);
    }

    private Integer parseTimePart(String timeRange, int group) {
        if (timeRange == null || timeRange.isBlank()) {
            return null;
        }
        Matcher matcher = TIME_RANGE_PATTERN.matcher(timeRange);
        if (!matcher.find()) {
            return null;
        }
        try {
            LocalTime time = LocalTime.parse(matcher.group(group), TIME_FORMATTER);
            return time.getHour() * 60 + time.getMinute();
        } catch (Exception e) {
            return null;
        }
    }

    private Integer toInt(Object value) {
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private double calculateStraightDistance(String loc1, String loc2) {
        try {
            String[] p1 = loc1.split(",");
            String[] p2 = loc2.split(",");
            double lon1 = Math.toRadians(Double.parseDouble(p1[0]));
            double lat1 = Math.toRadians(Double.parseDouble(p1[1]));
            double lon2 = Math.toRadians(Double.parseDouble(p2[0]));
            double lat2 = Math.toRadians(Double.parseDouble(p2[1]));

            double radius = 6371000;
            double dLat = lat2 - lat1;
            double dLon = lon2 - lon1;
            double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                    + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
            return radius * c;
        } catch (Exception e) {
            log.warn("直线距离计算失败: loc1={}, loc2={}", loc1, loc2);
            return Double.MAX_VALUE;
        }
    }

    private static class GeoPoint {
        private final String location;
        private final String city;

        private GeoPoint(String location, String city) {
            this.location = location;
            this.city = city;
        }
    }
}
