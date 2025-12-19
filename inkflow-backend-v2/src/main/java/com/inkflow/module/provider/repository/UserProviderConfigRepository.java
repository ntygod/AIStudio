package com.inkflow.module.provider.repository;

import com.inkflow.module.provider.entity.ProviderType;
import com.inkflow.module.provider.entity.UserProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 用户级AI提供商配置仓库
 * 
 * Requirements: 1.1, 1.2
 */
@Repository
public interface UserProviderConfigRepository extends JpaRepository<UserProviderConfig, UUID> {

    /**
     * 根据用户ID查找配置
     * 
     * @param userId 用户ID
     * @return 用户配置（如果存在）
     */
    Optional<UserProviderConfig> findByUserId(UUID userId);

    /**
     * 检查用户是否已有配置
     * 
     * @param userId 用户ID
     * @return 是否存在配置
     */
    boolean existsByUserId(UUID userId);

    /**
     * 根据用户ID删除配置
     * 
     * @param userId 用户ID
     */
    void deleteByUserId(UUID userId);

    /**
     * 查找使用指定提供商的用户数量
     * 用于统计分析
     * 
     * @param providerType 提供商类型
     * @return 用户数量
     */
    @Query("SELECT COUNT(c) FROM UserProviderConfig c WHERE c.preferredProvider = :providerType")
    long countByPreferredProvider(ProviderType providerType);

    /**
     * 查找启用了推理模型的用户配置数量
     * 
     * @return 启用推理模型的用户数量
     */
    @Query("SELECT COUNT(c) FROM UserProviderConfig c WHERE c.reasoningEnabled = true")
    long countByReasoningEnabled();
}
