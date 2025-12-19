package com.inkflow.module.content.dto;

import com.inkflow.module.content.entity.BlockType;

import java.util.Map;

/**
 * 更新剧情块请求
 */
public record UpdateStoryBlockRequest(
    String content,
    BlockType blockType,
    Map<String, Object> metadata
) {
}
