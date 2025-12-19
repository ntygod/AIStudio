package com.inkflow.module.consistency.controller;

import com.inkflow.module.consistency.dto.BulkResolveRequest;
import com.inkflow.module.consistency.dto.ConsistencyWarningDto;
import com.inkflow.module.consistency.dto.CreateWarningRequest;
import com.inkflow.module.consistency.dto.ResolveWarningRequest;
import com.inkflow.module.consistency.entity.ConsistencyWarning.Severity;
import com.inkflow.module.consistency.entity.ConsistencyWarning.WarningType;
import com.inkflow.module.consistency.service.ConsistencyWarningService;
import com.inkflow.module.evolution.entity.EntityType;
import com.inkflow.common.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 一致性检查控制器
 * 提供警告管理的 REST API
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@RestController
@RequestMapping("/consistency")
@RequiredArgsConstructor
public class ConsistencyController {

    private final ConsistencyWarningService warningService;

    /**
     * 获取项目的未解决警告列表
     */
    @GetMapping("/projects/{projectId}/warnings")
    public ResponseEntity<List<ConsistencyWarningDto>> getUnresolvedWarnings(
            @PathVariable UUID projectId,
            @RequestParam(required = false) EntityType entityType,
            @RequestParam(required = false) WarningType warningType) {
        
        List<ConsistencyWarningDto> warnings;
        
        if (entityType != null) {
            warnings = warningService.getUnresolvedWarningsByEntityType(projectId, entityType);
        } else if (warningType != null) {
            warnings = warningService.getUnresolvedWarningsByWarningType(projectId, warningType);
        } else {
            warnings = warningService.getUnresolvedWarnings(projectId);
        }
        
        return ResponseEntity.ok(warnings);
    }

    /**
     * 获取项目的未解决警告数量
     */
    @GetMapping("/projects/{projectId}/warnings/count")
    public ResponseEntity<Map<String, Object>> getWarningCount(
            @PathVariable UUID projectId) {
        
        long total = warningService.getUnresolvedCount(projectId);
        long errors = warningService.getUnresolvedCountBySeverity(projectId, Severity.ERROR);
        long warnings = warningService.getUnresolvedCountBySeverity(projectId, Severity.WARNING);
        long info = warningService.getUnresolvedCountBySeverity(projectId, Severity.INFO);
        
        return ResponseEntity.ok(Map.of(
                "total", total,
                "error", errors,
                "warning", warnings,
                "info", info
        ));
    }

    /**
     * 获取警告详情
     */
    @GetMapping("/warnings/{warningId}")
    public ResponseEntity<ConsistencyWarningDto> getWarningDetails(
            @PathVariable UUID warningId) {
        
        return warningService.getWarning(warningId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("警告不存在: " + warningId));
    }

    /**
     * 创建警告
     */
    @PostMapping("/warnings")
    public ResponseEntity<ConsistencyWarningDto> createWarning(
            @Valid @RequestBody CreateWarningRequest request) {
        
        ConsistencyWarningDto warning = warningService.createWarning(request);
        return ResponseEntity.ok(warning);
    }

    /**
     * 解决警告
     */
    @PostMapping("/warnings/{warningId}/resolve")
    public ResponseEntity<ConsistencyWarningDto> resolveWarning(
            @PathVariable UUID warningId,
            @Valid @RequestBody ResolveWarningRequest request) {
        
        ConsistencyWarningDto warning = warningService.resolveWarning(warningId, request.resolutionMethod());
        return ResponseEntity.ok(warning);
    }

    /**
     * 忽略警告
     */
    @PostMapping("/warnings/{warningId}/dismiss")
    public ResponseEntity<ConsistencyWarningDto> dismissWarning(
            @PathVariable UUID warningId) {
        
        ConsistencyWarningDto warning = warningService.dismissWarning(warningId);
        return ResponseEntity.ok(warning);
    }

    /**
     * 批量解决警告
     */
    @PostMapping("/warnings/bulk-resolve")
    public ResponseEntity<Map<String, Integer>> bulkResolve(
            @Valid @RequestBody BulkResolveRequest request) {
        
        int count = warningService.bulkResolve(request.warningIds(), request.resolutionMethod());
        return ResponseEntity.ok(Map.of("resolved", count));
    }

    /**
     * 批量忽略警告
     */
    @PostMapping("/warnings/bulk-dismiss")
    public ResponseEntity<Map<String, Integer>> bulkDismiss(
            @RequestBody List<UUID> warningIds) {
        
        int count = warningService.bulkDismiss(warningIds);
        return ResponseEntity.ok(Map.of("dismissed", count));
    }

    /**
     * 获取实体的所有警告
     */
    @GetMapping("/entities/{entityId}/warnings")
    public ResponseEntity<List<ConsistencyWarningDto>> getWarningsByEntity(
            @PathVariable UUID entityId) {
        
        List<ConsistencyWarningDto> warnings = warningService.getWarningsByEntity(entityId);
        return ResponseEntity.ok(warnings);
    }

    /**
     * 解决实体的所有警告
     */
    @PostMapping("/entities/{entityId}/warnings/resolve")
    public ResponseEntity<Map<String, Integer>> resolveWarningsByEntity(
            @PathVariable UUID entityId,
            @Valid @RequestBody ResolveWarningRequest request) {
        
        int count = warningService.resolveWarningsByEntity(entityId, request.resolutionMethod());
        return ResponseEntity.ok(Map.of("resolved", count));
    }

    /**
     * 删除实体的所有警告
     */
    @DeleteMapping("/entities/{entityId}/warnings")
    public ResponseEntity<Void> deleteWarningsByEntity(
            @PathVariable UUID entityId) {
        
        warningService.deleteWarningsByEntity(entityId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 删除项目的所有警告
     */
    @DeleteMapping("/projects/{projectId}/warnings")
    public ResponseEntity<Void> deleteWarningsByProject(
            @PathVariable UUID projectId) {
        
        warningService.deleteWarningsByProject(projectId);
        return ResponseEntity.noContent().build();
    }
}
