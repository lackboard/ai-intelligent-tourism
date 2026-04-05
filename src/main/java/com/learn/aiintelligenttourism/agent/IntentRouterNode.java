package com.learn.aiintelligenttourism.agent;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
/**
 * Graph 的意图路由节点：
 * - 从状态中读取用户本轮输入；
 * - 调用统一意图识别服务（含前置 RAG）；
 * - 将意图结果写回状态，交给条件边做分流。
 */
public class IntentRouterNode implements NodeActionWithConfig {

    private final IntentRecognitionService intentRecognitionService;

    public IntentRouterNode(IntentRecognitionService intentRecognitionService) {
        this.intentRecognitionService = intentRecognitionService;
    }

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) {
        log.info(">>> 进入节点: IntentRouterNode (意图判断)");

        // 只使用 userMessage 做当前轮分类，避免历史消息干扰标签。
        String message = state.value("userMessage")
                .map(v -> (String) v)
                .orElseThrow(() -> new IllegalStateException("用户输入信息为空"));
        log.info("IntentRouterNode 用户信息: {}", message);

        // 统一走 IntentRecognitionService，保证 Graph 与 SSE 链路判定标准一致。
        IntentRecognitionResult recognitionResult = intentRecognitionService.recognize(message);
        return Map.of(
                "intent", recognitionResult.intent(),
                "next_node", recognitionResult.nextNode(),
                // 召回样例和原始输出回写到 state，便于 checkpoint 调试与 bad case 复盘。
                "intent_recall_cases", recognitionResult.recalledCases(),
                "intent_raw_output", recognitionResult.rawModelOutput()
        );
    }
}
