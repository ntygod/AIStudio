package com.inkflow.module.wiki.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 创建知识条目请求
 */
public record CreateWikiEntryRequest(
    @NotNull(message = "项目ID不能为空")
    UUID projectId,
    
    @NotBlank(message = "标题不能为空")
    String title,
    
    @NotBlank(message = "类型不能为空")
    String type,
    
    String content,
    
    String[] aliases,
    
    String[] tags,
    
    String timeVersion
) {}
