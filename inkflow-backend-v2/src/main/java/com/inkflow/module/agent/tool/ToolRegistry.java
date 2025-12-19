package com.inkflow.module.agent.tool;

import com.inkflow.module.project.entity.CreationPhase;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 工具注册表
 * 自动发现和管理所有 @Tool 注解的方法
 * 
 * Requirements: 14.1-14.5
 */
@Slf4j
@Component
public class ToolRegistry {

    private final ApplicationContext applicationContext;
    
    /**
     * 工具信息映射: toolName -> ToolInfo
     */
    private final Map<String, ToolInfo> tools = new ConcurrentHashMap<>();
    
    /**
     * 阶段工具映射: phase -> toolNames
     */
    private final Map<CreationPhase, Set<String>> phaseTools = new ConcurrentHashMap<>();
    
    /**
     * 工具调用日志
     */
    private final List<ToolInvocationRecord> invocationLog = Collections.synchronizedList(new ArrayList<>());

    public ToolRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void init() {
        discoverTools();
        log.info("[ToolRegistry] 初始化完成，发现 {} 个工具", tools.size());
    }

    /**
     * 自动发现所有 @Tool 注解的方法
     */
    private void discoverTools() {
        // 获取所有 Bean
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        
        for (String beanName : beanNames) {
            try {
                Object bean = applicationContext.getBean(beanName);
                Class<?> beanClass = bean.getClass();
                
                // 检查所有方法
                for (Method method : beanClass.getDeclaredMethods()) {
                    Tool toolAnnotation = method.getAnnotation(Tool.class);
                    if (toolAnnotation != null) {
                        registerTool(bean, method, toolAnnotation);
                    }
                }
            } catch (Exception e) {
                // 忽略无法获取的 Bean
            }
        }
    }

    /**
     * 注册工具
     */
    private void registerTool(Object bean, Method method, Tool toolAnnotation) {
        String toolName = method.getName();
        String description = toolAnnotation.description();
        
        ToolInfo toolInfo = new ToolInfo(
            toolName,
            description,
            bean,
            method,
            bean.getClass().getSimpleName(),
            inferApplicablePhases(toolName, description)
        );
        
        tools.put(toolName, toolInfo);
        
        // 按阶段分类
        for (CreationPhase phase : toolInfo.applicablePhases()) {
            phaseTools.computeIfAbsent(phase, k -> ConcurrentHashMap.newKeySet())
                    .add(toolName);
        }
        
        log.debug("[ToolRegistry] 注册工具: {} - {}", toolName, description);
    }

    /**
     * 推断工具适用的创作阶段
     */
    private Set<CreationPhase> inferApplicablePhases(String toolName, String description) {
        Set<CreationPhase> phases = new HashSet<>();
        String combined = (toolName + " " + description).toLowerCase();
        
        // 根据关键词推断
        if (combined.contains("search") || combined.contains("检索") || combined.contains("查询")) {
            phases.addAll(Arrays.asList(CreationPhase.values()));
        }
        if (combined.contains("character") || combined.contains("角色")) {
            phases.add(CreationPhase.CHARACTER);
            phases.add(CreationPhase.WRITING);
        }
        if (combined.contains("world") || combined.contains("设定") || combined.contains("wiki")) {
            phases.add(CreationPhase.WORLDBUILDING);
            phases.add(CreationPhase.WRITING);
        }
        if (combined.contains("plot") || combined.contains("伏笔") || combined.contains("大纲")) {
            phases.add(CreationPhase.OUTLINE);
            phases.add(CreationPhase.WRITING);
        }
        if (combined.contains("style") || combined.contains("风格")) {
            phases.add(CreationPhase.WRITING);
            phases.add(CreationPhase.REVISION);
        }
        if (combined.contains("check") || combined.contains("检查") || combined.contains("preflight")) {
            phases.add(CreationPhase.WRITING);
            phases.add(CreationPhase.REVISION);
        }
        if (combined.contains("create") || combined.contains("创建") || combined.contains("crud")) {
            phases.addAll(Arrays.asList(CreationPhase.values()));
        }
        
        // 如果没有匹配，默认适用于所有阶段
        if (phases.isEmpty()) {
            phases.addAll(Arrays.asList(CreationPhase.values()));
        }
        
        return phases;
    }

    /**
     * 获取所有工具
     */
    public List<ToolInfo> getAllTools() {
        return new ArrayList<>(tools.values());
    }

    /**
     * 获取指定阶段的工具
     */
    public List<ToolInfo> getToolsForPhase(CreationPhase phase) {
        Set<String> toolNames = phaseTools.getOrDefault(phase, Set.of());
        return toolNames.stream()
                .map(tools::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 获取工具信息
     */
    public Optional<ToolInfo> getTool(String toolName) {
        return Optional.ofNullable(tools.get(toolName));
    }

    /**
     * 记录工具调用
     */
    public void logInvocation(String toolName, String agentName, Map<String, Object> params, 
                              String result, long durationMs, boolean success) {
        ToolInvocationRecord record = new ToolInvocationRecord(
            toolName,
            agentName,
            params,
            result,
            durationMs,
            success,
            System.currentTimeMillis()
        );
        invocationLog.add(record);
        
        // 保持日志大小在合理范围
        if (invocationLog.size() > 1000) {
            invocationLog.subList(0, 500).clear();
        }
        
        log.debug("[ToolRegistry] 工具调用: {} by {} - {}ms - {}", 
                toolName, agentName, durationMs, success ? "成功" : "失败");
    }

    /**
     * 获取最近的调用记录
     */
    public List<ToolInvocationRecord> getRecentInvocations(int limit) {
        int size = invocationLog.size();
        int start = Math.max(0, size - limit);
        return new ArrayList<>(invocationLog.subList(start, size));
    }

    /**
     * 获取工具调用统计
     */
    public Map<String, ToolStats> getToolStats() {
        Map<String, ToolStats> stats = new HashMap<>();
        
        for (ToolInvocationRecord record : invocationLog) {
            stats.computeIfAbsent(record.toolName(), k -> new ToolStats(k, 0, 0, 0))
                    .update(record);
        }
        
        return stats;
    }

    /**
     * 工具信息
     */
    public record ToolInfo(
        String name,
        String description,
        Object bean,
        Method method,
        String beanClassName,
        Set<CreationPhase> applicablePhases
    ) {}

    /**
     * 工具调用记录
     */
    public record ToolInvocationRecord(
        String toolName,
        String agentName,
        Map<String, Object> params,
        String result,
        long durationMs,
        boolean success,
        long timestamp
    ) {}

    /**
     * 工具统计
     */
    public static class ToolStats {
        private final String toolName;
        private int totalCalls;
        private int successCalls;
        private long totalDurationMs;

        public ToolStats(String toolName, int totalCalls, int successCalls, long totalDurationMs) {
            this.toolName = toolName;
            this.totalCalls = totalCalls;
            this.successCalls = successCalls;
            this.totalDurationMs = totalDurationMs;
        }

        public void update(ToolInvocationRecord record) {
            totalCalls++;
            if (record.success()) {
                successCalls++;
            }
            totalDurationMs += record.durationMs();
        }

        public String getToolName() { return toolName; }
        public int getTotalCalls() { return totalCalls; }
        public int getSuccessCalls() { return successCalls; }
        public double getSuccessRate() { return totalCalls > 0 ? (double) successCalls / totalCalls : 0; }
        public double getAvgDurationMs() { return totalCalls > 0 ? (double) totalDurationMs / totalCalls : 0; }
    }
}
