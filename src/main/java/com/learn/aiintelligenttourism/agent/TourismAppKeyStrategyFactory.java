package com.learn.aiintelligenttourism.agent;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

import java.util.HashMap;

public class TourismAppKeyStrategyFactory{
    // 配置状态键策略
    public static KeyStrategyFactory createKeyStrategyFactory() {
        return () -> {
            HashMap<String, KeyStrategy> strategies = new HashMap<>();
            // 原始输入
            strategies.put("userMessage", new ReplaceStrategy());
            strategies.put("chatId", new ReplaceStrategy());
            // 2. 意图分类 (CHAT / PLAN)
            strategies.put("intent", new ReplaceStrategy());
            // 3. 提取到的关键信息 (槽位)，规划相关
            strategies.put("travelRequirements", new ReplaceStrategy());  // 旅游信息

            // 4. 工具调用结果
            strategies.put("searchResults", new ReplaceStrategy());
            // 5. 最终产物
            strategies.put("itinerary", new ReplaceStrategy()); // 结构化行程单
            strategies.put("finalResponse", new ReplaceStrategy()); // 给用户的自然语言回复


            strategies.put("messages", new AppendStrategy()); // 会话记录
            strategies.put("next_node", new ReplaceStrategy());


            return strategies;
        };
    }
}
