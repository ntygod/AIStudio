package com.inkflow.module.session.dto;

import com.inkflow.module.project.entity.CreationPhase;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class SessionDto {
    private UUID id;
    private UUID userId;
    private String deviceInfo;
    private String ipAddress;
    private String userAgent;
    private UUID currentProjectId;
    private CreationPhase currentPhase;
    private LocalDateTime lastActivityAt;
    private LocalDateTime expiresAt;
    private boolean active;
    private LocalDateTime createdAt;
}
