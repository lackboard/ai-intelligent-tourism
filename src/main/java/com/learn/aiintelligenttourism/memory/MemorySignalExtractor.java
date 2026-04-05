package com.learn.aiintelligenttourism.memory;

import com.learn.aiintelligenttourism.Model.ItineraryResponse;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 记忆信号提取器。
 *
 * <p>全面升级为大模型(LLM)结构化抽取 (Structured Output)。
 * 极大地提升提取精准度，解决正则在否定句式上的误判等问题。
 */
@Slf4j
@Component
public class MemorySignalExtractor {

    private final ChatClient chatClient;
    private final DashScopeChatOptions chatOptions;

    // LRU 缓存，防止同一条消息被多个系统流程重复解析。
    // 这是一个全局组件，因此缓存跨所有用户和所有Session共享。相同的话（如"好的"）只会调用大模型一次！
    private final Map<String, ExtractedSignals> signalCache = Collections.synchronizedMap(
            new LinkedHashMap<String, ExtractedSignals>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ExtractedSignals> eldest) {
                    return size() > 500; // 调大全局缓存容量，提高通用回复的命中率
                }
            }
    );

    public record ExtractedSignals(
            boolean isAccepted,
            boolean isRejected,
            boolean isSessionClosed,
            String travelPace,
            String budgetRange,
            String hotelPreference,
            List<String> interestTags,
            String transportPreference,
            String destination
    ) {}

    public MemorySignalExtractor(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
        // 使用稍微便宜一点的模型做判定，比如 qwen-plus，降低 Token 成本
        this.chatOptions = DashScopeChatOptions.builder()
                .withModel("qwen-plus") 
                .build();
    }

    private ExtractedSignals analyzeMessage(String text) {
        if (text == null || text.isBlank()) {
            return new ExtractedSignals(false, false, false, null, null, null, List.of(), null, null);
        }
        
        String normalized = text.trim();
        
        // 1. 极其轻量的前置拦截：比如纯标点、或是无意义的单字（且不是常见确认字），直接跳过 LLM 节约成本
        if (normalized.length() <= 1 && !normalized.matches("[好行可棒嗯撤退换查]")) {
            return new ExtractedSignals(false, false, false, null, null, null, List.of(), null, null);
        }

        return signalCache.computeIfAbsent(normalized, key -> {
            try {
                log.info("调用大模型提取记忆信号: {}", key);
                String systemPrompt = """
                        你是一个专业的旅游意图与偏好分析助手。
                        请阅读用户的发言，提取对应字段并严格以 JSON 格式输出。
                        要求：
                        1. L3 事件：
                           - isAccepted(布尔): 用户发言是否【明确同意/采纳/满意】了当前的行程方案。
                           - isRejected(布尔): 用户发言是否【明确拒绝/推翻/不满意/要求重做】了当前的行程方案。
                           - isSessionClosed(布尔): 用户发言是否表示【结束对话/道别】，如谢谢、先这样。
                        
                        2. L2 稳定画像偏好（只提取跨会话稳定的偏好，不可把临时诉求当做长期偏好，若未体现，设为 null）：
                           - travelPace(字符串): 比如"偏轻松慢节奏"、"偏高密度行程"。
                           - budgetRange(字符串): 比如"偏经济型"、"偏舒适或高品质"。
                           - hotelPreference(字符串): 比如"靠近地铁"、"市中心"。
                           - interestTags(字符串数组): 如["美食", "自然风光", "历史文化"]，未体现传空数组[]。
                           - transportPreference(字符串): 如"高铁"、"自驾"。
                           
                        3. L1 临时变量：
                           - destination(字符串): 提取本次旅行的目的地名称，去掉“旅游”等修饰词。没有设为 null。
                        """;

                return chatClient.prompt()
                        .system(systemPrompt)
                        .user(key)
                        .options(this.chatOptions)
                        .call()
                        .entity(ExtractedSignals.class);
            } catch (Exception e) {
                log.error("大模型提取意图失败，退化为默认空结果", e);
                return new ExtractedSignals(false, false, false, null, null, null, List.of(), null, null);
            }
        });
    }

    /**
     * L2 最小版只保留五类稳定画像字段。
     */
    public List<ProfileMemoryFact> extractProfileFacts(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return List.of();
        }

        ExtractedSignals signals = analyzeMessage(userMessage);
        List<ProfileMemoryFact> facts = new ArrayList<>();

        if (signals.travelPace() != null && !signals.travelPace().isBlank()) {
            facts.add(new ProfileMemoryFact("travel_pace", signals.travelPace(), 0.82, "llm_extractor"));
        }
        if (signals.budgetRange() != null && !signals.budgetRange().isBlank()) {
            facts.add(new ProfileMemoryFact("budget_range", signals.budgetRange(), 0.78, "llm_extractor"));
        }
        if (signals.hotelPreference() != null && !signals.hotelPreference().isBlank()) {
            facts.add(new ProfileMemoryFact("hotel_preference", signals.hotelPreference(), 0.76, "llm_extractor"));
        }
        if (signals.interestTags() != null && !signals.interestTags().isEmpty()) {
            facts.add(new ProfileMemoryFact("interest_tags", String.join(",", signals.interestTags()), 0.72, "llm_extractor"));
        }
        if (signals.transportPreference() != null && !signals.transportPreference().isBlank()) {
            facts.add(new ProfileMemoryFact("transport_preference", signals.transportPreference(), 0.75, "llm_extractor"));
        }

        return facts;
    }

    /**
     * 抽到了 profile fact，不等于一定要写入 L2。
     * 因为 LLM 已经在 prompt 层面做了防脏判断，这里可以直接放行提取到的 Fact。
     */
    public boolean shouldStoreAsProfileFact(ProfileMemoryFact fact, String userMessage) {
        return fact != null && fact.value() != null && !fact.value().isBlank();
    }

    public List<String> extractInterestTags(String text) {
        ExtractedSignals signals = analyzeMessage(text);
        if (signals.interestTags() != null) {
            return signals.interestTags();
        }
        return List.of();
    }

    public List<String> buildLongTermTags(String userMessage, ItineraryResponse itinerary) {
        Set<String> tags = new LinkedHashSet<>(extractInterestTags(userMessage));
        String destination = extractDestination(userMessage);
        if (destination != null) {
            tags.add(destination);
        }

        if (itinerary != null && itinerary.getDays() != null) {
            itinerary.getDays().stream()
                    .map(ItineraryResponse.DailyPlan::getCity)
                    .filter(city -> city != null && !city.isBlank())
                    .forEach(tags::add);
        }
        return new ArrayList<>(tags);
    }

    public String extractDestination(String text) {
        ExtractedSignals signals = analyzeMessage(text);
        return signals.destination();
    }

    public String buildItinerarySummary(ItineraryResponse itinerary) {
        if (itinerary == null) {
            return "";
        }

        Set<String> cities = new LinkedHashSet<>();
        if (itinerary.getDays() != null) {
            itinerary.getDays().stream()
                    .map(ItineraryResponse.DailyPlan::getCity)
                    .filter(city -> city != null && !city.isBlank())
                    .forEach(cities::add);
        }

        StringBuilder summary = new StringBuilder();
        summary.append("生成过一份行程单《").append(itinerary.getTitle()).append("》");
        if (!cities.isEmpty()) {
            summary.append("，覆盖城市：").append(String.join("、", cities));
        }
        if (itinerary.getTotalBudget() > 0) {
            summary.append("，预估总预算约 ").append(String.format(Locale.ROOT, "%.0f", itinerary.getTotalBudget())).append(" 元");
        }
        summary.append("。");
        return summary.toString();
    }

    /**
     * 接受信号偏保守。
     */
    public boolean isItineraryAcceptedMessage(String text) {
        return analyzeMessage(text).isAccepted();
    }

    /**
     * rejection 事件必须足够明确。
     */
    public boolean isItineraryRejectedMessage(String text) {
        return analyzeMessage(text).isRejected();
    }

    /**
     * 用于捕获“会话收尾”时机。
     */
    public boolean isSessionClosureMessage(String text) {
        return analyzeMessage(text).isSessionClosed();
    }

    public boolean hasLongTermPreferenceSignal(String text) {
        // 大模型已经在提取时做了长期和短期判断，这里不再单独使用这个规则兜底
        return !extractProfileFacts(text).isEmpty();
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.trim();
    }

}
