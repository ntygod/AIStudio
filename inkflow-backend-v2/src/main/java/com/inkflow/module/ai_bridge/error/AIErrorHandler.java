package com.inkflow.module.ai_bridge.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * AI 错误处理器
 * 提供指数退避重试和用户友好错误消息
 * 
 * Requirements: 18.1, 18.2, 18.3, 18.4, 18.5
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@Component
public class AIErrorHandler {

    @Value("${inkflow.ai.error.max-retries:3}")
    private int maxRetries;

    @Value("${inkflow.ai.error.initial-backoff-ms:1000}")
    private long initialBackoffMs;

    @Value("${inkflow.ai.error.max-backoff-ms:30000}")
    private long maxBackoffMs;

    @Value("${inkflow.ai.error.backoff-multiplier:2.0}")
    private double backoffMultiplier;

    /**
     * 执行带重试的操作
     * 
     * @param operation 要执行的操作
     * @param context 操作上下文描述（用于日志）
     * @return 操作结果
     * @throws AIOperationException 如果重试耗尽仍然失败
     */
    public <T> T executeWithRetry(Supplier<T> operation, String context) {
        int attempts = 0;
        Exception lastException = null;
        
        while (attempts < maxRetries) {
            try {
                return operation.get();
            } catch (Exception e) {
                lastException = e;
                attempts++;
                
                if (!isRetryable(e)) {
                    log.warn("Non-retryable error in {}: {}", context, e.getMessage());
                    break;
                }
                
                if (attempts < maxRetries) {
                    long backoffMs = calculateBackoff(attempts);
                    log.warn("Retryable error in {} (attempt {}/{}), retrying in {}ms: {}", 
                        context, attempts, maxRetries, backoffMs, e.getMessage());
                    
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new AIOperationException(
                            formatUserMessage(e, context), e);
                    }
                } else {
                    log.error("Max retries ({}) exhausted for {}: {}", 
                        maxRetries, context, e.getMessage());
                }
            }
        }
        
        throw new AIOperationException(
            formatUserMessage(lastException, context), lastException);
    }

    /**
     * 执行带重试的操作（无返回值）
     */
    public void executeWithRetry(Runnable operation, String context) {
        executeWithRetry(() -> {
            operation.run();
            return null;
        }, context);
    }

    /**
     * 判断异常是否可重试
     */
    public boolean isRetryable(Exception e) {
        // 超时异常 - 可重试
        if (e instanceof TimeoutException) {
            return true;
        }
        
        // 网络连接异常 - 可重试
        if (e instanceof ResourceAccessException) {
            return true;
        }
        
        // 服务端错误 (5xx) - 可重试
        if (e instanceof HttpServerErrorException) {
            return true;
        }
        
        // 速率限制 (429) - 可重试
        if (e instanceof HttpClientErrorException hce) {
            int statusCode = hce.getStatusCode().value();
            return statusCode == 429 || statusCode >= 500;
        }
        
        // 检查异常消息中的关键词
        String message = e.getMessage();
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            return lowerMessage.contains("timeout") ||
                   lowerMessage.contains("rate limit") ||
                   lowerMessage.contains("too many requests") ||
                   lowerMessage.contains("service unavailable") ||
                   lowerMessage.contains("connection reset") ||
                   lowerMessage.contains("connection refused");
        }
        
        return false;
    }

    /**
     * 计算指数退避时间
     */
    private long calculateBackoff(int attempt) {
        // 指数退避: initialBackoff * (multiplier ^ (attempt - 1))
        double backoff = initialBackoffMs * Math.pow(backoffMultiplier, attempt - 1);
        
        // 添加随机抖动 (±10%)
        double jitter = backoff * 0.1 * (Math.random() * 2 - 1);
        backoff += jitter;
        
        // 限制最大退避时间
        return Math.min((long) backoff, maxBackoffMs);
    }

    /**
     * 格式化用户友好的错误消息
     * Requirements: 18.5 - 不暴露内部错误细节
     */
    public String formatUserMessage(Exception e, String context) {
        if (e == null) {
            return "抱歉，处理您的请求时遇到问题，请稍后重试。";
        }
        
        // 速率限制
        if (isRateLimitError(e)) {
            return "请求过于频繁，请稍等片刻后重试。";
        }
        
        // 超时
        if (e instanceof TimeoutException || 
            (e.getMessage() != null && e.getMessage().toLowerCase().contains("timeout"))) {
            return "请求处理超时，请稍后重试。如果问题持续，请尝试简化您的请求。";
        }
        
        // 网络问题
        if (e instanceof ResourceAccessException) {
            return "网络连接出现问题，请检查网络后重试。";
        }
        
        // 服务不可用
        if (e instanceof HttpServerErrorException) {
            return "AI 服务暂时不可用，请稍后重试。";
        }
        
        // 默认消息
        return "抱歉，处理您的请求时遇到问题，请稍后重试。";
    }

    /**
     * 判断是否为速率限制错误
     */
    private boolean isRateLimitError(Exception e) {
        if (e instanceof HttpClientErrorException hce) {
            return hce.getStatusCode().value() == 429;
        }
        
        String message = e.getMessage();
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            return lowerMessage.contains("rate limit") ||
                   lowerMessage.contains("too many requests");
        }
        
        return false;
    }

    /**
     * 获取建议的重试等待时间
     */
    public Duration getSuggestedRetryDelay(Exception e) {
        // 如果是速率限制，返回较长的等待时间
        if (isRateLimitError(e)) {
            return Duration.ofSeconds(60);
        }
        
        // 默认返回初始退避时间
        return Duration.ofMillis(initialBackoffMs);
    }

    /**
     * 获取最大重试次数
     */
    public int getMaxRetries() {
        return maxRetries;
    }
}
