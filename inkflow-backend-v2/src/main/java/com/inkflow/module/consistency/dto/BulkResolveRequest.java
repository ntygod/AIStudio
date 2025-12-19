package com.inkflow.module.consistency.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * 批量解决警告请求
 *
 * @author zsg
 * @date 2025/12/17
 */
public record BulkResolveRequest(
        @NotEmpty(message = "警告ID列表不能为空")
        List<UUID> warningIds,
        
        @NotBlank(message = "解决方法不能为空")
        String resolutionMethod
) {}
