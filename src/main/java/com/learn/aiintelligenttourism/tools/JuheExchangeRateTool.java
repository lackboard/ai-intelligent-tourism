package com.learn.aiintelligenttourism.tools;


import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;

/**
 * 聚合数据  汇率  查询工具
 */
@Component
@Slf4j
public class JuheExchangeRateTool {


    private String apiKey;

    // 聚合数据天气接口地址
    private static final String API_URL = "http://op.juhe.cn/onebox/exchange/currency";


    public JuheExchangeRateTool(@Value("${juhe.exchange_rate.key}") String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * 定义工具方法
     * description:
     */
    @Tool(description = "Real-time currency exchange rate query and conversion, the data is for reference only, and the transaction price at the bank counter shall prevail. The exchange rate between some currencies may not be found.")
    public String getExchangeRate(@ToolParam(description = "Currency code before exchange rate conversion such as EUR, USD, GBP, etc.") String from,
                                  @ToolParam(description = "Currency code converted to exchange rate such as EUR, USD, GBP, etc.") String to) {

        try {
            HashMap<String, String> map = new HashMap<>();
            map.put("key", apiKey);
            map.put("from", from);
            map.put("to", to);
            map.put("version", "2");

            return JuheApi.callApi(API_URL,map);

        } catch (Exception e) {
            // 如果出错，返回错误信息给大模型，让大模型告诉用户“查询失败”
            return String.format("{\"error\": \"查询汇率失败: %s\"}", e.getMessage());
        }
    }


}