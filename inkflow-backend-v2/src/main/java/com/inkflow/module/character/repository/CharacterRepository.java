package com.inkflow.module.character.repository;

import com.inkflow.module.character.entity.Character;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 角色仓储接口
 */
@Repository
public interface CharacterRepository extends JpaRepository<Character, UUID> {

    /**
     * 根据项目ID查询所有角色
     */
    List<Character> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    /**
     * 根据项目ID和角色类型查询
     */
    List<Character> findByProjectIdAndRole(UUID projectId, String role);

    /**
     * 根据项目ID和名称查询
     */
    Optional<Character> findByProjectIdAndName(UUID projectId, String name);

    /**
     * 根据项目ID查询活跃角色
     */
    List<Character> findByProjectIdAndIsActiveTrue(UUID projectId);

    /**
     * 根据项目ID和原型查询
     */
    List<Character> findByProjectIdAndArchetype(UUID projectId, String archetype);

    /**
     * 统计项目中的角色数量
     */
    long countByProjectId(UUID projectId);

    /**
     * 检查角色名称是否已存在
     */
    boolean existsByProjectIdAndName(UUID projectId, String name);

    /**
     * 根据项目ID和状态查询
     */
    List<Character> findByProjectIdAndStatus(UUID projectId, String status);

    /**
     * 搜索角色 (名称或描述包含关键词)
     */
    @Query("SELECT c FROM Character c WHERE c.projectId = :projectId " +
           "AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(c.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Character> searchByKeyword(@Param("projectId") UUID projectId, 
                                    @Param("keyword") String keyword);

    /**
     * 查询与指定角色有关系的所有角色ID
     */
    @Query(value = "SELECT DISTINCT (r->>'targetId')::uuid FROM characters c, " +
                   "jsonb_array_elements(c.relationships) r " +
                   "WHERE c.id = :characterId AND c.deleted_at IS NULL",
           nativeQuery = true)
    List<UUID> findRelatedCharacterIds(@Param("characterId") UUID characterId);
}
