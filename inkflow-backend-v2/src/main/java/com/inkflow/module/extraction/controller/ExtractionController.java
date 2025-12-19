package com.inkflow.module.extraction.controller;

import com.inkflow.module.extraction.dto.*;
import com.inkflow.module.extraction.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 内容提取控制器
 */
@RestController
@RequestMapping("/api/extraction")
public class ExtractionController {

    private final ContentExtractionService extractionService;
    private final EntityDeduplicationService deduplicationService;
    private final RelationshipInferenceService inferenceService;

    public ExtractionController(
            ContentExtractionService extractionService,
            EntityDeduplicationService deduplicationService,
            RelationshipInferenceService inferenceService) {
        this.extractionService = extractionService;
        this.deduplicationService = deduplicationService;
        this.inferenceService = inferenceService;
    }

    /**
     * 从内容提取实体和关系
     */
    @PostMapping("/extract")
    public ResponseEntity<ExtractionResult> extractFromContent(@RequestBody ExtractionRequest request) {
        ExtractionResult result = extractionService.extractFromContent(
                request.projectId(),
                request.chapterId(),
                request.content()
        );
        return ResponseEntity.ok(result);
    }

    /**
     * 提取并去重
     *
     */
    @PostMapping("/extract-and-dedupe")
    public ResponseEntity<ExtractionResult> extractAndDedupe(@RequestBody ExtractionRequest request) {
        ExtractionResult rawResult = extractionService.extractFromContent(
                request.projectId(),
                request.chapterId(),
                request.content()
        );

        if (rawResult.status() == ExtractionResult.ExtractionStatus.FAILED) {
            return ResponseEntity.ok(rawResult);
        }

        // 去重
        List<ExtractedEntity> dedupedEntities = deduplicationService
                .deduplicateAndMerge(rawResult.entities());

        return ResponseEntity.ok(ExtractionResult.success(
                request.projectId(),
                request.chapterId(),
                dedupedEntities,
                rawResult.relationships()
        ));
    }

    /**
     * 推断关系
     */
    @PostMapping("/infer-relationships")
    public ResponseEntity<List<ExtractedRelationship>> inferRelationships(
            @RequestBody InferenceRequest request) {

        List<ExtractedRelationship> inferred = inferenceService.inferRelationships(
                request.entities(),
                request.existingRelationships(),
                request.context()
        );
        return ResponseEntity.ok(inferred);
    }

    /**
     * 构建关系图谱
     */
    @PostMapping("/build-graph")
    public ResponseEntity<Map<String, List<RelationshipInferenceService.RelationshipEdge>>> buildGraph(
            @RequestBody List<ExtractedRelationship> relationships) {

        Map<String, List<RelationshipInferenceService.RelationshipEdge>> graph =
                inferenceService.buildRelationshipGraph(relationships);
        return ResponseEntity.ok(graph);
    }

    /**
     * 完整提取流程（提取 -> 去重 -> 推断）
     */
    @PostMapping("/full-extraction")
    public ResponseEntity<FullExtractionResult> fullExtraction(@RequestBody ExtractionRequest request) {
        // 1. 提取
        ExtractionResult rawResult = extractionService.extractFromContent(
                request.projectId(),
                request.chapterId(),
                request.content()
        );

        if (rawResult.status() == ExtractionResult.ExtractionStatus.FAILED) {
            return ResponseEntity.ok(new FullExtractionResult(
                    rawResult, List.of(), Map.of()
            ));
        }

        // 2. 去重
        List<ExtractedEntity> dedupedEntities = deduplicationService
                .deduplicateAndMerge(rawResult.entities());

        // 3. 推断额外关系
        List<ExtractedRelationship> inferredRelationships = inferenceService.inferRelationships(
                dedupedEntities,
                rawResult.relationships(),
                request.content()
        );

        // 4. 合并所有关系
        List<ExtractedRelationship> allRelationships = new java.util.ArrayList<>(rawResult.relationships());
        allRelationships.addAll(inferredRelationships);

        // 5. 构建图谱
        Map<String, List<RelationshipInferenceService.RelationshipEdge>> graph =
                inferenceService.buildRelationshipGraph(allRelationships);

        ExtractionResult finalResult = ExtractionResult.success(
                request.projectId(),
                request.chapterId(),
                dedupedEntities,
                allRelationships
        );

        return ResponseEntity.ok(new FullExtractionResult(finalResult, inferredRelationships, graph));
    }

    // Request/Response DTOs
    public record ExtractionRequest(
            UUID projectId,
            UUID chapterId,
            String content
    ) {}

    public record InferenceRequest(
            List<ExtractedEntity> entities,
            List<ExtractedRelationship> existingRelationships,
            String context
    ) {}

    public record FullExtractionResult(
            ExtractionResult extractionResult,
            List<ExtractedRelationship> inferredRelationships,
            Map<String, List<RelationshipInferenceService.RelationshipEdge>> relationshipGraph
    ) {}
}
