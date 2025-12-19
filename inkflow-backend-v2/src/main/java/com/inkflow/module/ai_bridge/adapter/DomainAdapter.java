package com.inkflow.module.ai_bridge.adapter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 领域适配器接口
 * 连接AI工具与领域服务
 * 
 * Requirements: 10.2, 10.4
 *
 * @param <T> 实体类型
 * @author zsg
 * @date 2025/12/17
 */
public interface DomainAdapter<T> {

    /**
     * 获取实体类型名称
     */
    String getEntityType();

    /**
     * 创建实体
     * 
     * @param params 创建参数
     * @return 创建的实体
     */
    T create(Map<String, Object> params);

    /**
     * 更新实体
     * 
     * @param id 实体ID
     * @param params 更新参数
     * @return 更新后的实体
     */
    T update(UUID id, Map<String, Object> params);

    /**
     * 根据ID查找实体
     * 
     * @param id 实体ID
     * @return 实体（可能为空）
     */
    Optional<T> findById(UUID id);

    /**
     * 根据项目ID查找所有实体
     * 
     * @param projectId 项目ID
     * @return 实体列表
     */
    List<T> findByProjectId(UUID projectId);

    /**
     * 删除实体
     * 
     * @param id 实体ID
     */
    void delete(UUID id);
}
