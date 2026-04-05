package com.learn.aiintelligenttourism.tools;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest
class RealWeatherToolTest {

    @Resource
    JuheWeatherTool juheWeatherTool;

    @Test
    void getWeather() {
        String res = juheWeatherTool.getWeather("洛阳");
        Assertions.assertNotNull(res);

    }
}