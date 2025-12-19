package com.inkflow.module.content.repository;

import com.inkflow.module.content.entity.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 章节数据访问层
 */
@Repository
public interface ChapterRepository extends JpaRepository<Chapter, UUID> {
    
    /**
     * 根据分卷ID查找所有章节（按顺序）
     */
    List<Chapter> findByVolumeIdAndDeletedFalseOrderByOrderIndexAsc(UUID volumeId);
    
    /**
     * 根据项目ID查找所有章节（按顺序）
     */
    @Query("SELECT c FROM Chapter c WHERE c.projectId = :projectId AND c.deleted = false " +
           "ORDER BY c.volumeId, c.orderIndex")
    List<Chapter> findByProjectIdOrderByVolumeAndOrder(@Param("projectId") UUID projectId);
    
    /**
     * 根据ID和项目ID查找章节
     */
    Optional<Chapter> findByIdAndProjectIdAndDeletedFalse(UUID id, UUID projectId);
    
    /**
     * 获取分卷的最大排序索引
     */
    @Query("SELECT COALESCE(MAX(c.orderIndex), -1) FROM Chapter c WHERE c.volumeId = :volumeId AND c.deleted = false")
    int findMaxOrderIndex(@Param("volumeId") UUID volumeId);
    
    /**
     * 统计分卷的章节数量
     */
    long countByVolumeIdAndDeletedFalse(UUID volumeId);
    
    /**
     * 统计项目的章节数量
     */
    long countByProjectIdAndDeletedFalse(UUID projectId);
    
    /**
     * 计算项目总字数
     */
    @Query("SELECT COALESCE(SUM(c.wordCount), 0) FROM Chapter c WHERE c.projectId = :projectId AND c.deleted = false")
    long sumWordCountByProjectId(@Param("projectId") UUID projectId);

    /**
     * 计算分卷总字数
     */
    @Query("SELECT COALESCE(SUM(c.wordCount), 0) FROM Chapter c WHERE c.volumeId = :volumeId AND c.deleted = false")
    long sumWordCountByVolumeId(@Param("volumeId") UUID volumeId);

    /**
     * 检查章节是否属于指定用户（通过项目关联）
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Chapter c " +
           "JOIN Volume v ON c.volumeId = v.id " +
           "JOIN Project p ON v.projectId = p.id " +
           "WHERE c.id = :chapterId AND p.userId = :userId AND c.deleted = false")
    boolean existsByIdAndVolumeProjectUserId(@Param("chapterId") UUID chapterId, @Param("userId") UUID userId);
}
