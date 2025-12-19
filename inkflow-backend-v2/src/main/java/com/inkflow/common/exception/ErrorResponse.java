package com.inkflow.common.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 标准错误响应格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    
    /**
     * 错误发生时间
     */
    private LocalDateTime timestamp;
    
    /**
     * HTTP状态码
     */
    private int status;
    
    /**
     * 错误代码 (业务错误码)
     */
    private String code;
    
    /**
     * 错误消息 (用户可读)
     */
    private String message;
    
    /**
     * 详细错误信息 (如字段校验错误)
     */
    private Map<String, String> details;
    
    /**
     * 请求追踪ID
     */
    private String requestId;
}
