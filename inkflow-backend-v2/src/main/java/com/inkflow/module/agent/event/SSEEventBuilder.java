package com.inkflow.module.agent.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.codec.ServerSentEvent;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * SSE 事件构建器（统一版本）
 * 提供统一的 SSE 事件构建方法，确保前端可以一致地处理响应
 * 
 * <p>标准事件类型：
 * <ul>
 * <li>content - 流式内容输出</li>
 * <li>thought - Agent 思考过程</li>
 * <li>tool_start - 工具调用开始</li>
 * <li>tool_end - 工具调用结束</li>
 * <li>done - 处理完成（含 token 使用量）</li>
 * <li>error - 错误信息（用户友好消息）</li>
 * </ul>
 * 
 * Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6
 */
public class SSEEventBuilder {

    private static final ObjectMapper MAPPER = createObjectMapper();

    // ==================== 事件类型常量 ====================

    /**
     * 内容事件类型 - 流式输出的文本内容
     * Requirements: 7.1
     */
    public static final String EVENT_CONTENT = "content";

    /**
     * 思考事件类型 - Agent 的思考过程
     * Requirements: 7.2
     */
    public static final String EVENT_THOUGHT = "thought";

    /**
     * 工具开始事件类型 - Tool 调用开始
     * Requirements: 7.3
     */
    public static final String EVENT_TOOL_START = "tool_start";

    /**
     * 工具结束事件类型 - Tool 调用完成
     * Requirements: 7.4
     */
    public static final String EVENT_TOOL_END = "tool_end";

    /**
     * 完成事件类型 - 处理结束，包含 token 使用量
     * Requirements: 7.5
     */
    public static final String EVENT_DONE = "done";

    /**
     * 错误事件类型 - 处理失败，包含用户友好消息
     * Requirements: 7.6
     */
    public static final String EVENT_ERROR = "error";

    /**
     * 预检结果事件类型 - 预检完成，包含检查结果
     * Requirements: 9.3
     */
    public static final String EVENT_PREFLIGHT_RESULT = "preflight_result";

    /**
     * 一致性警告事件类型 - 检测到一致性问题
     * Requirements: 9.1
     */
    public static final String EVENT_CONSISTENCY_WARNING = "consistency_warning";

    /**
     * 一致性检查完成事件类型 - 一致性检查完成
     * Requirements: 9.1
     */
    public static final String EVENT_CONSISTENCY_CHECK_COMPLETE = "consistency_check_complete";

    /**
     * 演进更新事件类型 - 演进快照创建或更新
     * Requirements: 9.2
     */
    public static final String EVENT_EVOLUTION_UPDATE = "evolution_update";

    /**
     * 快照创建事件类型 - 状态快照创建
     * Requirements: 9.2
     */
    public static final String EVENT_SNAPSHOT_CREATED = "snapshot_created";

    // ==================== 私有构造器 ====================

    private SSEEventBuilder() {
        // 工具类，禁止实例化
    }

    // ==================== 内容事件 ====================

    /**
     * 构建内容事件
     * 用于流式输出 AI 生成的文本内容
     * 
     * Requirements: 7.1
     * 
     * @param content 文本内容
     * @return ServerSentEvent
     */
    public static ServerSentEvent<String> content(String content) {
        return ServerSentEvent.<String>builder()
                .event(EVENT_CONTENT)
                .data(content)
                .build();
    }

    /**
     * 构建内容事件（带元数据）
     * 
     * Requirements: 7.1
     * 
     * @param content 文本内容
     * @param metadata 元数据
     * @return ServerSentEvent
     */
    public static ServerSentEvent<String> content(String content, Map<String, Object> metadata) {
        Map<String, Object> data = new HashMap<>();
        data.put("content", content);
        if (metadata != null) {
            data.putAll(metadata);
        }
        return buildJsonEvent(EVENT_CONTENT, data);
    }

    // ==================== 思考事件 ====================

    /**
     * 构建思考事件
     * 用于展示 Agent 的思考过程
     * 
     * Requirements: 7.2
     * 
     * @param agentName Agent 名称
     * @param thought 思考内容
     * @return ServerSentEvent
     */
    public static ServerSentEvent<String> thought(String agentName, String thought) {
        Map<String, Object> data = new HashMap<>();
        data.put("agent", agentName);
        data.put("message", thought);
        data.put("timestamp", LocalDateTime.now().toString());
        return buildJsonEvent(EVENT_THOUGHT, data);
    }

    /**
     * 构建思考事件（简化版）
     * 
     * Requirements: 7.2
     * 
     * @param thought 思考内容
     * @return ServerSentEvent
     */
    public static ServerSentEvent<String> thought(String thought) {
        Map<String, Object> data = new HashMap<>();
        data.put("message", thought);
        data.put("timestamp", LocalDateTime.now().toString());
        return buildJsonEvent(EVENT_THOUGHT, data);
    }

    // ==================== 工具事件 ====================

    /**
     * 构建工具开始事件
     * 用于通知前端 Tool 调用开始
     * 
     * Requirements: 7.3
     * 
     * @param agentName Agent 名称
     * @param toolName 工具名称
     * @return ServerSentEvent
     */
    public static ServerSentEvent<String> toolStart(String agentName, String toolName) {
        Map<String, Object> data = new HashMap<>();
        data.put("agent", agentName);
        data.put("tool", toolName);
        data.put("timestamp", LocalDateTime.now().toString());
        return buildJsonEvent(EVENT_TOOL_START, data);
    }

    /**
     * 构建工具开始事件（带参数）
     * 
     * Requirements: 7.3
     * 
     * @param agentName Agent 名称
     * @param toolName 工具名称
     * @param parameters 工具参数
     * @return ServerSentEvent
     */
    public static ServerSentEvent<String> toolStart(String agentName, String toolName, Map<String, Object> parameters) {
        Map<String, Object> data = new HashMap<>();
        data.put("agent", agentName);
        data.put("tool", toolName);
        data.put("parameters", parameters);
        data.put("timestamp", LocalDateTime.now().toString());
        return buildJsonEvent(EVENT_TOOL_START, data);
    }

    /**
     * 构建工具结束事件
     * 用于通知前端 Tool 调用完成
     * 
     * Requirements: 7.4
     * 
     * @param agentName Agent 名称
     * @param toolName 工具名称
     * @param durationMs 执行耗时（毫秒）
     * @return ServerSentEvent
     */
    public static ServerSentEvent<String> toolEnd(String agentName, String toolName, long durationMs) {
        Map<String, Object> data = new HashMap<>();
        data.put("agent", agentName);
        data.put("tool", toolName);
        data.put("durationMs", durationMs);
        data.put("timestamp", LocalDateTime.now().toString());
        return buildJsonEvent(EVENT_TOOL_END, data);
    }

    /**
     * 构建工具结束事件（带结果）
     * 
     * Requirements: 7.4
     * 
     * @param agentName Agent 名称
     * @param toolName 工具名称
     * @param durationMs 执行耗时（毫秒）
     * @param result 工具执行结果
     * @return ServerSentEvent
     */
    public static ServerSentEvent<String> toolEnd(String agentName, String toolName, long durationMs, Object result) {
        Map<String, Object> data = new HashMap<>();
        data.put("agent", agentName);
        data.put("tool", toolName);
        data.put("durationMs", durationMs);
        data.put("result", result);
        data.put("timestamp", LocalDateTime.now().toString());
        return buildJsonEvent(EVENT_TOOL_END, data);
    }

    // ==================== 完成事件 ====================


    /**
     * 构建完成事件
     * 用于通知前端处理完成，包含 token 使用量
     * 
     * Requirements: 7.5
     * 
     * @param promptTokens 输入 token 数量
     * @param completionTokens 输出 token 数量
     * @param totalTokens 总 token 数量
     * @param latencyMs 总耗时（毫秒）
     * @return ServerSentEvent
     */
    public static ServerSentEvent<String> done(long promptTokens, long completionTokens, long totalTokens, long latencyMs) {
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> usage = new HashMap<>();
        usage.put("promptTokens", promptTokens);
        usage.put("completionTokens", completionTokens);
        usage.put("totalTokens", totalTokens);
        data.put("usage", usage);
        data.put("latencyMs", latencyMs);
        data.put("timestamp", LocalDateTime.now().toString());
        return buildJsonEvent(EVENT_DONE, data);
    }

    /**
     * 构建完成事件（简化版，仅总 token）
     * 
     * Requirements: 7.5
     * 
     * @param totalTokens 总 token 数量
     * @param latencyMs 总耗时（毫秒）
     * @return ServerSentEvent
     */
    public static ServerSentEvent<String> done(long totalTokens, long latencyMs) {
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> usage = new HashMap<>();
        usage.put("totalTokens", totalTokens);
        data.put("usage", usage);
        data.put("latencyMs", latencyMs);
        data.put("timestamp", LocalDateTime.now().toString());
        return buildJsonEvent(EVENT_DONE, data);
    }

    /**
     * 构建完成事件（无 token 信息）
     * 
     * Requirements: 7.5
     * 
     * @return ServerSentEvent
     */
    public static ServerSentEvent<String> done() {
        Map<String, Object> data = new HashMap<>();
        data.put("timestamp", LocalDateTime.now().toString());
        return buildJsonEvent(EVENT_DONE, data);
    }

    /**
     * 构建完成事件（带自定义数据）
     * 
     * Requirements: 7.5
     * 
     * @param tokenUsage token 使用量信息
     * @param latencyMs 总耗时（毫秒）
     * @param metadata 额外元数据
     * @return ServerSentEvent
     */
    public static ServerSentEvent<String> done(Map<String, Object> tokenUsage, long latencyMs, Map<String, Object> metadata) {
        Map<String, Object> data = new HashMap<>();
        if (tokenUsage != null) {
            data.put("usage", tokenUsage);
        }
        data.put("latencyMs", latencyMs);
        if (metadata != null) {
            data.putAll(metadata);
        }
        data.put("timestamp", LocalDateTime.now().toString());
        return buildJsonEvent(EVENT_DONE, data);
    }

    // ==================== 错误事件 ====================

    /**
     * 构建错误事件
     * 用于通知前端处理失败，包含用户友好消息
     * 
     * Requirements: 7.6
     * 
     * @param userFriendlyMessage 用户友好的错误消息
     * @return ServerSentEvent
     */
    public static ServerSentEvent<String> error(String userFriendlyMessage) {
        Map<String, Object> data = new HashMap<>();
        data.put("message", userFriendlyMessage);
        data.put("timestamp", LocalDateTime.now().toString());
        return buildJsonEvent(EVENT_ERROR, data);
    }

    /**
     * 构建错误事件（带错误码）
     * 
     * Requirements: 7.6
     * 
     * @param userFriendlyMessage 用户友好的错误消息
     * @param errorCode 错误码
     * @return ServerSentEvent
     */
    public static ServerSentEvent<String> error(String userFriendlyMessage, String errorCode) {
        Map<String, Object> data = new HashMap<>();
        data.put("message", userFriendlyMessage);
        data.put("code", errorCode);
        data.put("timestamp", LocalDateTime.now().toString());
        return buildJsonEvent(EVENT_ERROR, data);
    }

    /**
     * 构建错误事件（从异常）
     * 自动转换为用户友好消息
     * 
     * Requirements: 7.6
     * 
     * @param exception 异常
     * @return ServerSentEvent
     */
    public static ServerSentEvent<String> error(Throwable exception) {
        String userFriendlyMessage = toUserFriendlyMessage(exception);
        Map<String, Object> data = new HashMap<>();
        data.put("message", userFriendlyMessage);
        data.put("errorType", exception.getClass().getSimpleName());
        data.put("timestamp", LocalDateTime.now().toString());
        return buildJsonEvent(EVENT_ERROR, data);
    }

    /**
     * 构建错误事件（从异常，带自定义消息）
     * 
     * Requirements: 7.6
     * 
     * @param userFriendlyMessage 用户友好的错误消息
     * @param exception 异常（用于记录详细信息）
     * @return ServerSentEvent
     */
    public static ServerSentEvent<String> error(String userFriendlyMessage, Throwable exception) {
        Map<String, Object> data = new HashMap<>();
        data.put("message", userFriendlyMessage);
        data.put("errorType", exception.getClass().getSimpleName());
        data.put("timestamp", LocalDateTime.now().toString());
        return buildJsonEvent(EVENT_ERROR, data);
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建 JSON 格式的 SSE 事件
     */
    private static ServerSentEvent<String> buildJsonEvent(String eventType, Map<String, Object> data) {
        try {
            String json = MAPPER.writeValueAsString(data);
            return ServerSentEvent.<String>builder()
                    .event(eventType)
                    .data(json)
                    .build();
        } catch (JsonProcessingException e) {
            // 序列化失败时返回简单错误
            return ServerSentEvent.<String>builder()
                    .event(EVENT_ERROR)
                    .data("{\"message\":\"事件序列化失败\"}")
                    .build();
        }
    }

    /**
     * 将异常转换为用户友好消息
     */
    private static String toUserFriendlyMessage(Throwable exception) {
        if (exception == null) {
            return "发生未知错误";
        }

        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "处理请求时发生错误，请稍后重试";
        }

        // 常见异常的友好消息映射
        String className = exception.getClass().getSimpleName();
        return switch (className) {
            case "TimeoutException" -> "请求超时，请稍后重试";
            case "ConnectException" -> "网络连接失败，请检查网络后重试";
            case "IllegalArgumentException" -> "请求参数无效：" + message;
            case "IllegalStateException" -> "系统状态异常，请稍后重试";
            case "NullPointerException" -> "系统内部错误，请稍后重试";
            default -> message.length() > 200 ? message.substring(0, 200) + "..." : message;
        };
    }

    /**
     * 创建配置好的 ObjectMapper
     */
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    // ==================== 一致性和演进事件 ====================

    /**
     * 构建预检结果事件
     * 用于通知前端预检完成
     * 
     * Requirements: 9.3
     * 
     * @param canProceed 是否可以继续
     * @param issueCount 问题数量
     * @param issues 问题列表
     * @return ServerSentEvent
     */
    public static ServerSentEvent<String> preflightResult(boolean canProceed, int issueCount, java.util.List<?> issues) {
        Map<String, Object> data = new HashMap<>();
        data.put("canProceed", canProceed);
        data.put("issueCount", issueCount);
        if (issues != null && !issues.isEmpty()) {
            data.put("issues", issues);
        }
        data.put("timestamp", LocalDateTime.now().toString());
        return buildJsonEvent(EVENT_PREFLIGHT_RESULT, data);
    }

    /**
     * 构建预检结果事件（简化版）
     * 
     * Requirements: 9.3
     * 
     * @param canProceed 是否可以继续
     * @return ServerSentEvent
     */
    public static ServerSentEvent<String> preflightResult(boolean canProceed) {
        Map<String, Object> data = new HashMap<>();
        data.put("canProceed", canProceed);
        data.put("timestamp", LocalDateTime.now().toString());
        return buildJsonEvent(EVENT_PREFLIGHT_RESULT, data);
    }

    /**
     * 构建一致性警告事件
     * 用于通知前端检测到一致性问题
     * 
     * Requirements: 9.1
     * 
     * @param warningType 警告类型
     * @param severity 严重程度
     * @param description 问题描述
     * @param suggestion 建议
     * @return ServerSentEvent
     */
    public static ServerSentEvent<String> consistencyWarning(String warningType, String severity, 
                                                              String description, String suggestion) {
        Map<String, Object> data = new HashMap<>();
        data.put("warningType", warningType);
        data.put("severity", severity);
        data.put("description", description);
        if (suggestion != null) {
            data.put("suggestion", suggestion);
        }
        data.put("timestamp", LocalDateTime.now().toString());
        return buildJsonEvent(EVENT_CONSISTENCY_WARNING, data);
    }

    /**
     * 构建一致性警告事件（简化版）
     * 
     * Requirements: 9.1
     * 
     * @param description 问题描述
     * @return ServerSentEvent
     */
    public static ServerSentEvent<String> consistencyWarning(String description) {
        Map<String, Object> data = new HashMap<>();
        data.put("description", description);
        data.put("timestamp", LocalDateTime.now().toString());
        return buildJsonEvent(EVENT_CONSISTENCY_WARNING, data);
    }

    /**
     * 构建一致性检查完成事件
     * 
     * Requirements: 9.1
     * 
     * @param warningCount 警告数量
     * @param errorCount 错误数量
     * @return ServerSentEvent
     */
    public static ServerSentEvent<String> consistencyCheckComplete(int warningCount, int errorCount) {
        Map<String, Object> data = new HashMap<>();
        data.put("warningCount", warningCount);
        data.put("errorCount", errorCount);
        data.put("passed", errorCount == 0);
        data.put("timestamp", LocalDateTime.now().toString());
        return buildJsonEvent(EVENT_CONSISTENCY_CHECK_COMPLETE, data);
    }

    /**
     * 构建演进更新事件
     * 用于通知前端演进快照已更新
     * 
     * Requirements: 9.2
     * 
     * @param entityType 实体类型
     * @param entityId 实体ID
     * @param entityName 实体名称
     * @return ServerSentEvent
     */
    public static ServerSentEvent<String> evolutionUpdate(String entityType, String entityId, String entityName) {
        Map<String, Object> data = new HashMap<>();
        data.put("entityType", entityType);
        data.put("entityId", entityId);
        if (entityName != null) {
            data.put("entityName", entityName);
        }
        data.put("timestamp", LocalDateTime.now().toString());
        return buildJsonEvent(EVENT_EVOLUTION_UPDATE, data);
    }

    /**
     * 构建快照创建事件
     * 
     * Requirements: 9.2
     * 
     * @param snapshotId 快照ID
     * @param entityType 实体类型
     * @param entityId 实体ID
     * @param isKeyframe 是否为关键帧
     * @return ServerSentEvent
     */
    public static ServerSentEvent<String> snapshotCreated(String snapshotId, String entityType, 
                                                          String entityId, boolean isKeyframe) {
        Map<String, Object> data = new HashMap<>();
        data.put("snapshotId", snapshotId);
        data.put("entityType", entityType);
        data.put("entityId", entityId);
        data.put("isKeyframe", isKeyframe);
        data.put("timestamp", LocalDateTime.now().toString());
        return buildJsonEvent(EVENT_SNAPSHOT_CREATED, data);
    }

    // ==================== 便捷方法：从 SSEEvent 转换 ====================

    /**
     * 将 SSEEvent 转换为 ServerSentEvent
     * 
     * @param event SSEEvent 对象
     * @return ServerSentEvent
     */
    public static ServerSentEvent<String> fromSSEEvent(SSEEvent event) {
        return event.toServerSentEvent();
    }
}
