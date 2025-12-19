package com.inkflow.module.usage.controller;

import com.inkflow.module.usage.service.TokenCounterService;
import com.inkflow.module.usage.service.TokenCounterService.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Token使用量控制器
 */
@RestController
@RequestMapping("/api/v1/usage")
@Tag(name = "使用量统计", description = "Token使用量统计API")
public class UsageController {

    private final TokenCounterService tokenCounterService;

    public UsageController(TokenCounterService tokenCounterService) {
        this.tokenCounterService = tokenCounterService;
    }

    @GetMapping("/today")
    @Operation(summary = "获取今日使用量")
    public ResponseEntity<UsageSummary> getTodayUsage(@RequestParam UUID userId) {
        return ResponseEntity.ok(tokenCounterService.getTodayUsage(userId));
    }

    @GetMapping("/monthly")
    @Operation(summary = "获取本月使用量")
    public ResponseEntity<UsageSummary> getMonthlyUsage(@RequestParam UUID userId) {
        return ResponseEntity.ok(tokenCounterService.getMonthlyUsage(userId));
    }

    @GetMapping("/project/{projectId}")
    @Operation(summary = "获取项目总使用量")
    public ResponseEntity<Map<String, Long>> getProjectUsage(@PathVariable UUID projectId) {
        Long total = tokenCounterService.getProjectTotalUsage(projectId);
        return ResponseEntity.ok(Map.of("totalTokens", total));
    }

    @GetMapping("/by-model")
    @Operation(summary = "按模型分组的使用统计")
    public ResponseEntity<Map<String, Long>> getUsageByModel(
            @RequestParam UUID userId,
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(tokenCounterService.getUsageByModel(userId, days));
    }

    @GetMapping("/trend")
    @Operation(summary = "获取每日使用趋势")
    public ResponseEntity<List<DailyUsage>> getDailyTrend(
            @RequestParam UUID userId,
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(tokenCounterService.getDailyUsageTrend(userId, days));
    }

    @PostMapping("/estimate")
    @Operation(summary = "估算文本Token数量")
    public ResponseEntity<Map<String, Integer>> estimateTokens(@RequestBody String text) {
        int tokens = tokenCounterService.estimateTokens(text);
        return ResponseEntity.ok(Map.of("estimatedTokens", tokens));
    }

    @PostMapping("/record")
    @Operation(summary = "记录Token使用")
    public ResponseEntity<Void> recordUsage(@RequestBody RecordUsageRequest request) {
        tokenCounterService.recordUsage(
                request.userId(),
                request.projectId(),
                request.modelName(),
                request.provider(),
                request.promptTokens(),
                request.completionTokens(),
                request.operationType()
        );
        return ResponseEntity.ok().build();
    }

    public record RecordUsageRequest(
            UUID userId,
            UUID projectId,
            String modelName,
            String provider,
            int promptTokens,
            int completionTokens,
            String operationType
    ) {}
}
