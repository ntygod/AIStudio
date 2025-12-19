package com.inkflow.module.progress.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 趋势数据点DTO
 */
@Data
@Builder
public class TrendDataPoint {
    
    private String period;
    private long wordCount;
    private long wordCountChange;
    private long chapterCount;
    private long characterCount;
}
