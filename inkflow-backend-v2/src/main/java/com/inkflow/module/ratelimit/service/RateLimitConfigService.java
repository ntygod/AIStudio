package com.inkflow.module.ratelimit.service;

import com.inkflow.module.ratelimit.dto.RateLimitConfigDto;
import com.inkflow.module.ratelimit.dto.RateLimitConfigRequest;
import com.inkflow.module.ratelimit.entity.RateLimitConfig;
import com.inkflow.module.ratelimit.repository.RateLimitConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户级别限流配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitConfigService {

    private final RateLimitConfigRepository configRepository;
    
    // 本地缓存，用于快速查找
    private final ConcurrentHashMap<UUID, RateLimitConfig> localCache = new ConcurrentHashMap<>();

    /**
     * 创建用户限流配置
     */
    @Transactional
    @CacheEvict(value = "rateLimitConfigs", key = "#request.userId")
    public RateLimitConfigDto createConfig(RateLimitConfigRequest request) {
        if (configRepository.existsByUserId(request.getUserId())) {
            throw new IllegalArgumentException("用户限流配置已存在: " + request.getUserId());
        }

        RateLimitConfig config = RateLimitConfig.builder()
                .userId(request.getUserId())
                .bucketCapacity(request.getBucketCapacity())
                .refillRate(request.getRefillRate())
                .windowSeconds(request.getWindowSeconds())
                .enabled(request.isEnabled())
                .build();

        config = configRepository.save(config);
        localCache.put(config.getUserId(), config);
        log.info("创建用户限流配置: userId={}, capacity={}", request.getUserId(), request.getBucketCapacity());
        
        return toDto(config);
    }

    /**
     * 更新用户限流配置
     */
    @Transactional
    @CacheEvict(value = "rateLimitConfigs", key = "#userId")
    public RateLimitConfigDto updateConfig(UUID userId, RateLimitConfigRequest request) {
        RateLimitConfig config = configRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户限流配置不存在: " + userId));

        config.setBucketCapacity(request.getBucketCapacity());
        config.setRefillRate(request.getRefillRate());
        config.setWindowSeconds(request.getWindowSeconds());
        config.setEnabled(request.isEnabled());

        config = configRepository.save(config);
        localCache.put(userId, config);
        log.info("更新用户限流配置: userId={}", userId);
        
        return toDto(config);
    }

    /**
     * 删除用户限流配置
     */
    @Transactional
    @CacheEvict(value = "rateLimitConfigs", key = "#userId")
    public void deleteConfig(UUID userId) {
        configRepository.findByUserId(userId).ifPresent(config -> {
            configRepository.delete(config);
            localCache.remove(userId);
            log.info("删除用户限流配置: userId={}", userId);
        });
    }

    /**
     * 获取用户限流配置
     */
    @Cacheable(value = "rateLimitConfigs", key = "#userId")
    public Optional<RateLimitConfigDto> getConfigForUser(UUID userId) {
        // 先查本地缓存
        RateLimitConfig cached = localCache.get(userId);
        if (cached != null && cached.isEnabled()) {
            return Optional.of(toDto(cached));
        }
        
        return configRepository.findByUserIdAndEnabledTrue(userId)
                .map(config -> {
                    localCache.put(userId, config);
                    return toDto(config);
                });
    }

    /**
     * 获取所有配置
     */
    public List<RateLimitConfigDto> getAllConfigs() {
        return configRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * 刷新本地缓存
     */
    public void refreshCache() {
        localCache.clear();
        configRepository.findAllByEnabledTrue().forEach(config -> 
                localCache.put(config.getUserId(), config));
        log.info("刷新限流配置缓存，加载 {} 条配置", localCache.size());
    }

    private RateLimitConfigDto toDto(RateLimitConfig config) {
        return RateLimitConfigDto.builder()
                .id(config.getId())
                .userId(config.getUserId())
                .bucketCapacity(config.getBucketCapacity())
                .refillRate(config.getRefillRate())
                .windowSeconds(config.getWindowSeconds())
                .enabled(config.isEnabled())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}
