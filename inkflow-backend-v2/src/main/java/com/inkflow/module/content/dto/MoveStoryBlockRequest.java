package com.inkflow.module.content.dto;

import java.util.UUID;

/**
 * 移动剧情块请求
 */
public record MoveStoryBlockRequest(
    UUID afterBlockId
) {
}
