package com.inkflow.module.ai_bridge.tool;

import com.inkflow.module.ai_bridge.adapter.DomainAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 通用CRUD工具
 * 为AI提供统一的领域实体操作接口
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UniversalCrudTool {

    private final Map<String, DomainAdapter<?>> adapters;

    @Tool(description = "创建领域实体，支持角色、百科、伏笔等类型")
    public String create(
            @ToolParam(description = "实体类型：character, wiki_entry, plot_loop, volume, chapter") String entityType,
            @ToolParam(description = "项目ID") String projectId,
            @ToolParam(description = "实体属性，JSON格式") Map<String, Object> properties) {

        log.info("创建实体: type={}, projectId={}", entityType, projectId);

        DomainAdapter<?> adapter = getAdapter(entityType);
        if (adapter == null) {
            return "错误：不支持的实体类型 " + entityType;
        }

        try {
            properties.put("projectId", UUID.fromString(projectId));
            Object entity = adapter.create(properties);
            return "创建成功：" + entity.toString();
        } catch (Exception e) {
            log.error("创建实体失败", e);
            return "创建失败：" + e.getMessage();
        }
    }

    @Tool(description = "查询单个领域实体")
    public String findById(
            @ToolParam(description = "实体类型") String entityType,
            @ToolParam(description = "实体ID") String entityId) {

        log.info("查询实体: type={}, id={}", entityType, entityId);

        DomainAdapter<?> adapter = getAdapter(entityType);
        if (adapter == null) {
            return "错误：不支持的实体类型 " + entityType;
        }

        try {
            return adapter.findById(UUID.fromString(entityId))
                    .map(Object::toString)
                    .orElse("未找到实体：" + entityId);
        } catch (Exception e) {
            log.error("查询实体失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    @Tool(description = "查询项目下的所有实体")
    public String findByProject(
            @ToolParam(description = "实体类型") String entityType,
            @ToolParam(description = "项目ID") String projectId) {

        log.info("查询项目实体: type={}, projectId={}", entityType, projectId);

        DomainAdapter<?> adapter = getAdapter(entityType);
        if (adapter == null) {
            return "错误：不支持的实体类型 " + entityType;
        }

        try {
            List<?> entities = adapter.findByProjectId(UUID.fromString(projectId));
            if (entities.isEmpty()) {
                return "项目中没有" + entityType + "类型的实体";
            }

            StringBuilder result = new StringBuilder();
            result.append("找到").append(entities.size()).append("个实体：\n");
            for (Object entity : entities) {
                result.append("- ").append(entity.toString()).append("\n");
            }
            return result.toString();
        } catch (Exception e) {
            log.error("查询项目实体失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    @Tool(description = "更新领域实体")
    public String update(
            @ToolParam(description = "实体类型") String entityType,
            @ToolParam(description = "实体ID") String entityId,
            @ToolParam(description = "更新的属性，JSON格式") Map<String, Object> properties) {

        log.info("更新实体: type={}, id={}", entityType, entityId);

        DomainAdapter<?> adapter = getAdapter(entityType);
        if (adapter == null) {
            return "错误：不支持的实体类型 " + entityType;
        }

        try {
            Object entity = adapter.update(UUID.fromString(entityId), properties);
            return "更新成功：" + entity.toString();
        } catch (Exception e) {
            log.error("更新实体失败", e);
            return "更新失败：" + e.getMessage();
        }
    }

    @Tool(description = "删除领域实体")
    public String delete(
            @ToolParam(description = "实体类型") String entityType,
            @ToolParam(description = "实体ID") String entityId) {

        log.info("删除实体: type={}, id={}", entityType, entityId);

        DomainAdapter<?> adapter = getAdapter(entityType);
        if (adapter == null) {
            return "错误：不支持的实体类型 " + entityType;
        }

        try {
            adapter.delete(UUID.fromString(entityId));
            return "删除成功：" + entityId;
        } catch (Exception e) {
            log.error("删除实体失败", e);
            return "删除失败：" + e.getMessage();
        }
    }

    @Tool(description = "列出支持的实体类型")
    public String listEntityTypes() {
        StringBuilder result = new StringBuilder("支持的实体类型：\n");
        for (String type : adapters.keySet()) {
            DomainAdapter<?> adapter = adapters.get(type);
            result.append("- ").append(type).append(": ").append(adapter.getEntityType()).append("\n");
        }
        return result.toString();
    }

    /**
     * 获取适配器
     */
    private DomainAdapter<?> getAdapter(String entityType) {
        return adapters.get(entityType.toLowerCase());
    }
}
