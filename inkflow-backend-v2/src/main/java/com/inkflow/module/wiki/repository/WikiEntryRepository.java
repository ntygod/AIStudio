package com.inkflow.module.wiki.repository;

import com.inkflow.module.wiki.entity.WikiEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 知识条目仓储接口
 */
@Repository
public interface WikiEntryRepository extends JpaRepository<WikiEntry, UUID> {

    /**
     * 根据项目ID查询所有条目
     */
    List<WikiEntry> findByProjectIdOrderByUpdatedAtDesc(UUID projectId);

    /**
     * 根据项目ID和类型查询
     */
    List<WikiEntry> findByProjectIdAndType(UUID projectId, String type);

    /**
     * 根据项目ID和标题查询
     */
    Optional<WikiEntry> findByProjectIdAndTitle(UUID projectId, String title);

    /**
     * 检查标题是否已存在
     */
    boolean existsByProjectIdAndTitle(UUID projectId, String title);

    /**
     * 根据项目ID和时间版本查询
     */
    List<WikiEntry> findByProjectIdAndTimeVersion(UUID projectId, String timeVersion);

    /**
     * 统计项目中的条目数量
     */
    long countByProjectId(UUID projectId);

    /**
     * 按类型统计条目数量
     */
    long countByProjectIdAndType(UUID projectId, String type);

    /**
     * 搜索条目 (标题或内容包含关键词)
     */
    @Query("SELECT w FROM WikiEntry w WHERE w.projectId = :projectId " +
           "AND (LOWER(w.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(w.content) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<WikiEntry> searchByKeyword(@Param("projectId") UUID projectId, 
                                    @Param("keyword") String keyword);

    /**
     * 根据别名搜索 (PostgreSQL数组包含查询)
     */
    @Query(value = "SELECT * FROM wiki_entries WHERE project_id = :projectId " +
                   "AND :alias = ANY(aliases) AND deleted_at IS NULL",
           nativeQuery = true)
    List<WikiEntry> findByAlias(@Param("projectId") UUID projectId, 
                                @Param("alias") String alias);

    /**
     * 根据标签搜索
     */
    @Query(value = "SELECT * FROM wiki_entries WHERE project_id = :projectId " +
                   "AND :tag = ANY(tags) AND deleted_at IS NULL",
           nativeQuery = true)
    List<WikiEntry> findByTag(@Param("projectId") UUID projectId, 
                              @Param("tag") String tag);

    /**
     * 获取项目中所有不同的类型
     */
    @Query("SELECT DISTINCT w.type FROM WikiEntry w WHERE w.projectId = :projectId")
    List<String> findDistinctTypesByProjectId(@Param("projectId") UUID projectId);

    /**
     * 获取项目中所有不同的时间版本
     */
    @Query("SELECT DISTINCT w.timeVersion FROM WikiEntry w WHERE w.projectId = :projectId AND w.timeVersion IS NOT NULL")
    List<String> findDistinctTimeVersionsByProjectId(@Param("projectId") UUID projectId);
}
