package com.inkflow.module.agent.skill;

import com.inkflow.module.agent.core.BaseAgent;
import com.inkflow.module.project.entity.CreationPhase;

import java.util.HashSet;
import java.util.Set;

/**
 * 技能槽抽象基类
 * 提供 SkillSlot 的通用实现
 * 
 * Requirements: 17.1-17.5
 */
public abstract class AbstractSkillSlot implements ConfigurableSkillSlot {
    
    private boolean enabled = true;
    private int priority;
    private final Set<String> triggerKeywords = new HashSet<>();
    
    protected AbstractSkillSlot(int defaultPriority) {
        this.priority = defaultPriority;
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    @Override
    public int getPriority() {
        return priority;
    }
    
    @Override
    public void setPriority(int priority) {
        this.priority = priority;
    }
    
    @Override
    public Set<String> getTriggerKeywords() {
        return Set.copyOf(triggerKeywords);
    }
    
    @Override
    public void addTriggerKeyword(String keyword) {
        triggerKeywords.add(keyword);
    }
    
    @Override
    public void removeTriggerKeyword(String keyword) {
        triggerKeywords.remove(keyword);
    }
    
    @Override
    public SkillConfiguration getConfiguration() {
        return new SkillConfiguration(
            getId(),
            enabled,
            priority,
            Set.copyOf(triggerKeywords)
        );
    }
    
    @Override
    public void applyConfiguration(SkillConfiguration config) {
        if (config.skillId().equals(getId())) {
            this.enabled = config.enabled();
            this.priority = config.priority();
            this.triggerKeywords.clear();
            this.triggerKeywords.addAll(config.triggerKeywords());
        }
    }
    
    /**
     * 检查是否适用于指定的 Agent 和阶段
     */
    public boolean isApplicable(Class<? extends BaseAgent<?, ?>> agentClass, CreationPhase phase) {
        if (!enabled) {
            return false;
        }
        boolean agentMatch = getApplicableAgents().isEmpty() || 
                            getApplicableAgents().stream()
                                .anyMatch(a -> a.isAssignableFrom(agentClass));
        boolean phaseMatch = getApplicablePhases().isEmpty() || 
                            getApplicablePhases().contains(phase);
        return agentMatch && phaseMatch;
    }
    
    /**
     * 检查用户消息是否触发此技能
     */
    public boolean isTriggeredBy(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        if (triggerKeywords.isEmpty()) {
            return true; // 无关键词限制时默认触发
        }
        return triggerKeywords.stream()
            .anyMatch(userMessage::contains);
    }
}
