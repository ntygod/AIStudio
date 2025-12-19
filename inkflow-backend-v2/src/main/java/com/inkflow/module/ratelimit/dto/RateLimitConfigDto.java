package com.inkflow.module.ratelimit.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class RateLimitConfigDto {
    private UUID id;
    private UUID userId;
    private int bucketCapacity;
    private int refillRate;
    private int windowSeconds;
    private boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
