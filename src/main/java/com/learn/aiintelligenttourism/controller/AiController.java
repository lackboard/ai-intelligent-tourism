package com.learn.aiintelligenttourism.controller;

import com.learn.aiintelligenttourism.Model.ChatRequest;
import com.learn.aiintelligenttourism.app.TourismApp;
import com.learn.aiintelligenttourism.app.TourismGraphService;
import com.learn.aiintelligenttourism.config.LangfuseTraceContextHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private TourismApp tourismApp;

    @Autowired
    private TourismGraphService tourismGraphService;

    /**
     * 流式聊天接口仍然走 JDBC Chat Memory。
     * 为兼容旧调用方，保留 chatId 参数，但内部优先使用 threadId。
     */
    @GetMapping(value = "/tourism_app/chat/sse_emitter")
    public SseEmitter doChatWithTourismAppSseEmitter(
            @RequestParam String message,
            @RequestParam(required = false) String visitorId,
            @RequestParam(required = false) String threadId,
            @RequestParam(required = false) String chatId
    ) {
        String conversationId = (threadId != null && !threadId.isBlank()) ? threadId : chatId;
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("threadId 不能为空");
        }
        String resolvedVisitorId = (visitorId == null || visitorId.isBlank()) ? conversationId : visitorId.trim();

        // Keep user/session in thread context so ObservationFilter can enrich Langfuse spans.
        LangfuseTraceContextHolder.set(resolvedVisitorId, conversationId);
        SseEmitter emitter = new SseEmitter(180000L);
        tourismApp.doChatWithIntentionJudgmentByStream(message, resolvedVisitorId, conversationId)
                .doFinally(signalType -> LangfuseTraceContextHolder.clear())
                .subscribe(
                        dataMap -> {
                            try {
                                emitter.send(dataMap);
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        },
                        error -> {
                            try {
                                emitter.send(Map.of("type", "error", "data", "内部服务异常"));
                            } catch (IOException ignored) {
                            }
                            emitter.completeWithError(error);
                        },
                        emitter::complete
                );

        return emitter;
    }

    /**
     * Graph 对话接口统一以 threadId 作为 checkpoint 主键。
     */
    @PostMapping("/tourism_app/chat/manus")
    public Map<String, Object> doChatWithManus(@RequestBody ChatRequest request) {
        if (request.getThreadId() == null || request.getThreadId().isBlank()) {
            throw new IllegalArgumentException("threadId 不能为空");
        }
        String resolvedVisitorId = (request.getVisitorId() == null || request.getVisitorId().isBlank())
                ? request.getThreadId().trim()
                : request.getVisitorId().trim();

        LangfuseTraceContextHolder.set(resolvedVisitorId, request.getThreadId().trim());
        try {
            return tourismGraphService.handleChat(request);
        } catch (Exception e) {
            log.error("Graph chat error", e);
            return Map.of("type", "error", "data", "服务器出现问题");
        } finally {
            LangfuseTraceContextHolder.clear();
        }
    }
}
