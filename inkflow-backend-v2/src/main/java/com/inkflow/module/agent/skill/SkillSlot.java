package com.inkflow.module.agent.skill;

import com.inkflow.module.agent.core.BaseAgent;
import com.inkflow.module.project.entity.CreationPhase;

import java.util.Set;

/**
 * 技能槽接口
 * 定义可动态注入到 Agent 的技能单元
 * 通过 Prompt Injection 方式增强 Agent 能力
 * 
 * Requirements: 17.1-17.5
 */
public interface SkillSlot {
    
    /**
     * 获取技能唯一标识
     */
    String getId();
    
    /**
     * 获取技能名称
     */
    String getName();
    
    /**
     * 获取技能描述
     */
    String getDescription();
    
    /**
     * 获取适用的 Agent 类型
     */
    Set<Class<? extends BaseAgent<?, ?>>> getApplicableAgents();
    
    /**
     * 获取适用的创作阶段
     */
    Set<CreationPhase> getApplicablePhases();
    
    /**
     * 生成提示词片段
     * 
     * @param context 技能上下文
     * @return 要注入到系统提示词的片段
     */
    String generatePromptFragment(SkillContext context);
    
    /**
     * 获取优先级（数值越大优先级越高）
     */
    int getPriority();
    
    /**
     * 是否启用
     */
    boolean isEnabled();
    
    /**
     * 获取触发关键词（用于自动选择）
     */
    Set<String> getTriggerKeywords();
}
