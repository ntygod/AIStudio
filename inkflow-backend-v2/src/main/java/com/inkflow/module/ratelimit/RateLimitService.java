package com.inkflow.module.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 限流服务
 * 使用Token Bucket算法
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    // 默认配置
    private static final int DEFAULT_BUCKET_CAPACITY = 100;  // 桶容量
    private static final int DEFAULT_REFILL_RATE = 10;       // 每秒补充数量
    private static final Duration DEFAULT_WINDOW = Duration.ofMinutes(1);

    private final RedisTemplate<String, String> redisTemplate;
    private final ConcurrentHashMap<String, TokenBucket> localBuckets = new ConcurrentHashMap<>();

    public RateLimitService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 尝试获取令牌
     */
    public boolean tryAcquire(String key) {
        return tryAcquire(key, 1);
    }

    /**
     * 尝试获取指定数量的令牌
     */
    public boolean tryAcquire(String key, int tokens) {
        return tryAcquire(key, tokens, DEFAULT_BUCKET_CAPACITY, DEFAULT_REFILL_RATE);
    }

    /**
     * 尝试获取令牌（自定义配置）
     */
    public boolean tryAcquire(String key, int tokens, int capacity, int refillRate) {
        TokenBucket bucket = localBuckets.computeIfAbsent(key, 
                k -> new TokenBucket(capacity, refillRate));
        return bucket.tryConsume(tokens);
    }

    /**
     * 检查是否被限流
     */
    public boolean isRateLimited(String key) {
        TokenBucket bucket = localBuckets.get(key);
        if (bucket == null) {
            return false;
        }
        return bucket.getAvailableTokens() <= 0;
    }

    /**
     * 获取剩余令牌数
     */
    public int getRemainingTokens(String key) {
        TokenBucket bucket = localBuckets.get(key);
        if (bucket == null) {
            return DEFAULT_BUCKET_CAPACITY;
        }
        return bucket.getAvailableTokens();
    }

    /**
     * 重置限流
     */
    public void reset(String key) {
        localBuckets.remove(key);
    }

    /**
     * 使用Redis的分布式限流
     */
    public boolean tryAcquireDistributed(String key, int maxRequests, Duration window) {
        String redisKey = "rate_limit:" + key;
        
        try {
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count == null) {
                return false;
            }
            
            if (count == 1) {
                redisTemplate.expire(redisKey, window);
            }
            
            return count <= maxRequests;
        } catch (Exception e) {
            log.warn("Redis rate limit check failed, falling back to local", e);
            return tryAcquire(key);
        }
    }

    /**
     * Token Bucket实现
     */
    private static class TokenBucket {
        private final int capacity;
        private final int refillRate;
        private final AtomicInteger tokens;
        private final AtomicLong lastRefillTime;

        public TokenBucket(int capacity, int refillRate) {
            this.capacity = capacity;
            this.refillRate = refillRate;
            this.tokens = new AtomicInteger(capacity);
            this.lastRefillTime = new AtomicLong(System.currentTimeMillis());
        }

        public boolean tryConsume(int tokensToConsume) {
            refill();
            
            int currentTokens;
            int newTokens;
            do {
                currentTokens = tokens.get();
                if (currentTokens < tokensToConsume) {
                    return false;
                }
                newTokens = currentTokens - tokensToConsume;
            } while (!tokens.compareAndSet(currentTokens, newTokens));
            
            return true;
        }

        public int getAvailableTokens() {
            refill();
            return tokens.get();
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long lastRefill = lastRefillTime.get();
            long elapsedMs = now - lastRefill;
            
            if (elapsedMs < 1000) {
                return; // 不到1秒不补充
            }
            
            int tokensToAdd = (int) (elapsedMs / 1000) * refillRate;
            if (tokensToAdd > 0 && lastRefillTime.compareAndSet(lastRefill, now)) {
                int currentTokens;
                int newTokens;
                do {
                    currentTokens = tokens.get();
                    newTokens = Math.min(capacity, currentTokens + tokensToAdd);
                } while (!tokens.compareAndSet(currentTokens, newTokens));
            }
        }
    }
}
