package com.inkflow.module.session.entity;

import com.inkflow.module.project.entity.CreationPhase;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户会话实体
 */
@Entity
@Table(name = "user_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "device_info")
    private String deviceInfo;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "current_project_id")
    private UUID currentProjectId;

    @Column(name = "current_phase")
    @Enumerated(EnumType.STRING)
    private CreationPhase currentPhase;

    @Column(name = "last_activity_at", nullable = false)
    private LocalDateTime lastActivityAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (lastActivityAt == null) {
            lastActivityAt = LocalDateTime.now();
        }
    }

    /**
     * 检查会话是否已过期
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * 更新最后活动时间
     */
    public void updateActivity() {
        this.lastActivityAt = LocalDateTime.now();
    }

    /**
     * 终止会话
     */
    public void terminate() {
        this.active = false;
    }
}
