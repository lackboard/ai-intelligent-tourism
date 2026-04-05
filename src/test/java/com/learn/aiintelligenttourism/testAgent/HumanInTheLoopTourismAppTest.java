package com.learn.aiintelligenttourism.testAgent;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
class HumanInTheLoopTourismAppTest {


    @Resource
    private HumanInTheLoopTourismApp app;

    @Test
    void mainTest() throws Exception {
        app.mainTest();
    }
}