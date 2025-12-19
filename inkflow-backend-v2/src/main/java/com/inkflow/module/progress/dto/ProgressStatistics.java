package com.inkflow.module.progress.dto;

import com.inkflow.module.project.entity.CreationPhase;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * 进度统计DTO
 * 已移除不实用的 phaseCompletion 和 suggestedNextPhase 字段
 * 
 * Requirements: 12.1-12.4
 */
@Data
@Builder
public class ProgressStatistics {
    
    private UUID projectId;
    private CreationPhase currentPhase;
    private long characterCount;
    private long wikiEntryCount;
    private long volumeCount;
    private long chapterCount;
    private long wordCount;
    private long plotLoopCount;
    private long openPlotLoops;
    private long closedPlotLoops;
}
