package com.inkflow.module.ratelimit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class RateLimitConfigRequest {
    
    @NotNull(message = "用户ID不能为空")
    private UUID userId;
    
    @Min(value = 1, message = "桶容量最小为1")
    @Max(value = 10000, message = "桶容量最大为10000")
    private int bucketCapacity = 100;
    
    @Min(value = 1, message = "补充速率最小为1")
    @Max(value = 1000, message = "补充速率最大为1000")
    private int refillRate = 10;
    
    @Min(value = 1, message = "窗口时间最小为1秒")
    @Max(value = 3600, message = "窗口时间最大为3600秒")
    private int windowSeconds = 60;
    
    private boolean enabled = true;
}
