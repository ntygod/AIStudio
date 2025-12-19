package com.inkflow.module.ratelimit.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 端点级别限流规则实体
 */
@Entity
@Table(name = "rate_limit_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RateLimitRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "endpoint_pattern", nullable = false)
    private String endpointPattern;

    @Column(name = "http_method")
    private String httpMethod;

    @Column(name = "bucket_capacity", nullable = false)
    @Builder.Default
    private int bucketCapacity = 100;

    @Column(name = "refill_rate", nullable = false)
    @Builder.Default
    private int refillRate = 10;

    @Column(name = "priority", nullable = false)
    @Builder.Default
    private int priority = 0;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "description")
    private String description;

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

    /**
     * 检查路径是否匹配此规则
     */
    public boolean matches(String path, String method) {
        if (!enabled) return false;
        
        // 检查 HTTP 方法
        if (httpMethod != null && !httpMethod.equalsIgnoreCase(method)) {
            return false;
        }
        
        // 简单的通配符匹配
        return matchesPattern(path, endpointPattern);
    }

    private boolean matchesPattern(String path, String pattern) {
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return path.startsWith(prefix);
        } else if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            return path.startsWith(prefix) && !path.substring(prefix.length()).contains("/");
        } else {
            return path.equals(pattern);
        }
    }
}
