package com.inkflow.module.content.entity;

import com.inkflow.common.entity.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 剧情块实体
 * 
 * 代表章节中的一个内容块，使用Lexorank排序：
 * - 支持无限插入而不需要重排序
 * - 支持拖拽排序
 * - 支持版本控制
 */
@Entity
@Table(name = "story_blocks", indexes = {
    @Index(name = "idx_story_blocks_chapter_id", columnList = "chapter_id"),
    @Index(name = "idx_story_blocks_rank", columnList = "chapter_id, rank")
})
public class StoryBlock extends BaseEntity {
    
    /**
     * 所属章节ID
     */
    @Column(name = "chapter_id", nullable = false)
    private UUID chapterId;
    
    /**
     * 剧情块类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "block_type", nullable = false, length = 30)
    private BlockType blockType = BlockType.NARRATIVE;
    
    /**
     * 内容文本
     */
    @Column(columnDefinition = "TEXT")
    private String content;
    
    /**
     * Lexorank排序值
     * 
     * 使用字符串比较实现排序：
     * - 初始值: "a0"
     * - 在两个值之间插入: 计算中间值
     * - 支持无限精度
     */
    @Column(nullable = false, length = 100)
    private String rank;
    
    /**
     * 版本号（乐观锁）
     */
    @Version
    @Column(nullable = false)
    private int version = 0;
    
    /**
     * 字数统计
     */
    @Column(name = "word_count", nullable = false)
    private int wordCount = 0;
    
    /**
     * 剧情块元数据（JSONB）
     * 
     * 可存储：
     * - speaker: 对话发言人
     * - emotion: 情感标记
     * - notes: 创作笔记
     * - aiGenerated: 是否AI生成
     */
    @Column(columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata = new HashMap<>();
    
    // ==================== Getters & Setters ====================
    
    public UUID getChapterId() {
        return chapterId;
    }
    
    public void setChapterId(UUID chapterId) {
        this.chapterId = chapterId;
    }
    
    public BlockType getBlockType() {
        return blockType;
    }
    
    public void setBlockType(BlockType blockType) {
        this.blockType = blockType;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
        // 自动更新字数
        this.wordCount = content != null ? content.length() : 0;
    }
    
    public String getRank() {
        return rank;
    }
    
    public void setRank(String rank) {
        this.rank = rank;
    }
    
    public int getVersion() {
        return version;
    }
    
    public void setVersion(int version) {
        this.version = version;
    }
    
    public int getWordCount() {
        return wordCount;
    }
    
    public void setWordCount(int wordCount) {
        this.wordCount = wordCount;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
