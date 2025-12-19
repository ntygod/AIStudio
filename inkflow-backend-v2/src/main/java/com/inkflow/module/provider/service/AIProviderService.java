package com.inkflow.module.provider.service;

import com.inkflow.common.exception.BusinessException;
import com.inkflow.common.util.CryptoUtil;
import com.inkflow.module.provider.dto.ProviderConfigDto;
import com.inkflow.module.provider.dto.ProviderConnectionInfo;
import com.inkflow.module.provider.dto.SaveProviderConfigRequest;
import com.inkflow.module.provider.entity.AIProviderConfig;
import com.inkflow.module.provider.entity.ProviderType;
import com.inkflow.module.provider.repository.AIProviderConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * AI 服务商配置服务
 */
@Service
@Transactional(readOnly = true)
public class AIProviderService {

    private static final Logger log = LoggerFactory.getLogger(AIProviderService.class);

    private final AIProviderConfigRepository configRepository;
    private final String encryptionKey;

    public AIProviderService(
            AIProviderConfigRepository configRepository,
            @Value("${inkflow.security.encryption-key}") String encryptionKey) {
        this.configRepository = configRepository;
        this.encryptionKey = encryptionKey;
    }

    /**
     * 保存服务商配置
     */
    @Transactional
    public ProviderConfigDto saveConfig(UUID userId, SaveProviderConfigRequest request) {
        log.debug("为用户 {} 保存服务商配置: {}", userId, request.providerType());

        validateRequest(request);

        AIProviderConfig config = configRepository
            .findByUserIdAndProviderType(userId, request.providerType())
            .orElseGet(() -> {
                AIProviderConfig newConfig = new AIProviderConfig();
                newConfig.setUserId(userId);
                newConfig.setProviderType(request.providerType());
                return newConfig;
            });

        // 更新 API Key
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            String encryptedKey = CryptoUtil.encrypt(request.apiKey(), encryptionKey);
            config.setEncryptedKey(encryptedKey);
            config.setKeyHint(generateKeyHint(request.apiKey()));
        }

        // 更新其他配置
        config.setBaseUrl(request.baseUrl());
        config.setDefaultModel(request.defaultModel());
        config.setModelMapping(request.modelMapping());
        
        if (Boolean.TRUE.equals(request.isDefault())) {
            clearDefaultProvider(userId);
            config.setIsDefault(true);
        }

        config.updateConfiguredStatus();
        config = configRepository.save(config);

        log.info("为用户 {} 保存服务商配置: {}", userId, request.providerType());
        return ProviderConfigDto.from(config);
    }

    /**
     * 获取用户所有服务商配置
     */
    public List<ProviderConfigDto> getAllConfigs(UUID userId) {
        List<AIProviderConfig> savedConfigs = configRepository.findByUserId(userId);

        return Arrays.stream(ProviderType.values())
            .map(providerType -> savedConfigs.stream()
                .filter(c -> c.getProviderType() == providerType)
                .findFirst()
                .map(ProviderConfigDto::from)
                .orElse(ProviderConfigDto.unconfigured(providerType)))
            .toList();
    }

    /**
     * 获取已配置的服务商列表
     */
    public List<ProviderType> getConfiguredProviders(UUID userId) {
        return configRepository.findConfiguredProviderTypesByUserId(userId);
    }

    /**
     * 获取服务商连接信息（内部使用）
     */
    public ProviderConnectionInfo getConnectionInfo(UUID userId, ProviderType providerType) {
        AIProviderConfig config = configRepository
            .findByUserIdAndProviderType(userId, providerType)
            .orElseThrow(() -> new BusinessException("请先配置 " + providerType.getDisplayName() + " 的 API Key"));

        if (!Boolean.TRUE.equals(config.getIsConfigured())) {
            throw new BusinessException("请先配置 " + providerType.getDisplayName() + " 的 API Key");
        }

        String decryptedKey = CryptoUtil.decrypt(config.getEncryptedKey(), encryptionKey);
        return new ProviderConnectionInfo(
            providerType,
            decryptedKey,
            config.getEffectiveBaseUrl(),
            config.getDefaultModel()
        );
    }

    /**
     * 获取默认服务商连接信息
     */
    public ProviderConnectionInfo getDefaultConnectionInfo(UUID userId) {
        AIProviderConfig config = configRepository.findByUserIdAndIsDefaultTrue(userId)
            .orElseThrow(() -> new BusinessException("请先设置默认 AI 服务商"));

        String decryptedKey = CryptoUtil.decrypt(config.getEncryptedKey(), encryptionKey);
        return new ProviderConnectionInfo(
            config.getProviderType(),
            decryptedKey,
            config.getEffectiveBaseUrl(),
            config.getDefaultModel()
        );
    }

    /**
     * 删除服务商配置
     */
    @Transactional
    public void deleteConfig(UUID userId, ProviderType providerType) {
        if (!configRepository.existsByUserIdAndProviderType(userId, providerType)) {
            throw new BusinessException("服务商配置不存在");
        }
        configRepository.deleteByUserIdAndProviderType(userId, providerType);
        log.info("删除用户 {} 的服务商配置: {}", userId, providerType);
    }

    /**
     * 检查用户是否已配置指定服务商
     */
    public boolean hasConfig(UUID userId, ProviderType providerType) {
        return configRepository.existsConfiguredByUserIdAndProviderType(userId, providerType);
    }

    // ==================== 私有方法 ====================

    private void validateRequest(SaveProviderConfigRequest request) {
        if (request.providerType() == null) {
            throw new BusinessException("服务商类型不能为空");
        }
        if (request.apiKey() != null && !request.apiKey().isBlank() && request.apiKey().length() < 10) {
            throw new BusinessException("API Key 长度过短");
        }
        if (request.baseUrl() != null && !request.baseUrl().isBlank()) {
            if (!request.baseUrl().startsWith("http://") && !request.baseUrl().startsWith("https://")) {
                throw new BusinessException("Base URL 必须以 http:// 或 https:// 开头");
            }
        }
    }

    private void clearDefaultProvider(UUID userId) {
        configRepository.findByUserIdAndIsDefaultTrue(userId)
            .ifPresent(config -> {
                config.setIsDefault(false);
                configRepository.save(config);
            });
    }

    private String generateKeyHint(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "";
        }
        int length = apiKey.length();
        return length <= 4 ? apiKey : apiKey.substring(length - 4);
    }
}
