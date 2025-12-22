package com.learn.aiintelligenttourism.Model;

import lombok.Data;

@Data
public class ChatRequest {
    private String threadId;    // 用于标识会话/用户，保持记忆
    private String message;     // 用户输入的内容
    private String chatId;      // 业务ID，可选
}