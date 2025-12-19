package com.inkflow.module.content.controller;

import com.inkflow.module.auth.security.UserPrincipal;
import com.inkflow.module.content.dto.*;
import com.inkflow.module.content.service.VolumeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 分卷管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/projects/{projectId}/volumes")
@RequiredArgsConstructor
@Tag(name = "Volumes", description = "分卷管理接口")
public class VolumeController {

    private final VolumeService volumeService;

    @GetMapping
    @Operation(summary = "获取分卷列表", description = "获取项目的所有分卷")
    public ResponseEntity<List<VolumeDto>> listVolumes(
            @Parameter(description = "项目ID") @PathVariable UUID projectId,
            @AuthenticationPrincipal UserPrincipal user) {
        List<VolumeDto> volumes = volumeService.list(projectId, user.getId());
        return ResponseEntity.ok(volumes);
    }

    @GetMapping("/{volumeId}")
    @Operation(summary = "获取分卷详情", description = "获取单个分卷的详细信息")
    public ResponseEntity<VolumeDto> getVolume(
            @Parameter(description = "项目ID") @PathVariable UUID projectId,
            @Parameter(description = "分卷ID") @PathVariable UUID volumeId,
            @AuthenticationPrincipal UserPrincipal user) {
        VolumeDto volume = volumeService.getById(projectId, volumeId, user.getId());
        return ResponseEntity.ok(volume);
    }

    @PostMapping
    @Operation(summary = "创建分卷", description = "在项目中创建新分卷")
    public ResponseEntity<VolumeDto> createVolume(
            @Parameter(description = "项目ID") @PathVariable UUID projectId,
            @Valid @RequestBody CreateVolumeRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        VolumeDto volume = volumeService.create(projectId, user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(volume);
    }

    @PutMapping("/{volumeId}")
    @Operation(summary = "更新分卷", description = "更新分卷信息")
    public ResponseEntity<VolumeDto> updateVolume(
            @Parameter(description = "项目ID") @PathVariable UUID projectId,
            @Parameter(description = "分卷ID") @PathVariable UUID volumeId,
            @Valid @RequestBody UpdateVolumeRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        VolumeDto volume = volumeService.update(projectId, volumeId, user.getId(), request);
        return ResponseEntity.ok(volume);
    }

    @DeleteMapping("/{volumeId}")
    @Operation(summary = "删除分卷", description = "删除分卷（软删除）")
    public ResponseEntity<Void> deleteVolume(
            @Parameter(description = "项目ID") @PathVariable UUID projectId,
            @Parameter(description = "分卷ID") @PathVariable UUID volumeId,
            @AuthenticationPrincipal UserPrincipal user) {
        volumeService.delete(projectId, volumeId, user.getId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/reorder")
    @Operation(summary = "重排序分卷", description = "调整分卷顺序")
    public ResponseEntity<Void> reorderVolumes(
            @Parameter(description = "项目ID") @PathVariable UUID projectId,
            @Valid @RequestBody List<ReorderRequest> requests,
            @AuthenticationPrincipal UserPrincipal user) {
        volumeService.reorder(projectId, user.getId(), requests);
        return ResponseEntity.ok().build();
    }
}
