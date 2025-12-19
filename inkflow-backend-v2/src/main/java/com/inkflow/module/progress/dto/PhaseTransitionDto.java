package com.inkflow.module.progress.dto;

import com.inkflow.module.project.entity.CreationPhase;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 阶段转换DTO
 */
@Data
@Builder
public class PhaseTransitionDto {
    
    private UUID id;
    private UUID projectId;
    private CreationPhase fromPhase;
    private CreationPhase toPhase;
    private String reason;
    private String triggeredBy;
    private LocalDateTime transitionedAt;
}
