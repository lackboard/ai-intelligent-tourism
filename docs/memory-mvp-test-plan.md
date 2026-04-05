# 三层记忆 MVP 测试说明

这份说明对应当前最小可落地版本：

1. `L2` 只写 5 类稳定偏好
2. `L3` 只写 4 类高价值事件

## 前置条件

1. 后端已经启动
2. 如果刚更新过代码，请先重启后端
3. 数据库中允许 `MemorySchemaInitializer` 自动执行表结构升级

## 现有调试接口

1. `GET /api/memory/debug/snapshot`
2. `DELETE /api/memory/debug/reset`
3. `GET /api/memory/debug/recommended-cases`

## 推荐测试方式

优先直接运行脚本：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-memory-mvp.ps1
```

如果服务地址不是默认值，可指定：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-memory-mvp.ps1 -BaseUrl "http://localhost:8120/api"
```

## 脚本覆盖的测试点

1. `L2` 稳定偏好写入
2. `L3 itinerary_generated`
3. `L3 itinerary_accepted`
4. `L3 session_closed`
5. `L3 itinerary_rejected`
6. 同一 `visitorId` 跨 `threadId` 召回
7. 不同 `visitorId` 隔离

## 测试产物

脚本会把每一步的响应和快照写到：

```text
target/memory-mvp-test-output/
```

建议重点看这些文件：

1. `01-l2-snapshot.json`
2. `02-itinerary-generated-snapshot.json`
3. `03-itinerary-accepted-snapshot.json`
4. `04-session-closed-snapshot.json`
5. `05-itinerary-rejected-snapshot.json`
6. `06-recall-snapshot.json`
7. `07-isolation-snapshot.json`

## 如何判断通过

### L2

在 `01-l2-snapshot.json` 里应能看到：

1. `travel_pace`
2. `budget_range`
3. `hotel_preference`
4. `transport_preference`

### L3 generated

在 `02-itinerary-generated-snapshot.json` 的 `storedLongTermMemories` 中应出现：

```text
eventType = itinerary_generated
```

### L3 accepted

在 `03-itinerary-accepted-snapshot.json` 的 `storedLongTermMemories` 中应出现：

```text
eventType = itinerary_accepted
```

### L3 session_closed

在 `04-session-closed-snapshot.json` 的 `storedLongTermMemories` 中应出现：

```text
eventType = session_closed
```

### L3 rejected

在 `05-itinerary-rejected-snapshot.json` 的 `storedLongTermMemories` 中应同时出现：

1. `eventType = itinerary_generated`
2. `eventType = itinerary_rejected`

### 跨线程召回

在 `06-recall-snapshot.json` 中：

1. `recalledLongTermMemories` 不应为空
2. 其中应至少包含一条 `eventType = itinerary_generated`

### 隔离性

在 `07-isolation-snapshot.json` 中：

1. 不应召回旧访客的 `itinerary_generated`
2. 新访客的 `recalledLongTermMemories` 应只来自自己的数据

## 如果失败，优先看哪里

1. `L2` 空
原因通常是提取规则没有命中，或输入更像单次变量，被过滤掉了

2. `itinerary_generated` 没写入
原因通常是行程没有真正生成成功，或主链路没有走到 `rememberItinerary(...)`

3. `accepted/rejected/session_closed` 没写入
原因通常是关键词没命中，或当前线程里没有先出现 `itinerary_generated`

4. `recalledLongTermMemories` 空
原因通常是：
   - 没有写入高价值事件
   - 查询词不够贴近历史记忆
   - 向量检索结果被 `visitorId` 过滤后清空

5. 隔离性失败
优先检查 `memory_vector_store.metadata.visitorId` 和 `visitor_long_term_memory.visitor_id`

## 手工补测建议

如果你还想补测普通 SSE 链路，可以手工走：

1. `GET /api/ai/tourism_app/chat/sse_emitter`
2. 再调用 `snapshot`

这样可以补一遍 JDBC `L1` 与 Graph `L1` 的差异验证。
