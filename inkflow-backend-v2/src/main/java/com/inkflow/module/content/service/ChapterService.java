package com.inkflow.module.content.service;

import com.inkflow.common.exception.ResourceNotFoundException;
import com.inkflow.module.content.dto.*;
import com.inkflow.module.content.entity.BlockType;
import com.inkflow.module.content.entity.Chapter;
import com.inkflow.module.content.entity.ChapterStatus;
import com.inkflow.module.content.entity.StoryBlock;
import com.inkflow.module.content.repository.ChapterRepository;
import com.inkflow.module.content.repository.StoryBlockRepository;
import com.inkflow.module.content.repository.VolumeRepository;
import com.inkflow.module.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 章节服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterService {

    private final ChapterRepository chapterRepository;
    private final VolumeRepository volumeRepository;
    private final ProjectRepository projectRepository;
    private final StoryBlockRepository storyBlockRepository;
    private final LexorankService lexorankService;

    /**
     * 获取项目的所有章节
     */
    @Transactional(readOnly = true)
    public List<ChapterDto> list(UUID projectId, UUID userId) {
        verifyProjectAccess(projectId, userId);
        return chapterRepository.findByProjectIdOrderByVolumeAndOrder(projectId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * 获取分卷的所有章节
     */
    @Transactional(readOnly = true)
    public List<ChapterDto> listByVolume(UUID projectId, UUID volumeId, UUID userId) {
        verifyProjectAccess(projectId, userId);
        return chapterRepository.findByVolumeIdAndDeletedFalseOrderByOrderIndexAsc(volumeId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * 获取单个章节
     */
    @Transactional(readOnly = true)
    public ChapterDto getById(UUID projectId, UUID chapterId, UUID userId) {
        verifyProjectAccess(projectId, userId);
        Chapter chapter = chapterRepository.findByIdAndProjectIdAndDeletedFalse(chapterId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("章节不存在"));
        return toDto(chapter);
    }

    /**
     * 创建章节
     */
    @Transactional
    public ChapterDto create(UUID projectId, UUID userId, CreateChapterRequest request) {
        verifyProjectAccess(projectId, userId);
        
        // 验证分卷存在
        volumeRepository.findByIdAndProjectIdAndDeletedFalse(request.volumeId(), projectId)
                .orElseThrow(() -> new ResourceNotFoundException("分卷不存在"));
        
        int maxOrder = chapterRepository.findMaxOrderIndex(request.volumeId());
        
        Chapter chapter = new Chapter();
        chapter.setProjectId(projectId);
        chapter.setVolumeId(request.volumeId());
        chapter.setTitle(request.title());
        chapter.setSummary(request.summary());
        chapter.setOrderIndex(maxOrder + 1);
        chapter.setStatus(ChapterStatus.DRAFT);
        chapter.setWordCount(0);
        
        chapter = chapterRepository.save(chapter);
        log.info("创建章节: projectId={}, chapterId={}, title={}", projectId, chapter.getId(), request.title());
        
        return toDto(chapter);
    }

    /**
     * 更新章节
     */
    @Transactional
    public ChapterDto update(UUID projectId, UUID chapterId, UUID userId, UpdateChapterRequest request) {
        verifyProjectAccess(projectId, userId);
        
        Chapter chapter = chapterRepository.findByIdAndProjectIdAndDeletedFalse(chapterId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("章节不存在"));
        
        if (request.volumeId() != null) {
            // 验证新分卷存在
            volumeRepository.findByIdAndProjectIdAndDeletedFalse(request.volumeId(), projectId)
                    .orElseThrow(() -> new ResourceNotFoundException("分卷不存在"));
            chapter.setVolumeId(request.volumeId());
        }
        if (request.title() != null) {
            chapter.setTitle(request.title());
        }
        if (request.summary() != null) {
            chapter.setSummary(request.summary());
        }
        if (request.status() != null) {
            chapter.setStatus(request.status());
        }
        if (request.metadata() != null) {
            chapter.setMetadata(request.metadata());
        }
        
        chapter = chapterRepository.save(chapter);
        log.info("更新章节: chapterId={}", chapterId);
        
        return toDto(chapter);
    }

    /**
     * 删除章节（软删除）
     */
    @Transactional
    public void delete(UUID projectId, UUID chapterId, UUID userId) {
        verifyProjectAccess(projectId, userId);
        
        Chapter chapter = chapterRepository.findByIdAndProjectIdAndDeletedFalse(chapterId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("章节不存在"));
        
        chapter.setDeleted(true);
        chapterRepository.save(chapter);
        log.info("删除章节: chapterId={}", chapterId);
    }

    /**
     * 重排序章节
     */
    @Transactional
    public void reorder(UUID projectId, UUID userId, List<ReorderRequest> requests) {
        verifyProjectAccess(projectId, userId);
        
        for (ReorderRequest request : requests) {
            Chapter chapter = chapterRepository.findByIdAndProjectIdAndDeletedFalse(request.id(), projectId)
                    .orElseThrow(() -> new ResourceNotFoundException("章节不存在: " + request.id()));
            chapter.setOrderIndex(request.newOrder());
            chapterRepository.save(chapter);
        }
        log.info("重排序章节: projectId={}, count={}", projectId, requests.size());
    }

    /**
     * 移动章节到另一个分卷
     */
    @Transactional
    public ChapterDto moveToVolume(UUID projectId, UUID chapterId, UUID targetVolumeId, UUID userId) {
        verifyProjectAccess(projectId, userId);
        
        Chapter chapter = chapterRepository.findByIdAndProjectIdAndDeletedFalse(chapterId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("章节不存在"));
        
        volumeRepository.findByIdAndProjectIdAndDeletedFalse(targetVolumeId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("目标分卷不存在"));
        
        int maxOrder = chapterRepository.findMaxOrderIndex(targetVolumeId);
        chapter.setVolumeId(targetVolumeId);
        chapter.setOrderIndex(maxOrder + 1);
        
        chapter = chapterRepository.save(chapter);
        log.info("移动章节: chapterId={}, targetVolumeId={}", chapterId, targetVolumeId);
        
        return toDto(chapter);
    }

    private void verifyProjectAccess(UUID projectId, UUID userId) {
        boolean exists = projectRepository.existsByIdAndUserIdAndDeletedFalse(projectId, userId);
        if (!exists) {
            throw new ResourceNotFoundException("项目不存在或无权访问");
        }
    }

    /**
     * 获取章节内容
     * 聚合所有 StoryBlock 的内容返回
     */
    @Transactional(readOnly = true)
    public ChapterContentDto getContent(UUID projectId, UUID chapterId, UUID userId) {
        verifyProjectAccess(projectId, userId);
        
        Chapter chapter = chapterRepository.findByIdAndProjectIdAndDeletedFalse(chapterId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("章节不存在"));
        
        // 聚合所有 StoryBlock 的内容
        List<StoryBlock> blocks = storyBlockRepository.findByChapterIdAndDeletedFalseOrderByRankAsc(chapterId);
        StringBuilder contentBuilder = new StringBuilder();
        
        for (StoryBlock block : blocks) {
            if (block.getBlockType() != BlockType.COMMENT && block.getContent() != null) {
                contentBuilder.append(block.getContent()).append("\n\n");
            }
        }
        
        String content = contentBuilder.toString().trim();
        
        return new ChapterContentDto(
                chapter.getId(),
                chapter.getId(),
                content,
                chapter.getWordCount(),
                chapter.getUpdatedAt()
        );
    }
    
    /**
     * 保存章节内容
     * 将内容保存为单个 StoryBlock（简化模式）
     */
    @Transactional
    public ChapterContentDto saveContent(UUID projectId, UUID chapterId, UUID userId, SaveContentRequest request) {
        verifyProjectAccess(projectId, userId);
        
        Chapter chapter = chapterRepository.findByIdAndProjectIdAndDeletedFalse(chapterId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("章节不存在"));
        
        // 获取现有的 StoryBlock 列表
        List<StoryBlock> existingBlocks = storyBlockRepository.findByChapterIdAndDeletedFalseOrderByRankAsc(chapterId);
        
        String content = request.content();
        int wordCount = countWords(content);
        
        if (existingBlocks.isEmpty()) {
            // 创建新的 StoryBlock
            StoryBlock block = new StoryBlock();
            block.setChapterId(chapterId);
            block.setContent(content);
            block.setBlockType(BlockType.NARRATIVE);
            block.setRank(lexorankService.generateRankAfter(null));
            storyBlockRepository.save(block);
        } else {
            // 更新第一个 StoryBlock，删除其他的
            StoryBlock firstBlock = existingBlocks.get(0);
            firstBlock.setContent(content);
            storyBlockRepository.save(firstBlock);
            
            // 软删除其他 blocks
            for (int i = 1; i < existingBlocks.size(); i++) {
                StoryBlock block = existingBlocks.get(i);
                block.setDeleted(true);
                storyBlockRepository.save(block);
            }
        }
        
        // 更新章节字数
        chapter.setWordCount(wordCount);
        chapter = chapterRepository.save(chapter);
        
        log.info("保存章节内容: chapterId={}, wordCount={}", chapterId, wordCount);
        
        return new ChapterContentDto(
                chapter.getId(),
                chapter.getId(),
                content,
                wordCount,
                chapter.getUpdatedAt()
        );
    }
    
    /**
     * 计算字数（中文按字符计算，英文按单词计算）
     */
    private int countWords(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        
        int count = 0;
        // 计算中文字符
        for (char c : content.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                count++;
            }
        }
        
        // 计算英文单词
        String englishOnly = content.replaceAll("[\\u4e00-\\u9fa5]", " ");
        String[] words = englishOnly.trim().split("\\s+");
        for (String word : words) {
            if (!word.isEmpty() && word.matches(".*[a-zA-Z].*")) {
                count++;
            }
        }
        
        return count;
    }

    private ChapterDto toDto(Chapter chapter) {
        return new ChapterDto(
                chapter.getId(),
                chapter.getProjectId(),
                chapter.getVolumeId(),
                chapter.getTitle(),
                chapter.getSummary(),
                chapter.getOrderIndex(),
                chapter.getStatus(),
                chapter.getWordCount(),
                chapter.getMetadata(),
                chapter.getCreatedAt(),
                chapter.getUpdatedAt()
        );
    }
}
