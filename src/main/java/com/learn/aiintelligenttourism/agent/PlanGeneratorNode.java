package com.learn.aiintelligenttourism.agent;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;

import com.learn.aiintelligenttourism.Model.ItineraryResponse;
import com.learn.aiintelligenttourism.Model.TravelRequirements;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.converter.BeanOutputConverter;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class PlanGeneratorNode implements NodeActionWithConfig {

    private final ChatClient chatClient;

    public PlanGeneratorNode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) {
        log.info(">>> 进入节点: PlanGeneratorNode (最终规划)");

        // 1. 获取上下文输入
        // 用户的原始需求 (目的地、时间、偏好)
        TravelRequirements req = state.value("travelRequirements")
                .map(v -> (TravelRequirements) v)
                .orElseThrow(() -> new IllegalStateException("用户的原始需求 异常"));

        // ResearchAgent 查到的天气、攻略、避坑指南 (通常是一大段文本或JSON字符串)
        // 注意：这里要对应你 KeyStrategyFactory 里的 key: "search_results"
        String researchData = state.value("searchResults")
                .map(v -> (String) v)
                .orElseThrow(() -> new IllegalStateException("searchResults 异常"));

        //String chatId = state.value("chatId")
        //        .map(v -> (String) v)
        //        .orElseThrow(() -> new IllegalStateException("会话ID 异常"));

        if (req == null) {
            throw new IllegalStateException("缺失旅游需求数据，无法规划");
        }

        String promptText = """
    你是一位拥有 20 年经验的资深旅行规划师，擅长根据碎片化信息制定可落地的旅行方案。
    请根据【用户需求】和【调研资料】，生成一份详细的结构化行程单。

    ### 1. 用户需求输入
    - 目的地: %s
    - 出行时间: %s
    - 用户预算: %s
    - 个人偏好: %s

    ### 2. 前期调研资料 (天气、交通、攻略)
    %s

    ### 3. 核心规划逻辑 (必须严格遵守)
    1.  **预算估算规则 (至关重要)**：
        *   **若用户提供了具体预算**：请在预算范围内合理分配。
        *   **若用户未提供预算、或预算显示为空/null/0**：请务必根据当地的平均消费水平（餐饮、门票、交通、住宿）进行**合理的预估**。
        *   **严禁出现 0 元**：除非该景点明确是免费的（如公园），否则必须预估门票和交通餐饮费用。请按“中等舒适型”标准预估。
    2.  **路线合理性**：行程必须顺路，避免折返跑。考虑到游玩疲劳度，每天不要安排超过 4 个主要景点。
    3.  **动态适配**：
        *   结合【调研资料】中的天气，雨天优先安排室内（博物馆、商场），晴天安排户外。
        *   必须避开【调研资料】中提到的“坑”或风险点。
    4.  **餐饮推荐**：每天必须包含午餐和晚餐的推荐（具体到餐厅类型或特色菜）。

    """.formatted(
                req.destination(),
                req.travelDate(),
                // 这里做一个简单的 Java 层面的空值处理，防止直接传 null 进去看着奇怪，但主要逻辑还是靠 Prompt
                (req.budget() == null || req.budget().trim().isEmpty()) ? "未指定（请按当地中等消费水平自动预估）" : req.budget(),
                req.preference(),
                researchData != null ? researchData : "无详细调研数据，请基于通用知识及网络常识规划。"
        );

        // 3. 调用 AI 生成结构化数据
        // 建议：这里可以将 Temperature 设为 0，保证输出格式稳定
        ItineraryResponse itinerary = chatClient.prompt()
                .user(promptText)
                //.advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId)) // 关键：带上记忆！
                .call()
                .entity(ItineraryResponse.class);

        assert itinerary != null;
        log.info("行程规划完成: {}", itinerary.getTitle());


        // 5. 更新状态并结束
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("itinerary", itinerary);       // 存入对象，供前端渲染卡片
        outputs.put("next_node", "end");           // 路由到结束
        outputs.put("messages", new AssistantMessage(itinerary.toString()));
        
        return outputs;
    }
}