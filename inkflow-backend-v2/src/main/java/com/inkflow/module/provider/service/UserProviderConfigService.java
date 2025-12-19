package com.inkflow.module.provider.service;

import com.inkflow.module.provider.entity.ProviderType;
import com.inkflow.module.provider.entity.UserProviderConfig;
import com.inkflow.module.provider.repository.UserProviderConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 用户级AI提供商配置服务
 * 
 * 提供用户AI配置的CRUD操作，并实现5分钟缓存以减少数据库查询。
 * 
 * Requirements: 1.1, 1.2, 1.4
 */
@Service
public class UserProviderConfigService {

    private static final Logger log = LoggerFactory.getLogger(UserProviderConfigService.class);

    /**
     * 缓存名称
     */
    public static final String CACHE_NAME = "userProviderConfig";

    private final UserProviderConfigRepository repository;

    public UserProviderConfigService(UserProviderConfigRepository repository) {
        this.repository = repository;
    }

    /**
     * 获取用户配置（带5分钟缓存）
     * 
     * 缓存策略：
     * - 缓存命中：直接返回缓存数据
     * - 缓存未命中：查询数据库并缓存结果
     * - 缓存TTL：5分钟（由CacheConfig配置）
     * 
     * @param userId 用户ID
     * @return 用户配置，如果未配置则返回 Optional.empty()
     * 
     * Requirements: 1.1, 1.2, 1.4
     */
    @Cacheable(value = CACHE_NAME, key = "#userId.toString()", unless = "#result == null || !#result.isPresent()")
    public Optional<UserProviderConfig> getUserConfig(UUID userId) {
        log.debug("从数据库获取用户 {} 的提供商配置", userId);
        return repository.findByUserId(userId);
    }

    /**
     * 保存用户配置并更新缓存
     * 
     * @param userId 用户ID
     * @param config 用户配置
     * @return 保存后的配置
     * 
     * Requirements: 1.1, 1.2
     */
    @Transactional
    @CacheEvict(value = CACHE_NAME, key = "#userId.toString()")
    public UserProviderConfig saveUserConfig(UUID userId, UserProviderConfig config) {
        log.info("保存用户 {} 的提供商配置: provider={}", userId, config.getPreferredProvider());
        
        // 确保userId一致
        config.setUserId(userId);
        
        // 检查是否已存在配置
        Optional<UserProviderConfig> existing = repository.findByUserId(userId);
        if (existing.isPresent()) {
            // 更新现有配置
            UserProviderConfig existingConfig = existing.get();
            existingConfig.setPreferredProvider(config.getPreferredProvider());
            existingConfig.setPreferredModel(config.getPreferredModel());
            existingConfig.setReasoningEnabled(config.getReasoningEnabled());
            existingConfig.setReasoningProvider(config.getReasoningProvider());
            existingConfig.setReasoningModel(config.getReasoningModel());
            return repository.save(existingConfig);
        } else {
            // 创建新配置
            return repository.save(config);
        }
    }

    /**
     * 更新用户的偏好提供商
     * 
     * @param userId 用户ID
     * @param providerType 提供商类型
     * @return 更新后的配置
     * 
     * Requirements: 1.1
     */
    @Transactional
    @CacheEvict(value = CACHE_NAME, key = "#userId.toString()")
    public UserProviderConfig updatePreferredProvider(UUID userId, ProviderType providerType) {
        log.info("更新用户 {} 的偏好提供商为: {}", userId, providerType);
        
        UserProviderConfig config = repository.findByUserId(userId)
                .orElseGet(() -> {
                    UserProviderConfig newConfig = new UserProviderConfig();
                    newConfig.setUserId(userId);
                    return newConfig;
                });
        
        config.setPreferredProvider(providerType);
        return repository.save(config);
    }

    /**
     * 更新用户的推理模型配置
     * 
     * @param userId 用户ID
     * @param enabled 是否启用
     * @param providerType 推理模型提供商
     * @param modelName 推理模型名称
     * @return 更新后的配置
     */
    @Transactional
    @CacheEvict(value = CACHE_NAME, key = "#userId.toString()")
    public UserProviderConfig updateReasoningConfig(UUID userId, boolean enabled, 
                                                     ProviderType providerType, String modelName) {
        log.info("更新用户 {} 的推理模型配置: enabled={}, provider={}", userId, enabled, providerType);
        
        UserProviderConfig config = repository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("用户配置不存在，请先设置偏好提供商"));
        
        config.setReasoningEnabled(enabled);
        config.setReasoningProvider(providerType);
        config.setReasoningModel(modelName);
        return repository.save(config);
    }

    /**
     * 删除用户配置
     * 
     * @param userId 用户ID
     */
    @Transactional
    @CacheEvict(value = CACHE_NAME, key = "#userId.toString()")
    public void deleteUserConfig(UUID userId) {
        log.info("删除用户 {} 的提供商配置", userId);
        repository.deleteByUserId(userId);
    }

    /**
     * 清除用户的配置缓存
     * 当用户更新配置时调用
     * 
     * @param userId 用户ID
     * 
     * Requirements: 1.4
     */
    @CacheEvict(value = CACHE_NAME, key = "#userId.toString()")
    public void invalidateCache(UUID userId) {
        log.debug("清除用户 {} 的提供商配置缓存", userId);
    }

    /**
     * 检查用户是否已配置
     * 
     * @param userId 用户ID
     * @return 是否已配置
     */
    public boolean hasConfig(UUID userId) {
        return repository.existsByUserId(userId);
    }

    /**
     * 获取用户的偏好提供商类型
     * 
     * @param userId 用户ID
     * @return 偏好提供商类型，如果未配置则返回 Optional.empty()
     * 
     * Requirements: 1.1
     */
    public Optional<ProviderType> getPreferredProvider(UUID userId) {
        return getUserConfig(userId).map(UserProviderConfig::getPreferredProvider);
    }

    /**
     * 获取用户的推理模型提供商类型
     * 
     * @param userId 用户ID
     * @return 推理模型提供商类型，如果未配置则返回 Optional.empty()
     */
    public Optional<ProviderType> getReasoningProvider(UUID userId) {
        return getUserConfig(userId)
                .filter(UserProviderConfig::hasReasoningConfig)
                .map(UserProviderConfig::getReasoningProvider);
    }
}
