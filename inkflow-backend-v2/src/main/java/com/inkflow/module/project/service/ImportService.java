package com.inkflow.module.project.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.inkflow.common.exception.BusinessException;
import com.inkflow.module.content.entity.*;
import com.inkflow.module.content.repository.ChapterRepository;
import com.inkflow.module.content.repository.StoryBlockRepository;
import com.inkflow.module.content.repository.VolumeRepository;
import com.inkflow.module.project.dto.export.*;
import com.inkflow.module.project.entity.CreationPhase;
import com.inkflow.module.project.entity.Project;
import com.inkflow.module.project.entity.ProjectStatus;
import com.inkflow.module.project.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 项目导入服务
 * 
 * 从JSON格式导入项目数据，支持：
 * - 完整项目数据导入
 * - UUID重新生成（避免冲突）
 * - 版本兼容性检查
 */
@Service
public class ImportService {
    
    private static final Logger log = LoggerFactory.getLogger(ImportService.class);
    
    /**
     * 支持的最低导出版本
     */
    private static final String MIN_SUPPORTED_VERSION = "2.0";
    
    private final ProjectRepository projectRepository;
    private final VolumeRepository volumeRepository;
    private final ChapterRepository chapterRepository;
    private final StoryBlockRepository storyBlockRepository;
    private final ObjectMapper objectMapper;
    
    public ImportService(
        ProjectRepository projectRepository,
        VolumeRepository volumeRepository,
        ChapterRepository chapterRepository,
        StoryBlockRepository storyBlockRepository
    ) {
        this.projectRepository = projectRepository;
        this.volumeRepository = volumeRepository;
        this.chapterRepository = chapterRepository;
        this.storyBlockRepository = storyBlockRepository;
        
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    /**
     * 从JSON字符串导入项目
     * 
     * @param json JSON字符串
     * @param userId 导入用户ID
     * @return 导入的项目ID
     */
    @Transactional
    public UUID importFromJson(String json, UUID userId) {
        try {
            ExportData exportData = objectMapper.readValue(json, ExportData.class);
            return importFromData(exportData, userId);
        } catch (JsonProcessingException e) {
            log.error("解析导入数据失败", e);
            throw new BusinessException("导入数据格式错误: " + e.getMessage());
        }
    }
    
    /**
     * 从ExportData对象导入项目
     * 
     * @param exportData 导出数据
     * @param userId 导入用户ID
     * @return 导入的项目ID
     */
    @Transactional
    public UUID importFromData(ExportData exportData, UUID userId) {
        // 验证版本兼容性
        validateVersion(exportData.metadata());
        
        ExportProjectDto projectDto = exportData.project();
        
        // 创建项目（生成新UUID）
        Project project = new Project();
        project.setUserId(userId);
        project.setTitle(projectDto.title() + " (导入)");
        project.setDescription(projectDto.description());
        project.setCoverUrl(projectDto.coverUrl());
        project.setStatus(parseStatus(projectDto.status()));
        project.setCreationPhase(parsePhase(projectDto.creationPhase()));
        
        if (projectDto.metadata() != null) {
            project.setMetadata(projectDto.metadata());
        }
        if (projectDto.worldSettings() != null) {
            project.setWorldSettings(projectDto.worldSettings());
        }
        
        project = projectRepository.save(project);
        
        // 导入分卷
        if (projectDto.volumes() != null) {
            for (ExportVolumeDto volumeDto : projectDto.volumes()) {
                importVolume(volumeDto, project.getId());
            }
        }
        
        log.info("导入项目成功: projectId={}, userId={}, title={}", 
            project.getId(), userId, project.getTitle());
        
        return project.getId();
    }
    
    /**
     * 导入分卷
     */
    private void importVolume(ExportVolumeDto volumeDto, UUID projectId) {
        Volume volume = new Volume();
        volume.setProjectId(projectId);
        volume.setTitle(volumeDto.title());
        volume.setDescription(volumeDto.description());
        volume.setOrderIndex(volumeDto.orderIndex());
        
        volume = volumeRepository.save(volume);
        
        // 导入章节
        if (volumeDto.chapters() != null) {
            for (ExportChapterDto chapterDto : volumeDto.chapters()) {
                importChapter(chapterDto, projectId, volume.getId());
            }
        }
    }
    
    /**
     * 导入章节
     */
    private void importChapter(ExportChapterDto chapterDto, UUID projectId, UUID volumeId) {
        Chapter chapter = new Chapter();
        chapter.setProjectId(projectId);
        chapter.setVolumeId(volumeId);
        chapter.setTitle(chapterDto.title());
        chapter.setSummary(chapterDto.summary());
        chapter.setOrderIndex(chapterDto.orderIndex());
        chapter.setStatus(parseChapterStatus(chapterDto.status()));
        
        if (chapterDto.metadata() != null) {
            chapter.setMetadata(chapterDto.metadata());
        }
        
        chapter = chapterRepository.save(chapter);
        
        // 导入剧情块
        if (chapterDto.blocks() != null) {
            int wordCount = 0;
            for (ExportStoryBlockDto blockDto : chapterDto.blocks()) {
                StoryBlock block = importStoryBlock(blockDto, chapter.getId());
                wordCount += block.getWordCount();
            }
            // 更新章节字数
            chapter.setWordCount(wordCount);
            chapterRepository.save(chapter);
        }
    }
    
    /**
     * 导入剧情块
     */
    private StoryBlock importStoryBlock(ExportStoryBlockDto blockDto, UUID chapterId) {
        StoryBlock block = new StoryBlock();
        block.setChapterId(chapterId);
        block.setBlockType(parseBlockType(blockDto.blockType()));
        block.setContent(blockDto.content());
        block.setRank(blockDto.rank());
        
        if (blockDto.metadata() != null) {
            block.setMetadata(blockDto.metadata());
        }
        
        return storyBlockRepository.save(block);
    }
    
    /**
     * 验证版本兼容性
     */
    private void validateVersion(ExportMetadata metadata) {
        if (metadata == null || metadata.version() == null) {
            throw new BusinessException("导入数据缺少版本信息");
        }
        
        String version = metadata.version();
        if (version.compareTo(MIN_SUPPORTED_VERSION) < 0) {
            throw new BusinessException("不支持的导出版本: " + version + "，最低支持版本: " + MIN_SUPPORTED_VERSION);
        }
    }
    
    /**
     * 解析项目状态
     */
    private ProjectStatus parseStatus(String status) {
        try {
            return status != null ? ProjectStatus.valueOf(status) : ProjectStatus.DRAFT;
        } catch (IllegalArgumentException e) {
            return ProjectStatus.DRAFT;
        }
    }
    
    /**
     * 解析创作阶段
     */
    private CreationPhase parsePhase(String phase) {
        try {
            return phase != null ? CreationPhase.valueOf(phase) : CreationPhase.IDEA;
        } catch (IllegalArgumentException e) {
            return CreationPhase.IDEA;
        }
    }
    
    /**
     * 解析章节状态
     */
    private ChapterStatus parseChapterStatus(String status) {
        try {
            return status != null ? ChapterStatus.valueOf(status) : ChapterStatus.DRAFT;
        } catch (IllegalArgumentException e) {
            return ChapterStatus.DRAFT;
        }
    }
    
    /**
     * 解析剧情块类型
     */
    private BlockType parseBlockType(String type) {
        try {
            return type != null ? BlockType.valueOf(type) : BlockType.NARRATIVE;
        } catch (IllegalArgumentException e) {
            return BlockType.NARRATIVE;
        }
    }
}
