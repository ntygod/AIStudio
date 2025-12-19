package com.inkflow.module.character.dto;

import java.util.List;
import java.util.UUID;

/**
 * 角色关系图谱DTO
 */
public record RelationshipGraphDto(
    List<GraphNode> nodes,
    List<GraphEdge> edges
) {
    /**
     * 图谱节点 (角色)
     */
    public record GraphNode(
        UUID id,
        String name,
        String role,
        String status,
        String archetype,
        boolean isActive
    ) {}

    /**
     * 图谱边 (关系)
     */
    public record GraphEdge(
        UUID source,
        UUID target,
        String type,
        String description,
        Integer strength,
        boolean bidirectional
    ) {}
}
