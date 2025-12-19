package com.inkflow.module.project.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.inkflow.common.exception.BusinessException;
import com.inkflow.common.exception.ResourceNotFoundException;
import com.inkflow.module.content.entity.Chapter;
import com.inkflow.module.content.entity.StoryBlock;
import com.inkflow.module.content.entity.Volume;
import com.inkflow.module.content.repository.ChapterRepository;
import com.inkflow.module.content.repository.StoryBlockRepository;
import com.inkflow.module.content.repository.VolumeRepository;
import com.inkflow.module.project.dto.export.*;
import com.inkflow.module.project.entity.Project;
import com.inkflow.module.project.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 项目导出服务
 * 
 * 将项目数据导出为JSON格式，支持：
 * - 完整项目数据导出
 * - Pretty-print格式化
 * - 版本信息记录
 */
@Service
public class ExportService {
    
    private static final Logger log = LoggerFactory.getLogger(ExportService.class);
    
    /**
     * 导出格式版本
     */
    private static final String EXPORT_VERSION = "2.0";
    
    private final ProjectRepository projectRepository;
    private final VolumeRepository volumeRepository;
    private final ChapterRepository chapterRepository;
    private final StoryBlockRepository storyBlockRepository;
    private final ObjectMapper objectMapper;
    
    public ExportService(
        ProjectRepository projectRepository,
        VolumeRepository volumeRepository,
        ChapterRepository chapterRepository,
        StoryBlockRepository storyBlockRepository
    ) {
        this.projectRepository = projectRepository;
        this.volumeRepository = volumeRepository;
        this.chapterRepository = chapterRepository;
        this.storyBlockRepository = storyBlockRepository;
        
        // 配置ObjectMapper
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    
    /**
     * 导出项目为JSON字符串
     * 
     * @param projectId 项目ID
     * @param userId 用户ID（权限验证）
     * @return JSON字符串
     */
    @Transactional(readOnly = true)
    public String exportToJson(UUID projectId, UUID userId) {
        // 验证权限并获取项目
        Project project = projectRepository.findByIdAndUserIdAndDeletedFalse(projectId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("项目不存在或无权访问"));
        
        // 构建导出数据
        ExportData exportData = buildExportData(project);
        
        try {
            String json = objectMapper.writeValueAsString(exportData);
            log.info("导出项目成功: projectId={}, size={}bytes", projectId, json.length());
            return json;
        } catch (JsonProcessingException e) {
            log.error("导出项目失败: projectId={}", projectId, e);
            throw new BusinessException("导出失败: " + e.getMessage());
        }
    }
    
    /**
     * 导出项目为ExportData对象
     * 
     * @param projectId 项目ID
     * @param userId 用户ID
     * @return 导出数据对象
     */
    @Transactional(readOnly = true)
    public ExportData exportToData(UUID projectId, UUID userId) {
        Project project = projectRepository.findByIdAndUserIdAndDeletedFalse(projectId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("项目不存在或无权访问"));
        
        return buildExportData(project);
    }
    
    /**
     * 构建导出数据
     */
    private ExportData buildExportData(Project project) {
        // 导出分卷
        List<Volume> volumes = volumeRepository.findByProjectIdAndDeletedFalseOrderByOrderIndexAsc(project.getId());
        List<ExportVolumeDto> exportVolumes = volumes.stream()
            .map(this::exportVolume)
            .toList();
        
        // 构建项目导出数据
        ExportProjectDto exportProject = new ExportProjectDto(
            project.getTitle(),
            project.getDescription(),
            project.getCoverUrl(),
            project.getStatus().name(),
            project.getCreationPhase().name(),
            project.getMetadata(),
            project.getWorldSettings(),
            exportVolumes
        );
        
        // 构建元数据
        ExportMetadata metadata = new ExportMetadata(
            EXPORT_VERSION,
            LocalDateTime.now(),
            "InkFlow 2.0"
        );
        
        return new ExportData(metadata, exportProject);
    }
    
    /**
     * 导出分卷
     */
    private ExportVolumeDto exportVolume(Volume volume) {
        List<Chapter> chapters = chapterRepository.findByVolumeIdAndDeletedFalseOrderByOrderIndexAsc(volume.getId());
        List<ExportChapterDto> exportChapters = chapters.stream()
            .map(this::exportChapter)
            .toList();
        
        return new ExportVolumeDto(
            volume.getTitle(),
            volume.getDescription(),
            volume.getOrderIndex(),
            exportChapters
        );
    }
    
    /**
     * 导出章节
     */
    private ExportChapterDto exportChapter(Chapter chapter) {
        List<StoryBlock> blocks = storyBlockRepository.findByChapterIdAndDeletedFalseOrderByRankAsc(chapter.getId());
        List<ExportStoryBlockDto> exportBlocks = blocks.stream()
            .map(this::exportStoryBlock)
            .toList();
        
        return new ExportChapterDto(
            chapter.getTitle(),
            chapter.getSummary(),
            chapter.getOrderIndex(),
            chapter.getStatus().name(),
            chapter.getMetadata(),
            exportBlocks
        );
    }
    
    /**
     * 导出剧情块
     */
    private ExportStoryBlockDto exportStoryBlock(StoryBlock block) {
        return new ExportStoryBlockDto(
            block.getBlockType().name(),
            block.getContent(),
            block.getRank(),
            block.getMetadata()
        );
    }
}
