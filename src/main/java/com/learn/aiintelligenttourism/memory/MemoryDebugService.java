package com.learn.aiintelligenttourism.memory;

import com.learn.aiintelligenttourism.app.TourismGraphService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 专门给 MVP 测试阶段使用的调试服务。
 * 它不参与主业务流程，只负责暴露“写了什么、召回了什么、怎么清理”的能力。
 */
@Service
public class MemoryDebugService {

    private static final int DEBUG_L3_STORED_LIMIT = 20;
    private static final int DEBUG_L3_RECALL_LIMIT = 8;

    private final WorkingMemoryService workingMemoryService;
    private final ProfileMemoryService profileMemoryService;
    private final LongTermMemoryService longTermMemoryService;
    private final TourismGraphService tourismGraphService;

    public MemoryDebugService(
            WorkingMemoryService workingMemoryService,
            ProfileMemoryService profileMemoryService,
            LongTermMemoryService longTermMemoryService,
            TourismGraphService tourismGraphService
    ) {
        this.workingMemoryService = workingMemoryService;
        this.profileMemoryService = profileMemoryService;
        this.longTermMemoryService = longTermMemoryService;
        this.tourismGraphService = tourismGraphService;
    }

    public MemoryDebugSnapshot buildSnapshot(String visitorId, String threadId, String query) {
        String normalizedVisitorId = normalize(visitorId);
        String normalizedThreadId = normalize(threadId);
        String recallQuery = normalizeQuery(query, normalizedThreadId);
        WorkingMemorySnapshot workingMemorySnapshot = resolveWorkingMemorySnapshot(normalizedThreadId);

        return new MemoryDebugSnapshot(
                normalizedVisitorId,
                normalizedThreadId,
                recallQuery,
                workingMemorySnapshot.source(),
                workingMemorySnapshot.summary(),
                workingMemorySnapshot.recentMessages(),
                profileMemoryService.findAllFacts(normalizedVisitorId),
                longTermMemoryService.findRecentStoredMemories(normalizedVisitorId, DEBUG_L3_STORED_LIMIT),
                longTermMemoryService.recall(normalizedVisitorId, recallQuery, DEBUG_L3_RECALL_LIMIT)
        );
    }

    /**
     * 返回删除结果明细，便于测试前确认是否清理干净。
     */
    public Map<String, Object> resetVisitorMemory(String visitorId, String threadId, boolean clearWorkingMemory) {
        String normalizedVisitorId = normalize(visitorId);
        String normalizedThreadId = normalize(threadId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("visitorId", normalizedVisitorId);
        result.put("threadId", normalizedThreadId);
        result.put("clearedProfileRows", profileMemoryService.clearVisitorFacts(normalizedVisitorId));
        result.put("clearedLongTermRows", longTermMemoryService.clearVisitorMemories(normalizedVisitorId));

        if (clearWorkingMemory && normalizedThreadId != null) {
            workingMemoryService.clearConversation(normalizedThreadId);
            result.put("clearedWorkingMemory", true);
        } else {
            result.put("clearedWorkingMemory", false);
        }
        return result;
    }

    public List<Map<String, String>> recommendedTestCases() {
        return List.of(
                testCase(
                        "画像写入",
                        "我打算五一带父母去杭州玩三天，不想太累，预算每人3000，酒店最好离西湖近一点。",
                        "应写入 L2：慢节奏、预算偏好、住宿偏好。"
                ),
                testCase(
                        "L1 连续上下文",
                        "第二轮补一句：我们还带一个8岁孩子，最好多安排轻松一点的地方。",
                        "应追加到 L1，同时 L2 可能强化慢节奏或兴趣主题。"
                ),
                testCase(
                        "L3 写入",
                        "让系统实际生成一版杭州 3 天行程。",
                        "应写入 eventType=itinerary_generated 的长期记忆。"
                ),
                testCase(
                        "L3 接受事件",
                        "在生成行程后补一句：可以，就按这个来。",
                        "应新增 eventType=itinerary_accepted。"
                ),
                testCase(
                        "L3 拒绝事件",
                        "在生成行程后补一句：这个太赶了，换一个轻松一点的。",
                        "应新增 eventType=itinerary_rejected。"
                ),
                testCase(
                        "L3 会话收尾",
                        "在线程已经产生过高价值事件后补一句：谢谢，先这样吧。",
                        "应新增 eventType=session_closed。"
                ),
                testCase(
                        "L3 召回",
                        "新开 threadId，但 visitorId 不变，再问：这次还是想带家人去杭州，节奏轻松点。",
                        "应能在 snapshot 的 recalledLongTermMemories 里看到之前的杭州/慢节奏相关记忆。"
                ),
                testCase(
                        "隔离性",
                        "换一个全新的 visitorId，输入同样问题。",
                        "新 visitor 不应读到旧 visitor 的 L2/L3。"
                )
        );
    }

    private Map<String, String> testCase(String name, String prompt, String expected) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("prompt", prompt);
        item.put("expected", expected);
        return item;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeQuery(String query, String fallbackThreadId) {
        if (query != null && !query.isBlank()) {
            return query.trim();
        }
        if (fallbackThreadId != null && !fallbackThreadId.isBlank()) {
            return "threadId=" + fallbackThreadId;
        }
        return "memory debug";
    }

    private WorkingMemorySnapshot resolveWorkingMemorySnapshot(String threadId) {
        WorkingMemorySnapshot jdbcSnapshot = workingMemoryService.getWorkingMemorySnapshot(
                threadId,
                MemoryConstants.WORKING_MEMORY_WINDOW_SIZE
        );
        if (jdbcSnapshot.hasContent()) {
            return jdbcSnapshot;
        }
        return tourismGraphService.getGraphWorkingMemorySnapshot(
                threadId,
                MemoryConstants.WORKING_MEMORY_WINDOW_SIZE
        );
    }
}
