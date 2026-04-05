package com.learn.aiintelligenttourism.agent;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.learn.aiintelligenttourism.memory.MemoryConstants;
import com.learn.aiintelligenttourism.memory.MemoryPromptSupport;

import java.util.HashMap;

public class TourismAppKeyStrategyFactory {

    public static KeyStrategyFactory createKeyStrategyFactory() {
        return () -> {
            HashMap<String, KeyStrategy> strategies = new HashMap<>();

            strategies.put("userMessage", new ReplaceStrategy());
            strategies.put("visitorId", new ReplaceStrategy());
            strategies.put("threadId", new ReplaceStrategy());
            strategies.put("intent", new ReplaceStrategy());
            // 保存意图判定证据，方便 checkpoint 追踪与 bad case 回放。
            strategies.put("intent_recall_cases", new ReplaceStrategy());
            strategies.put("intent_raw_output", new ReplaceStrategy());
            strategies.put("travelRequirements", new ReplaceStrategy());
            strategies.put("pendingQuestion", new ReplaceStrategy());
            strategies.put("searchResults", new ReplaceStrategy());
            strategies.put("itinerary", new ReplaceStrategy());
            strategies.put("finalResponse", new ReplaceStrategy());
            strategies.put("validationPassed", new ReplaceStrategy());
            strategies.put("validationFeedback", new ReplaceStrategy());
            strategies.put("retryCount", new ReplaceStrategy());
            strategies.put("next_node", new ReplaceStrategy());
            strategies.put(MemoryPromptSupport.MEMORY_CONTEXT_PROMPT_KEY, new ReplaceStrategy());
            strategies.put(MemoryPromptSupport.CURRENT_TURN_USER_MESSAGE_PERSISTED_KEY, new ReplaceStrategy());

            // Graph checkpoint 里只保留最近几轮自然语言对话，避免状态无限增长。
            strategies.put("messages", new BoundedMessageAppendStrategy(MemoryConstants.WORKING_MEMORY_WINDOW_SIZE));

            return strategies;
        };
    }
}
