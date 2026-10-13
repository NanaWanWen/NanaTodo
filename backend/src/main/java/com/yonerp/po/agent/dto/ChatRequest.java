package com.yonerp.po.agent.dto;

import lombok.Data;

import java.util.List;

/**
 * Agent 聊天请求
 */
@Data
public class ChatRequest {

    /** 用户消息 */
    private String message;

    /** 会话历史（最近 N 条，可选） */
    private List<ChatMessage> history;

    @Data
    public static class ChatMessage {
        private String role;    // user / assistant
        private String content;
    }
}
