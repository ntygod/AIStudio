package com.inkflow.module.ratelimit.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class RateLimitRuleDto {
    private UUID id;
    private String endpointPattern;
    private String httpMethod;
    private int bucketCapacity;
    private int refillRate;
    private int priority;
    private boolean enabled;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
