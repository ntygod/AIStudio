package com.inkflow.module.character.controller;

import com.inkflow.module.character.dto.*;
import com.inkflow.module.character.service.CharacterArchetypeService;
import com.inkflow.module.character.service.CharacterService;
import com.inkflow.module.character.service.RelationshipGraphService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 角色管理控制器
 */
@RestController
@RequestMapping("/api/characters")
public class CharacterController {

    private final CharacterService characterService;
    private final RelationshipGraphService graphService;
    private final CharacterArchetypeService archetypeService;

    public CharacterController(
            CharacterService characterService,
            RelationshipGraphService graphService,
            CharacterArchetypeService archetypeService) {
        this.characterService = characterService;
        this.graphService = graphService;
        this.archetypeService = archetypeService;
    }

    // ==================== 角色CRUD ====================

    /**
     * 创建角色
     */
    @PostMapping
    public ResponseEntity<CharacterDto> create(@Valid @RequestBody CreateCharacterRequest request) {
        CharacterDto character = characterService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(character);
    }

    /**
     * 根据ID查询角色
     */
    @GetMapping("/{id}")
    public ResponseEntity<CharacterDto> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(characterService.findById(id));
    }

    /**
     * 根据项目ID查询所有角色
     */
    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<CharacterDto>> findByProjectId(@PathVariable UUID projectId) {
        return ResponseEntity.ok(characterService.findByProjectId(projectId));
    }

    /**
     * 根据项目ID和角色类型查询
     */
    @GetMapping("/project/{projectId}/role/{role}")
    public ResponseEntity<List<CharacterDto>> findByRole(
            @PathVariable UUID projectId,
            @PathVariable String role) {
        return ResponseEntity.ok(characterService.findByProjectIdAndRole(projectId, role));
    }

    /**
     * 查询项目中的活跃角色
     */
    @GetMapping("/project/{projectId}/active")
    public ResponseEntity<List<CharacterDto>> findActive(@PathVariable UUID projectId) {
        return ResponseEntity.ok(characterService.findActiveByProjectId(projectId));
    }

    /**
     * 搜索角色
     */
    @GetMapping("/project/{projectId}/search")
    public ResponseEntity<List<CharacterDto>> search(
            @PathVariable UUID projectId,
            @RequestParam String keyword) {
        return ResponseEntity.ok(characterService.search(projectId, keyword));
    }

    /**
     * 更新角色
     */
    @PutMapping("/{id}")
    public ResponseEntity<CharacterDto> update(
            @PathVariable UUID id,
            @RequestBody UpdateCharacterRequest request) {
        return ResponseEntity.ok(characterService.update(id, request));
    }

    /**
     * 更新角色状态
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<CharacterDto> updateStatus(
            @PathVariable UUID id,
            @RequestParam String status) {
        return ResponseEntity.ok(characterService.updateStatus(id, status));
    }

    /**
     * 删除角色
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        characterService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 角色关系 ====================

    /**
     * 添加角色关系
     */
    @PostMapping("/{id}/relationships")
    public ResponseEntity<CharacterDto> addRelationship(
            @PathVariable UUID id,
            @Valid @RequestBody AddRelationshipRequest request) {
        return ResponseEntity.ok(characterService.addRelationship(id, request));
    }

    /**
     * 移除角色关系
     */
    @DeleteMapping("/{id}/relationships/{targetId}")
    public ResponseEntity<CharacterDto> removeRelationship(
            @PathVariable UUID id,
            @PathVariable UUID targetId) {
        return ResponseEntity.ok(characterService.removeRelationship(id, targetId));
    }

    // ==================== 关系图谱 ====================

    /**
     * 获取项目的完整关系图谱
     */
    @GetMapping("/project/{projectId}/graph")
    public ResponseEntity<RelationshipGraphDto> getGraph(@PathVariable UUID projectId) {
        return ResponseEntity.ok(graphService.buildGraph(projectId));
    }

    /**
     * 获取角色的关系子图
     */
    @GetMapping("/{id}/graph")
    public ResponseEntity<RelationshipGraphDto> getSubGraph(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "2") int depth) {
        return ResponseEntity.ok(graphService.buildSubGraph(id, depth));
    }

    /**
     * 获取关系统计分析
     */
    @GetMapping("/project/{projectId}/graph/stats")
    public ResponseEntity<Map<String, Object>> getGraphStats(@PathVariable UUID projectId) {
        return ResponseEntity.ok(graphService.analyzeRelationships(projectId));
    }

    /**
     * 查找两个角色之间的关系路径
     */
    @GetMapping("/path")
    public ResponseEntity<List<UUID>> findPath(
            @RequestParam UUID source,
            @RequestParam UUID target,
            @RequestParam(defaultValue = "5") int maxDepth) {
        return ResponseEntity.ok(graphService.findPath(source, target, maxDepth));
    }

    // ==================== 角色原型 ====================

    /**
     * 获取所有角色原型
     */
    @GetMapping("/archetypes")
    public ResponseEntity<List<CharacterArchetypeDto>> getAllArchetypes() {
        return ResponseEntity.ok(archetypeService.findAll());
    }

    /**
     * 根据名称获取角色原型
     */
    @GetMapping("/archetypes/{name}")
    public ResponseEntity<CharacterArchetypeDto> getArchetype(@PathVariable String name) {
        return ResponseEntity.ok(archetypeService.findByName(name));
    }

    /**
     * 生成基于原型的角色提示词
     */
    @GetMapping("/archetypes/{name}/prompt")
    public ResponseEntity<String> getArchetypePrompt(@PathVariable String name) {
        return ResponseEntity.ok(archetypeService.generatePrompt(name));
    }

    /**
     * 统计项目中的角色数量
     */
    @GetMapping("/project/{projectId}/count")
    public ResponseEntity<Long> countByProjectId(@PathVariable UUID projectId) {
        return ResponseEntity.ok(characterService.countByProjectId(projectId));
    }
}
