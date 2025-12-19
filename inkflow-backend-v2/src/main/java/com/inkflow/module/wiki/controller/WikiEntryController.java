package com.inkflow.module.wiki.controller;

import com.inkflow.module.wiki.dto.*;
import com.inkflow.module.wiki.service.WikiEntryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 知识条目控制器
 */
@RestController
@RequestMapping("/api/wiki")
public class WikiEntryController {

    private final WikiEntryService wikiEntryService;

    public WikiEntryController(WikiEntryService wikiEntryService) {
        this.wikiEntryService = wikiEntryService;
    }

    /**
     * 创建知识条目
     */
    @PostMapping
    public ResponseEntity<WikiEntryDto> create(@Valid @RequestBody CreateWikiEntryRequest request) {
        WikiEntryDto entry = wikiEntryService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(entry);
    }

    /**
     * 根据ID查询条目
     */
    @GetMapping("/{id}")
    public ResponseEntity<WikiEntryDto> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(wikiEntryService.findById(id));
    }

    /**
     * 根据项目ID查询所有条目
     */
    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<WikiEntryDto>> findByProjectId(@PathVariable UUID projectId) {
        return ResponseEntity.ok(wikiEntryService.findByProjectId(projectId));
    }

    /**
     * 根据项目ID和类型查询
     */
    @GetMapping("/project/{projectId}/type/{type}")
    public ResponseEntity<List<WikiEntryDto>> findByType(
            @PathVariable UUID projectId,
            @PathVariable String type) {
        return ResponseEntity.ok(wikiEntryService.findByProjectIdAndType(projectId, type));
    }

    /**
     * 搜索条目
     */
    @GetMapping("/project/{projectId}/search")
    public ResponseEntity<List<WikiEntryDto>> search(
            @PathVariable UUID projectId,
            @RequestParam String keyword) {
        return ResponseEntity.ok(wikiEntryService.search(projectId, keyword));
    }

    /**
     * 根据别名搜索
     */
    @GetMapping("/project/{projectId}/alias")
    public ResponseEntity<List<WikiEntryDto>> findByAlias(
            @PathVariable UUID projectId,
            @RequestParam String alias) {
        return ResponseEntity.ok(wikiEntryService.findByAlias(projectId, alias));
    }

    /**
     * 根据标签搜索
     */
    @GetMapping("/project/{projectId}/tag")
    public ResponseEntity<List<WikiEntryDto>> findByTag(
            @PathVariable UUID projectId,
            @RequestParam String tag) {
        return ResponseEntity.ok(wikiEntryService.findByTag(projectId, tag));
    }

    /**
     * 根据时间版本查询
     */
    @GetMapping("/project/{projectId}/version")
    public ResponseEntity<List<WikiEntryDto>> findByTimeVersion(
            @PathVariable UUID projectId,
            @RequestParam String version) {
        return ResponseEntity.ok(wikiEntryService.findByTimeVersion(projectId, version));
    }

    /**
     * 更新条目
     */
    @PutMapping("/{id}")
    public ResponseEntity<WikiEntryDto> update(
            @PathVariable UUID id,
            @RequestBody UpdateWikiEntryRequest request) {
        return ResponseEntity.ok(wikiEntryService.update(id, request));
    }

    /**
     * 添加别名
     */
    @PostMapping("/{id}/aliases")
    public ResponseEntity<WikiEntryDto> addAlias(
            @PathVariable UUID id,
            @RequestParam String alias) {
        return ResponseEntity.ok(wikiEntryService.addAlias(id, alias));
    }

    /**
     * 添加标签
     */
    @PostMapping("/{id}/tags")
    public ResponseEntity<WikiEntryDto> addTag(
            @PathVariable UUID id,
            @RequestParam String tag) {
        return ResponseEntity.ok(wikiEntryService.addTag(id, tag));
    }

    /**
     * 删除条目
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        wikiEntryService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 统计项目中的条目数量
     */
    @GetMapping("/project/{projectId}/count")
    public ResponseEntity<Long> countByProjectId(@PathVariable UUID projectId) {
        return ResponseEntity.ok(wikiEntryService.countByProjectId(projectId));
    }

    /**
     * 按类型统计条目数量
     */
    @GetMapping("/project/{projectId}/count/{type}")
    public ResponseEntity<Long> countByType(
            @PathVariable UUID projectId,
            @PathVariable String type) {
        return ResponseEntity.ok(wikiEntryService.countByType(projectId, type));
    }

    /**
     * 获取项目中所有不同的类型
     */
    @GetMapping("/project/{projectId}/types")
    public ResponseEntity<List<String>> getDistinctTypes(@PathVariable UUID projectId) {
        return ResponseEntity.ok(wikiEntryService.getDistinctTypes(projectId));
    }

    /**
     * 获取项目中所有不同的时间版本
     */
    @GetMapping("/project/{projectId}/versions")
    public ResponseEntity<List<String>> getDistinctTimeVersions(@PathVariable UUID projectId) {
        return ResponseEntity.ok(wikiEntryService.getDistinctTimeVersions(projectId));
    }
}
