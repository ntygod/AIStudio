package com.inkflow.module.snapshot.controller;

import com.inkflow.module.auth.security.UserPrincipal;
import com.inkflow.module.snapshot.dto.ChapterSnapshotDto;
import com.inkflow.module.snapshot.dto.CreateSnapshotRequest;
import com.inkflow.module.snapshot.service.ChapterSnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 章节快照控制器
 */
@Tag(name = "章节快照", description = "章节版本历史管理 API")
@RestController
@RequestMapping("/api/chapters/{chapterId}/snapshots")
public class ChapterSnapshotController {

    private final ChapterSnapshotService snapshotService;

    public ChapterSnapshotController(ChapterSnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @Operation(summary = "获取章节快照列表", description = "获取章节的所有历史快照")
    @GetMapping
    public ResponseEntity<List<ChapterSnapshotDto>> getSnapshots(
            @PathVariable UUID chapterId,
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(snapshotService.getSnapshots(chapterId, user.getId()));
    }

    @Operation(summary = "创建快照", description = "手动创建章节快照")
    @PostMapping
    public ResponseEntity<ChapterSnapshotDto> createSnapshot(
            @PathVariable UUID chapterId,
            @Valid @RequestBody CreateSnapshotRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(snapshotService.createSnapshot(chapterId, request, user.getId()));
    }

    @Operation(summary = "获取单个快照")
    @GetMapping("/{snapshotId}")
    public ResponseEntity<ChapterSnapshotDto> getSnapshot(
            @PathVariable UUID chapterId,
            @PathVariable UUID snapshotId,
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(snapshotService.getSnapshot(snapshotId, user.getId()));
    }

    @Operation(summary = "删除快照")
    @DeleteMapping("/{snapshotId}")
    public ResponseEntity<Void> deleteSnapshot(
            @PathVariable UUID chapterId,
            @PathVariable UUID snapshotId,
            @AuthenticationPrincipal UserPrincipal user) {
        snapshotService.deleteSnapshot(snapshotId, user.getId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "删除所有快照", description = "删除章节的所有历史快照")
    @DeleteMapping
    public ResponseEntity<Void> deleteAllSnapshots(
            @PathVariable UUID chapterId,
            @AuthenticationPrincipal UserPrincipal user) {
        snapshotService.deleteAllSnapshots(chapterId, user.getId());
        return ResponseEntity.noContent().build();
    }
}
