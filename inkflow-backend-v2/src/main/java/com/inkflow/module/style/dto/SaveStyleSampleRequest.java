package com.inkflow.module.style.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 保存风格样本请求
 */
public record SaveStyleSampleRequest(
    @NotNull(message = "项目 ID 不能为空")
    UUID projectId,
    
    UUID chapterId,
    
    @NotBlank(message = "AI 原始内容不能为空")
    String originalAI,
    
    @NotBlank(message = "用户修改内容不能为空")
    String userFinal
) {}
