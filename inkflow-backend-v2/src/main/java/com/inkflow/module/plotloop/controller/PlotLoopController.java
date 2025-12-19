package com.inkflow.module.plotloop.controller;

import com.inkflow.module.plotloop.dto.*;
import com.inkflow.module.plotloop.entity.PlotLoopStatus;
import com.inkflow.module.plotloop.service.PlotLoopService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 伏笔管理控制器
 */
@RestController
@RequestMapping("/api/plotloops")
public class PlotLoopController {

    private final PlotLoopService plotLoopService;

    public PlotLoopController(PlotLoopService plotLoopService) {
        this.plotLoopService = plotLoopService;
    }

    /**
     * 创建伏笔
     */
    @PostMapping
    public ResponseEntity<PlotLoopDto> create(@Valid @RequestBody CreatePlotLoopRequest request) {
        PlotLoopDto plotLoop = plotLoopService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(plotLoop);
    }

    /**
     * 根据ID查询伏笔
     */
    @GetMapping("/{id}")
    public ResponseEntity<PlotLoopDto> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(plotLoopService.findById(id));
    }

    /**
     * 根据项目ID查询所有伏笔
     */
    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<PlotLoopDto>> findByProjectId(@PathVariable UUID projectId) {
        return ResponseEntity.ok(plotLoopService.findByProjectId(projectId));
    }

    /**
     * 根据状态查询伏笔
     */
    @GetMapping("/project/{projectId}/status/{status}")
    public ResponseEntity<List<PlotLoopDto>> findByStatus(
            @PathVariable UUID projectId,
            @PathVariable PlotLoopStatus status) {
        return ResponseEntity.ok(plotLoopService.findByStatus(projectId, status));
    }

    /**
     * 查询开放和紧急的伏笔
     */
    @GetMapping("/project/{projectId}/active")
    public ResponseEntity<List<PlotLoopDto>> findOpenAndUrgent(@PathVariable UUID projectId) {
        return ResponseEntity.ok(plotLoopService.findOpenAndUrgent(projectId));
    }

    /**
     * 搜索伏笔
     */
    @GetMapping("/project/{projectId}/search")
    public ResponseEntity<List<PlotLoopDto>> search(
            @PathVariable UUID projectId,
            @RequestParam String keyword) {
        return ResponseEntity.ok(plotLoopService.search(projectId, keyword));
    }

    /**
     * 更新伏笔
     */
    @PutMapping("/{id}")
    public ResponseEntity<PlotLoopDto> update(
            @PathVariable UUID id,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description) {
        return ResponseEntity.ok(plotLoopService.update(id, title, description));
    }

    /**
     * 解决伏笔
     */
    @PostMapping("/{id}/resolve")
    public ResponseEntity<PlotLoopDto> resolve(
            @PathVariable UUID id,
            @RequestParam UUID chapterId,
            @RequestParam Integer chapterOrder) {
        return ResponseEntity.ok(plotLoopService.resolve(id, chapterId, chapterOrder));
    }

    /**
     * 放弃伏笔
     */
    @PostMapping("/{id}/abandon")
    public ResponseEntity<PlotLoopDto> abandon(
            @PathVariable UUID id,
            @RequestParam String reason) {
        return ResponseEntity.ok(plotLoopService.abandon(id, reason));
    }

    /**
     * 重新打开伏笔
     */
    @PostMapping("/{id}/reopen")
    public ResponseEntity<PlotLoopDto> reopen(@PathVariable UUID id) {
        return ResponseEntity.ok(plotLoopService.reopen(id));
    }

    /**
     * 检查并更新紧急状态
     */
    @PostMapping("/project/{projectId}/check-urgent")
    public ResponseEntity<Map<String, Integer>> checkUrgent(
            @PathVariable UUID projectId,
            @RequestParam int currentChapterOrder) {
        int count = plotLoopService.checkAndUpdateUrgentStatus(projectId, currentChapterOrder);
        return ResponseEntity.ok(Map.of("updatedCount", count));
    }

    /**
     * 删除伏笔
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        plotLoopService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 获取伏笔统计
     */
    @GetMapping("/project/{projectId}/stats")
    public ResponseEntity<Map<String, Long>> getStatistics(@PathVariable UUID projectId) {
        return ResponseEntity.ok(plotLoopService.getStatistics(projectId));
    }

    /**
     * 生成AI上下文
     */
    @GetMapping("/project/{projectId}/ai-context")
    public ResponseEntity<String> generateAIContext(@PathVariable UUID projectId) {
        return ResponseEntity.ok(plotLoopService.generateContextForAI(projectId));
    }
}
