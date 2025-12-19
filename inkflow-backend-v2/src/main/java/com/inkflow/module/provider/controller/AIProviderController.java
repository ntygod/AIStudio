package com.inkflow.module.provider.controller;

import com.inkflow.module.auth.security.UserPrincipal;
import com.inkflow.module.provider.dto.ProviderConfigDto;
import com.inkflow.module.provider.dto.SaveProviderConfigRequest;
import com.inkflow.module.provider.entity.ProviderType;
import com.inkflow.module.provider.service.AIProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 服务商配置控制器
 */
@Tag(name = "AI 服务商配置", description = "管理 AI 服务商的 API Key 和配置")
@RestController
@RequestMapping("/api/ai-providers")
public class AIProviderController {

    private final AIProviderService providerService;

    public AIProviderController(AIProviderService providerService) {
        this.providerService = providerService;
    }

    @Operation(summary = "获取所有服务商配置", description = "获取用户的所有 AI 服务商配置状态")
    @GetMapping
    public ResponseEntity<List<ProviderConfigDto>> getAllConfigs(
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(providerService.getAllConfigs(user.getId()));
    }

    @Operation(summary = "获取已配置的服务商", description = "获取用户已配置 API Key 的服务商列表")
    @GetMapping("/configured")
    public ResponseEntity<List<ProviderType>> getConfiguredProviders(
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(providerService.getConfiguredProviders(user.getId()));
    }

    @Operation(summary = "保存服务商配置", description = "保存或更新服务商的 API Key 和配置")
    @PostMapping
    public ResponseEntity<ProviderConfigDto> saveConfig(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody SaveProviderConfigRequest request) {
        return ResponseEntity.ok(providerService.saveConfig(user.getId(), request));
    }

    @Operation(summary = "删除服务商配置", description = "删除指定服务商的配置")
    @DeleteMapping("/{providerType}")
    public ResponseEntity<Void> deleteConfig(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable ProviderType providerType) {
        providerService.deleteConfig(user.getId(), providerType);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "检查服务商配置", description = "检查用户是否已配置指定服务商")
    @GetMapping("/{providerType}/check")
    public ResponseEntity<Boolean> checkConfig(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable ProviderType providerType) {
        return ResponseEntity.ok(providerService.hasConfig(user.getId(), providerType));
    }
}
