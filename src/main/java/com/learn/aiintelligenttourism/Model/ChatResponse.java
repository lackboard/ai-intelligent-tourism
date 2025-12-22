package com.learn.aiintelligenttourism.Model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatResponse {
    private String threadId;
    private String status;          // "FINISHED" (完成) 或 "INTERRUPTED" (中断/待用户输入)
    private String interruptingNode; // 如果中断，记录是哪个节点中断的
}