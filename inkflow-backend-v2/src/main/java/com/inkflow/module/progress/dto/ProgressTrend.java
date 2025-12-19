package com.inkflow.module.progress.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 进度趋势DTO
 */
@Data
@Builder
public class ProgressTrend {
    
    private UUID projectId;
    private TrendPeriod period;
    private List<TrendDataPoint> dataPoints;
}
