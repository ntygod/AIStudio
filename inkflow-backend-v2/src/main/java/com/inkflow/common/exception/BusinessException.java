package com.inkflow.common.exception;

import lombok.Getter;

/**
 * 业务异常基类
 * 
 * 用于表示业务逻辑错误，如参数无效、状态不正确等
 */
@Getter
public class BusinessException extends RuntimeException {
    
    /**
     * 错误代码
     */
    private final String code;
    
    public BusinessException(String message) {
        super(message);
        this.code = "BUSINESS_ERROR";
    }
    
    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }
    
    public BusinessException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
