package com.learn.aiintelligenttourism.agent;

import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.aiintelligenttourism.Model.ItineraryResponse;
import com.learn.aiintelligenttourism.Model.TravelRequirements;
import com.learn.aiintelligenttourism.memory.MemoryPromptSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.converter.BeanOutputConverter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class PlanGeneratorNode implements NodeActionWithConfig {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChatClient chatClient;

    public PlanGeneratorNode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) {
        log.info(">>> 进入节点: PlanGeneratorNode (最终规划)");

        TravelRequirements req = state.value("travelRequirements")
                .map(v -> (TravelRequirements) v)
                .orElseThrow(() -> new IllegalStateException("用户原始需求缺失"));

        String researchData = state.value("searchResults")
                .map(v -> (String) v)
                .orElseThrow(() -> new IllegalStateException("searchResults 缺失"));

        String validationFeedback = state.value("validationFeedback")
                .map(Object::toString)
                .orElse("");
        String memoryContextPrompt = MemoryPromptSupport.fromState(state);
        int retryCount = state.value("retryCount")
                .map(this::toInt)
                .orElse(0);

        ItineraryResponse previousItinerary = state.value("itinerary")
                .map(v -> (ItineraryResponse) v)
                .orElse(null);


        String previousItinerarySummary = buildPreviousItinerarySummary(previousItinerary);
        String correctionPrompt = validationFeedback.isBlank() ? "" : """
            ### 6. 上一版行程校验失败点（必须逐条修正）
            %s
        
            ### 7. 上一版行程摘要（必须基于此局部修正，而不是完全重写）
            %s
        
            修正要求：
            - 仅调整冲突段，不要推翻全部已合理安排。
            - 优先替换冲突地点为同类型但更近的备选。
            - 保持总天数、核心偏好、预算级别不变。
            - 以上一版行程结构为基础修正；只有确实冲突的活动才允许替换。
            """.formatted(validationFeedback, previousItinerarySummary);

        BeanOutputConverter<ItineraryResponse> outputConverter = new BeanOutputConverter<>(ItineraryResponse.class);

        String promptText = """
    你是一位拥有 20 年经验的资深旅行规划师，擅长根据碎片化信息制定可落地的旅行方案。
    请根据【用户需求】和【调研资料】，生成一份详细的结构化行程单。

    ### 0. 三层记忆参考（如与当前用户明确要求冲突，以当前要求为准）
    %s

    ### 1. 用户需求输入
    - 目的地: %s
    - 出行时间: %s
    - 用户预算: %s
    - 个人偏好: %s

    ### 2. 前期调研资料（天气、交通、攻略）
    %s

    ### 3. 核心规划逻辑（必须严格遵守）
    1. **预算估算规则（至关重要）**
       - 若用户提供了具体预算：请在预算范围内合理分配。
       - 若用户未提供预算、或预算显示为空/null/0：请务必根据当地平均消费水平（餐饮、门票、交通、住宿）进行合理预估。
       - 严禁出现 0 元：除非该景点明确免费，否则必须预估门票、交通和餐饮费用，并按“中等舒适型”标准估算。
    2. **路线合理性**
       - 行程必须顺路，避免折返跑。
       - 每天不要安排超过 4 个主要景点。
    3. **动态适配**
       - 结合【调研资料】中的天气，雨天优先安排室内，晴天安排户外。
       - 尽量避开【调研资料】中提到的“坑”或风险点。
    4. **餐饮建议**
       - 尽量包含午餐和晚餐建议。
       - 默认只输出“就餐区域（景区周边/商圈/商场） + 餐饮类型或特色菜 + 人均预算”。
       - 不要强行生成难以稳定定位的具体餐厅店名，除非该餐厅在调研资料中被明确提到且高度唯一。

    ### 4. ActivityItem 字段要求
    - `location`：面向用户的生动描述，可以使用括号、书名号等修饰，如 "建设路小吃街「叶婆婆钵钵鸡」"。
    - `poiName`：专门用于高精度地图检索的纯实体地名。
      - 必须剥离所有无关修饰词、标点符号。
      - 为提高检索精准度，请尽可能补全该地点的行政区划前缀（如区/县/乡镇/村）。如果是特定店名或景点，请输出“行政区 + 核心专有名词”的形式（例如："锦江区叶婆婆钵钵鸡"、"青羊区文殊院"、"理县毕棚沟"）。
      - 午餐、晚餐、小吃、咖啡、茶歇等餐饮类活动：默认视为“区域性建议”而非精确 POI，`poiName` 必须输出 `"NON_PHYSICAL"`。
      - 返程、出发、休息、自由活动、睡觉等非实体物理空间：`poiName` 也必须输出 `"NON_PHYSICAL"`。
    - `type`：必须清晰标记活动类型。
      - 景点用 `attraction`
      - 交通用 `transport`
      - 酒店用 `hotel`
      - 餐饮用 `meal`
      - 休息或自由活动用 `break`

    ### 5. 输出要求
    - 只输出合法 JSON，不要输出 markdown 代码块或解释说明。
    - 保证 `days[].activities[]` 顺序就是当天实际动线顺序。

    ### 6. 结构化输出格式（必须严格遵守）
    %s

    %s
    """.formatted(
                memoryContextPrompt == null || memoryContextPrompt.isBlank() ? "无可用历史记忆。" : memoryContextPrompt,
                req.destination(),
                req.travelDate(),
                StrUtil.isBlank(StrUtil.trim(req.budget())) ? "未指定（请按当地中等消费水平自动预估）" : req.budget(),
                req.preference(),
                researchData,
                outputConverter.getFormat(),
                correctionPrompt
        );

        ItineraryResponse itinerary = null;
        int maxSelfCorrectionRetries = 3;
        int currentAttempt = 0;
        String lastErrorMsg = "";
        String rawContent = "";

        while (currentAttempt < maxSelfCorrectionRetries && itinerary == null) {
            currentAttempt++;
            String currentPromptText = promptText;

            if (currentAttempt > 1) {
                log.warn(">>> [自纠错第 {} 次尝试] 修正 JSON 格式...", currentAttempt);
                currentPromptText = """
                    %s

                    【极其重要 - FORMAT CORRECTION】
                    你上一次的输出不是合法 JSON。解析失败报错为：
                    %s

                    你上一次的错误原样输出是：
                    %s

                    请基于上面的报错精确修复，仅返回符合结构化格式的 JSON，不要返回 markdown 代码块，也不要返回解释性文本。
                    """.formatted(promptText, lastErrorMsg, rawContent);
            }

            try {
                rawContent = chatClient.prompt()
                        .user(currentPromptText)
                        .call()
                        .content();

                if (rawContent == null || rawContent.isBlank()) {
                    throw new IllegalStateException("AI 返回空白内容");
                }

                String cleanedJson = cleanJsonString(rawContent);
                itinerary = outputConverter.convert(cleanedJson);
            } catch (Exception e) {
                lastErrorMsg = e.getMessage();
                log.error("AI 结构化结果解析失败: {}", lastErrorMsg);
            }
        }

        if (itinerary == null) {
            log.error(">>> 经历 {} 次自纠错后，仍无法得到合法的 JSON 行程。采取兜底平滑降级。", maxSelfCorrectionRetries);
            itinerary = new ItineraryResponse(
                    "抱歉，AI 正在整理思路，请重试",
                    Collections.emptyList(),
                    0.0
            );
        } else {
            log.info("行程规划完成: {}", itinerary.getTitle());
        }

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("itinerary", itinerary);
        outputs.put("next_node", "end");
        // L1 只保留用户实际看得到的内容。
        // 这里不写内部确认语，而是写一条与行程卡片一致的可见摘要，便于后续轮次理解“刚刚展示过什么”。
        String userMessage = state.value("userMessage")
                .map(String.class::cast)
                .orElse("");
        outputs.put("messages", buildVisibleMessages(state, userMessage, buildVisibleItineraryMessage(itinerary)));
        outputs.put(MemoryPromptSupport.CURRENT_TURN_USER_MESSAGE_PERSISTED_KEY, true);
        outputs.put("retryCount", retryCount);
        outputs.put("validationFeedback", "");
        outputs.put("validationPassed", false);
        return outputs;
    }

    private String buildPreviousItinerarySummary(ItineraryResponse itinerary) {
        if (itinerary == null) {
            return "无上一版行程。";
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("title", itinerary.getTitle());
        summary.put("totalBudget", itinerary.getTotalBudget());

        List<Map<String, Object>> daySummaries = new ArrayList<>();
        if (itinerary.getDays() != null) {
            for (ItineraryResponse.DailyPlan day : itinerary.getDays()) {
                Map<String, Object> daySummary = new LinkedHashMap<>();
                daySummary.put("day", day.getDay());
                daySummary.put("city", day.getCity());

                List<Map<String, Object>> activitySummaries = new ArrayList<>();
                if (day.getActivities() != null) {
                    for (ItineraryResponse.ActivityItem activity : day.getActivities()) {
                        Map<String, Object> activitySummary = new LinkedHashMap<>();
                        activitySummary.put("time", activity.getTime());
                        activitySummary.put("location", activity.getLocation());
                        activitySummary.put("poiName", activity.getPoiName());
                        activitySummary.put("type", activity.getType());
                        activitySummaries.add(activitySummary);
                    }
                }
                daySummary.put("activities", activitySummaries);
                daySummaries.add(daySummary);
            }
        }
        summary.put("days", daySummaries);

        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(summary);
        } catch (JsonProcessingException e) {
            log.warn("上一版 itinerary 摘要序列化失败: {}", e.getMessage());
            return "上一版行程序列化失败。";
        }
    }

    private Integer toInt(Object value) {
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private String buildVisibleItineraryMessage(ItineraryResponse itinerary) {
        if (itinerary == null) {
            return "已展示一张行程卡片。";
        }

        StringBuilder message = new StringBuilder("已向用户展示行程卡片：");
        message.append("标题《").append(itinerary.getTitle()).append("》");

        if (itinerary.getTotalBudget() > 0) {
            message.append("，总预算约 ").append((int) Math.round(itinerary.getTotalBudget())).append(" 元");
        }

        if (itinerary.getDays() != null && !itinerary.getDays().isEmpty()) {
            message.append("。包含 ");
            List<String> daySummaries = new ArrayList<>();
            for (ItineraryResponse.DailyPlan day : itinerary.getDays()) {
                if (day == null) {
                    continue;
                }
                String city = day.getCity() == null || day.getCity().isBlank() ? "未知城市" : day.getCity();
                int activityCount = day.getActivities() == null ? 0 : day.getActivities().size();
                daySummaries.add("Day " + day.getDay() + " " + city + "（" + activityCount + " 个活动）");
            }
            message.append(String.join("；", daySummaries));
        }

        return message.toString();
    }

    private List<Message> buildVisibleMessages(OverAllState state, String userMessage, String assistantMessage) {
        List<Message> messages = new ArrayList<>();
        boolean userMessagePersisted = MemoryPromptSupport.isCurrentTurnUserMessagePersisted(
                state.data().get(MemoryPromptSupport.CURRENT_TURN_USER_MESSAGE_PERSISTED_KEY)
        );
        if (!userMessagePersisted && userMessage != null && !userMessage.isBlank()) {
            messages.add(new UserMessage(userMessage));
        }
        messages.add(new AssistantMessage(assistantMessage));
        return messages;
    }

    private String cleanJsonString(String rawJson) {
        if (rawJson == null) {
            return "";
        }
        String cleaned = rawJson.trim();
        Matcher matcher = Pattern.compile("(?s)```(?:json)?(.*?)```").matcher(cleaned);
        if (matcher.find()) {
            cleaned = matcher.group(1).trim();
        }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start != -1 && end != -1 && end >= start) {
            cleaned = cleaned.substring(start, end + 1);
        }
        return cleaned;
    }
}
