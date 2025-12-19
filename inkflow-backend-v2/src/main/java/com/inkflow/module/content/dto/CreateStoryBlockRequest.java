package com.inkflow.module.content.dto;

import com.inkflow.module.content.entity.BlockType;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * 创建剧情块请求
 */
public record CreateStoryBlockRequest(
    @NotNull(message = "章节ID不能为空")
    UUID chapterId,
    
    String content,
    
    BlockType blockType,
    
    UUID afterBlockId,
    
    Map<String, Object> metadata
) {
}
