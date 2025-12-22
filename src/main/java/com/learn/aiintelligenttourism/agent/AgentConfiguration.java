package com.learn.aiintelligenttourism.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolretry.ToolRetryInterceptor;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.learn.aiintelligenttourism.tools.JuheWeatherTool;
import com.learn.aiintelligenttourism.tools.TourismKnowledgeTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfiguration {


    @Bean
    public ReactAgent researchAgent(
            ChatModel chatModel,
            ToolCallback[] allTools
    ) {
        String systemInstruction = """
           你是 "AI 智游" 团队的 **首席信息采集员**。
       
           你的唯一目标：根据用户的【目的地】和【时间】，调用工具获取原始数据，并将其**原样**传递给下游节点。

           请严格遵循以下执行流程（ReAct 模式）：

           1.  **提取要素**：识别用户要去哪里（Destination）、什么时候去（Date）。
           2.  **数据采集**：
               *   **天气查询**：调用天气工具，获取指定日期的天气预报。
               *   **资讯检索**：调用搜索工具，查找当地的景点信息、近期活动或基础游玩资讯。
           3.  **客观输出**：将工具返回的结果整理输出。

           **【核心原则与严厉禁令】**
           1.  **禁止主观总结**：不要尝试对工具返回的内容进行“提炼”、“润色”或“写成通顺的段落”。工具返回什么，你就保留什么核心事实。
           2.  **禁止提出建议**：**绝对不要**根据搜索结果给出“建议您穿衣...”或“建议您避开...”之类的文字。这些是【规划节点】的工作，不是你的工作。
           3.  **诚实反馈缺失**：如果工具调用没有返回结果，或返回内容为空，请直接输出“未检索到相关[天气/攻略]信息”，**严禁编造**或使用通用话术（如“当地四季如春”）来填充。
           4.  **禁止规划**：不要生成任何行程安排。

           **【输出格式要求】**
           请按以下结构直接罗列数据：

           【天气数据】：
           [工具返回的具体天气内容]

           【搜集到的资讯】：
           [工具返回的具体搜索内容]

           （如果某项未搜到，直接填“未检索到数据”）
           """;

        return ReactAgent.builder()
                .name("research_agent")
                .model(chatModel)
                .hooks(ModelCallLimitHook.builder().runLimit(5).build())
                .systemPrompt(systemInstruction)
                .tools(allTools) // 赋予它“眼睛”和“耳朵”
                .interceptors(ToolRetryInterceptor.builder()
                        .maxRetries(2)
                        .onFailure(ToolRetryInterceptor.OnFailureBehavior.RETURN_MESSAGE)
                        .build())
                .build();
    }
}