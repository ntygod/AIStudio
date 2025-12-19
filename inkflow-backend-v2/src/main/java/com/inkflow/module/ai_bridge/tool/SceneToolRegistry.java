package com.inkflow.module.ai_bridge.tool;

import com.inkflow.module.project.entity.CreationPhase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * 场景工具注册表
 * 根据创作阶段注册和管理AI工具
 * 
 * <p>阶段-工具映射：
 * <ul>
 *   <li>IDEA: CRUD + CreativeGen</li>
 *   <li>WORLDBUILDING: CRUD + RAG + CreativeGen</li>
 *   <li>CHARACTER: CRUD + RAG + CreativeGen</li>
 *   <li>OUTLINE: CRUD + RAG + Preflight + DeepReasoning</li>
 *   <li>WRITING: 全部工具</li>
 *   <li>REVISION: CRUD + RAG + Preflight</li>
 * </ul>
 *
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@Component
public class SceneToolRegistry {

    private final UniversalCrudTool universalCrudTool;
    private final RAGSearchTool ragSearchTool;
    private final Optional<CreativeGenTool> creativeGenTool;
    private final Optional<DeepReasoningTool> deepReasoningTool;
    private final Optional<PreflightTool> preflightTool;
    private final Optional<StyleRetrieveTool> styleRetrieveTool;

    /**
     * 工具注册表：阶段 -> 工具列表
     */
    private final Map<CreationPhase, List<Object>> phaseTools = new EnumMap<>(CreationPhase.class);

    /**
     * 所有已注册的工具
     */
    private final Map<String, Object> allTools = new HashMap<>();

    public SceneToolRegistry(
            UniversalCrudTool universalCrudTool,
            RAGSearchTool ragSearchTool,
            Optional<CreativeGenTool> creativeGenTool,
            Optional<DeepReasoningTool> deepReasoningTool,
            Optional<PreflightTool> preflightTool,
            Optional<StyleRetrieveTool> styleRetrieveTool) {
        this.universalCrudTool = universalCrudTool;
        this.ragSearchTool = ragSearchTool;
        this.creativeGenTool = creativeGenTool;
        this.deepReasoningTool = deepReasoningTool;
        this.preflightTool = preflightTool;
        this.styleRetrieveTool = styleRetrieveTool;
    }

    @PostConstruct
    public void init() {
        // 1. 注册通用 CRUD 工具 - 所有阶段可用
        registerTool("universalCrud", universalCrudTool, EnumSet.allOf(CreationPhase.class));

        // 2. 注册 RAG 搜索工具 - 世界观、角色、大纲、写作、修订阶段
        registerTool("ragSearch", ragSearchTool, EnumSet.of(
                CreationPhase.WORLDBUILDING,
                CreationPhase.CHARACTER,
                CreationPhase.OUTLINE,
                CreationPhase.WRITING,
                CreationPhase.REVISION
        ));

        // 3. 注册创意生成工具 - 灵感、世界观、角色、写作阶段
        creativeGenTool.ifPresent(tool -> registerTool("creativeGen", tool, EnumSet.of(
                CreationPhase.IDEA,
                CreationPhase.WORLDBUILDING,
                CreationPhase.CHARACTER,
                CreationPhase.WRITING
        )));

        // 4. 注册深度推理工具 - 大纲、写作阶段
        deepReasoningTool.ifPresent(tool -> registerTool("deepReasoning", tool, EnumSet.of(
                CreationPhase.OUTLINE,
                CreationPhase.WRITING
        )));

        // 5. 注册预检工具 - 大纲、写作、修订阶段
        preflightTool.ifPresent(tool -> registerTool("preflight", tool, EnumSet.of(
                CreationPhase.OUTLINE,
                CreationPhase.WRITING,
                CreationPhase.REVISION
        )));

        // 6. 注册风格检索工具 - 写作阶段
        styleRetrieveTool.ifPresent(tool -> registerTool("styleRetrieve", tool, EnumSet.of(
                CreationPhase.WRITING
        )));

        log.info("SceneToolRegistry初始化完成，已注册{}个工具", allTools.size());
        logPhaseToolMapping();
    }

    /**
     * 记录阶段-工具映射日志
     */
    private void logPhaseToolMapping() {
        for (CreationPhase phase : CreationPhase.values()) {
            int toolCount = getToolCountForPhase(phase);
            log.debug("阶段 {} 可用工具数: {}", phase.getDisplayName(), toolCount);
        }
    }

    /**
     * 注册工具到指定阶段
     * 
     * @param name 工具名称
     * @param tool 工具实例
     * @param phases 适用的创作阶段
     */
    public void registerTool(String name, Object tool, Set<CreationPhase> phases) {
        allTools.put(name, tool);

        for (CreationPhase phase : phases) {
            phaseTools.computeIfAbsent(phase, k -> new ArrayList<>()).add(tool);
        }

        log.debug("注册工具: {} -> {}", name, phases);
    }

    /**
     * 获取指定阶段的工具数组
     * 
     * @param phase 创作阶段
     * @return 工具数组
     */
    public Object[] getToolsArrayForScene(CreationPhase phase) {
        List<Object> tools = phaseTools.getOrDefault(phase, Collections.emptyList());
        return tools.toArray();
    }

    /**
     * 获取指定阶段的工具列表
     */
    public List<Object> getToolsForPhase(CreationPhase phase) {
        return new ArrayList<>(phaseTools.getOrDefault(phase, Collections.emptyList()));
    }

    /**
     * 获取所有工具
     */
    public Object[] getAllTools() {
        return allTools.values().toArray();
    }

    /**
     * 根据名称获取工具
     */
    public Optional<Object> getTool(String name) {
        return Optional.ofNullable(allTools.get(name));
    }

    /**
     * 检查工具是否在指定阶段可用
     */
    public boolean isToolAvailable(String toolName, CreationPhase phase) {
        Object tool = allTools.get(toolName);
        if (tool == null) {
            return false;
        }
        List<Object> tools = phaseTools.get(phase);
        return tools != null && tools.contains(tool);
    }

    /**
     * 获取已注册的工具数量
     */
    public int getToolCount() {
        return allTools.size();
    }

    /**
     * 获取指定阶段的工具数量
     */
    public int getToolCountForPhase(CreationPhase phase) {
        return phaseTools.getOrDefault(phase, Collections.emptyList()).size();
    }
}
