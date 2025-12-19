package com.inkflow.module.plotloop.repository;

import com.inkflow.module.plotloop.entity.PlotLoop;
import com.inkflow.module.plotloop.entity.PlotLoopStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 伏笔仓储接口
 */
@Repository
public interface PlotLoopRepository extends JpaRepository<PlotLoop, UUID> {

    /**
     * 根据项目ID查询所有伏笔
     */
    List<PlotLoop> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    /**
     * 根据项目ID和状态查询
     */
    List<PlotLoop> findByProjectIdAndStatus(UUID projectId, PlotLoopStatus status);

    /**
     * 查询项目中的开放和紧急伏笔 (用于AI上下文)
     */
    @Query("SELECT p FROM PlotLoop p WHERE p.projectId = :projectId " +
           "AND p.status IN ('OPEN', 'URGENT') ORDER BY p.status DESC, p.introChapterOrder ASC")
    List<PlotLoop> findOpenAndUrgent(@Param("projectId") UUID projectId);

    /**
     * 统计项目中的伏笔数量
     */
    long countByProjectId(UUID projectId);

    /**
     * 按状态统计伏笔数量
     */
    long countByProjectIdAndStatus(UUID projectId, PlotLoopStatus status);

    /**
     * 查询需要标记为紧急的伏笔 (超过10章未回收)
     */
    @Query("SELECT p FROM PlotLoop p WHERE p.projectId = :projectId " +
           "AND p.status = 'OPEN' " +
           "AND p.introChapterOrder IS NOT NULL " +
           "AND :currentChapterOrder - p.introChapterOrder > 10")
    List<PlotLoop> findShouldBeUrgent(@Param("projectId") UUID projectId, 
                                       @Param("currentChapterOrder") int currentChapterOrder);

    /**
     * 根据引入章节查询
     */
    List<PlotLoop> findByIntroChapterId(UUID chapterId);

    /**
     * 根据解决章节查询
     */
    List<PlotLoop> findByResolutionChapterId(UUID chapterId);

    /**
     * 搜索伏笔 (标题或描述包含关键词)
     */
    @Query("SELECT p FROM PlotLoop p WHERE p.projectId = :projectId " +
           "AND (LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<PlotLoop> searchByKeyword(@Param("projectId") UUID projectId, 
                                   @Param("keyword") String keyword);
}
