package com.learn.aiintelligenttourism.app;


import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.learn.aiintelligenttourism.Model.ItineraryResponse;
import com.learn.aiintelligenttourism.RAG.TourismAppDocumentReader;
import com.learn.aiintelligenttourism.RAG.TourismAppRagCustomAdvisorFactory;
import com.learn.aiintelligenttourism.advisor.MyLoggerAdvisor;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.util.Map;

@Component
public class TourismApp {

    private final ChatClient chatClient;

    @Autowired
    JdbcChatMemoryRepository chatMemoryRepository; // 配置存储

    // 控制记忆长度
    ChatMemory messageWindowChatMemory = MessageWindowChatMemory.builder()
            .chatMemoryRepository(chatMemoryRepository)
            .maxMessages(15)
            .build();
    @Autowired
    private DashScopeChatModel dashscopeChatModel;
    // 获取当前日期
    String today = LocalDate.now().toString();

    /**
     * 初始化 AI 客户端
     *  Resource systemResource 要放在里边，否则执行顺序有问题，会导致systemResource 为 null
     * @param dashscopeChatModel
     */
    public TourismApp(@Value("classpath:/prompts/system-message.st") Resource systemResource, ChatModel dashscopeChatModel) {
        //String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";

        this.chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(systemResource) // 动态注入日期)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(messageWindowChatMemory).build(),
                        // 自定义拦截器
                        new MyLoggerAdvisor()
                )
                .build();
    }


    /**
     * AI 基础对话（支持多轮对话记忆）
     * @param message
     * @param chatId
     * @return
     */
    public String doChat(String message,String chatId){
        ChatResponse chatResponse = this.chatClient
                .prompt()
                //.system(s -> s.param("current_date", today)) // 动态注入日期
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        return content;
    }

    @Autowired
    VectorStore vectorStore;

    @jakarta.annotation.Resource
    private TourismAppDocumentReader tourismAppDocumentReader;

    @jakarta.annotation.Resource
    private ToolCallback[] allTools;

    @Autowired
    private Advisor tourismAppRagCustomAdvisor ;



    /**
     * AI 基础对话，并且实现了RAG检索和工具调用
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithRag(String message,String chatId){
        // 加载文档
        //List<Document> documents = tourismAppDocumentReader.loadMarkdowns();
        //vectorStore.add(documents);
        // 获取当前日期

        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .system(s -> s.param("current_date", today)) // 动态注入日期
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                //.advisors(loveAppRagCloudAdvisor)
                .advisors(tourismAppRagCustomAdvisor)
                .toolCallbacks(allTools)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        return content;
    }


    /**
     * AI 基础对话，并且实现了RAG检索和工具调用 ，还有意图判断（什么时候回复结构化数据）
     * @param message
     * @param chatId
     * @return
     */
    public Map<String, Object> doChatWithIntentionJudgment(String message,String chatId){

        // AI 意图判断结果
        boolean isPlanning = checkIntent(message);

        if(isPlanning){
            ItineraryResponse itinerary = chatClient
                    .prompt()
                    .user(message)
                    .system(s -> s.param("current_date", today)) // 动态注入日期
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                    //.advisors(loveAppRagCloudAdvisor)
                    .advisors(tourismAppRagCustomAdvisor)
                    .toolCallbacks(allTools)
                    .call()
                    .entity(ItineraryResponse.class);
            assert itinerary != null;
            return Map.of("type", "card", "data", itinerary);
        }else{
            ChatResponse chatResponse = chatClient
                    .prompt()
                    .user(message)
                    .system(s -> s.param("current_date", today)) // 动态注入日期
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                    //.advisors(loveAppRagCloudAdvisor)
                    .advisors(tourismAppRagCustomAdvisor)
                    .toolCallbacks(allTools)
                    .call()
                    .chatResponse();
            assert chatResponse != null;
            String content = chatResponse.getResult().getOutput().getText();
            // 返回给前端：类型是 "text"
            assert content != null;
            return Map.of("type", "text", "data", content);
        }
    }

    /**
     * AI 基础对话（流式），包含意图判断、RAG检索和工具调用
     * 返回的数据结构约定：
     *  - 文本流：{"type": "text", "data": "部分字符"}
     *  - 卡片流：{"type": "card", "data": {完整对象}}
     */
    public Flux<Map<String, Object>> doChatWithIntentionJudgmentByStream(String message, String chatId) {
        // 使用 Flux.defer 确保 checkIntent 在订阅时才执行，不会阻塞主线程
        return Flux.defer(() -> {
            // 1. AI 意图判断 (假设 checkIntent 是个耗时操作)
            boolean isPlanning = checkIntent(message);

            if (isPlanning) {
                // === 分支 A：意图为生成行程单 (返回 Card) ===
                // 对于结构化数据，通常建议生成完整对象后一次性返回，或者返回 JSON 字符串
                // 这里我们使用阻塞的 call() 获取完整结果，然后包装成 Flux 发送一次

                try {
                    ItineraryResponse itinerary = chatClient
                            .prompt()
                            .user(message)
                            .system(s -> s.param("current_date", today))
                            .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                            //.advisors(loveAppRagCloudAdvisor)
                            .advisors(tourismAppRagCustomAdvisor)
                            .toolCallbacks(allTools)
                            .call()
                            .entity(ItineraryResponse.class);

                    if (itinerary != null) {
                        // 发送单个事件
                        return Flux.just(Map.of("type", "card", "data", itinerary));
                    } else {
                        return Flux.error(new RuntimeException("数据生成异常"));
                    }
                } catch (Exception e) {
                    return Flux.error(e);
                }

            } else {
                // === 分支 B：普通对话 (返回 Text 流) ===
                // 使用 stream() 接口，将每个 token 包装成 Map 返回

                return chatClient
                        .prompt()
                        .user(message)
                        .system(s -> s.param("current_date", today))
                        .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                        //.advisors(loveAppRagCloudAdvisor)
                        .advisors(tourismAppRagCustomAdvisor)
                        .toolCallbacks(allTools)
                        .stream()
                        .content() // 获取流式字符串内容
                        .map(content -> {
                            // 将每个字符串片段包装成前端需要的格式
                            return Map.of("type", "text", "data", content);
                        });
            }
        });
    }

    /**
     * 意图判断逻辑 (用 便宜AI 实现)
     * @param systemResource
     * @param message
     * @return
     */
    private boolean checkIntent(Resource systemResource,String message) {

        try {
            DashScopeChatOptions dashScopeChatOptions = DashScopeChatOptions.builder().withModel("qwen-flash").build();
            String content = this.chatClient.prompt()
                    .options(dashScopeChatOptions)
                    .system(systemResource)
                    .user(message)
                    .call()
                    .content();// 获取 AI 的回复
            // 清洗结果（防止 AI 偶尔输出 "TRUE." 或 "Result: TRUE"）
            assert content != null;
            String cleanResult = content.trim().toUpperCase().replaceAll("[^A-Z]", "");
            return "TRUE".equals(cleanResult);
        } catch (Exception e) {
            // 兜底逻辑：如果 AI 调用失败，回退到简单的关键词判断，保证系统不挂
            System.err.println("意图识别服务异常: " + e.getMessage());
            return message.contains("规划") || message.contains("行程") || message.contains("安排");
        }
    }

    // 重载方法
    private boolean checkIntent(String message) {
        return checkIntent(new ClassPathResource("/prompts/system-message-intention-judgment.st"), message);
    }
}
