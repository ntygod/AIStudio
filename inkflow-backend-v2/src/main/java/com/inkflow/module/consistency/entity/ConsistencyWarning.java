package com.inkflow.module.consistency.entity;

import com.inkflow.module.evolution.entity.EntityType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 一致性警告实体
 * 存储静默检测到的一致性问题
 *
 * @author zsg
 * @date 2025/12/17
 */
@Entity
@Table(name = "consistency_warnings", indexes = {
    @Index(name = "idx_consistency_warning_project", columnList = "project_id"),
    @Index(name = "idx_consistency_warning_entity", columnList = "entity_id"),
    @Index(name = "idx_consistency_warning_resolved", columnList = "resolved")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsistencyWarning {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "entity_type", length = 50)
    @Enumerated(EnumType.STRING)
    private EntityType entityType;

    @Column(name = "entity_name")
    private String entityName;

    /**
     * 警告类型
     */
    @Column(name = "warning_type", length = 50, nullable = false)
    @Enumerated(EnumType.STRING)
    private WarningType warningType;

    /**
     * 严重程度
     */
    @Column(length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private Severity severity;

    /**
     * 警告描述
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    /**
     * 修复建议
     */
    @Column(columnDefinition = "TEXT")
    private String suggestion;

    /**
     * 相关字段路径
     */
    @Column(name = "field_path")
    private String fieldPath;

    /**
     * 期望值
     */
    @Column(name = "expected_value", columnDefinition = "TEXT")
    private String expectedValue;

    /**
     * 实际值
     */
    @Column(name = "actual_value", columnDefinition = "TEXT")
    private String actualValue;

    /**
     * 相关实体ID列表
     */
    @Column(name = "related_entity_ids", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<UUID> relatedEntityIds;

    /**
     * 建议的解决方案
     */
    @Column(name = "suggested_resolution", columnDefinition = "TEXT")
    private String suggestedResolution;

    /**
     * 是否已解决
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean resolved = false;

    /**
     * 是否已忽略
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean dismissed = false;

    /**
     * 解决方法
     */
    @Column(name = "resolution_method")
    private String resolutionMethod;

    /**
     * 解决时间
     */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

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

    /**
     * 警告类型枚举
     */
    public enum WarningType {
        CHARACTER_INCONSISTENCY,    // 角色不一致
        TIMELINE_CONFLICT,          // 时间线冲突
        SETTING_VIOLATION,          // 设定违反
        PLOT_LOOP_UNCLOSED,         // 伏笔未闭合
        RELATIONSHIP_CONFLICT,      // 关系冲突
        NAME_DUPLICATE,             // 名称重复
        REFERENCE_BROKEN,           // 引用断裂
        OTHER                       // 其他
    }

    /**
     * 严重程度枚举
     */
    public enum Severity {
        INFO,       // 信息
        WARNING,    // 警告
        ERROR       // 错误
    }
}
