package com.inkflow.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 多级缓存配置
 * 
 * L1缓存: Caffeine (本地内存缓存)
 * - 响应速度: 纳秒级
 * - 适用场景: 热点数据、频繁访问的小数据
 * 
 * L2缓存: Redis Stack 7.4+
 * - 响应速度: 毫秒级
 * - 适用场景: 分布式缓存、向量缓存、会话数据
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${inkflow.cache.local.ttl:5m}")
    private Duration localCacheTtl;

    @Value("${inkflow.cache.local.max-size:1000}")
    private int localCacheMaxSize;

    @Value("${inkflow.cache.embedding.ttl:24h}")
    private Duration embeddingCacheTtl;

    /**
     * L1缓存 - Caffeine本地缓存管理器
     * 用于高频访问的热点数据
     */
    @Bean(name = "caffeineCacheManager")
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                // 最大缓存条目数
                .maximumSize(localCacheMaxSize)
                // 写入后过期时间
                .expireAfterWrite(localCacheTtl.toMinutes(), TimeUnit.MINUTES)
                // 访问后过期时间
                .expireAfterAccess(localCacheTtl.toMinutes() * 2, TimeUnit.MINUTES)
                // 开启统计
                .recordStats());
        return cacheManager;
    }

    /**
     * L2缓存 - Redis缓存管理器
     * 用于分布式缓存和持久化缓存
     */
    @Bean(name = "redisCacheManager")
    @Primary
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        // 默认缓存配置
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                // 嵌入向量缓存 - 24小时过期
                .withCacheConfiguration("embeddings",
                        defaultConfig.entryTtl(embeddingCacheTtl))
                // 项目数据缓存 - 1小时过期
                .withCacheConfiguration("projects",
                        defaultConfig.entryTtl(Duration.ofHours(1)))
                // 用户会话缓存 - 30分钟过期
                .withCacheConfiguration("sessions",
                        defaultConfig.entryTtl(Duration.ofMinutes(30)))
                // 用户提供商配置缓存 - 5分钟过期 (Requirements: 1.4)
                .withCacheConfiguration("userProviderConfig",
                        defaultConfig.entryTtl(Duration.ofMinutes(5)))
                .build();
    }
}
