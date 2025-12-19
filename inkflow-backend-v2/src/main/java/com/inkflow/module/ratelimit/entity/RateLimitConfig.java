package com.inkflow.module.ratelimit.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户级别限流配置实体
 */
@Entity
@Table(name = "rate_limit_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RateLimitConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "bucket_capacity", nullable = false)
    @Builder.Default
    private int bucketCapacity = 100;

    @Column(name = "refill_rate", nullable = false)
    @Builder.Default
    private int refillRate = 10;

    @Column(name = "window_seconds", nullable = false)
    @Builder.Default
    private int windowSeconds = 60;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
