package com.learn.aiintelligenttourism.agent;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ResearchNode implements NodeActionWithConfig {

    @Resource
    private ReactAgent researchAgent;

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        System.out.println(">>> 进入 Research Agent 节点 (手动隔离模式)");

        // 关键点：创建一个全新的 Config，不包含 resume 信号
        // 这样 Agent 就会把它当做一个全新的请求来处理，不会去查不存在的 Checkpoint
        RunnableConfig cleanConfig = RunnableConfig.builder()
                .build();

        // 执行 Agent
        // 注意：ReactAgent 的 invoke 入参和出参通常是 Map<String, Object>

        Optional<OverAllState> agentOutput = this.researchAgent.invoke((List<Message>) state.data().get("messages"), cleanConfig);

        // 处理输出：提取 Agent 的结果并放入主图需要的 key (searchResults)
        // ReactAgent 通常将最终结果放在 "output" 或 "payload" 中，也可能包含 "messages"

        String finalResult = "未获取到有效结果";

        if (agentOutput.isPresent()) {
            // 2. 获取内部的 OverAllState 对象
            OverAllState overallState = agentOutput.get();

            // 3. 获取 data 数据 (Map)
            // 注意：根据你的截图，OverAllState 应该有一个 getData() 方法返回 Map<String, Object>
            Map<String, Object> dataMap = overallState.data();

            if (dataMap != null && dataMap.containsKey("messages")) {
                Object messagesObj = dataMap.get("messages");

                // 4. 转换并获取消息列表
                if (messagesObj instanceof List) {
                    List<Message> messages = (List<Message>) messagesObj;

                    if (!messages.isEmpty()) {
                        // 5. 获取最后一个消息 (根据截图，最后一条是总结性的 AssistantMessage)
                        Message lastMsgObj = messages.getLast();

                        // 6. 类型判断并提取文本
                        if (lastMsgObj instanceof AssistantMessage) {
                            finalResult = lastMsgObj.getText();
                        } else  {
                            // 兜底：如果是其他类型的 Message (如 UserMessage)，也尝试获取内容
                            finalResult = lastMsgObj.getText();
                        }
                    }
                }
            }
        }

        // 7. 返回给主 Graph 的结果
        assert finalResult != null;
        return Map.of("searchResults", finalResult);
    }
}
