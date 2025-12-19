package com.inkflow.module.ratelimit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RateLimitRuleRequest {
    
    @NotBlank(message = "端点模式不能为空")
    private String endpointPattern;
    
    private String httpMethod;
    
    @Min(value = 1, message = "桶容量最小为1")
    @Max(value = 10000, message = "桶容量最大为10000")
    private int bucketCapacity = 100;
    
    @Min(value = 1, message = "补充速率最小为1")
    @Max(value = 1000, message = "补充速率最大为1000")
    private int refillRate = 10;
    
    @Min(value = 0, message = "优先级最小为0")
    @Max(value = 1000, message = "优先级最大为1000")
    private int priority = 0;
    
    private boolean enabled = true;
    
    private String description;
}
