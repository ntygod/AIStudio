package com.inkflow.module.character.dto;

import com.inkflow.module.character.entity.Character;
import com.inkflow.module.character.entity.CharacterRelationship;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 角色DTO
 */
public record CharacterDto(
    UUID id,
    UUID projectId,
    String name,
    String role,
    String description,
    Map<String, Object> personality,
    List<CharacterRelationship> relationships,
    String status,
    Boolean isActive,
    String archetype,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static CharacterDto from(Character entity) {
        return new CharacterDto(
            entity.getId(),
            entity.getProjectId(),
            entity.getName(),
            entity.getRole(),
            entity.getDescription(),
            entity.getPersonality(),
            entity.getRelationships(),
            entity.getStatus(),
            entity.getIsActive(),
            entity.getArchetype(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
