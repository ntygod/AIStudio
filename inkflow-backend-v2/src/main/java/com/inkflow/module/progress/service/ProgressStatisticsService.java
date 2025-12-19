package com.inkflow.module.progress.service;

import com.inkflow.module.progress.dto.*;
import com.inkflow.module.progress.entity.PhaseTransition;
import com.inkflow.module.progress.entity.ProgressSnapshot;
import com.inkflow.module.progress.repository.PhaseTransitionRepository;
import com.inkflow.module.progress.repository.ProgressSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 进度统计服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgressStatisticsService {

    private final CreationProgressService progressService;
    private final ProgressSnapshotRepository snapshotRepository;
    private final PhaseTransitionRepository transitionRepository;

    /**
     * 获取进度统计
     */
    @Transactional(readOnly = true)
    public ProgressStatistics getStatistics(UUID projectId) {
        CreationProgress progress = progressService.getProgress(projectId);
        
        return ProgressStatistics.builder()
                .projectId(projectId)
                .currentPhase(progress.getCurrentPhase())
                .characterCount(progress.getCharacterCount())
                .wikiEntryCount(progress.getWikiEntryCount())
                .volumeCount(progress.getVolumeCount())
                .chapterCount(progress.getChapterCount())
                .wordCount(progress.getWordCount())
                .plotLoopCount(progress.getPlotLoopCount())
                .openPlotLoops(progress.getOpenPlotLoops())
                .closedPlotLoops(progress.getClosedPlotLoops())
                .build();
    }

    /**
     * 获取进度趋势
     */
    @Transactional(readOnly = true)
    public ProgressTrend getTrend(UUID projectId, TrendPeriod period) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = switch (period) {
            case DAILY -> now.minusDays(7);
            case WEEKLY -> now.minusWeeks(4);
            case MONTHLY -> now.minusMonths(6);
        };

        List<ProgressSnapshot> snapshots = snapshotRepository.findByProjectIdAndSnapshotAtBetween(
                projectId, start, now);

        // 按时间段分组
        Map<String, List<ProgressSnapshot>> grouped = groupByPeriod(snapshots, period);

        List<TrendDataPoint> dataPoints = grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<ProgressSnapshot> periodSnapshots = entry.getValue();
                    ProgressSnapshot latest = periodSnapshots.get(periodSnapshots.size() - 1);
                    ProgressSnapshot earliest = periodSnapshots.get(0);
                    
                    return TrendDataPoint.builder()
                            .period(entry.getKey())
                            .wordCount(latest.getWordCount())
                            .wordCountChange(latest.getWordCount() - earliest.getWordCount())
                            .chapterCount(latest.getChapterCount())
                            .characterCount(latest.getCharacterCount())
                            .build();
                })
                .toList();

        return ProgressTrend.builder()
                .projectId(projectId)
                .period(period)
                .dataPoints(dataPoints)
                .build();
    }

    /**
     * 获取阶段转换历史
     */
    @Transactional(readOnly = true)
    public List<PhaseTransitionDto> getPhaseHistory(UUID projectId) {
        return transitionRepository.findByProjectIdOrderByTransitionedAtDesc(projectId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * 获取字数统计
     */
    @Transactional(readOnly = true)
    public WordCountStatistics getWordCountStats(UUID projectId) {
        CreationProgress progress = progressService.getProgress(projectId);
        
        long avgWordsPerChapter = progress.getChapterCount() > 0 
                ? progress.getWordCount() / progress.getChapterCount() 
                : 0;

        // 获取最近7天的快照计算日均字数
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        List<ProgressSnapshot> recentSnapshots = snapshotRepository.findRecentSnapshots(projectId, weekAgo);
        
        long dailyAverage = 0;
        if (recentSnapshots.size() >= 2) {
            ProgressSnapshot oldest = recentSnapshots.get(0);
            ProgressSnapshot newest = recentSnapshots.get(recentSnapshots.size() - 1);
            long days = ChronoUnit.DAYS.between(oldest.getSnapshotAt(), newest.getSnapshotAt());
            if (days > 0) {
                dailyAverage = (newest.getWordCount() - oldest.getWordCount()) / days;
            }
        }

        return WordCountStatistics.builder()
                .projectId(projectId)
                .totalWords(progress.getWordCount())
                .averageWordsPerChapter(avgWordsPerChapter)
                .dailyAverageWords(dailyAverage)
                .chapterCount(progress.getChapterCount())
                .build();
    }

    /**
     * 获取实体统计
     */
    @Transactional(readOnly = true)
    public EntityStatistics getEntityStats(UUID projectId) {
        CreationProgress progress = progressService.getProgress(projectId);
        
        return EntityStatistics.builder()
                .projectId(projectId)
                .characterCount(progress.getCharacterCount())
                .wikiEntryCount(progress.getWikiEntryCount())
                .volumeCount(progress.getVolumeCount())
                .chapterCount(progress.getChapterCount())
                .plotLoopCount(progress.getPlotLoopCount())
                .openPlotLoops(progress.getOpenPlotLoops())
                .closedPlotLoops(progress.getClosedPlotLoops())
                .plotLoopClosureRate(progress.getPlotLoopCount() > 0 
                        ? (double) progress.getClosedPlotLoops() / progress.getPlotLoopCount() * 100 
                        : 0)
                .build();
    }

    private Map<String, List<ProgressSnapshot>> groupByPeriod(List<ProgressSnapshot> snapshots, TrendPeriod period) {
        return snapshots.stream()
                .collect(Collectors.groupingBy(s -> formatPeriod(s.getSnapshotAt(), period)));
    }

    private String formatPeriod(LocalDateTime dateTime, TrendPeriod period) {
        return switch (period) {
            case DAILY -> dateTime.toLocalDate().toString();
            case WEEKLY -> dateTime.getYear() + "-W" + (dateTime.getDayOfYear() / 7);
            case MONTHLY -> dateTime.getYear() + "-" + String.format("%02d", dateTime.getMonthValue());
        };
    }

    private PhaseTransitionDto toDto(PhaseTransition transition) {
        return PhaseTransitionDto.builder()
                .id(transition.getId())
                .projectId(transition.getProjectId())
                .fromPhase(transition.getFromPhase())
                .toPhase(transition.getToPhase())
                .reason(transition.getReason())
                .triggeredBy(transition.getTriggeredBy())
                .transitionedAt(transition.getTransitionedAt())
                .build();
    }
}
