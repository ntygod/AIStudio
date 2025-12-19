package com.inkflow.module.character.dto;

import com.inkflow.module.character.entity.CharacterArchetype;

import java.util.Map;
import java.util.UUID;

/**
 * 角色原型DTO
 */
public record CharacterArchetypeDto(
    UUID id,
    String name,
    String nameCn,
    String description,
    Map<String, Object> template,
    String[] examples
) {
    public static CharacterArchetypeDto from(CharacterArchetype entity) {
        return new CharacterArchetypeDto(
            entity.getId(),
            entity.getName(),
            entity.getNameCn(),
            entity.getDescription(),
            entity.getTemplate(),
            entity.getExamples()
        );
    }
}
