package com.inkflow.module.ai_bridge.chat;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Tool 状态事件 DTO
 * 用于 SSE 流中传递 Tool 执行状态
 * 
 * Requirements: 6.3, 6.4
 *
 * @author zsg
 * @date 2025/12/17
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolStatusEvent(
    String eventType,
    String toolName,
    Boolean success,
    String message,
    Long durationMs
) {
    /**
     * 创建 Tool 开始事件
     */
    public static ToolStatusEvent toolStart(String toolName) {
        return new ToolStatusEvent("tool_start", toolName, null, null, null);
    }

    /**
     * 创建 Tool 结束事件（成功）
     */
    public static ToolStatusEvent toolEnd(String toolName, boolean success) {
        return new ToolStatusEvent("tool_end", toolName, success, null, null);
    }

    /**
     * 创建 Tool 结束事件（带详情）
     */
    public static ToolStatusEvent toolEnd(String toolName, boolean success, String message, Long durationMs) {
        return new ToolStatusEvent("tool_end", toolName, success, message, durationMs);
    }

    /**
     * 获取事件类型
     */
    public String getEventType() {
        return eventType;
    }

    /**
     * 获取工具名称
     */
    public String getToolName() {
        return toolName;
    }
}
