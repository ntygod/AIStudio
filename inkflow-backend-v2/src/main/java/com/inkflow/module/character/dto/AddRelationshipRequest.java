package com.inkflow.module.character.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 添加角色关系请求
 */
public record AddRelationshipRequest(
    @NotNull(message = "目标角色ID不能为空")
    UUID targetId,
    
    @NotBlank(message = "关系类型不能为空")
    String type,
    
    String description,
    
    Integer strength,
    
    Boolean bidirectional
) {}
