package com.inkflow.module.project.controller;

import com.inkflow.module.auth.security.UserPrincipal;
import com.inkflow.module.progress.service.CreationProgress;
import com.inkflow.module.progress.service.CreationProgressService;
import com.inkflow.module.progress.service.PhaseTransitionCheck;
import com.inkflow.module.project.dto.*;
import com.inkflow.module.project.entity.CreationPhase;
import com.inkflow.module.project.entity.ProjectStatus;
import com.inkflow.module.project.service.ExportService;
import com.inkflow.module.project.service.ImportService;
import com.inkflow.module.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 项目控制器
 * 
 * 提供项目管理的REST API：
 * - POST /api/projects - 创建项目
 * - GET /api/projects - 获取项目列表
 * - GET /api/projects/{id} - 获取项目详情
 * - PUT /api/projects/{id} - 更新项目
 * - DELETE /api/projects/{id} - 删除项目
 * - PATCH /api/projects/{id}/phase - 更新创作阶段
 * - GET /api/projects/recent - 获取最近项目
 * - GET /api/projects/search - 搜索项目
 */
@RestController
@RequestMapping("/api/projects")
@Tag(name = "Project", description = "项目管理接口")
public class ProjectController {
    
    private final ProjectService projectService;
    private final CreationProgressService progressService;
    private final ExportService exportService;
    private final ImportService importService;
    
    public ProjectController(
            ProjectService projectService, 
            CreationProgressService progressService,
            ExportService exportService,
            ImportService importService) {
        this.projectService = projectService;
        this.progressService = progressService;
        this.exportService = exportService;
        this.importService = importService;
    }
    
    /**
     * 创建项目
     */
    @PostMapping
    public ResponseEntity<ProjectDto> createProject(
        @AuthenticationPrincipal UserPrincipal user,
        @Valid @RequestBody CreateProjectRequest request
    ) {
        ProjectDto project = projectService.createProject(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(project);
    }
    
    /**
     * 获取项目列表（分页）
     */
    @GetMapping
    public ResponseEntity<Page<ProjectDto>> getProjects(
        @AuthenticationPrincipal UserPrincipal user,
        @RequestParam(required = false) ProjectStatus status,
        @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<ProjectDto> projects = projectService.getUserProjects(user.getId(), status, pageable);
        return ResponseEntity.ok(projects);
    }
    
    /**
     * 获取项目详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProjectDto> getProject(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable UUID id
    ) {
        ProjectDto project = projectService.getProject(id, user.getId());
        return ResponseEntity.ok(project);
    }
    
    /**
     * 更新项目
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProjectDto> updateProject(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable UUID id,
        @Valid @RequestBody UpdateProjectRequest request
    ) {
        ProjectDto project = projectService.updateProject(id, user.getId(), request);
        return ResponseEntity.ok(project);
    }
    
    /**
     * 删除项目
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable UUID id
    ) {
        projectService.deleteProject(id, user.getId());
        return ResponseEntity.noContent().build();
    }
    
    /**
     * 更新创作阶段
     */
    @PatchMapping("/{id}/phase")
    public ResponseEntity<ProjectDto> updateCreationPhase(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable UUID id,
        @RequestParam CreationPhase phase
    ) {
        ProjectDto project = projectService.updateCreationPhase(id, user.getId(), phase);
        return ResponseEntity.ok(project);
    }
    
    /**
     * 获取最近更新的项目
     */
    @GetMapping("/recent")
    public ResponseEntity<List<ProjectDto>> getRecentProjects(
        @AuthenticationPrincipal UserPrincipal user,
        @RequestParam(defaultValue = "5") int limit
    ) {
        List<ProjectDto> projects = projectService.getRecentProjects(user.getId(), Math.min(limit, 10));
        return ResponseEntity.ok(projects);
    }
    
    /**
     * 搜索项目
     */
    @GetMapping("/search")
    public ResponseEntity<Page<ProjectDto>> searchProjects(
        @AuthenticationPrincipal UserPrincipal user,
        @RequestParam String keyword,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<ProjectDto> projects = projectService.searchProjects(user.getId(), keyword, pageable);
        return ResponseEntity.ok(projects);
    }
    
    /**
     * 获取项目统计
     */
    @GetMapping("/stats")
    public ResponseEntity<ProjectStatsDto> getProjectStats(
        @AuthenticationPrincipal UserPrincipal user
    ) {
        long totalCount = projectService.countUserProjects(user.getId());
        return ResponseEntity.ok(new ProjectStatsDto(totalCount));
    }
    
    /**
     * 项目统计DTO
     */
    public record ProjectStatsDto(long totalCount) {}
    
    // ==================== 创作进度 API ====================
    
    /**
     * 获取项目创作进度
     * 已简化：移除了不实用的阶段完成度计算功能
     * Requirements: 12.1-12.4
     */
    @GetMapping("/{id}/progress")
    @Operation(summary = "获取创作进度", description = "获取项目的创作进度，包括实体数量等")
    public ResponseEntity<CreationProgress> getProgress(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable UUID id
    ) {
        // 验证用户有权访问该项目
        projectService.getProject(id, user.getId());
        
        CreationProgress progress = progressService.getProgress(id);
        return ResponseEntity.ok(progress);
    }
    
    /**
     * 检查阶段转换
     * 已简化：允许任意阶段转换，由用户自行决定
     * Requirements: 12.1-12.4
     */
    @GetMapping("/{id}/progress/check-transition")
    @Operation(summary = "检查阶段转换", description = "检查是否可以进入目标阶段")
    public ResponseEntity<PhaseTransitionCheck> checkPhaseTransition(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable UUID id,
        @RequestParam CreationPhase targetPhase
    ) {
        // 验证用户有权访问该项目
        projectService.getProject(id, user.getId());
        
        PhaseTransitionCheck check = progressService.checkPhaseTransition(id, targetPhase);
        return ResponseEntity.ok(check);
    }
    
    /**
     * 更新项目阶段
     * 已简化：移除了复杂的前置条件检查
     * Requirements: 12.1-12.4
     */
    @PatchMapping("/{id}/progress/phase")
    @Operation(summary = "更新创作阶段", description = "更新创作阶段")
    public ResponseEntity<CreationProgress> updatePhaseWithCheck(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable UUID id,
        @RequestParam CreationPhase targetPhase,
        @RequestParam(defaultValue = "false") boolean force
    ) {
        // 验证用户有权访问该项目
        projectService.getProject(id, user.getId());
        
        // 更新阶段
        progressService.updatePhase(id, targetPhase);
        
        // 返回更新后的进度
        CreationProgress progress = progressService.getProgress(id);
        return ResponseEntity.ok(progress);
    }
    
    // ==================== 导入导出 API ====================
    
    /**
     * 导出项目
     * 将项目导出为 JSON 文件下载
     */
    @GetMapping("/{id}/export")
    @Operation(summary = "导出项目", description = "导出项目为 JSON 文件")
    public ResponseEntity<Resource> exportProject(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable UUID id
    ) {
        String json = exportService.exportToJson(id, user.getId());
        
        ByteArrayResource resource = new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8));
        
        // 获取项目标题用于文件名
        ProjectDto project = projectService.getProject(id, user.getId());
        String filename = sanitizeFilename(project.title()) + "-export.json";
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_JSON)
            .contentLength(resource.contentLength())
            .body(resource);
    }
    
    /**
     * 导入项目
     * 从 JSON 文件导入项目
     */
    @PostMapping("/import")
    @Operation(summary = "导入项目", description = "从 JSON 文件导入项目")
    public ResponseEntity<ProjectDto> importProject(
        @AuthenticationPrincipal UserPrincipal user,
        @RequestBody String json
    ) {
        UUID projectId = importService.importFromJson(json, user.getId());
        ProjectDto project = projectService.getProject(projectId, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(project);
    }
    
    /**
     * 清理文件名中的非法字符
     */
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "project";
        }
        // 移除文件名中的非法字符
        return filename.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}
