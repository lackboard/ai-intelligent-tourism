package com.learn.aiintelligenttourism.Model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatResponse {
    private String threadId;
    private String status;
    private String interruptingNode;
}

