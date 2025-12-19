package com.inkflow.module.provider.service;

import com.inkflow.module.provider.dto.ProviderConnectionInfo;
import com.inkflow.module.provider.entity.ProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * 用户AI提供商配置缓存服务
 * 提供5分钟缓存以减少数据库查询
 * 
 * Requirements: 1.1, 1.2, 1.3, 1.4
 */
@Service
public class UserProviderConfigCacheService {

    private static final Logger log = LoggerFactory.getLogger(UserProviderConfigCacheService.class);
    
    public static final String CACHE_NAME = "userProviderConfig";

    private final AIProviderService aiProviderService;

    public UserProviderConfigCacheService(AIProviderService aiProviderService) {
        this.aiProviderService = aiProviderService;
    }

    /**
     * 获取用户的默认提供商连接信息（带缓存）
     * 缓存TTL: 5分钟
     * 
     * @param userId 用户ID
     * @return 提供商连接信息，如果未配置则返回 Optional.empty()
     */
    @Cacheable(value = CACHE_NAME, key = "#userId.toString() + ':default'", unless = "#result == null || !#result.isPresent()")
    public Optional<ProviderConnectionInfo> getDefaultConnectionInfo(UUID userId) {
        log.debug("从数据库获取用户 {} 的默认提供商配置", userId);
        try {
            ProviderConnectionInfo info = aiProviderService.getDefaultConnectionInfo(userId);
            return Optional.of(info);
        } catch (Exception e) {
            log.debug("用户 {} 未配置默认提供商: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 获取用户指定提供商的连接信息（带缓存）
     * 缓存TTL: 5分钟
     * 
     * @param userId 用户ID
     * @param providerType 提供商类型
     * @return 提供商连接信息，如果未配置则返回 Optional.empty()
     */
    @Cacheable(value = CACHE_NAME, key = "#userId.toString() + ':' + #providerType.name()", unless = "#result == null || !#result.isPresent()")
    public Optional<ProviderConnectionInfo> getConnectionInfo(UUID userId, ProviderType providerType) {
        log.debug("从数据库获取用户 {} 的 {} 提供商配置", userId, providerType);
        try {
            ProviderConnectionInfo info = aiProviderService.getConnectionInfo(userId, providerType);
            return Optional.of(info);
        } catch (Exception e) {
            log.debug("用户 {} 未配置 {} 提供商: {}", userId, providerType, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 检查用户是否配置了指定提供商
     * 
     * @param userId 用户ID
     * @param providerType 提供商类型
     * @return 是否已配置
     */
    public boolean hasConfig(UUID userId, ProviderType providerType) {
        return aiProviderService.hasConfig(userId, providerType);
    }

    /**
     * 清除用户的配置缓存
     * 当用户更新配置时调用
     * 
     * @param userId 用户ID
     */
    @CacheEvict(value = CACHE_NAME, allEntries = true, condition = "#userId != null")
    public void invalidateCache(UUID userId) {
        log.info("清除用户 {} 的提供商配置缓存", userId);
    }

    /**
     * 清除所有用户的配置缓存
     */
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void invalidateAllCache() {
        log.info("清除所有用户的提供商配置缓存");
    }
}
