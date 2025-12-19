package com.inkflow.module.ratelimit.controller;

import com.inkflow.module.ratelimit.dto.*;
import com.inkflow.module.ratelimit.service.RateLimitConfigService;
import com.inkflow.module.ratelimit.service.RateLimitMetricsService;
import com.inkflow.module.ratelimit.service.RateLimitRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 限流管理 API
 */
@RestController
@RequestMapping("/api/admin/rate-limits")
@RequiredArgsConstructor
public class RateLimitController {

    private final RateLimitConfigService configService;
    private final RateLimitRuleService ruleService;
    private final RateLimitMetricsService metricsService;

    // ==================== 用户配置 API ====================

    @GetMapping("/configs")
    public ResponseEntity<List<RateLimitConfigDto>> getAllConfigs() {
        return ResponseEntity.ok(configService.getAllConfigs());
    }

    @GetMapping("/configs/user/{userId}")
    public ResponseEntity<RateLimitConfigDto> getConfigForUser(@PathVariable UUID userId) {
        return configService.getConfigForUser(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/configs")
    public ResponseEntity<RateLimitConfigDto> createConfig(@Valid @RequestBody RateLimitConfigRequest request) {
        return ResponseEntity.ok(configService.createConfig(request));
    }

    @PutMapping("/configs/user/{userId}")
    public ResponseEntity<RateLimitConfigDto> updateConfig(
            @PathVariable UUID userId,
            @Valid @RequestBody RateLimitConfigRequest request) {
        return ResponseEntity.ok(configService.updateConfig(userId, request));
    }

    @DeleteMapping("/configs/user/{userId}")
    public ResponseEntity<Void> deleteConfig(@PathVariable UUID userId) {
        configService.deleteConfig(userId);
        return ResponseEntity.noContent().build();
    }

    // ==================== 端点规则 API ====================

    @GetMapping("/rules")
    public ResponseEntity<List<RateLimitRuleDto>> getAllRules() {
        return ResponseEntity.ok(ruleService.getAllRules());
    }

    @PostMapping("/rules")
    public ResponseEntity<RateLimitRuleDto> createRule(@Valid @RequestBody RateLimitRuleRequest request) {
        return ResponseEntity.ok(ruleService.createRule(request));
    }

    @PutMapping("/rules/{ruleId}")
    public ResponseEntity<RateLimitRuleDto> updateRule(
            @PathVariable UUID ruleId,
            @Valid @RequestBody RateLimitRuleRequest request) {
        return ResponseEntity.ok(ruleService.updateRule(ruleId, request));
    }

    @DeleteMapping("/rules/{ruleId}")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID ruleId) {
        ruleService.deleteRule(ruleId);
        return ResponseEntity.noContent().build();
    }

    // ==================== 缓存管理 ====================

    @PostMapping("/refresh-cache")
    public ResponseEntity<Void> refreshCache() {
        configService.refreshCache();
        ruleService.refreshCache();
        return ResponseEntity.ok().build();
    }

    // ==================== 指标 API ====================

    /**
     * 获取全局限流指标摘要
     */
    @GetMapping("/metrics/summary")
    public ResponseEntity<RateLimitMetricsService.GlobalMetricsSummary> getMetricsSummary() {
        return ResponseEntity.ok(metricsService.getGlobalSummary());
    }

    /**
     * 获取所有限流指标
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, RateLimitMetricsService.RateLimitMetrics>> getAllMetrics() {
        return ResponseEntity.ok(metricsService.getAllMetrics());
    }

    /**
     * 获取指定key的限流指标
     */
    @GetMapping("/metrics/{key}")
    public ResponseEntity<RateLimitMetricsService.RateLimitMetrics> getMetrics(@PathVariable String key) {
        return ResponseEntity.ok(metricsService.getMetrics(key));
    }

    /**
     * 重置所有指标
     */
    @PostMapping("/metrics/reset")
    public ResponseEntity<Void> resetMetrics() {
        metricsService.resetAll();
        return ResponseEntity.ok().build();
    }
}
