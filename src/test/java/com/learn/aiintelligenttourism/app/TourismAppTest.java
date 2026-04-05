package com.learn.aiintelligenttourism.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TourismAppTest {

    @Resource
    private TourismApp tourismApp;

    @Test
    void doChat() {
        String chatId  = UUID.randomUUID().toString();
        String userText = "我现在在北京想要去上海，请帮我查询一下车票";
        String res = tourismApp.doChat(userText, chatId);
        Assertions.assertNotNull(res);
    }



    @Test
    void doChatWithRag() {
        String chatId  = UUID.randomUUID().toString().replace("-", "");
        String userText = "我要去南京游玩，有什么需要注意的么？";
        //String userText = "我过两天要去泰国玩，你能帮我查一下天气和汇率么？";

        String res = tourismApp.doChatWithRag(userText, chatId);
        Assertions.assertNotNull(res);

    }

    @Test
    void doChatWithIntentionJudgment() {

        String chatId  = UUID.randomUUID().toString();
        String userText = "我最近想去西藏旅行，请帮我做一个规划。";
        Map<String, Object> stringObjectMap = tourismApp.doChatWithIntentionJudgment(userText, chatId);
        Assertions.assertNotNull(stringObjectMap);
    }


    @Test
    void doChatWithIntentionJudgmentByStream() {
        String chatId  = UUID.randomUUID().toString();
        String userText = "我最近想去西藏旅行，请帮我做一个规划。";
        Map<String, Object> stringObjectMap = tourismApp.doChatWithIntentionJudgment(userText, chatId);
        Assertions.assertNotNull(stringObjectMap);

    }
}