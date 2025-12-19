package com.inkflow.module.provider.entity;

import com.inkflow.common.entity.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.util.Map;
import java.util.UUID;

/**
 * AI 服务商配置实体
 * 简化版设计：单表存储用户的 AI 配置
 */
@Entity
@Table(name = "ai_provider_configs", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "provider_type"})
})
public class AIProviderConfig extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false)
    private ProviderType providerType;

    /**
     * 加密存储的 API Key
     */
    @Column(name = "encrypted_key")
    private String encryptedKey;

    /**
     * API Key 提示（最后4位）
     */
    @Column(name = "key_hint")
    private String keyHint;

    /**
     * 自定义 Base URL
     */
    @Column(name = "base_url")
    private String baseUrl;

    /**
     * 默认模型
     */
    @Column(name = "default_model")
    private String defaultModel;

    /**
     * 功能场景到模型的映射
     * 例如: {"chat": "gpt-4", "embedding": "text-embedding-ada-002"}
     */
    @Type(JsonType.class)
    @Column(name = "model_mapping", columnDefinition = "jsonb")
    private Map<String, String> modelMapping;

    /**
     * 是否为默认服务商
     */
    @Column(name = "is_default")
    private Boolean isDefault = false;

    /**
     * 是否已配置（有有效的 API Key）
     */
    @Column(name = "is_configured")
    private Boolean isConfigured = false;

    // Getters and Setters
    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public ProviderType getProviderType() {
        return providerType;
    }

    public void setProviderType(ProviderType providerType) {
        this.providerType = providerType;
    }

    public String getEncryptedKey() {
        return encryptedKey;
    }

    public void setEncryptedKey(String encryptedKey) {
        this.encryptedKey = encryptedKey;
    }

    public String getKeyHint() {
        return keyHint;
    }

    public void setKeyHint(String keyHint) {
        this.keyHint = keyHint;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public Map<String, String> getModelMapping() {
        return modelMapping;
    }

    public void setModelMapping(Map<String, String> modelMapping) {
        this.modelMapping = modelMapping;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    public Boolean getIsConfigured() {
        return isConfigured;
    }

    public void setIsConfigured(Boolean isConfigured) {
        this.isConfigured = isConfigured;
    }

    /**
     * 更新配置状态
     */
    public void updateConfiguredStatus() {
        this.isConfigured = encryptedKey != null && !encryptedKey.isBlank();
    }

    /**
     * 获取有效的 Base URL
     */
    public String getEffectiveBaseUrl() {
        return baseUrl != null && !baseUrl.isBlank() 
            ? baseUrl 
            : providerType.getDefaultBaseUrl();
    }
}
