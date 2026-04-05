package com.learn.aiintelligenttourism.controller;

import com.learn.aiintelligenttourism.memory.MemoryDebugService;
import com.learn.aiintelligenttourism.memory.MemoryDebugSnapshot;
import com.learn.aiintelligenttourism.memory.MemorySignalExtractor;
import com.learn.aiintelligenttourism.memory.ProfileMemoryFact;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MVP 阶段的记忆调试接口。
 * 当前项目没有登录体系，因此测试时必须显式观察 visitorId 维度的数据是否真的隔离。
 */
@RestController
@RequestMapping("/memory/debug")
public class MemoryDebugController {

    private final MemoryDebugService memoryDebugService;
    private final MemorySignalExtractor memorySignalExtractor;

    public MemoryDebugController(MemoryDebugService memoryDebugService, MemorySignalExtractor memorySignalExtractor) {
        this.memoryDebugService = memoryDebugService;
        this.memorySignalExtractor = memorySignalExtractor;
    }

    /**
     * 测试大模型意图提取效果专用接口
     * 在 Knife4j 页面输入各种复杂的话，直接看大模型 JSON 提取结果！
     */
    @GetMapping("/extract-test")
    public Map<String, Object> testSignalExtraction(@RequestParam String text) {
        Map<String, Object> result = new HashMap<>();
        result.put("OriginalText", text);
        result.put("ProfileFacts_L2", memorySignalExtractor.extractProfileFacts(text));
        result.put("IsItineraryAccepted_L3", memorySignalExtractor.isItineraryAcceptedMessage(text));
        result.put("IsItineraryRejected_L3", memorySignalExtractor.isItineraryRejectedMessage(text));
        result.put("Destination_L1", memorySignalExtractor.extractDestination(text));
        return result;
    }

    /**
     * 同时查看 L1/L2/L3 三层记忆。
     * 推荐在每一轮测试后都调用一次，用结果反推“写入”还是“召回”出了问题。
     */
    @GetMapping("/snapshot")
    public MemoryDebugSnapshot snapshot(
            @RequestParam String visitorId,
            @RequestParam String threadId,
            @RequestParam(required = false) String query
    ) {
        return memoryDebugService.buildSnapshot(visitorId, threadId, query);
    }

    /**
     * 测试前清理指定访客的记忆数据。
     * Graph checkpoint 仍建议通过更换 threadId 的方式隔离，因为 Graph 里可能保留流程态。
     */
    @DeleteMapping("/reset")
    public Map<String, Object> reset(
            @RequestParam String visitorId,
            @RequestParam(required = false) String threadId,
            @RequestParam(defaultValue = "true") boolean clearWorkingMemory
    ) {
        return memoryDebugService.resetVisitorMemory(visitorId, threadId, clearWorkingMemory);
    }

    /**
     * 给手工测试准备的话术模板，避免测试时临时编 prompt 导致变量太多。
     */
    @GetMapping("/recommended-cases")
    public List<Map<String, String>> recommendedCases() {
        return memoryDebugService.recommendedTestCases();
    }
}
