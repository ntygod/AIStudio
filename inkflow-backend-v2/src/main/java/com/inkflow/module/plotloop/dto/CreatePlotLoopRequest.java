package com.inkflow.module.plotloop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 创建伏笔请求
 */
public record CreatePlotLoopRequest(
    @NotNull(message = "项目ID不能为空")
    UUID projectId,
    
    @NotBlank(message = "伏笔标题不能为空")
    String title,
    
    String description,
    
    UUID introChapterId,
    
    Integer introChapterOrder
) {}
