package com.learn.aiintelligenttourism.app;

import com.learn.aiintelligenttourism.agent.IntentRecognitionResult;
import com.learn.aiintelligenttourism.agent.IntentRecognitionService;
import com.learn.aiintelligenttourism.Model.ItineraryResponse;
import com.learn.aiintelligenttourism.advisor.MyLoggerAdvisor;
import com.learn.aiintelligenttourism.memory.ConversationIdentity;
import com.learn.aiintelligenttourism.memory.MemoryConstants;
import com.learn.aiintelligenttourism.memory.MemoryOrchestrator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.util.Map;

@Component
public class TourismApp {

    private final ChatClient chatClient;
    private final String today = LocalDate.now().toString();
    private final MemoryOrchestrator memoryOrchestrator;
    // 与 Graph 共用同一意图识别内核，避免双链路行为不一致。
    private final IntentRecognitionService intentRecognitionService;

    @Autowired
    private ToolCallback[] allTools;

    @Autowired
    private Advisor tourismAppRagCustomAdvisor;

    public TourismApp(
            @Value("classpath:/prompts/system-message.st") Resource systemResource,
            ChatModel dashscopeChatModel,
            JdbcChatMemoryRepository chatMemoryRepository,
            MemoryOrchestrator memoryOrchestrator,
            IntentRecognitionService intentRecognitionService
    ) {
        this.memoryOrchestrator = memoryOrchestrator;
        this.intentRecognitionService = intentRecognitionService;
        // 这条链路显式依赖 JDBC chat memory，因此在构造阶段完成初始化，避免字段注入时序问题。
        ChatMemory messageWindowChatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(MemoryConstants.WORKING_MEMORY_WINDOW_SIZE)
                .build();

        this.chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(systemResource)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(messageWindowChatMemory).build(),
                        new MyLoggerAdvisor()
                )
                .build();
    }

    public String doChat(String message, String threadId) {
        return doChat(message, threadId, threadId);
    }

    public String doChat(String message, String visitorId, String threadId) {
        ConversationIdentity identity = new ConversationIdentity(resolveVisitorId(visitorId, threadId), threadId);
        String memoryPrompt = memoryOrchestrator.buildPrompt(identity, message);

        ChatResponse chatResponse = applyMemoryContext(this.chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, threadId)), memoryPrompt)
                .call()
                .chatResponse();

        String content = chatResponse.getResult().getOutput().getText();
        memoryOrchestrator.rememberTextTurn(identity, message, content);
        return content;
    }

    public String doChatWithRag(String message, String threadId) {
        return doChatWithRag(message, threadId, threadId);
    }

    public String doChatWithRag(String message, String visitorId, String threadId) {
        ConversationIdentity identity = new ConversationIdentity(resolveVisitorId(visitorId, threadId), threadId);
        String memoryPrompt = memoryOrchestrator.buildPrompt(identity, message);

        ChatResponse chatResponse = applyMemoryContext(chatClient
                .prompt()
                .user(message)
                .system(spec -> spec.param("current_date", today))
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, threadId))
                .advisors(tourismAppRagCustomAdvisor)
                .toolCallbacks(allTools), memoryPrompt)
                .call()
                .chatResponse();

        String content = chatResponse.getResult().getOutput().getText();
        memoryOrchestrator.rememberTextTurn(identity, message, content);
        return content;
    }

    public Map<String, Object> doChatWithIntentionJudgment(String message, String threadId) {
        return doChatWithIntentionJudgment(message, threadId, threadId);
    }

    public Map<String, Object> doChatWithIntentionJudgment(String message, String visitorId, String threadId) {
        ConversationIdentity identity = new ConversationIdentity(resolveVisitorId(visitorId, threadId), threadId);
        String memoryPrompt = memoryOrchestrator.buildPrompt(identity, message);
        // 非 Graph 同样走“前置召回增强分类”。
        IntentRecognitionResult intentResult = intentRecognitionService.recognize(message);
        boolean isPlanning = intentResult.isPlan();

        if (isPlanning) {
            ItineraryResponse itinerary = applyMemoryContext(chatClient
                    .prompt()
                    .user(message)
                    .system(spec -> spec.param("current_date", today))
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, threadId))
                    .advisors(tourismAppRagCustomAdvisor)
                    .toolCallbacks(allTools), memoryPrompt)
                    .call()
                    .entity(ItineraryResponse.class);
            memoryOrchestrator.rememberItinerary(identity, message, itinerary);
            return Map.of("type", "card", "data", itinerary);
        }

        ChatResponse chatResponse = applyMemoryContext(chatClient
                .prompt()
                .user(message)
                .system(spec -> spec.param("current_date", today))
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, threadId))
                .advisors(tourismAppRagCustomAdvisor)
                .toolCallbacks(allTools), memoryPrompt)
                .call()
                .chatResponse();

        String content = chatResponse.getResult().getOutput().getText();
        memoryOrchestrator.rememberTextTurn(identity, message, content);
        return Map.of("type", "text", "data", content);
    }

    /**
     * 这条流式链路继续使用 JDBC chat memory，不和 Graph checkpoint 混用。
     */
    public Flux<Map<String, Object>> doChatWithIntentionJudgmentByStream(String message, String threadId) {
        return doChatWithIntentionJudgmentByStream(message, threadId, threadId);
    }

    /**
     * visitorId 用于聚合同一访客的长期记忆；threadId 继续只代表当前会话线程。
     */
    public Flux<Map<String, Object>> doChatWithIntentionJudgmentByStream(String message, String visitorId, String threadId) {
        ConversationIdentity identity = new ConversationIdentity(resolveVisitorId(visitorId, threadId), threadId);
        String memoryPrompt = memoryOrchestrator.buildPrompt(identity, message);

        return Flux.defer(() -> {
            // SSE 链路使用同一套判定逻辑，减少线上路由偏差。
            IntentRecognitionResult intentResult = intentRecognitionService.recognize(message);
            boolean isPlanning = intentResult.isPlan();

            if (isPlanning) {
                try {
                    ItineraryResponse itinerary = applyMemoryContext(chatClient
                            .prompt()
                            .user(message)
                            .system(spec -> spec.param("current_date", today))
                            .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, threadId))
                            .advisors(tourismAppRagCustomAdvisor)
                            .toolCallbacks(allTools), memoryPrompt)
                            .call()
                            .entity(ItineraryResponse.class);

                    if (itinerary != null) {
                        memoryOrchestrator.rememberItinerary(identity, message, itinerary);
                        return Flux.just(Map.of("type", "card", "data", itinerary));
                    }
                    return Flux.error(new RuntimeException("数据生成异常"));
                } catch (Exception e) {
                    return Flux.error(e);
                }
            }

            StringBuilder assistantReply = new StringBuilder();
            return applyMemoryContext(chatClient
                    .prompt()
                    .user(message)
                    .system(spec -> spec.param("current_date", today))
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, threadId))
                    .advisors(tourismAppRagCustomAdvisor)
                    .toolCallbacks(allTools), memoryPrompt)
                    .stream()
                    .content()
                    .doOnNext(assistantReply::append)
                    .doOnComplete(() -> memoryOrchestrator.rememberTextTurn(identity, message, assistantReply.toString()))
                    .map(content -> Map.of("type", "text", "data", content));
        });
    }


    private ChatClientRequestSpec applyMemoryContext(ChatClientRequestSpec spec, String memoryPrompt) {
        if (memoryPrompt == null || memoryPrompt.isBlank()) {
            return spec;
        }
        return spec.system(memoryPrompt);
    }

    private String resolveVisitorId(String visitorId, String threadId) {
        if (visitorId == null || visitorId.isBlank()) {
            // 兼容旧调用方：没有 visitorId 时退化为 threadId 作用域，但无法沉淀跨会话记忆。
            return threadId;
        }
        return visitorId.trim();
    }
}
