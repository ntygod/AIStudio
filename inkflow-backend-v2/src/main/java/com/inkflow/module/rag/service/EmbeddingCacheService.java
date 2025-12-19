package com.inkflow.module.rag.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 向量嵌入缓存服务
 * 实现L1 Caffeine + L2 Redis两级缓存
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@Service
public class EmbeddingCacheService {

    private final RedisTemplate<String, float[]> redisTemplate;

    /**
     * L1本地缓存 (Caffeine)
     * 最大10000条，24小时过期
     */
    private final Cache<String, float[]> localCache;

    private static final String REDIS_KEY_PREFIX = "embedding:";
    private static final Duration REDIS_TTL = Duration.ofHours(24);

    public EmbeddingCacheService(@Qualifier("embeddingRedisTemplate") RedisTemplate<String, float[]> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.localCache = Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(24, TimeUnit.HOURS)
                .recordStats()
                .build();
    }

    /**
     * 从缓存获取向量
     * 先查L1，再查L2
     *
     * @param text 原始文本
     * @return 向量数组
     */
    public Mono<float[]> get(String text) {
        String hash = calculateHash(text);

        // L1: 本地缓存
        float[] localResult = localCache.getIfPresent(hash);
        if (localResult != null) {
            log.debug("L1缓存命中: {}", hash.substring(0, 8));
            return Mono.just(localResult);
        }

        // L2: Redis缓存
        return Mono.fromCallable(() -> {
            String redisKey = REDIS_KEY_PREFIX + hash;
            float[] redisResult = redisTemplate.opsForValue().get(redisKey);
            if (redisResult != null) {
                log.debug("L2缓存命中: {}", hash.substring(0, 8));
                // 回填L1
                localCache.put(hash, redisResult);
                return redisResult;
            }
            return null;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .filter(Objects::nonNull);
    }


    /**
     * 存入缓存
     * 同时写入L1和L2
     *
     * @param text 原始文本
     * @param embedding 向量数组
     * @return 完成信号
     */
    public Mono<Void> put(String text, float[] embedding) {
        String hash = calculateHash(text);

        return Mono.fromRunnable(() -> {
            // L1: 本地缓存
            localCache.put(hash, embedding);

            // L2: Redis缓存
            String redisKey = REDIS_KEY_PREFIX + hash;
            redisTemplate.opsForValue().set(redisKey, embedding, REDIS_TTL);

            log.debug("缓存写入: {}", hash.substring(0, 8));
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * 清空所有缓存
     */
    public Mono<Void> clear() {
        return Mono.fromRunnable(() -> {
            localCache.invalidateAll();
            // Redis缓存通过TTL自动过期，不主动清理
            log.info("本地缓存已清空");
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * 获取缓存统计信息
     */
    public CacheStats getStats() {
        var caffeineStats = localCache.stats();
        return new CacheStats(
                localCache.estimatedSize(),
                caffeineStats.hitCount(),
                caffeineStats.missCount(),
                caffeineStats.hitRate(),
                10000L // maxSize from Caffeine configuration
        );
    }

    /**
     * 计算文本的SHA-256哈希
     */
    private String calculateHash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256不可用", e);
        }
    }

    /**
     * 缓存统计信息
     */
    public record CacheStats(
            long size,
            long hits,
            long misses,
            double hitRate,
            long maxSize
    ) {}
}
