package com.inkflow.module.provider.dto;

import com.inkflow.module.provider.entity.ProviderType;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 保存服务商配置请求
 */
public record SaveProviderConfigRequest(
    @NotNull(message = "服务商类型不能为空")
    ProviderType providerType,
    
    String apiKey,
    
    String baseUrl,
    
    String defaultModel,
    
    Map<String, String> modelMapping,
    
    Boolean isDefault
) {}
