package com.inkflow.module.ai_bridge.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Tool 调用日志实体
 * 
 * Requirements: 17.1, 17.2, 17.3
 *
 * @author zsg
 * @date 2025/12/17
 */
@Entity
@Table(name = "tool_invocation_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolInvocationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Column(name = "tool_name", nullable = false, length = 100)
    private String toolName;

    @Column(columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> parameters;

    @Column(nullable = false)
    @Builder.Default
    private Boolean success = true;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "result_summary", columnDefinition = "TEXT")
    private String resultSummary;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "error_stack_trace", columnDefinition = "TEXT")
    private String errorStackTrace;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * 创建成功日志
     */
    public static ToolInvocationLog success(
            String requestId,
            String toolName,
            Map<String, Object> parameters,
            Long durationMs,
            String resultSummary,
            UUID userId,
            UUID projectId) {
        return ToolInvocationLog.builder()
            .requestId(requestId)
            .toolName(toolName)
            .parameters(parameters)
            .success(true)
            .durationMs(durationMs)
            .resultSummary(resultSummary)
            .userId(userId)
            .projectId(projectId)
            .createdAt(LocalDateTime.now())
            .build();
    }

    /**
     * 创建失败日志
     */
    public static ToolInvocationLog failure(
            String requestId,
            String toolName,
            Map<String, Object> parameters,
            Long durationMs,
            String errorMessage,
            String errorStackTrace,
            UUID userId,
            UUID projectId) {
        return ToolInvocationLog.builder()
            .requestId(requestId)
            .toolName(toolName)
            .parameters(parameters)
            .success(false)
            .durationMs(durationMs)
            .errorMessage(errorMessage)
            .errorStackTrace(errorStackTrace)
            .userId(userId)
            .projectId(projectId)
            .createdAt(LocalDateTime.now())
            .build();
    }
}
