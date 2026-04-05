package com.learn.aiintelligenttourism.tools;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
class JuheExchangeRateToolTest {


    @Resource
    JuheExchangeRateTool juheExchangeRateTool;

    @Test
    void getExchangeRate() {
        String exchangeRate = juheExchangeRateTool.getExchangeRate("EUR", "USD");
        Assertions.assertNotNull(exchangeRate);

    }
}