package com.inkflow.module.agent.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.codec.ServerSentEvent;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * SSE 事件数据
 * 用于构建统一格式的 SSE 响应
 * 
 * Requirements: 15.3-15.5
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SSEEvent(
    SSEEventType type,
    String content,
    String agent,
    String tool,
    Map<String, Object> data,
    String message,
    Long tokens,
    Long latencyMs,
    LocalDateTime timestamp
) {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    /**
     * 创建内容事件
     */
    public static SSEEvent content(String content) {
        return new SSEEvent(SSEEventType.CONTENT, content, null, null, null, null, null, null, LocalDateTime.now());
    }
    
    /**
     * 创建思考事件
     */
    public static SSEEvent thought(String agent, String message) {
        return new SSEEvent(SSEEventType.THOUGHT, null, agent, null, null, message, null, null, LocalDateTime.now());
    }
    
    /**
     * 创建思考事件（简化版，无 agent）
     */
    public static SSEEvent thought(String message) {
        return new SSEEvent(SSEEventType.THOUGHT, null, null, null, null, message, null, null, LocalDateTime.now());
    }
    
    /**
     * 创建工具开始事件
     */
    public static SSEEvent toolStart(String agent, String tool) {
        return new SSEEvent(SSEEventType.TOOL_START, null, agent, tool, null, null, null, null, LocalDateTime.now());
    }
    
    /**
     * 创建工具结束事件
     */
    public static SSEEvent toolEnd(String agent, String tool, long durationMs) {
        return new SSEEvent(SSEEventType.TOOL_END, null, agent, tool, null, null, null, durationMs, LocalDateTime.now());
    }
    
    /**
     * 创建数据块事件
     */
    public static SSEEvent dataBlock(String agent, Map<String, Object> data) {
        return new SSEEvent(SSEEventType.DATA_BLOCK, null, agent, null, data, null, null, null, LocalDateTime.now());
    }
    
    /**
     * 创建警告事件
     */
    public static SSEEvent warning(String agent, String message) {
        return new SSEEvent(SSEEventType.WARNING, null, agent, null, null, message, null, null, LocalDateTime.now());
    }
    
    /**
     * 创建错误事件
     */
    public static SSEEvent error(String message) {
        return new SSEEvent(SSEEventType.ERROR, null, null, null, null, message, null, null, LocalDateTime.now());
    }
    
    /**
     * 创建完成事件
     */
    public static SSEEvent done(long tokens, long latencyMs) {
        return new SSEEvent(SSEEventType.DONE, null, null, null, null, null, tokens, latencyMs, LocalDateTime.now());
    }
    
    /**
     * 创建完成事件（简化版）
     */
    public static SSEEvent done() {
        return new SSEEvent(SSEEventType.DONE, null, null, null, null, null, null, null, LocalDateTime.now());
    }
    
    /**
     * 转换为 ServerSentEvent
     */
    public ServerSentEvent<String> toServerSentEvent() {
        try {
            String json = MAPPER.writeValueAsString(this);
            return ServerSentEvent.<String>builder()
                    .event(type.getValue())
                    .data(json)
                    .build();
        } catch (JsonProcessingException e) {
            return ServerSentEvent.<String>builder()
                    .event(SSEEventType.ERROR.getValue())
                    .data("{\"message\":\"序列化失败\"}")
                    .build();
        }
    }
    
    /**
     * 简化的内容事件（仅发送文本）
     */
    public ServerSentEvent<String> toContentSSE() {
        return ServerSentEvent.<String>builder()
                .event(type.getValue())
                .data(content)
                .build();
    }
}
