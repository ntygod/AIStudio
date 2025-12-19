package com.inkflow.module.progress.controller;

import com.inkflow.module.progress.dto.*;
import com.inkflow.module.progress.entity.ProgressSnapshot;
import com.inkflow.module.progress.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 进度管理控制器
 *
 */
@Slf4j
@RestController
@RequestMapping("/api/projects/{projectId}/progress")
@RequiredArgsConstructor
public class ProgressController {

    private final CreationProgressService progressService;
    private final ProgressPersistenceService persistenceService;
    private final ProgressStatisticsService statisticsService;
    private final PhaseTransitionService transitionService;

    /**
     * 获取当前进度
     *
     */
    @GetMapping
    public ResponseEntity<CreationProgress> getCurrentProgress(@PathVariable UUID projectId) {
        CreationProgress progress = progressService.getProgress(projectId);
        return ResponseEntity.ok(progress);
    }

    /**
     * 获取进度历史
     *
     */
    @GetMapping("/history")
    public ResponseEntity<List<ProgressSnapshot>> getProgressHistory(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "20") int limit) {
        List<ProgressSnapshot> history = persistenceService.getHistory(projectId, limit);
        return ResponseEntity.ok(history);
    }

    /**
     * 获取指定时间范围的进度历史
     *
     */
    @GetMapping("/history/range")
    public ResponseEntity<List<ProgressSnapshot>> getProgressHistoryRange(
            @PathVariable UUID projectId,
            @RequestParam LocalDateTime start,
            @RequestParam LocalDateTime end) {
        List<ProgressSnapshot> history = persistenceService.getHistoryBetween(projectId, start, end);
        return ResponseEntity.ok(history);
    }

    /**
     * 获取进度统计
     *
     */
    @GetMapping("/statistics")
    public ResponseEntity<ProgressStatistics> getStatistics(@PathVariable UUID projectId) {
        ProgressStatistics statistics = statisticsService.getStatistics(projectId);
        return ResponseEntity.ok(statistics);
    }

    /**
     * 获取进度趋势
     *
     */
    @GetMapping("/trends")
    public ResponseEntity<ProgressTrend> getTrends(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "DAILY") TrendPeriod period) {
        ProgressTrend trend = statisticsService.getTrend(projectId, period);
        return ResponseEntity.ok(trend);
    }

    /**
     * 获取字数统计
     *
     */
    @GetMapping("/word-count")
    public ResponseEntity<WordCountStatistics> getWordCountStats(@PathVariable UUID projectId) {
        WordCountStatistics stats = statisticsService.getWordCountStats(projectId);
        return ResponseEntity.ok(stats);
    }

    /**
     * 获取实体统计
     *
     */
    @GetMapping("/entities")
    public ResponseEntity<EntityStatistics> getEntityStats(@PathVariable UUID projectId) {
        EntityStatistics stats = statisticsService.getEntityStats(projectId);
        return ResponseEntity.ok(stats);
    }

    /**
     * 获取阶段转换历史
     *
     */
    @GetMapping("/phase-history")
    public ResponseEntity<List<PhaseTransitionDto>> getPhaseHistory(@PathVariable UUID projectId) {
        List<PhaseTransitionDto> history = transitionService.getTransitionHistory(projectId);
        return ResponseEntity.ok(history);
    }

    /**
     * 检查阶段转换
     * 已简化：允许任意阶段转换，由用户自行决定
     */
    @GetMapping("/phase-check")
    public ResponseEntity<PhaseTransitionCheck> checkPhaseTransition(
            @PathVariable UUID projectId,
            @RequestParam String targetPhase) {
        com.inkflow.module.project.entity.CreationPhase phase = 
                com.inkflow.module.project.entity.CreationPhase.valueOf(targetPhase);
        PhaseTransitionCheck check = progressService.checkPhaseTransition(projectId, phase);
        return ResponseEntity.ok(check);
    }

    /**
     * 手动保存进度快照
     */
    @PostMapping("/snapshot")
    public ResponseEntity<ProgressSnapshot> saveSnapshot(@PathVariable UUID projectId) {
        ProgressSnapshot snapshot = progressService.saveSnapshot(projectId);
        return ResponseEntity.ok(snapshot);
    }
}
