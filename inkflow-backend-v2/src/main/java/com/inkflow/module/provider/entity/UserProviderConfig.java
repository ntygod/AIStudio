package com.inkflow.module.provider.entity;

import com.inkflow.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

/**
 * 用户级AI提供商配置实体
 * 存储用户的默认AI提供商偏好设置
 * 
 * 与 AIProviderConfig 的区别：
 * - UserProviderConfig: 每个用户一条记录，存储用户的默认偏好
 * - AIProviderConfig: 每个用户可以有多条记录，每个提供商一条
 * 
 * Requirements: 1.1, 1.2
 */
@Entity
@Table(name = "user_provider_configs", indexes = {
    @Index(name = "idx_user_provider_configs_user_id", columnList = "user_id", unique = true)
})
public class UserProviderConfig extends BaseEntity {

    /**
     * 用户ID（唯一约束，每个用户只有一条配置）
     */
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    /**
     * 用户偏好的AI提供商类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_provider", nullable = false)
    private ProviderType preferredProvider;

    /**
     * 用户偏好的模型名称（可选）
     * 如果为空，则使用提供商的默认模型
     */
    @Column(name = "preferred_model")
    private String preferredModel;

    /**
     * 是否启用推理模型（用于深度思考场景）
     */
    @Column(name = "reasoning_enabled")
    private Boolean reasoningEnabled = false;

    /**
     * 推理模型的提供商类型（可选）
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "reasoning_provider")
    private ProviderType reasoningProvider;

    /**
     * 推理模型名称（可选）
     */
    @Column(name = "reasoning_model")
    private String reasoningModel;

    // ==================== Getters & Setters ====================

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public ProviderType getPreferredProvider() {
        return preferredProvider;
    }

    public void setPreferredProvider(ProviderType preferredProvider) {
        this.preferredProvider = preferredProvider;
    }

    public String getPreferredModel() {
        return preferredModel;
    }

    public void setPreferredModel(String preferredModel) {
        this.preferredModel = preferredModel;
    }

    public Boolean getReasoningEnabled() {
        return reasoningEnabled;
    }

    public void setReasoningEnabled(Boolean reasoningEnabled) {
        this.reasoningEnabled = reasoningEnabled;
    }

    public ProviderType getReasoningProvider() {
        return reasoningProvider;
    }

    public void setReasoningProvider(ProviderType reasoningProvider) {
        this.reasoningProvider = reasoningProvider;
    }

    public String getReasoningModel() {
        return reasoningModel;
    }

    public void setReasoningModel(String reasoningModel) {
        this.reasoningModel = reasoningModel;
    }

    // ==================== 业务方法 ====================

    /**
     * 检查是否配置了推理模型
     */
    public boolean hasReasoningConfig() {
        return Boolean.TRUE.equals(reasoningEnabled) && reasoningProvider != null;
    }

    /**
     * 获取有效的推理提供商（如果未配置则返回默认提供商）
     */
    public ProviderType getEffectiveReasoningProvider() {
        return reasoningProvider != null ? reasoningProvider : preferredProvider;
    }
}
