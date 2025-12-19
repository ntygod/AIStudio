package com.inkflow.module.agent.skill;

import com.inkflow.module.agent.core.BaseAgent;
import com.inkflow.module.project.entity.CreationPhase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 提示词注入器
 * 将技能动态注入到 Agent 系统提示词
 * 
 * Requirements: 17.2-17.4
 */
@Slf4j
@Component
public class PromptInjector {
    
    private final SkillRegistry skillRegistry;
    
    public PromptInjector(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }
    
    /**
     * 构建增强的系统提示词
     * 将适用的技能片段按优先级顺序注入到基础提示词中
     * 
     * Requirements: 17.2-17.4
     * 
     * @param baseSystemPrompt 基础系统提示词
     * @param agentClass Agent 类型
     * @param context 技能上下文
     * @return 增强后的系统提示词
     */
    public String buildEnhancedSystemPrompt(
            String baseSystemPrompt,
            Class<? extends BaseAgent<?, ?>> agentClass,
            SkillContext context) {
        
        // 获取适用的技能（已按优先级排序）
        List<SkillSlot> applicableSkills = skillRegistry.getSkillsFor(
            agentClass, 
            context.currentPhase()
        );
        
        if (applicableSkills.isEmpty()) {
            log.debug("无适用技能，返回原始提示词");
            return baseSystemPrompt;
        }
        
        // 构建增强提示词
        StringBuilder enhanced = new StringBuilder(baseSystemPrompt);
        enhanced.append("\n\n=== 已激活的技能 ===\n");
        
        int injectedCount = 0;
        for (SkillSlot skill : applicableSkills) {
            try {
                String fragment = skill.generatePromptFragment(context);
                if (fragment != null && !fragment.isBlank()) {
                    enhanced.append("\n### ").append(skill.getName()).append("\n");
                    enhanced.append(fragment);
                    enhanced.append("\n");
                    injectedCount++;
                    log.debug("注入技能: {} (优先级: {})", skill.getName(), skill.getPriority());
                }
            } catch (Exception e) {
                log.warn("技能 {} 生成提示词片段失败: {}", skill.getId(), e.getMessage());
            }
        }
        
        if (injectedCount == 0) {
            return baseSystemPrompt;
        }
        
        log.info("已注入 {} 个技能到 {} 的系统提示词", injectedCount, agentClass.getSimpleName());
        return enhanced.toString();
    }
    
    /**
     * 自动选择技能（基于关键词匹配）
     * 
     * @param agentClass Agent 类型
     * @param context 技能上下文
     * @return 被触发的技能列表
     */
    public List<SkillSlot> autoSelectSkills(
            Class<? extends BaseAgent<?, ?>> agentClass,
            SkillContext context) {
        
        return skillRegistry.getTriggeredSkills(
            agentClass,
            context.currentPhase(),
            context.userMessage()
        );
    }
    
    /**
     * 构建仅包含被触发技能的增强提示词
     * 
     * @param baseSystemPrompt 基础系统提示词
     * @param agentClass Agent 类型
     * @param context 技能上下文
     * @return 增强后的系统提示词
     */
    public String buildTriggeredPrompt(
            String baseSystemPrompt,
            Class<? extends BaseAgent<?, ?>> agentClass,
            SkillContext context) {
        
        List<SkillSlot> triggeredSkills = autoSelectSkills(agentClass, context);
        
        if (triggeredSkills.isEmpty()) {
            return baseSystemPrompt;
        }
        
        StringBuilder enhanced = new StringBuilder(baseSystemPrompt);
        enhanced.append("\n\n=== 根据您的请求激活的技能 ===\n");
        
        for (SkillSlot skill : triggeredSkills) {
            try {
                String fragment = skill.generatePromptFragment(context);
                if (fragment != null && !fragment.isBlank()) {
                    enhanced.append("\n### ").append(skill.getName()).append("\n");
                    enhanced.append(fragment);
                    enhanced.append("\n");
                }
            } catch (Exception e) {
                log.warn("技能 {} 生成提示词片段失败: {}", skill.getId(), e.getMessage());
            }
        }
        
        return enhanced.toString();
    }
    
    /**
     * 获取技能摘要信息（用于调试和日志）
     */
    public String getSkillSummary(
            Class<? extends BaseAgent<?, ?>> agentClass,
            CreationPhase phase) {
        
        List<SkillSlot> skills = skillRegistry.getSkillsFor(agentClass, phase);
        
        if (skills.isEmpty()) {
            return "无适用技能";
        }
        
        return skills.stream()
            .map(s -> String.format("%s(P%d)", s.getName(), s.getPriority()))
            .collect(Collectors.joining(", "));
    }
}
