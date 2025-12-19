package com.inkflow.module.project.repository;

import com.inkflow.module.project.entity.Project;
import com.inkflow.module.project.entity.ProjectStatus;
import org.springframework.data.domain.Page;
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
 * 项目数据访问层
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {
    
    /**
     * 根据用户ID查找项目列表（分页）
     */
    Page<Project> findByUserIdAndDeletedFalse(UUID userId, Pageable pageable);
    
    /**
     * 根据用户ID和状态查找项目
     */
    Page<Project> findByUserIdAndStatusAndDeletedFalse(UUID userId, ProjectStatus status, Pageable pageable);
    
    /**
     * 根据ID查找未删除的项目
     */
    Optional<Project> findByIdAndDeletedFalse(UUID id);
    
    /**
     * 根据ID和用户ID查找项目（权限验证）
     */
    Optional<Project> findByIdAndUserIdAndDeletedFalse(UUID id, UUID userId);
    
    /**
     * 统计用户的项目数量
     */
    long countByUserIdAndDeletedFalse(UUID userId);
    
    /**
     * 统计用户特定状态的项目数量
     */
    long countByUserIdAndStatusAndDeletedFalse(UUID userId, ProjectStatus status);
    
    /**
     * 搜索项目（标题模糊匹配）
     */
    @Query("SELECT p FROM Project p WHERE p.userId = :userId AND p.deleted = false " +
           "AND LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Project> searchByTitle(@Param("userId") UUID userId, @Param("keyword") String keyword, Pageable pageable);
    
    /**
     * 更新项目状态
     */
    @Modifying
    @Query("UPDATE Project p SET p.status = :status WHERE p.id = :projectId")
    void updateStatus(@Param("projectId") UUID projectId, @Param("status") ProjectStatus status);
    
    /**
     * 获取用户最近更新的项目
     */
    @Query("SELECT p FROM Project p WHERE p.userId = :userId AND p.deleted = false " +
           "ORDER BY p.updatedAt DESC")
    List<Project> findRecentProjects(@Param("userId") UUID userId, Pageable pageable);
    
    /**
     * 检查项目是否存在且属于指定用户
     */
    boolean existsByIdAndUserIdAndDeletedFalse(UUID id, UUID userId);
}
