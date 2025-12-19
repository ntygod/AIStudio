package com.inkflow.module.progress.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * 实体统计DTO
 */
@Data
@Builder
public class EntityStatistics {
    
    private UUID projectId;
    private long characterCount;
    private long wikiEntryCount;
    private long volumeCount;
    private long chapterCount;
    private long plotLoopCount;
    private long openPlotLoops;
    private long closedPlotLoops;
    private double plotLoopClosureRate;
}
