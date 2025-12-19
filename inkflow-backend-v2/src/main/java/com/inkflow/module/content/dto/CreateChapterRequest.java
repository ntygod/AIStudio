package com.inkflow.module.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * 创建章节请求
 */
public record CreateChapterRequest(
    @NotNull(message = "分卷ID不能为空")
    UUID volumeId,
    
    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不能超过200字符")
    String title,
    
    @Size(max = 5000, message = "摘要不能超过5000字符")
    String summary
) {
}
