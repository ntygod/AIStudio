package com.inkflow.module.agent.skill;

/**
 * 可配置技能槽接口
 * 扩展 SkillSlot，支持运行时配置
 * 
 * Requirements: 17.1-17.5
 */
public interface ConfigurableSkillSlot extends SkillSlot {
    
    /**
     * 设置是否启用
     */
    void setEnabled(boolean enabled);
    
    /**
     * 设置优先级
     */
    void setPriority(int priority);
    
    /**
     * 添加触发关键词
     */
    void addTriggerKeyword(String keyword);
    
    /**
     * 移除触发关键词
     */
    void removeTriggerKeyword(String keyword);
    
    /**
     * 获取配置信息
     */
    SkillConfiguration getConfiguration();
    
    /**
     * 应用配置
     */
    void applyConfiguration(SkillConfiguration config);
}
