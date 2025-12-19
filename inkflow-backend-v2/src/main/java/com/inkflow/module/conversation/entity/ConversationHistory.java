package com.inkflow.module.conversation.entity;

import com.inkflow.module.project.entity.CreationPhase;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 对话历史实体
 * 存储 AI 对话消息，支持 ChatMemory 持久化
 *
 * @author zsg
 * @date 2025/12/17
 */
@Entity
@Table(name = "conversation_history", indexes = {
    @Index(name = "idx_conversation_history_user", columnList = "user_id"),
    @Index(name = "idx_conversation_history_project", columnList = "project_id"),
    @Index(name = "idx_conversation_history_session", columnList = "session_id, message_order")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "project_id")
    private UUID projectId;

    /**
     * 会话ID，用于分组同一会话的消息
     */
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    /**
     * 消息角色: user, assistant, system, tool
     */
    @Column(nullable = false, length = 20)
    private String role;

    /**
     * 消息内容
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * 工具调用信息 (如果是 tool 角色)
     */
    @Column(name = "tool_calls", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> toolCalls;

    /**
     * 消息顺序
     */
    @Column(name = "message_order", nullable = false)
    private Integer messageOrder;

    /**
     * 创作阶段 (消息发送时的阶段)
     */
    @Column(name = "creation_phase", length = 50)
    @Enumerated(EnumType.STRING)
    private CreationPhase creationPhase;

    /**
     * 创建时间
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
