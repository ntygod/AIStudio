package com.inkflow.module.rag.repository;

import com.inkflow.module.rag.entity.KnowledgeChunk;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 知识块仓储接口
 * 包含向量相似性搜索和版本控制支持
 *
 * @author zsg
 * @date 2025/12/17
 */
@Repository
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, UUID> {

    // ==================== 基础查询 ====================

    /**
     * 按项目ID查找所有活跃的知识块
     */
    List<KnowledgeChunk> findByProjectIdAndIsActiveTrue(UUID projectId);

    /**
     * 按来源ID查找活跃的知识块
     */
    Optional<KnowledgeChunk> findBySourceIdAndIsActiveTrue(UUID sourceId);

    /**
     * 按来源ID和类型查找活跃的知识块
     */
    Optional<KnowledgeChunk> findBySourceIdAndSourceTypeAndIsActiveTrue(UUID sourceId, String sourceType);

    /**
     * 按父块ID查找所有子块
     */
    List<KnowledgeChunk> findByParentIdAndIsActiveTrue(UUID parentId);

    /**
     * 按项目ID和来源类型查找
     */
    List<KnowledgeChunk> findByProjectIdAndSourceTypeAndIsActiveTrue(UUID projectId, String sourceType);

    // ==================== 版本控制查询 ====================

    /**
     * 查找来源的最新版本号
     */
    @Query("SELECT MAX(kc.version) FROM KnowledgeChunk kc WHERE kc.sourceId = :sourceId")
    Optional<Integer> findMaxVersionBySourceId(@Param("sourceId") UUID sourceId);

    /**
     * 查找脏数据块
     */
    List<KnowledgeChunk> findByProjectIdAndIsDirtyTrue(UUID projectId);

    /**
     * 查找所有脏数据块
     */
    List<KnowledgeChunk> findByIsDirtyTrue();


    // ==================== 向量相似性搜索 ====================

    /**
     * 使用余弦距离执行向量相似性搜索
     * 只返回活跃且非脏的数据
     *
     * @param projectId 项目ID
     * @param queryVector 查询向量字符串 (例如 "[0.1, 0.2, ...]")
     * @param limit 最大结果数
     * @return 按相似度排序的知识块列表
     */
    @Query(value = """
        SELECT kc.* FROM knowledge_chunks kc
        WHERE kc.project_id = :projectId
          AND kc.is_active = true
          AND kc.is_dirty = false
          AND kc.embedding IS NOT NULL
        ORDER BY kc.embedding <=> cast(:queryVector as vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<KnowledgeChunk> findSimilarByProjectId(
            @Param("projectId") UUID projectId,
            @Param("queryVector") String queryVector,
            @Param("limit") int limit);

    /**
     * 按来源类型过滤的向量相似性搜索
     */
    @Query(value = """
        SELECT kc.* FROM knowledge_chunks kc
        WHERE kc.project_id = :projectId
          AND kc.source_type = :sourceType
          AND kc.is_active = true
          AND kc.is_dirty = false
          AND kc.embedding IS NOT NULL
        ORDER BY kc.embedding <=> cast(:queryVector as vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<KnowledgeChunk> findSimilarByProjectIdAndSourceType(
            @Param("projectId") UUID projectId,
            @Param("sourceType") String sourceType,
            @Param("queryVector") String queryVector,
            @Param("limit") int limit);

    /**
     * 在子块中执行向量相似性搜索 (parent-child策略)
     */
    @Query(value = """
        SELECT kc.* FROM knowledge_chunks kc
        WHERE kc.project_id = :projectId
          AND kc.chunk_level = 'child'
          AND kc.is_active = true
          AND kc.is_dirty = false
          AND kc.embedding IS NOT NULL
        ORDER BY kc.embedding <=> cast(:queryVector as vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<KnowledgeChunk> findSimilarChildChunks(
            @Param("projectId") UUID projectId,
            @Param("queryVector") String queryVector,
            @Param("limit") int limit);

    /**
     * 带相似度阈值的搜索
     */
    @Query(value = """
        SELECT kc.* FROM knowledge_chunks kc
        WHERE kc.project_id = :projectId
          AND kc.is_active = true
          AND kc.is_dirty = false
          AND kc.embedding IS NOT NULL
          AND (kc.embedding <=> cast(:queryVector as vector)) < :threshold
        ORDER BY kc.embedding <=> cast(:queryVector as vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<KnowledgeChunk> findSimilarWithThreshold(
            @Param("projectId") UUID projectId,
            @Param("queryVector") String queryVector,
            @Param("threshold") double threshold,
            @Param("limit") int limit);

    // ==================== 带相似度分数的查询 ====================

    /**
     * 带余弦距离分数的相似性搜索
     */
    @Query(value = """
        SELECT kc.id as id,
               kc.source_type as sourceType,
               kc.source_id as sourceId,
               kc.content as content,
               kc.chunk_level as chunkLevel,
               kc.parent_id as parentId,
               kc.chunk_order as chunkOrder,
               (kc.embedding <=> cast(:queryVector as vector)) as cosineDistance
        FROM knowledge_chunks kc
        WHERE kc.project_id = :projectId
          AND kc.is_active = true
          AND kc.is_dirty = false
          AND kc.embedding IS NOT NULL
        ORDER BY kc.embedding <=> cast(:queryVector as vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<SimilarityProjection> findSimilarWithScore(
            @Param("projectId") UUID projectId,
            @Param("queryVector") String queryVector,
            @Param("limit") int limit);

    // ==================== 删除操作 ====================

    /**
     * 删除来源实体的所有知识块
     * @return 删除的记录数
     */
    @Modifying
    @Query("DELETE FROM KnowledgeChunk kc WHERE kc.sourceId = :sourceId")
    int deleteBySourceId(@Param("sourceId") UUID sourceId);

    /**
     * 删除项目的所有知识块
     */
    @Modifying
    @Query("DELETE FROM KnowledgeChunk kc WHERE kc.projectId = :projectId")
    void deleteByProjectId(@Param("projectId") UUID projectId);

    /**
     * 删除父块的所有子块
     */
    @Modifying
    @Query("DELETE FROM KnowledgeChunk kc WHERE kc.parentId = :parentId")
    void deleteByParentId(@Param("parentId") UUID parentId);

    /**
     * 停用旧版本 (原子切换)
     */
    @Modifying
    @Query("UPDATE KnowledgeChunk kc SET kc.isActive = false WHERE kc.sourceId = :sourceId AND kc.version < :version")
    int deactivateOldVersions(@Param("sourceId") UUID sourceId, @Param("version") int version);

    /**
     * 删除指定来源的旧版本非活跃记录
     * 用于清理旧版本以节省存储空间
     *
     * @param sourceId 来源ID
     * @param cutoffVersion 截止版本号，删除小于此版本的记录
     * @return 删除的记录数
     */
    @Modifying
    @Query("DELETE FROM KnowledgeChunk kc WHERE kc.sourceId = :sourceId AND kc.version < :cutoffVersion AND kc.isActive = false")
    int deleteBySourceIdAndVersionLessThanAndIsActiveFalse(
            @Param("sourceId") UUID sourceId,
            @Param("cutoffVersion") int cutoffVersion);

    // ==================== 统计查询 ====================

    long countByProjectId(UUID projectId);

    long countByProjectIdAndSourceType(UUID projectId, String sourceType);

    long countByProjectIdAndChunkLevel(UUID projectId, String chunkLevel);

    long countByProjectIdAndIsDirtyTrue(UUID projectId);

    // ==================== 缓存预热支持 ====================

    /**
     * 按项目ID查找最近更新的内容
     */
    @Query("SELECT kc FROM KnowledgeChunk kc WHERE kc.projectId = :projectId AND kc.isActive = true ORDER BY kc.updatedAt DESC")
    List<KnowledgeChunk> findRecentByProjectId(@Param("projectId") UUID projectId, Pageable pageable);

    /**
     * 查找所有活跃项目的最近更新内容
     */
    @Query("SELECT kc FROM KnowledgeChunk kc WHERE kc.isActive = true ORDER BY kc.updatedAt DESC")
    List<KnowledgeChunk> findRecentActive(Pageable pageable);

    /**
     * 查找子块的父块ID列表 (去重)
     */
    @Query("SELECT DISTINCT kc.parentId FROM KnowledgeChunk kc WHERE kc.projectId = :projectId AND kc.chunkLevel = 'child' AND kc.parentId IS NOT NULL")
    List<UUID> findDistinctParentIdsByProjectId(@Param("projectId") UUID projectId);

    /**
     * 检查来源是否已有知识块
     */
    boolean existsBySourceIdAndIsActiveTrue(UUID sourceId);
}
