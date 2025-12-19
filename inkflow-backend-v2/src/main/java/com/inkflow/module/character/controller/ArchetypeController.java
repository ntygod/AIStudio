package com.inkflow.module.character.controller;

import com.inkflow.module.character.dto.CharacterArchetypeDto;
import com.inkflow.module.character.service.CharacterArchetypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 角色原型控制器
 */
@Tag(name = "角色原型", description = "角色原型管理 API")
@RestController
@RequestMapping("/api/archetypes")
public class ArchetypeController {

    private final CharacterArchetypeService archetypeService;

    public ArchetypeController(CharacterArchetypeService archetypeService) {
        this.archetypeService = archetypeService;
    }

    @Operation(summary = "获取所有角色原型", description = "获取预设的角色原型列表")
    @GetMapping
    public ResponseEntity<List<CharacterArchetypeDto>> getAllArchetypes() {
        return ResponseEntity.ok(archetypeService.findAll());
    }

    @Operation(summary = "根据 ID 获取角色原型")
    @GetMapping("/{id}")
    public ResponseEntity<CharacterArchetypeDto> getArchetypeById(@PathVariable UUID id) {
        return ResponseEntity.ok(archetypeService.findById(id));
    }

    @Operation(summary = "根据名称获取角色原型")
    @GetMapping("/by-name/{name}")
    public ResponseEntity<CharacterArchetypeDto> getArchetypeByName(@PathVariable String name) {
        return ResponseEntity.ok(archetypeService.findByName(name));
    }

    @Operation(summary = "获取原型特征模板")
    @GetMapping("/{name}/template")
    public ResponseEntity<Map<String, Object>> getTemplate(@PathVariable String name) {
        return ResponseEntity.ok(archetypeService.getTemplate(name));
    }

    @Operation(summary = "生成角色创建提示词", description = "基于原型生成 AI 角色创建提示词")
    @GetMapping("/{name}/prompt")
    public ResponseEntity<Map<String, String>> generatePrompt(@PathVariable String name) {
        String prompt = archetypeService.generatePrompt(name);
        return ResponseEntity.ok(Map.of("prompt", prompt));
    }
}
