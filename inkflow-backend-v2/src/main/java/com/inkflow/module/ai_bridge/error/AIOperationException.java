package com.inkflow.module.ai_bridge.error;

/**
 * AI 操作异常
 * 封装 AI 相关操作的异常，提供用户友好的错误消息
 * 
 * Requirements: 18.2, 18.5
 *
 * @author zsg
 * @date 2025/12/17
 */
public class AIOperationException extends RuntimeException {

    private final String userMessage;
    private final boolean retryable;

    public AIOperationException(String userMessage) {
        super(userMessage);
        this.userMessage = userMessage;
        this.retryable = false;
    }

    public AIOperationException(String userMessage, Throwable cause) {
        super(userMessage, cause);
        this.userMessage = userMessage;
        this.retryable = false;
    }

    public AIOperationException(String userMessage, Throwable cause, boolean retryable) {
        super(userMessage, cause);
        this.userMessage = userMessage;
        this.retryable = retryable;
    }

    /**
     * 获取用户友好的错误消息
     */
    public String getUserMessage() {
        return userMessage;
    }

    /**
     * 是否可重试
     */
    public boolean isRetryable() {
        return retryable;
    }

    /**
     * 创建可重试的异常
     */
    public static AIOperationException retryable(String userMessage, Throwable cause) {
        return new AIOperationException(userMessage, cause, true);
    }

    /**
     * 创建不可重试的异常
     */
    public static AIOperationException nonRetryable(String userMessage, Throwable cause) {
        return new AIOperationException(userMessage, cause, false);
    }
}
