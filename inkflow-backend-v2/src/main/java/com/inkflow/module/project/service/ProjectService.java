package com.inkflow.module.project.service;

import com.inkflow.common.exception.BusinessException;
import com.inkflow.common.exception.ResourceNotFoundException;
import com.inkflow.module.content.repository.ChapterRepository;
import com.inkflow.module.project.dto.*;
import com.inkflow.module.project.entity.CreationPhase;
import com.inkflow.module.project.entity.Project;
import com.inkflow.module.project.entity.ProjectStatus;
import com.inkflow.module.project.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 项目服务
 * 
 * 提供项目的CRUD操作和业务逻辑
 */
@Service
public class ProjectService {
    
    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);
    
    private final ProjectRepository projectRepository;
    private final ChapterRepository chapterRepository;
    
    public ProjectService(ProjectRepository projectRepository, ChapterRepository chapterRepository) {
        this.projectRepository = projectRepository;
        this.chapterRepository = chapterRepository;
    }
    
    /**
     * 创建项目
     * 
     * @param userId 用户ID
     * @param request 创建请求
     * @return 项目信息
     */
    @Transactional
    public ProjectDto createProject(UUID userId, CreateProjectRequest request) {
        Project project = new Project();
        project.setUserId(userId);
        project.setTitle(request.title());
        project.setDescription(request.description());
        project.setCoverUrl(request.coverUrl());
        project.setStatus(ProjectStatus.DRAFT);
        project.setCreationPhase(CreationPhase.IDEA);
        
        if (request.metadata() != null) {
            project.setMetadata(request.metadata());
        }
        
        project = projectRepository.save(project);
        
        log.info("创建项目成功: projectId={}, userId={}, title={}", 
            project.getId(), userId, project.getTitle());
        
        return ProjectDto.fromEntity(project);
    }
    
    /**
     * 获取项目详情
     * 
     * @param projectId 项目ID
     * @param userId 用户ID（用于权限验证）
     * @return 项目信息
     */
    @Transactional(readOnly = true)
    public ProjectDto getProject(UUID projectId, UUID userId) {
        Project project = projectRepository.findByIdAndUserIdAndDeletedFalse(projectId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("项目不存在或无权访问"));
        Long wordCount = chapterRepository.sumWordCountByProjectId(projectId);
        return ProjectDto.fromEntity(project, wordCount);
    }
    
    /**
     * 获取项目详情（不验证用户）
     */
    @Transactional(readOnly = true)
    public ProjectDto getProjectById(UUID projectId) {
        Project project = projectRepository.findByIdAndDeletedFalse(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("项目不存在"));
        Long wordCount = chapterRepository.sumWordCountByProjectId(projectId);
        return ProjectDto.fromEntity(project, wordCount);
    }
    
    /**
     * 更新项目
     * 
     * @param projectId 项目ID
     * @param userId 用户ID
     * @param request 更新请求
     * @return 更新后的项目信息
     */
    @Transactional
    public ProjectDto updateProject(UUID projectId, UUID userId, UpdateProjectRequest request) {
        Project project = projectRepository.findByIdAndUserIdAndDeletedFalse(projectId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("项目不存在或无权访问"));
        
        // 更新字段（只更新非空值）
        if (request.title() != null) {
            project.setTitle(request.title());
        }
        if (request.description() != null) {
            project.setDescription(request.description());
        }
        if (request.coverUrl() != null) {
            project.setCoverUrl(request.coverUrl());
        }
        if (request.status() != null) {
            project.setStatus(request.status());
        }
        if (request.creationPhase() != null) {
            project.setCreationPhase(request.creationPhase());
        }
        if (request.metadata() != null) {
            project.getMetadata().putAll(request.metadata());
        }
        if (request.worldSettings() != null) {
            project.getWorldSettings().putAll(request.worldSettings());
        }
        
        project = projectRepository.save(project);
        
        log.debug("更新项目成功: projectId={}", projectId);
        
        return ProjectDto.fromEntity(project);
    }
    
    /**
     * 删除项目（软删除）
     * 
     * @param projectId 项目ID
     * @param userId 用户ID
     */
    @Transactional
    public void deleteProject(UUID projectId, UUID userId) {
        Project project = projectRepository.findByIdAndUserIdAndDeletedFalse(projectId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("项目不存在或无权访问"));
        
        project.setDeleted(true);
        projectRepository.save(project);
        
        log.info("删除项目成功: projectId={}, userId={}", projectId, userId);
    }
    
    /**
     * 获取用户的项目列表（分页）
     * 
     * @param userId 用户ID
     * @param status 项目状态（可选）
     * @param pageable 分页参数
     * @return 项目列表
     */
    @Transactional(readOnly = true)
    public Page<ProjectDto> getUserProjects(UUID userId, ProjectStatus status, Pageable pageable) {
        Page<Project> projects;
        
        if (status != null) {
            projects = projectRepository.findByUserIdAndStatusAndDeletedFalse(userId, status, pageable);
        } else {
            projects = projectRepository.findByUserIdAndDeletedFalse(userId, pageable);
        }
        
        return projects.map(project -> {
            Long wordCount = chapterRepository.sumWordCountByProjectId(project.getId());
            return ProjectDto.fromEntity(project, wordCount);
        });
    }
    
    /**
     * 搜索项目
     * 
     * @param userId 用户ID
     * @param keyword 搜索关键词
     * @param pageable 分页参数
     * @return 项目列表
     */
    @Transactional(readOnly = true)
    public Page<ProjectDto> searchProjects(UUID userId, String keyword, Pageable pageable) {
        return projectRepository.searchByTitle(userId, keyword, pageable)
            .map(project -> {
                Long wordCount = chapterRepository.sumWordCountByProjectId(project.getId());
                return ProjectDto.fromEntity(project, wordCount);
            });
    }
    
    /**
     * 获取用户最近更新的项目
     * 
     * @param userId 用户ID
     * @param limit 数量限制
     * @return 项目列表
     */
    @Transactional(readOnly = true)
    public List<ProjectDto> getRecentProjects(UUID userId, int limit) {
        return projectRepository.findRecentProjects(userId, PageRequest.of(0, limit))
            .stream()
            .map(project -> {
                Long wordCount = chapterRepository.sumWordCountByProjectId(project.getId());
                return ProjectDto.fromEntity(project, wordCount);
            })
            .toList();
    }
    
    /**
     * 更新创作阶段
     * 
     * @param projectId 项目ID
     * @param userId 用户ID
     * @param phase 新阶段
     * @return 更新后的项目信息
     */
    @Transactional
    public ProjectDto updateCreationPhase(UUID projectId, UUID userId, CreationPhase phase) {
        Project project = projectRepository.findByIdAndUserIdAndDeletedFalse(projectId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("项目不存在或无权访问"));
        
        // 验证阶段转换的合理性
        CreationPhase currentPhase = project.getCreationPhase();
        if (phase.ordinal() < currentPhase.ordinal() - 1) {
            // 允许回退一个阶段，但不能跳跃回退
            throw new BusinessException("不能跳跃回退创作阶段");
        }
        
        project.setCreationPhase(phase);
        project = projectRepository.save(project);
        
        log.info("更新创作阶段: projectId={}, phase={}", projectId, phase);
        
        return ProjectDto.fromEntity(project);
    }
    
    /**
     * 统计用户项目数量
     * 
     * @param userId 用户ID
     * @return 项目数量
     */
    @Transactional(readOnly = true)
    public long countUserProjects(UUID userId) {
        return projectRepository.countByUserIdAndDeletedFalse(userId);
    }
    
    /**
     * 验证用户对项目的访问权限
     * 
     * @param projectId 项目ID
     * @param userId 用户ID
     * @return 是否有权限
     */
    @Transactional(readOnly = true)
    public boolean hasAccess(UUID projectId, UUID userId) {
        return projectRepository.findByIdAndUserIdAndDeletedFalse(projectId, userId).isPresent();
    }
}
