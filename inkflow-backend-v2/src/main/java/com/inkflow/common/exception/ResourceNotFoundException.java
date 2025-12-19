package com.inkflow.common.exception;

import java.util.UUID;

/**
 * 资源未找到异常
 * 
 * 当请求的资源不存在时抛出
 */
public class ResourceNotFoundException extends RuntimeException {
    
    public ResourceNotFoundException(String message) {
        super(message);
    }
    
    public ResourceNotFoundException(String resourceType, UUID id) {
        super(String.format("%s 不存在: %s", resourceType, id));
    }
    
    public ResourceNotFoundException(String resourceType, String identifier) {
        super(String.format("%s 不存在: %s", resourceType, identifier));
    }
}
