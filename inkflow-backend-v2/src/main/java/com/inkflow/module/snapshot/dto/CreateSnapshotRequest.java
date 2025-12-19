package com.inkflow.module.snapshot.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建快照请求
 */
public record CreateSnapshotRequest(
    @NotBlank(message = "内容不能为空")
    String content,
    
    String note
) {}
