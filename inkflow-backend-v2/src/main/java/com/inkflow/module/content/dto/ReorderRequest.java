package com.inkflow.module.content.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 重排序请求
 */
public record ReorderRequest(
    @NotNull(message = "ID不能为空")
    UUID id,
    
    @NotNull(message = "新顺序不能为空")
    Integer newOrder
) {
}
