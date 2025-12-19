package com.inkflow.module.evolution.controller;

import com.inkflow.module.evolution.dto.*;
import com.inkflow.module.evolution.entity.EntityType;
import com.inkflow.module.evolution.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 演进时间线控制器
 */
@RestController
@RequestMapping("/api/evolution")
public class EvolutionController {

    private final StateRetrievalService stateRetrievalService;
    private final EvolutionAnalysisService analysisService;
    private final ConsistencyCheckService consistencyCheckService;
    private final PreflightService preflightService;

    public EvolutionController(
            StateRetrievalService stateRetrievalService,
            EvolutionAnalysisService analysisService,
            ConsistencyCheckService consistencyCheckService,
            PreflightService preflightService) {
        this.stateRetrievalService = stateRetrievalService;
        this.analysisService = analysisService;
        this.consistencyCheckService = consistencyCheckService;
        this.preflightService = preflightService;
    }

    /**
     * 获取实体在指定章节的状态
     */
    @GetMapping("/state")
    public ResponseEntity<Map<String, Object>> getStateAtChapter(
            @RequestParam EntityType entityType,
            @RequestParam UUID entityId,
            @RequestParam Integer chapterOrder) {

        return stateRetrievalService.getStateAtChapter(entityType, entityId, chapterOrder)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 获取实体的最新状态
     */
    @GetMapping("/state/latest")
    public ResponseEntity<Map<String, Object>> getLatestState(
            @RequestParam EntityType entityType,
            @RequestParam UUID entityId) {

        return stateRetrievalService.getLatestState(entityType, entityId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 获取实体的所有快照
     */
    @GetMapping("/snapshots")
    public ResponseEntity<List<StateSnapshotDto>> getAllSnapshots(
            @RequestParam EntityType entityType,
            @RequestParam UUID entityId) {

        List<StateSnapshotDto> snapshots = stateRetrievalService.getAllSnapshots(entityType, entityId);
        return ResponseEntity.ok(snapshots);
    }

    /**
     * 获取快照详情
     */
    @GetMapping("/snapshots/{snapshotId}")
    public ResponseEntity<StateSnapshotDto> getSnapshotDetails(@PathVariable UUID snapshotId) {
        return stateRetrievalService.getSnapshotWithDetails(snapshotId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 比较两个章节之间的状态差异
     */
    @GetMapping("/compare")
    public ResponseEntity<Map<String, StateChange>> compareStates(
            @RequestParam EntityType entityType,
            @RequestParam UUID entityId,
            @RequestParam Integer fromChapterOrder,
            @RequestParam Integer toChapterOrder) {

        Map<String, StateChange> changes = stateRetrievalService.compareStates(
                entityType, entityId, fromChapterOrder, toChapterOrder);
        return ResponseEntity.ok(changes);
    }

    /**
     * 获取实体的演进轨迹
     */
    @GetMapping("/track")
    public ResponseEntity<List<Map<String, Object>>> getEvolutionTrack(
            @RequestParam EntityType entityType,
            @RequestParam UUID entityId,
            @RequestParam Integer fromChapterOrder,
            @RequestParam Integer toChapterOrder) {

        List<Map<String, Object>> track = stateRetrievalService.getEvolutionTrack(
                entityType, entityId, fromChapterOrder, toChapterOrder);
        return ResponseEntity.ok(track);
    }

    /**
     * 分析章节内容的演进
     */
    @PostMapping("/analyze")
    public ResponseEntity<List<StateChange>> analyzeChapter(@RequestBody AnalyzeRequest request) {
        List<StateChange> changes = analysisService.analyzeChapterForEvolution(
                request.projectId(),
                request.chapterId(),
                request.chapterOrder(),
                request.chapterContent(),
                request.entityId(),
                request.entityType(),
                request.currentEntityState()
        );
        return ResponseEntity.ok(changes);
    }

    /**
     * 一致性检查
     */
    @PostMapping("/consistency-check")
    public ResponseEntity<List<InconsistencyReport>> checkConsistency(
            @RequestBody ConsistencyCheckRequest request) {

        List<ConsistencyCheckService.EntityReference> refs = request.entities().stream()
                .map(e -> new ConsistencyCheckService.EntityReference(
                        e.entityId(), e.entityType(), e.entityName()))
                .toList();

        List<InconsistencyReport> reports = consistencyCheckService.checkConsistency(
                request.projectId(),
                request.chapterId(),
                request.chapterOrder(),
                request.chapterContent(),
                refs
        );
        return ResponseEntity.ok(reports);
    }

    /**
     * 预检
     */
    @PostMapping("/preflight")
    public ResponseEntity<PreflightService.PreflightResult> preflight(
            @RequestBody PreflightService.PreflightRequest request) {

        PreflightService.PreflightResult result = preflightService.preflight(request);
        return ResponseEntity.ok(result);
    }

    // Request DTOs
    public record AnalyzeRequest(
            UUID projectId,
            UUID chapterId,
            Integer chapterOrder,
            String chapterContent,
            UUID entityId,
            EntityType entityType,
            Map<String, Object> currentEntityState
    ) {}

    public record ConsistencyCheckRequest(
            UUID projectId,
            UUID chapterId,
            Integer chapterOrder,
            String chapterContent,
            List<EntityRef> entities
    ) {}

    public record EntityRef(
            UUID entityId,
            EntityType entityType,
            String entityName
    ) {}
}
