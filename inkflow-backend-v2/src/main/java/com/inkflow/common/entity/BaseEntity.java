package com.inkflow.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 基础实体类
 * 
 * 所有领域实体的父类，提供:
 * - UUID主键
 * - 创建时间自动填充
 * - 更新时间自动填充
 * - 软删除支持
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    /**
     * 主键ID - 使用UUID
     * 分布式环境下保证唯一性
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 创建时间 - 自动填充
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间 - 自动填充
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 软删除时间
     * null表示未删除，有值表示已删除
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 软删除标记（用于JPA查询）
     * 与 deletedAt 保持同步
     */
    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    /**
     * 判断实体是否已被软删除
     */
    public boolean isDeleted() {
        return deleted;
    }

    /**
     * 执行软删除
     */
    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
        this.deleted = true;
    }

    /**
     * 标记为已删除（别名方法）
     */
    public void markDeleted() {
        softDelete();
    }

    /**
     * 设置删除状态
     * @param deleted true表示删除，false表示恢复
     */
    public void setDeleted(boolean deleted) {
        if (deleted) {
            softDelete();
        } else {
            restore();
        }
    }

    /**
     * 恢复软删除
     */
    public void restore() {
        this.deletedAt = null;
        this.deleted = false;
    }
}
