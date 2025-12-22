package com.learn.aiintelligenttourism.controller;


import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.learn.aiintelligenttourism.Model.ChatRequest;
import com.learn.aiintelligenttourism.Model.ChatResponse;
import com.learn.aiintelligenttourism.app.TourismApp;
import com.learn.aiintelligenttourism.app.TourismGraphService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;


import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/ai")
public class AiController {


    @Resource
    private TourismApp tourismApp;


    /**
     * 调用AI恋爱大师应用 Emmiter流式输出
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/tourism_app/chat/sse_emitter")
    public SseEmitter doChatWithTourismAppSseEmitter(String message, String chatId) {
        SseEmitter emitter = new SseEmitter(180000L); // 3分钟超时
        tourismApp.doChatWithIntentionJudgmentByStream(message, chatId)
                .subscribe(
                        // onNext: 处理每一个数据块
                        dataMap -> {
                            try {
                                // 直接发送 Map，Spring MVC 会自动将其序列化为 JSON
                                // 前端收到的 data 字段形如： {"type": "text", "data": "你好"}
                                emitter.send(dataMap);
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        },
                        // onError: 处理异常
                        error -> {
                            try {
                                // 可以发送一个特殊的错误类型给前端
                                emitter.send(Map.of("type", "error", "data", "内部服务异常"));
                            } catch (IOException e) {
                                // ignore
                            }
                            emitter.completeWithError(error);
                        },
                        // onComplete: 完成
                        emitter::complete
                );

        return emitter;

    }

    @Autowired
    private TourismGraphService tourismGraphService;

    /**
     * 统一对话接口
     * 既处理新对话，也处理中断后的恢复对话
     * 前端只需要保持 threadId 一致即可
     */
    @PostMapping("/tourism_app/chat/manus")
    public  Map<String, Object> doChatWithManus(@RequestBody ChatRequest request) {
        // 校验
        if (request.getThreadId() == null || request.getThreadId().isEmpty()) {
            throw new IllegalArgumentException("ThreadId cannot be null");
        }
        try {
            return tourismGraphService.handleChat(request);
        }catch (Exception e) {
            log.info("error", e);
            return Map.of("type", "error", "data","服务器出现问题");
        }

    }

}
