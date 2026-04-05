package com.learn.aiintelligenttourism.Model;

import lombok.Data;

@Data
public class ChatRequest {
    /**
     * 同一个访客的稳定标识。
     * 当前项目还没有登录体系，因此前端会为匿名访客生成 visitorId 并持久化到 localStorage。
     */
    private String visitorId;

    private String threadId;
    private String message;
    /**
     * 兼容旧字段，Graph 主链路已统一改为使用 threadId。
     */
    @Deprecated
    private String chatId;
}

