package com.inkflow.module.consistency.dto;

import com.inkflow.module.consistency.entity.ConsistencyWarning.Severity;
import com.inkflow.module.consistency.entity.ConsistencyWarning.WarningType;
import com.inkflow.module.evolution.entity.EntityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * 创建警告请求
 *
 * @author zsg
 * @date 2025/12/17
 */
public record CreateWarningRequest(
        @NotNull(message = "项目ID不能为空")
        UUID projectId,
        
        UUID entityId,
        
        EntityType entityType,
        
        String entityName,
        
        @NotNull(message = "警告类型不能为空")
        WarningType warningType,
        
        @NotNull(message = "严重程度不能为空")
        Severity severity,
        
        @NotBlank(message = "描述不能为空")
        String description,
        
        String suggestion,
        
        String fieldPath,
        
        String expectedValue,
        
        String actualValue,
        
        List<UUID> relatedEntityIds,
        
        String suggestedResolution
) {}
