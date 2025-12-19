package com.inkflow.module.character.service;

import com.inkflow.module.character.dto.RelationshipGraphDto;
import com.inkflow.module.character.dto.RelationshipGraphDto.GraphEdge;
import com.inkflow.module.character.dto.RelationshipGraphDto.GraphNode;
import com.inkflow.module.character.entity.Character;
import com.inkflow.module.character.entity.CharacterRelationship;
import com.inkflow.module.character.repository.CharacterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 角色关系图谱服务
 * 
 * 构建和分析角色之间的关系网络
 */
@Service
@Transactional(readOnly = true)
public class RelationshipGraphService {

    private static final Logger log = LoggerFactory.getLogger(RelationshipGraphService.class);

    private final CharacterRepository characterRepository;

    public RelationshipGraphService(CharacterRepository characterRepository) {
        this.characterRepository = characterRepository;
    }

    /**
     * 构建项目的完整角色关系图谱
     */
    public RelationshipGraphDto buildGraph(UUID projectId) {
        List<Character> characters = characterRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();
        Set<String> processedEdges = new HashSet<>(); // 用于去重双向边

        for (Character character : characters) {
            // 添加节点
            nodes.add(new GraphNode(
                character.getId(),
                character.getName(),
                character.getRole(),
                character.getStatus(),
                character.getArchetype(),
                Boolean.TRUE.equals(character.getIsActive())
            ));

            // 添加边
            if (character.getRelationships() != null) {
                for (CharacterRelationship rel : character.getRelationships()) {
                    String edgeKey = createEdgeKey(character.getId(), rel.getTargetId());
                    
                    // 避免双向关系重复添加
                    if (!processedEdges.contains(edgeKey)) {
                        edges.add(new GraphEdge(
                            character.getId(),
                            rel.getTargetId(),
                            rel.getType(),
                            rel.getDescription(),
                            rel.getStrength(),
                            Boolean.TRUE.equals(rel.getBidirectional())
                        ));
                        processedEdges.add(edgeKey);
                    }
                }
            }
        }

        log.debug("构建关系图谱: 项目={}, 节点数={}, 边数={}", projectId, nodes.size(), edges.size());
        return new RelationshipGraphDto(nodes, edges);
    }

    /**
     * 获取指定角色的关系子图
     * 
     * @param characterId 中心角色ID
     * @param depth 关系深度 (1=直接关系, 2=包含二级关系)
     */
    public RelationshipGraphDto buildSubGraph(UUID characterId, int depth) {
        Set<UUID> visitedIds = new HashSet<>();
        Set<UUID> currentLevel = new HashSet<>();
        currentLevel.add(characterId);

        // BFS遍历指定深度的关系
        for (int i = 0; i < depth && !currentLevel.isEmpty(); i++) {
            visitedIds.addAll(currentLevel);
            Set<UUID> nextLevel = new HashSet<>();

            for (UUID id : currentLevel) {
                Character character = characterRepository.findById(id).orElse(null);
                if (character != null && character.getRelationships() != null) {
                    for (CharacterRelationship rel : character.getRelationships()) {
                        if (!visitedIds.contains(rel.getTargetId())) {
                            nextLevel.add(rel.getTargetId());
                        }
                    }
                }
            }
            currentLevel = nextLevel;
        }
        visitedIds.addAll(currentLevel);

        // 构建子图
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();
        Set<String> processedEdges = new HashSet<>();

        for (UUID id : visitedIds) {
            Character character = characterRepository.findById(id).orElse(null);
            if (character == null) continue;

            nodes.add(new GraphNode(
                character.getId(),
                character.getName(),
                character.getRole(),
                character.getStatus(),
                character.getArchetype(),
                Boolean.TRUE.equals(character.getIsActive())
            ));

            if (character.getRelationships() != null) {
                for (CharacterRelationship rel : character.getRelationships()) {
                    if (visitedIds.contains(rel.getTargetId())) {
                        String edgeKey = createEdgeKey(character.getId(), rel.getTargetId());
                        if (!processedEdges.contains(edgeKey)) {
                            edges.add(new GraphEdge(
                                character.getId(),
                                rel.getTargetId(),
                                rel.getType(),
                                rel.getDescription(),
                                rel.getStrength(),
                                Boolean.TRUE.equals(rel.getBidirectional())
                            ));
                            processedEdges.add(edgeKey);
                        }
                    }
                }
            }
        }

        return new RelationshipGraphDto(nodes, edges);
    }

    /**
     * 分析角色的关系统计
     */
    public Map<String, Object> analyzeRelationships(UUID projectId) {
        List<Character> characters = characterRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        
        Map<String, Integer> relationshipTypeCounts = new HashMap<>();
        int totalRelationships = 0;
        int isolatedCharacters = 0;
        int maxConnections = 0;
        String mostConnectedCharacter = null;

        for (Character character : characters) {
            int connections = character.getRelationships() != null ? character.getRelationships().size() : 0;
            
            if (connections == 0) {
                isolatedCharacters++;
            } else {
                totalRelationships += connections;
                if (connections > maxConnections) {
                    maxConnections = connections;
                    mostConnectedCharacter = character.getName();
                }

                for (CharacterRelationship rel : character.getRelationships()) {
                    relationshipTypeCounts.merge(rel.getType(), 1, Integer::sum);
                }
            }
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCharacters", characters.size());
        stats.put("totalRelationships", totalRelationships / 2); // 双向关系只算一次
        stats.put("isolatedCharacters", isolatedCharacters);
        stats.put("mostConnectedCharacter", mostConnectedCharacter);
        stats.put("maxConnections", maxConnections);
        stats.put("relationshipTypeCounts", relationshipTypeCounts);
        stats.put("averageConnections", characters.isEmpty() ? 0 : 
            (double) totalRelationships / characters.size());

        return stats;
    }

    /**
     * 查找两个角色之间的关系路径
     */
    public List<UUID> findPath(UUID sourceId, UUID targetId, int maxDepth) {
        if (sourceId.equals(targetId)) {
            return List.of(sourceId);
        }

        // BFS查找最短路径
        Queue<List<UUID>> queue = new LinkedList<>();
        Set<UUID> visited = new HashSet<>();
        
        queue.offer(List.of(sourceId));
        visited.add(sourceId);

        while (!queue.isEmpty()) {
            List<UUID> path = queue.poll();
            if (path.size() > maxDepth) {
                break;
            }

            UUID current = path.get(path.size() - 1);
            Character character = characterRepository.findById(current).orElse(null);
            
            if (character != null && character.getRelationships() != null) {
                for (CharacterRelationship rel : character.getRelationships()) {
                    UUID nextId = rel.getTargetId();
                    
                    if (nextId.equals(targetId)) {
                        List<UUID> result = new ArrayList<>(path);
                        result.add(nextId);
                        return result;
                    }

                    if (!visited.contains(nextId)) {
                        visited.add(nextId);
                        List<UUID> newPath = new ArrayList<>(path);
                        newPath.add(nextId);
                        queue.offer(newPath);
                    }
                }
            }
        }

        return Collections.emptyList(); // 未找到路径
    }

    /**
     * 创建边的唯一标识 (用于去重)
     */
    private String createEdgeKey(UUID id1, UUID id2) {
        // 确保相同的两个ID总是生成相同的key
        if (id1.compareTo(id2) < 0) {
            return id1 + "-" + id2;
        }
        return id2 + "-" + id1;
    }
}
