package com.inkflow.module.project.dto.export;

import java.util.Map;

/**
 * 导出剧情块DTO
 */
public record ExportStoryBlockDto(
    String blockType,
    String content,
    String rank,
    Map<String, Object> metadata
) {}
