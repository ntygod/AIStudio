package com.inkflow.module.project.dto.export;

import java.util.List;
import java.util.Map;

/**
 * 导出项目DTO
 */
public record ExportProjectDto(
    String title,
    String description,
    String coverUrl,
    String status,
    String creationPhase,
    Map<String, Object> metadata,
    Map<String, Object> worldSettings,
    List<ExportVolumeDto> volumes
) {}
