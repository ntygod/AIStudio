package com.inkflow.module.project.dto.export;

/**
 * 导出数据根对象
 */
public record ExportData(
    /**
     * 导出元数据
     */
    ExportMetadata metadata,
    
    /**
     * 项目数据
     */
    ExportProjectDto project
) {}
