package com.inkflow.module.agent.skill;

import java.util.Set;

/**
 * 技能配置
 * 存储技能的运行时配置信息
 * 
 * Requirements: 17.1-17.5
 */
public record SkillConfiguration(
    String skillId,
    boolean enabled,
    int priority,
    Set<String> triggerKeywords
) {
    
    /**
     * 创建默认配置
     */
    public static SkillConfiguration defaultConfig(String skillId) {
        return new SkillConfiguration(
            skillId,
            true,
            50,
            Set.of()
        );
    }
    
    /**
     * 创建禁用配置
     */
    public static SkillConfiguration disabled(String skillId) {
        return new SkillConfiguration(
            skillId,
            false,
            0,
            Set.of()
        );
    }
    
    /**
     * 创建带优先级的配置
     */
    public SkillConfiguration withPriority(int newPriority) {
        return new SkillConfiguration(skillId, enabled, newPriority, triggerKeywords);
    }
    
    /**
     * 创建启用/禁用的配置
     */
    public SkillConfiguration withEnabled(boolean newEnabled) {
        return new SkillConfiguration(skillId, newEnabled, priority, triggerKeywords);
    }
    
    /**
     * 创建带关键词的配置
     */
    public SkillConfiguration withKeywords(Set<String> newKeywords) {
        return new SkillConfiguration(skillId, enabled, priority, newKeywords);
    }
}
