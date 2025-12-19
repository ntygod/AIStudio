package com.inkflow.module.provider.dto;

import com.inkflow.module.provider.entity.AIProviderConfig;
import com.inkflow.module.provider.entity.ProviderType;

import java.util.Map;

/**
 * 服务商配置 DTO
 */
public record ProviderConfigDto(
    ProviderType providerType,
    String displayName,
    String keyHint,
    String baseUrl,
    String defaultModel,
    Map<String, String> modelMapping,
    boolean isDefault,
    boolean isConfigured
) {
    public static ProviderConfigDto from(AIProviderConfig config) {
        return new ProviderConfigDto(
            config.getProviderType(),
            config.getProviderType().getDisplayName(),
            config.getKeyHint(),
            config.getBaseUrl(),
            config.getDefaultModel(),
            config.getModelMapping(),
            Boolean.TRUE.equals(config.getIsDefault()),
            Boolean.TRUE.equals(config.getIsConfigured())
        );
    }

    /**
     * 创建未配置的默认 DTO
     */
    public static ProviderConfigDto unconfigured(ProviderType providerType) {
        return new ProviderConfigDto(
            providerType,
            providerType.getDisplayName(),
            null,
            null,
            null,
            null,
            false,
            false
        );
    }
}
