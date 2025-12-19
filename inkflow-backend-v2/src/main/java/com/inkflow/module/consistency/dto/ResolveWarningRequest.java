package com.inkflow.module.consistency.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 解决警告请求
 *
 * @author zsg
 * @date 2025/12/17
 */
public record ResolveWarningRequest(
        @NotBlank(message = "解决方法不能为空")
        String resolutionMethod
) {}
