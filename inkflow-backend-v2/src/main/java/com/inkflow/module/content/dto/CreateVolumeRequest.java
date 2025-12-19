package com.inkflow.module.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建分卷请求
 */
public record CreateVolumeRequest(
    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不能超过200字符")
    String title,
    
    @Size(max = 2000, message = "描述不能超过2000字符")
    String description
) {
}
