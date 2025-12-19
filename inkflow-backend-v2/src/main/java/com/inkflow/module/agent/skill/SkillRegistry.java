package com.inkflow.module.agent.skill;

import com.inkflow.module.agent.core.BaseAgent;
import com.inkflow.module.project.entity.CreationPhase;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 技能注册表
 * 管理所有可用技能的注册表，支持运行时动态扩展
 *
 */
@Slf4j
@Component
public class SkillRegistry {
    
    private final ApplicationContext applicationContext;
    private final Map<String, SkillSlot> skills = new ConcurrentHashMap<>();
    
    public SkillRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
    
    /**
     * 自动发现并注册所有 SkillSlot 实现
     */
    @PostConstruct
    public void autoDiscover() {
        Map<String, SkillSlot> discoveredSkills = applicationContext.getBeansOfType(SkillSlot.class);
        
        discoveredSkills.values().forEach(skill -> {
            skills.put(skill.getId(), skill);
            log.info("注册技能: {} ({}), 优先级: {}", 
                skill.getName(), skill.getId(), skill.getPriority());
        });
        
        log.info("技能注册完成，共发现 {} 个技能", skills.size());
    }
    
    /**
     * 获取适用于指定 Agent 和阶段的技能列表
     * 按优先级降序排列
     *
     */
    public List<SkillSlot> getSkillsFor(Class<? extends BaseAgent<?, ?>> agentClass, CreationPhase phase) {
        return skills.values().stream()
            .filter(SkillSlot::isEnabled)
            .filter(skill -> isApplicable(skill, agentClass, phase))
            .sorted(Comparator.comparingInt(SkillSlot::getPriority).reversed())
            .collect(Collectors.toList());
    }
    
    /**
     * 获取被用户消息触发的技能列表
     * 
     * @param agentClass Agent 类型
     * @param phase 创作阶段
     * @param userMessage 用户消息
     * @return 被触发的技能列表（按优先级排序）
     */
    public List<SkillSlot> getTriggeredSkills(
            Class<? extends BaseAgent<?, ?>> agentClass, 
            CreationPhase phase, 
            String userMessage) {
        return skills.values().stream()
            .filter(SkillSlot::isEnabled)
            .filter(skill -> isApplicable(skill, agentClass, phase))
            .filter(skill -> isTriggeredBy(skill, userMessage))
            .sorted(Comparator.comparingInt(SkillSlot::getPriority).reversed())
            .collect(Collectors.toList());
    }
    
    /**
     * 运行时动态注册技能
     */
    public void register(SkillSlot skill) {
        skills.put(skill.getId(), skill);
        log.info("动态注册技能: {} ({})", skill.getName(), skill.getId());
    }
    
    /**
     * 注销技能
     */
    public void unregister(String skillId) {
        SkillSlot removed = skills.remove(skillId);
        if (removed != null) {
            log.info("注销技能: {} ({})", removed.getName(), skillId);
        }
    }
    
    /**
     * 获取所有已注册的技能
     */
    public Collection<SkillSlot> getAllSkills() {
        return Collections.unmodifiableCollection(skills.values());
    }
    
    /**
     * 根据 ID 获取技能
     */
    public Optional<SkillSlot> getSkill(String skillId) {
        return Optional.ofNullable(skills.get(skillId));
    }
    
    /**
     * 检查技能是否已注册
     */
    public boolean isRegistered(String skillId) {
        return skills.containsKey(skillId);
    }
    
    /**
     * 获取已注册技能数量
     */
    public int getSkillCount() {
        return skills.size();
    }
    
    /**
     * 获取启用的技能数量
     */
    public int getEnabledSkillCount() {
        return (int) skills.values().stream()
            .filter(SkillSlot::isEnabled)
            .count();
    }
    
    /**
     * 检查技能是否适用于指定的 Agent 和阶段
     */
    private boolean isApplicable(SkillSlot skill, Class<? extends BaseAgent<?, ?>> agentClass, CreationPhase phase) {
        // 检查 Agent 类型
        Set<Class<? extends BaseAgent<?, ?>>> applicableAgents = skill.getApplicableAgents();
        boolean agentMatch = applicableAgents.isEmpty() || 
            applicableAgents.stream().anyMatch(a -> a.isAssignableFrom(agentClass));
        
        // 检查创作阶段
        Set<CreationPhase> applicablePhases = skill.getApplicablePhases();
        boolean phaseMatch = applicablePhases.isEmpty() || 
            phase == null || 
            applicablePhases.contains(phase);
        
        return agentMatch && phaseMatch;
    }
    
    /**
     * 检查技能是否被用户消息触发
     */
    private boolean isTriggeredBy(SkillSlot skill, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return true; // 空消息时返回所有适用技能
        }
        
        Set<String> keywords = skill.getTriggerKeywords();
        if (keywords.isEmpty()) {
            return true; // 无关键词限制时默认触发
        }
        
        return keywords.stream().anyMatch(userMessage::contains);
    }
}
