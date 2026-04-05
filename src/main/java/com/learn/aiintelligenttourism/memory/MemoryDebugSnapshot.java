package com.learn.aiintelligenttourism.memory;

import java.util.List;

/**
 * 调试快照：把当前 visitor/thread 能看到的三层记忆一次性返回。
 */
public record MemoryDebugSnapshot(
        String visitorId,
        String threadId,
        String query,
        String workingMemorySource,
        String workingMemorySummary,
        List<WorkingMemoryMessageView> recentMessages,
        List<ProfileMemoryFact> profileFacts,
        List<StoredLongTermMemoryView> storedLongTermMemories,
        List<LongTermMemoryItem> recalledLongTermMemories
) {
}
