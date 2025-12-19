package com.inkflow.module.project.dto.export;

import java.util.List;
import java.util.Map;

/**
 * 导出章节DTO
 */
public record ExportChapterDto(
    String title,
    String summary,
    int orderIndex,
    String status,
    Map<String, Object> metadata,
    List<ExportStoryBlockDto> blocks
) {}
