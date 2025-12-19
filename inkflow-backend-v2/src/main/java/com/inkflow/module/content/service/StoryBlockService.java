package com.inkflow.module.content.service;

import com.inkflow.common.exception.ResourceNotFoundException;
import com.inkflow.module.content.entity.BlockType;
import com.inkflow.module.content.entity.Chapter;
import com.inkflow.module.content.entity.StoryBlock;
import com.inkflow.module.content.repository.ChapterRepository;
import com.inkflow.module.content.repository.StoryBlockRepository;
import com.inkflow.module.rag.entity.KnowledgeChunk;
import com.inkflow.module.rag.repository.KnowledgeChunkRepository;
import com.inkflow.module.rag.service.ParentChildSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 剧情块服务
 * 
 * 提供剧情块的CRUD操作和排序功能
 */
@Service
public class StoryBlockService {
    
    private static final Logger log = LoggerFactory.getLogger(StoryBlockService.class);
    
    private final StoryBlockRepository storyBlockRepository;
    private final ChapterRepository chapterRepository;
    private final LexorankService lexorankService;
    @Nullable
    private final ParentChildSearchService parentChildSearchService;
    private final KnowledgeChunkRepository knowledgeChunkRepository;
    
    public StoryBlockService(
        StoryBlockRepository storyBlockRepository,
        ChapterRepository chapterRepository,
        LexorankService lexorankService,
        @Nullable ParentChildSearchService parentChildSearchService,
        KnowledgeChunkRepository knowledgeChunkRepository
    ) {
        this.storyBlockRepository = storyBlockRepository;
        this.chapterRepository = chapterRepository;
        this.lexorankService = lexorankService;
        this.parentChildSearchService = parentChildSearchService;
        this.knowledgeChunkRepository = knowledgeChunkRepository;
    }
    
    /**
     * 获取章节的所有剧情块
     * 
     * @param chapterId 章节ID
     * @return 剧情块列表（按rank排序）
     */
    @Transactional(readOnly = true)
    public List<StoryBlock> getBlocksByChapter(UUID chapterId) {
        return storyBlockRepository.findByChapterIdAndDeletedFalseOrderByRankAsc(chapterId);
    }
    
    /**
     * 获取剧情块详情
     * 
     * @param blockId 剧情块ID
     * @param chapterId 章节ID
     * @return 剧情块
     */
    @Transactional(readOnly = true)
    public StoryBlock getBlock(UUID blockId, UUID chapterId) {
        return storyBlockRepository.findByIdAndChapterIdAndDeletedFalse(blockId, chapterId)
            .orElseThrow(() -> new ResourceNotFoundException("剧情块不存在"));
    }
    
    /**
     * 在章节末尾创建剧情块
     * 
     * @param chapterId 章节ID
     * @param content 内容
     * @param blockType 类型
     * @param metadata 元数据
     * @return 创建的剧情块
     */
    @Transactional
    public StoryBlock createBlock(UUID chapterId, String content, BlockType blockType, Map<String, Object> metadata) {
        // 获取最后一个剧情块的rank
        String lastRank = storyBlockRepository.findLastByChapterId(chapterId)
            .map(StoryBlock::getRank)
            .orElse(null);
        
        String newRank = lexorankService.generateRankAfter(lastRank);
        
        return createBlockWithRank(chapterId, content, blockType, newRank, metadata);
    }
    
    /**
     * 在指定位置之前插入剧情块
     * 
     * @param chapterId 章节ID
     * @param beforeBlockId 参考剧情块ID
     * @param content 内容
     * @param blockType 类型
     * @param metadata 元数据
     * @return 创建的剧情块
     */
    @Transactional
    public StoryBlock insertBlockBefore(UUID chapterId, UUID beforeBlockId, String content, BlockType blockType, Map<String, Object> metadata) {
        StoryBlock beforeBlock = getBlock(beforeBlockId, chapterId);
        
        // 获取前一个剧情块的rank
        String prevRank = storyBlockRepository.findPreviousByRank(chapterId, beforeBlock.getRank())
            .map(StoryBlock::getRank)
            .orElse(null);
        
        String newRank = lexorankService.generateRankBetween(prevRank, beforeBlock.getRank());
        
        return createBlockWithRank(chapterId, content, blockType, newRank, metadata);
    }
    
    /**
     * 在指定位置之后插入剧情块
     * 
     * @param chapterId 章节ID
     * @param afterBlockId 参考剧情块ID
     * @param content 内容
     * @param blockType 类型
     * @param metadata 元数据
     * @return 创建的剧情块
     */
    @Transactional
    public StoryBlock insertBlockAfter(UUID chapterId, UUID afterBlockId, String content, BlockType blockType, Map<String, Object> metadata) {
        StoryBlock afterBlock = getBlock(afterBlockId, chapterId);
        
        // 获取后一个剧情块的rank
        String nextRank = storyBlockRepository.findNextByRank(chapterId, afterBlock.getRank())
            .map(StoryBlock::getRank)
            .orElse(null);
        
        String newRank = lexorankService.generateRankBetween(afterBlock.getRank(), nextRank);
        
        return createBlockWithRank(chapterId, content, blockType, newRank, metadata);
    }
    
    /**
     * 更新剧情块内容
     * 
     * @param blockId 剧情块ID
     * @param chapterId 章节ID
     * @param content 新内容
     * @return 更新后的剧情块
     */
    @Transactional
    public StoryBlock updateContent(UUID blockId, UUID chapterId, String content) {
        StoryBlock block = getBlock(blockId, chapterId);
        block.setContent(content);
        block = storyBlockRepository.save(block);
        
        // 更新章节字数
        updateChapterWordCount(chapterId);
        
        // 触发父子块索引更新（会先删除旧索引再创建新索引）
        triggerIndexing(block);
        
        return block;
    }
    
    /**
     * 更新剧情块类型
     * 
     * @param blockId 剧情块ID
     * @param chapterId 章节ID
     * @param blockType 新类型
     * @return 更新后的剧情块
     */
    @Transactional
    public StoryBlock updateBlockType(UUID blockId, UUID chapterId, BlockType blockType) {
        StoryBlock block = getBlock(blockId, chapterId);
        block.setBlockType(blockType);
        return storyBlockRepository.save(block);
    }
    
    /**
     * 移动剧情块到新位置
     * 
     * @param blockId 剧情块ID
     * @param chapterId 章节ID
     * @param afterBlockId 移动到此剧情块之后（null表示移动到开头）
     * @return 移动后的剧情块
     */
    @Transactional
    public StoryBlock moveBlock(UUID blockId, UUID chapterId, UUID afterBlockId) {
        StoryBlock block = getBlock(blockId, chapterId);
        
        String newRank;
        if (afterBlockId == null) {
            // 移动到开头
            String firstRank = storyBlockRepository.findFirstByChapterId(chapterId)
                .map(StoryBlock::getRank)
                .orElse(null);
            newRank = lexorankService.generateRankBefore(firstRank);
        } else {
            // 移动到指定位置之后
            StoryBlock afterBlock = getBlock(afterBlockId, chapterId);
            String nextRank = storyBlockRepository.findNextByRank(chapterId, afterBlock.getRank())
                .map(StoryBlock::getRank)
                .orElse(null);
            newRank = lexorankService.generateRankBetween(afterBlock.getRank(), nextRank);
        }
        
        block.setRank(newRank);
        return storyBlockRepository.save(block);
    }
    
    /**
     * 删除剧情块（软删除）
     * 
     * @param blockId 剧情块ID
     * @param chapterId 章节ID
     */
    @Transactional
    public void deleteBlock(UUID blockId, UUID chapterId) {
        StoryBlock block = getBlock(blockId, chapterId);
        block.setDeleted(true);
        storyBlockRepository.save(block);
        
        // 更新章节字数
        updateChapterWordCount(chapterId);
        
        // 触发父子块索引删除
        triggerIndexDeletion(blockId);
        
        log.debug("删除剧情块: blockId={}, chapterId={}", blockId, chapterId);
    }
    
    /**
     * 合并章节内容为纯文本
     * 
     * @param chapterId 章节ID
     * @return 合并后的文本
     */
    @Transactional(readOnly = true)
    public String mergeChapterContent(UUID chapterId) {
        List<StoryBlock> blocks = getBlocksByChapter(chapterId);
        StringBuilder sb = new StringBuilder();
        
        for (StoryBlock block : blocks) {
            if (block.getBlockType() != BlockType.COMMENT && block.getContent() != null) {
                sb.append(block.getContent()).append("\n\n");
            }
        }
        
        return sb.toString().trim();
    }
    
    /**
     * 创建带有指定rank的剧情块
     */
    private StoryBlock createBlockWithRank(UUID chapterId, String content, BlockType blockType, String rank, Map<String, Object> metadata) {
        StoryBlock block = new StoryBlock();
        block.setChapterId(chapterId);
        block.setContent(content);
        block.setBlockType(blockType != null ? blockType : BlockType.NARRATIVE);
        block.setRank(rank);
        
        if (metadata != null) {
            block.setMetadata(metadata);
        }
        
        block = storyBlockRepository.save(block);
        
        // 更新章节字数
        updateChapterWordCount(chapterId);
        
        // 触发父子块索引创建
        triggerIndexing(block);
        
        log.debug("创建剧情块: blockId={}, chapterId={}, rank={}", block.getId(), chapterId, rank);
        
        return block;
    }
    
    /**
     * 更新章节字数统计
     */
    private void updateChapterWordCount(UUID chapterId) {
        int totalWordCount = storyBlockRepository.sumWordCountByChapterId(chapterId);
        chapterRepository.findById(chapterId).ifPresent(chapter -> {
            chapter.setWordCount(totalWordCount);
            chapterRepository.save(chapter);
        });
    }

    // ==================== 父子块索引方法 ====================

    /**
     * 异步触发剧情块的父子块索引创建
     * 在创建或更新剧情块后调用，为内容创建语义分块索引
     * 
     * @param block 剧情块
     */
    @Async
    public void triggerIndexing(StoryBlock block) {
        if (parentChildSearchService == null) {
            log.debug("ParentChildSearchService not available, skipping indexing for block: {}", block.getId());
            return;
        }

        if (block.getContent() == null || block.getContent().isBlank()) {
            log.debug("Block content is empty, skipping indexing for block: {}", block.getId());
            return;
        }

        try {
            // 获取章节信息以获取项目ID
            Chapter chapter = chapterRepository.findById(block.getChapterId())
                    .orElse(null);
            if (chapter == null) {
                log.warn("Chapter not found for block: {}, skipping indexing", block.getId());
                return;
            }

            UUID projectId = chapter.getProjectId();
            
            // 构建元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("chapterId", block.getChapterId().toString());
            metadata.put("blockType", block.getBlockType().name());
            if (block.getMetadata() != null) {
                metadata.putAll(block.getMetadata());
            }

            // 创建父子块索引
            parentChildSearchService.createParentChildIndex(
                    projectId,
                    KnowledgeChunk.SOURCE_TYPE_STORY_BLOCK,
                    block.getId(),
                    block.getContent(),
                    metadata
            ).subscribe(
                    parent -> log.debug("Created parent-child index for block: {}, parentId: {}", 
                            block.getId(), parent.getId()),
                    error -> log.error("Failed to create parent-child index for block: {}", 
                            block.getId(), error)
            );
        } catch (Exception e) {
            log.error("Error triggering indexing for block: {}", block.getId(), e);
        }
    }

    /**
     * 异步触发剧情块的索引删除
     * 在删除剧情块后调用，清理相关的知识块
     * 
     * @param blockId 剧情块ID
     */
    @Async
    public void triggerIndexDeletion(UUID blockId) {
        try {
            int deletedCount = knowledgeChunkRepository.deleteBySourceId(blockId);
            log.debug("Deleted {} knowledge chunks for block: {}", deletedCount, blockId);
        } catch (Exception e) {
            log.error("Error deleting knowledge chunks for block: {}", blockId, e);
        }
    }
}
