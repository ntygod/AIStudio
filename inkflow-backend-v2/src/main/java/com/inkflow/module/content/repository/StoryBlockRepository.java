package com.inkflow.module.content.repository;

import com.inkflow.module.content.entity.StoryBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 剧情块数据访问层
 */
@Repository
public interface StoryBlockRepository extends JpaRepository<StoryBlock, UUID> {
    
    /**
     * 根据章节ID查找所有剧情块（按rank排序）
     */
    List<StoryBlock> findByChapterIdAndDeletedFalseOrderByRankAsc(UUID chapterId);
    
    /**
     * 根据ID和章节ID查找剧情块
     */
    Optional<StoryBlock> findByIdAndChapterIdAndDeletedFalse(UUID id, UUID chapterId);
    
    /**
     * 获取章节的第一个剧情块
     */
    @Query("SELECT sb FROM StoryBlock sb WHERE sb.chapterId = :chapterId AND sb.deleted = false " +
           "ORDER BY sb.rank ASC LIMIT 1")
    Optional<StoryBlock> findFirstByChapterId(@Param("chapterId") UUID chapterId);
    
    /**
     * 获取章节的最后一个剧情块
     */
    @Query("SELECT sb FROM StoryBlock sb WHERE sb.chapterId = :chapterId AND sb.deleted = false " +
           "ORDER BY sb.rank DESC LIMIT 1")
    Optional<StoryBlock> findLastByChapterId(@Param("chapterId") UUID chapterId);
    
    /**
     * 获取指定rank之前的剧情块
     */
    @Query("SELECT sb FROM StoryBlock sb WHERE sb.chapterId = :chapterId AND sb.deleted = false " +
           "AND sb.rank < :rank ORDER BY sb.rank DESC LIMIT 1")
    Optional<StoryBlock> findPreviousByRank(@Param("chapterId") UUID chapterId, @Param("rank") String rank);
    
    /**
     * 获取指定rank之后的剧情块
     */
    @Query("SELECT sb FROM StoryBlock sb WHERE sb.chapterId = :chapterId AND sb.deleted = false " +
           "AND sb.rank > :rank ORDER BY sb.rank ASC LIMIT 1")
    Optional<StoryBlock> findNextByRank(@Param("chapterId") UUID chapterId, @Param("rank") String rank);
    
    /**
     * 统计章节的剧情块数量
     */
    long countByChapterIdAndDeletedFalse(UUID chapterId);
    
    /**
     * 计算章节总字数
     */
    @Query("SELECT COALESCE(SUM(sb.wordCount), 0) FROM StoryBlock sb WHERE sb.chapterId = :chapterId AND sb.deleted = false")
    int sumWordCountByChapterId(@Param("chapterId") UUID chapterId);
}
