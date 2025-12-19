package com.inkflow.module.project.dto.export;

import java.util.List;

/**
 * 导出分卷DTO
 */
public record ExportVolumeDto(
    String title,
    String description,
    int orderIndex,
    List<ExportChapterDto> chapters
) {}
