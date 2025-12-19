package com.inkflow.module.character.dto;

import com.inkflow.module.character.entity.CharacterRelationship;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 创建角色请求
 */
public record CreateCharacterRequest(
    @NotNull(message = "项目ID不能为空")
    UUID projectId,
    
    @NotBlank(message = "角色名称不能为空")
    String name,
    
    @NotBlank(message = "角色类型不能为空")
    String role,
    
    String description,
    
    Map<String, Object> personality,
    
    List<CharacterRelationship> relationships,
    
    String archetype
) {}
