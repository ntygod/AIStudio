package com.inkflow.module.project.dto.export;

import java.time.LocalDateTime;

/**
 * 导出元数据
 */
public record ExportMetadata(
    /**
     * 导出格式版本
     */
    String version,
    
    /**
     * 导出时间
     */
    LocalDateTime exportedAt,
    
    /**
     * 导出工具
     */
    String exportedBy
) {}
