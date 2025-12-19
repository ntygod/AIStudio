package com.inkflow.module.character.dto;

import com.inkflow.module.character.entity.CharacterRelationship;

import java.util.List;
import java.util.Map;

/**
 * 更新角色请求
 */
public record UpdateCharacterRequest(
    String name,
    String role,
    String description,
    Map<String, Object> personality,
    List<CharacterRelationship> relationships,
    String status,
    String archetype
) {}
