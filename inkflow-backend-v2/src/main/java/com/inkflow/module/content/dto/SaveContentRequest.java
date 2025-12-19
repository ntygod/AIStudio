package com.inkflow.module.content.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 保存章节内容请求
 */
public record SaveContentRequest(
    @NotNull(message = "内容不能为空")
    String content
) {
}
