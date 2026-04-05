package com.learn.aiintelligenttourism.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 高德地图地理编码查询工具
 */
@Component
public class AmapTool {

    private static final String GEOCODE_API_URL = "https://restapi.amap.com/v3/geocode/geo";
    private static final String DISTANCE_API_URL = "https://restapi.amap.com/v3/distance";
    private static final String POI_SEARCH_API_URL = "https://restapi.amap.com/v3/place/text";
    private static final String POI_DETAIL_API_URL = "https://restapi.amap.com/v3/place/detail";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_ORIGIN_COUNT = 100;
    private static final Pattern COORDINATE_PATTERN = Pattern.compile("^\\s*(-?\\d+(?:\\.\\d{1,6})?)\\s*,\\s*(-?\\d+(?:\\.\\d{1,6})?)\\s*$");

    private final String apiKey;

    public AmapTool(@Value("${amap.geocode.key}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Tool(description = "Convert a structured address into concise geocode fields by AMap API, returning location/province/city/district.")
    public String geocode(
            @ToolParam(description = "Structured address.Rule compliance: country, province, city, district/county, " +
                    "town, village, street, house number, housing estate, buildingfor , " +
                    "example: 北京市朝阳区阜通东大街6号") String address,
            @ToolParam(description = "Optional city condition, for example: 北京") String city
    ) {
        if (address == null || address.trim().isEmpty()) {
            return "{\"error\": \"address 不能为空\"}";
        }

        try {
            HashMap<String, String> params = new HashMap<>();
            params.put("key", apiKey);
            params.put("address", address.trim());
            if (city != null && !city.trim().isEmpty()) {
                params.put("city", city.trim());
            }
            params.put("output", "JSON");

            String response = HttpApiClient.callApi(GEOCODE_API_URL, params);
            return simplifyGeocodeResponse(response);
        } catch (Exception e) {
            return String.format("{\"error\": \"高德地理编码查询失败: %s\"}", e.getMessage());
        }
    }

    @Tool(description = "Search for a specific Point of Interest (POI) by keyword and city.")
    public String searchPoi(
            @ToolParam(description = "POI keyword, e.g., 故宫博物院") String keyword,
            @ToolParam(description = "City name to search in, e.g., 北京") String city
    ) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return "{\"error\": \"keyword 不能为空\"}";
        }

        try {
            HashMap<String, String> params = new HashMap<>();
            params.put("key", apiKey);
            params.put("keywords", keyword.trim());
            if (city != null && !city.trim().isEmpty()) {
                params.put("city", city.trim());
            }
            params.put("offset", "1");
            params.put("page", "1");
            params.put("extensions", "base");
            params.put("output", "JSON");

            return HttpApiClient.callApi(POI_SEARCH_API_URL, params);
        } catch (Exception e) {
            return String.format("{\"error\": \"高德POI查询失败: %s\"}", e.getMessage());
        }
    }

    @Tool(description = "Measure distance from one or more origins to one destination by AMap API, returning concise result list with distance and duration.")
    public String distanceMeasure(
            @ToolParam(description = "One or more origin coordinates in format lng,lat. Multiple origins should be separated by '|'.") String origins,
            @ToolParam(description = "Destination coordinate in format lng,lat. Example: 116.434446,39.90816") String destination,
            @ToolParam(description = "Optional route type: 0 direct distance, 1 driving distance, 3 walking distance. Default is 1.") String type
    ) {
        if (origins == null || origins.trim().isEmpty()) {
            return "{\"error\":\"origins 不能为空\"}";
        }
        if (destination == null || destination.trim().isEmpty()) {
            return "{\"error\":\"destination 不能为空\"}";
        }

        String[] originArray = origins.split("\\|");
        if (originArray.length > MAX_ORIGIN_COUNT) {
            return "{\"error\":\"origins 最多支持 100 个坐标\"}";
        }
        for (String originItem : originArray) {
            if (isInvalidCoordinate(originItem)) {
                return "{\"error\":\"origins 格式错误，必须是 lng,lat 且可用 | 分隔多个坐标\"}";
            }
        }
        if (isInvalidCoordinate(destination)) {
            return "{\"error\":\"destination 格式错误，必须是 lng,lat 且经纬度范围合法\"}";
        }

        String normalizedType = normalizeType(type);
        if (normalizedType == null) {
            return "{\"error\":\"type 仅支持 0、1、3\"}";
        }

        try {
            HashMap<String, String> params = new HashMap<>();
            params.put("key", apiKey);
            params.put("origins", normalizeOrigins(originArray));
            params.put("destination", normalizeCoordinate(destination));
            params.put("type", normalizedType);
            params.put("output", "JSON");

            String response = HttpApiClient.callApi(DISTANCE_API_URL, params);
            return simplifyDistanceResponse(response);
        } catch (Exception e) {
            return String.format("{\"error\":\"高德距离测量查询失败: %s\"}", e.getMessage());
        }
    }

    @Tool(description = "Query POI business status and opening time by AMap POI id.")
    public String queryPoiBusinessStatus(
            @ToolParam(description = "AMap POI id, usually returned by searchPoi") String poiId
    ) {
        if (poiId == null || poiId.trim().isEmpty()) {
            return "{\"error\":\"poiId 不能为空\"}";
        }

        try {
            HashMap<String, String> params = new HashMap<>();
            params.put("key", apiKey);
            params.put("id", poiId.trim());
            params.put("extensions", "all");
            params.put("output", "JSON");

            String response = HttpApiClient.callApi(POI_DETAIL_API_URL, params);
            return simplifyPoiDetailResponse(response);
        } catch (Exception e) {
            return String.format("{\"error\":\"高德POI营业状态查询失败: %s\"}", e.getMessage());
        }
    }

    String simplifyGeocodeResponse(String response) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(response);
            String status = root.path("status").asText();
            if (!"1".equals(status)) {
                String info = root.path("info").asText("未知错误");
                return String.format("{\"error\":\"高德地理编码返回失败: %s\"}", info);
            }

            JsonNode geocodes = root.path("geocodes");
            if (!geocodes.isArray() || geocodes.isEmpty()) {
                return "{\"error\":\"未找到匹配的地理编码结果\"}";
            }

            JsonNode first = geocodes.get(0);
            String location = first.path("location").asText("");
            String province = first.path("province").asText("");
            String city = readCity(first.path("city"));
            String district = first.path("district").asText("");

            return String.format(
                    "{\"location\":\"%s\",\"province\":\"%s\",\"city\":\"%s\",\"district\":\"%s\"}",
                    escapeJson(location),
                    escapeJson(province),
                    escapeJson(city),
                    escapeJson(district)
            );
        } catch (JsonProcessingException e) {
            return String.format("{\"error\":\"解析高德返回失败: %s\"}", e.getOriginalMessage());
        }
    }

    String simplifyDistanceResponse(String response) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(response);
            String status = root.path("status").asText();
            if (!"1".equals(status)) {
                String info = root.path("info").asText("未知错误");
                return String.format("{\"error\":\"高德距离测量返回失败: %s\"}", escapeJson(info));
            }

            JsonNode results = root.path("results");
            if (!results.isArray() || results.isEmpty()) {
                return "{\"error\":\"未找到可用的距离结果\"}";
            }

            ObjectNode result = OBJECT_MAPPER.createObjectNode();
            result.put("status", status);
            result.put("info", root.path("info").asText(""));

            JsonNode first = results.get(0);
            ObjectNode firstResult = result.putObject("result");
            firstResult.put("origin_id", first.path("origin_id").asText(""));
            firstResult.put("dest_id", first.path("dest_id").asText(""));
            firstResult.put("distance", first.path("distance").asText(""));
            firstResult.put("duration", first.path("duration").asText(""));
            if (first.has("info")) {
                firstResult.put("info", first.path("info").asText(""));
            }
            if (first.has("code")) {
                firstResult.put("code", first.path("code").asText(""));
            }

            return OBJECT_MAPPER.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return String.format("{\"error\":\"解析高德距离测量返回失败: %s\"}", e.getOriginalMessage());
        }
    }

    String simplifyPoiDetailResponse(String response) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(response);
            String status = root.path("status").asText();
            if (!"1".equals(status)) {
                String info = root.path("info").asText("未知错误");
                return String.format("{\"error\":\"高德POI详情返回失败: %s\"}", escapeJson(info));
            }

            JsonNode pois = root.path("pois");
            if (!pois.isArray() || pois.isEmpty()) {
                return "{\"error\":\"未找到可用POI详情\"}";
            }

            JsonNode first = pois.get(0);
            ObjectNode result = OBJECT_MAPPER.createObjectNode();
            result.put("poi_id", first.path("id").asText(""));
            result.put("name", first.path("name").asText(""));
            result.put("address", first.path("address").asText(""));
            result.put("location", first.path("location").asText(""));
            result.put("tel", first.path("tel").asText(""));
            result.put("business_status", readBusinessStatus(first));
            result.put("open_time", readOpenTime(first));
            result.put("website", first.path("website").asText(""));
            result.put("source", "amap_place_detail_api");

            return OBJECT_MAPPER.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return String.format("{\"error\":\"解析高德POI详情返回失败: %s\"}", e.getOriginalMessage());
        }
    }

    private String normalizeOrigins(String[] origins) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < origins.length; i++) {
            if (i > 0) {
                builder.append("|");
            }
            builder.append(normalizeCoordinate(origins[i]));
        }
        return builder.toString();
    }

    private String normalizeType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return "1";
        }
        String value = type.trim();
        if ("0".equals(value) || "1".equals(value) || "3".equals(value)) {
            return value;
        }
        return null;
    }

    private boolean isInvalidCoordinate(String coordinate) {
        Matcher matcher = COORDINATE_PATTERN.matcher(coordinate);
        if (!matcher.matches()) {
            return true;
        }
        double lng = Double.parseDouble(matcher.group(1));
        double lat = Double.parseDouble(matcher.group(2));
        return !(lng >= -180 && lng <= 180 && lat >= -90 && lat <= 90);
    }

    private String normalizeCoordinate(String coordinate) {
        Matcher matcher = COORDINATE_PATTERN.matcher(coordinate);
        if (!matcher.matches()) {
            return coordinate.trim();
        }
        return matcher.group(1) + "," + matcher.group(2);
    }

    private String readCity(JsonNode cityNode) {
        if (cityNode == null || cityNode.isMissingNode() || cityNode.isNull()) {
            return "";
        }
        if (cityNode.isArray()) {
            if (cityNode.isEmpty()) {
                return "";
            }
            return cityNode.get(0).asText("");
        }
        return cityNode.asText("");
    }

    private String readBusinessStatus(JsonNode poi) {
        String fromRoot = poi.path("business_status").asText("");
        if (!fromRoot.isBlank()) {
            return fromRoot;
        }
        return poi.path("biz_ext").path("business_status").asText("");
    }

    private String readOpenTime(JsonNode poi) {
        JsonNode bizExt = poi.path("biz_ext");
        String openTime = bizExt.path("open_time").asText("");
        if (!openTime.isBlank()) {
            return openTime;
        }
        String today = bizExt.path("opentime_today").asText("");
        if (!today.isBlank()) {
            return today;
        }
        return bizExt.path("opentime_week").asText("");
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

