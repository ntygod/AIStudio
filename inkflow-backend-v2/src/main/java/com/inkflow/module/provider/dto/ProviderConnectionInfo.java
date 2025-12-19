package com.inkflow.module.provider.dto;

import com.inkflow.module.provider.entity.ProviderType;

/**
 * 服务商连接信息（内部使用）
 * 包含解密后的 API Key
 */
public record ProviderConnectionInfo(
    ProviderType providerType,
    String apiKey,
    String baseUrl,
    String defaultModel
) {}
