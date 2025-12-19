package com.inkflow.module.content.dto;

import com.inkflow.module.content.entity.ChapterStatus;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

/**
 * 更新章节请求
 */
public record UpdateChapterRequest(
    UUID volumeId,
    
    @Size(max = 200, message = "标题不能超过200字符")
    String title,
    
    @Size(max = 5000, message = "摘要不能超过5000字符")
    String summary,
    
    ChapterStatus status,
    
    Map<String, Object> metadata
) {
}
