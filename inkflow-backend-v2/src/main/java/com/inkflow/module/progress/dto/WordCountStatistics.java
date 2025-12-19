package com.inkflow.module.progress.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * 字数统计DTO
 */
@Data
@Builder
public class WordCountStatistics {
    
    private UUID projectId;
    private long totalWords;
    private long averageWordsPerChapter;
    private long dailyAverageWords;
    private long chapterCount;
}
