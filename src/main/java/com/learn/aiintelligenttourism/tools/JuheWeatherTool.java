package com.learn.aiintelligenttourism.tools;


import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;

/**
 * 聚合数据天气查询工具
 */
@Component
@Slf4j
public class JuheWeatherTool {



    private String apiKey;

    // 聚合数据天气接口地址
    private static final String API_URL = "http://apis.juhe.cn/simpleWeather/query";

    public JuheWeatherTool(@Value("${juhe.weather.key}") String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * 定义工具方法
     * description: 非常重要，大模型会根据这个描述决定是否调用此方法
     */
    @Tool(description = "Get the real-time weather and weather conditions of China city in recent 5 days.")
    public String getWeather(@ToolParam(description = "The city name") String city) {


        try {
            HashMap<String, String> map = new HashMap<>();
            map.put("key", apiKey);
            map.put("city", city);


            return JuheApi.callApi(API_URL, map);

        } catch (Exception e) {
            // 如果出错，返回错误信息给大模型，让大模型告诉用户“查询失败”
            return String.format("{\"error\": \"查询天气失败: %s\"}", e.getMessage());
        }
    }

}