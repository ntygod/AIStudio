package com.inkflow.module.content.controller;

import com.inkflow.module.auth.security.UserPrincipal;
import com.inkflow.module.content.dto.*;
import com.inkflow.module.content.service.ChapterService;
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
 * 章节管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/projects/{projectId}/chapters")
@RequiredArgsConstructor
@Tag(name = "Chapters", description = "章节管理接口")
public class ChapterController {

    private final ChapterService chapterService;

    @GetMapping
    @Operation(summary = "获取章节列表", description = "获取项目的所有章节")
    public ResponseEntity<List<ChapterDto>> listChapters(
            @Parameter(description = "项目ID") @PathVariable UUID projectId,
            @AuthenticationPrincipal UserPrincipal user) {
        List<ChapterDto> chapters = chapterService.list(projectId, user.getId());
        return ResponseEntity.ok(chapters);
    }

    @GetMapping("/volume/{volumeId}")
    @Operation(summary = "获取分卷章节", description = "获取指定分卷的所有章节")
    public ResponseEntity<List<ChapterDto>> listChaptersByVolume(
            @Parameter(description = "项目ID") @PathVariable UUID projectId,
            @Parameter(description = "分卷ID") @PathVariable UUID volumeId,
            @AuthenticationPrincipal UserPrincipal user) {
        List<ChapterDto> chapters = chapterService.listByVolume(projectId, volumeId, user.getId());
        return ResponseEntity.ok(chapters);
    }

    @GetMapping("/{chapterId}")
    @Operation(summary = "获取章节详情", description = "获取单个章节的详细信息")
    public ResponseEntity<ChapterDto> getChapter(
            @Parameter(description = "项目ID") @PathVariable UUID projectId,
            @Parameter(description = "章节ID") @PathVariable UUID chapterId,
            @AuthenticationPrincipal UserPrincipal user) {
        ChapterDto chapter = chapterService.getById(projectId, chapterId, user.getId());
        return ResponseEntity.ok(chapter);
    }

    @PostMapping
    @Operation(summary = "创建章节", description = "在分卷中创建新章节")
    public ResponseEntity<ChapterDto> createChapter(
            @Parameter(description = "项目ID") @PathVariable UUID projectId,
            @Valid @RequestBody CreateChapterRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        ChapterDto chapter = chapterService.create(projectId, user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(chapter);
    }

    @PutMapping("/{chapterId}")
    @Operation(summary = "更新章节", description = "更新章节信息")
    public ResponseEntity<ChapterDto> updateChapter(
            @Parameter(description = "项目ID") @PathVariable UUID projectId,
            @Parameter(description = "章节ID") @PathVariable UUID chapterId,
            @Valid @RequestBody UpdateChapterRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        ChapterDto chapter = chapterService.update(projectId, chapterId, user.getId(), request);
        return ResponseEntity.ok(chapter);
    }

    @DeleteMapping("/{chapterId}")
    @Operation(summary = "删除章节", description = "删除章节（软删除）")
    public ResponseEntity<Void> deleteChapter(
            @Parameter(description = "项目ID") @PathVariable UUID projectId,
            @Parameter(description = "章节ID") @PathVariable UUID chapterId,
            @AuthenticationPrincipal UserPrincipal user) {
        chapterService.delete(projectId, chapterId, user.getId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/reorder")
    @Operation(summary = "重排序章节", description = "调整章节顺序")
    public ResponseEntity<Void> reorderChapters(
            @Parameter(description = "项目ID") @PathVariable UUID projectId,
            @Valid @RequestBody List<ReorderRequest> requests,
            @AuthenticationPrincipal UserPrincipal user) {
        chapterService.reorder(projectId, user.getId(), requests);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{chapterId}/move/{targetVolumeId}")
    @Operation(summary = "移动章节", description = "将章节移动到另一个分卷")
    public ResponseEntity<ChapterDto> moveChapter(
            @Parameter(description = "项目ID") @PathVariable UUID projectId,
            @Parameter(description = "章节ID") @PathVariable UUID chapterId,
            @Parameter(description = "目标分卷ID") @PathVariable UUID targetVolumeId,
            @AuthenticationPrincipal UserPrincipal user) {
        ChapterDto chapter = chapterService.moveToVolume(projectId, chapterId, targetVolumeId, user.getId());
        return ResponseEntity.ok(chapter);
    }

    @GetMapping("/{chapterId}/content")
    @Operation(summary = "获取章节内容", description = "获取章节的完整内容用于编辑")
    public ResponseEntity<ChapterContentDto> getChapterContent(
            @Parameter(description = "项目ID") @PathVariable UUID projectId,
            @Parameter(description = "章节ID") @PathVariable UUID chapterId,
            @AuthenticationPrincipal UserPrincipal user) {
        ChapterContentDto content = chapterService.getContent(projectId, chapterId, user.getId());
        return ResponseEntity.ok(content);
    }

    @PutMapping("/{chapterId}/content")
    @Operation(summary = "保存章节内容", description = "保存章节内容")
    public ResponseEntity<ChapterContentDto> saveChapterContent(
            @Parameter(description = "项目ID") @PathVariable UUID projectId,
            @Parameter(description = "章节ID") @PathVariable UUID chapterId,
            @Valid @RequestBody SaveContentRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        ChapterContentDto content = chapterService.saveContent(projectId, chapterId, user.getId(), request);
        return ResponseEntity.ok(content);
    }
}
