package com.inkflow.module.content.controller;

import com.inkflow.module.auth.security.UserPrincipal;
import com.inkflow.module.content.dto.*;
import com.inkflow.module.content.entity.StoryBlock;
import com.inkflow.module.content.service.StoryBlockService;
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
 * 剧情块管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/projects/{projectId}/chapters/{chapterId}/blocks")
@RequiredArgsConstructor
@Tag(name = "StoryBlocks", description = "剧情块管理接口")
public class StoryBlockController {

    private final StoryBlockService storyBlockService;

    @GetMapping
    @Operation(summary = "获取剧情块列表", description = "获取章节的所有剧情块")
    public ResponseEntity<List<StoryBlockDto>> listBlocks(
            @Parameter(description = "项目ID") @PathVariable UUID projectId,
            @Parameter(description = "章节ID") @PathVariable UUID chapterId,
            @AuthenticationPrincipal UserPrincipal user) {
        List<StoryBlock> blocks = storyBlockService.getBlocksByChapter(chapterId);
        List<StoryBlockDto> dtos = blocks.stream().map(this::toDto).toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{blockId}")
    @Operation(summary = "获取剧情块详情", description = "获取单个剧情块的详细信息")
    public ResponseEntity<StoryBlockDto> getBlock(
            @Parameter(description = "项目ID") @PathVariable UUID projectId,
            @Parameter(description = "章节ID") @PathVariable UUID chapterId,
            @Parameter(description = "剧情块ID") @PathVariable UUID blockId,
            @AuthenticationPrincipal UserPrincipal user) {
        StoryBlock block = storyBlockService.getBlock(blockId, chapterId);
        return ResponseEntity.ok(toDto(block));
    }

    @PostMapping
    @Operation(summary = "创建剧情块", description = "在章节中创建新剧情块")
    public ResponseEntity<StoryBlockDto> createBlock(
            @Parameter(description = "项目ID") @PathVariable UUID projectId,
            @Parameter(description = "章节ID") @PathVariable UUID chapterId,
            @Valid @RequestBody CreateStoryBlockRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        StoryBlock block;
        if (request.afterBlockId() != null) {
            block = storyBlockService.insertBlockAfter(
                    chapterId, request.afterBlockId(), 
                    request.content(), request.blockType(), request.metadata());
        } else {
            block = storyBlockService.createBlock(
                    chapterId, request.content(), 
                    request.blockType(), request.metadata());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(block));
    }

    @PutMapping("/{blockId}")
    @Operation(summary = "更新剧情块", description = "更新剧情块内容")
    public ResponseEntity<StoryBlockDto> updateBlock(
            @Parameter(description = "项目ID") @PathVariable UUID projectId,
            @Parameter(description = "章节ID") @PathVariable UUID chapterId,
            @Parameter(description = "剧情块ID") @PathVariable UUID blockId,
            @Valid @RequestBody UpdateStoryBlockRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        StoryBlock block = storyBlockService.getBlock(blockId, chapterId);
        
        if (request.content() != null) {
            block = storyBlockService.updateContent(blockId, chapterId, request.content());
        }
        if (request.blockType() != null) {
            block = storyBlockService.updateBlockType(blockId, chapterId, request.blockType());
        }
        
        return ResponseEntity.ok(toDto(block));
    }

    @DeleteMapping("/{blockId}")
    @Operation(summary = "删除剧情块", description = "删除剧情块（软删除）")
    public ResponseEntity<Void> deleteBlock(
            @Parameter(description = "项目ID") @PathVariable UUID projectId,
            @Parameter(description = "章节ID") @PathVariable UUID chapterId,
            @Parameter(description = "剧情块ID") @PathVariable UUID blockId,
            @AuthenticationPrincipal UserPrincipal user) {
        storyBlockService.deleteBlock(blockId, chapterId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{blockId}/move")
    @Operation(summary = "移动剧情块", description = "移动剧情块到新位置")
    public ResponseEntity<StoryBlockDto> moveBlock(
            @Parameter(description = "项目ID") @PathVariable UUID projectId,
            @Parameter(description = "章节ID") @PathVariable UUID chapterId,
            @Parameter(description = "剧情块ID") @PathVariable UUID blockId,
            @Valid @RequestBody MoveStoryBlockRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        StoryBlock block = storyBlockService.moveBlock(blockId, chapterId, request.afterBlockId());
        return ResponseEntity.ok(toDto(block));
    }

    @GetMapping("/merged")
    @Operation(summary = "获取合并内容", description = "获取章节所有剧情块的合并文本")
    public ResponseEntity<String> getMergedContent(
            @Parameter(description = "项目ID") @PathVariable UUID projectId,
            @Parameter(description = "章节ID") @PathVariable UUID chapterId,
            @AuthenticationPrincipal UserPrincipal user) {
        String content = storyBlockService.mergeChapterContent(chapterId);
        return ResponseEntity.ok(content);
    }

    private StoryBlockDto toDto(StoryBlock block) {
        return new StoryBlockDto(
                block.getId(),
                block.getChapterId(),
                block.getContent(),
                block.getBlockType(),
                block.getRank(),
                block.getWordCount(),
                block.getMetadata(),
                block.getCreatedAt(),
                block.getUpdatedAt()
        );
    }
}
