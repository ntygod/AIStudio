package com.inkflow.module.content.dto;

import jakarta.validation.constraints.Size;

/**
 * 更新分卷请求
 */
public record UpdateVolumeRequest(
    @Size(max = 200, message = "标题不能超过200字符")
    String title,
    
    @Size(max = 2000, message = "描述不能超过2000字符")
    String description
) {
}
