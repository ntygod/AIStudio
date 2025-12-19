package com.inkflow.module.agent.skill.impl;

import com.inkflow.module.agent.core.BaseAgent;
import com.inkflow.module.agent.impl.WriterAgent;
import com.inkflow.module.agent.skill.AbstractSkillSlot;
import com.inkflow.module.agent.skill.SkillContext;
import com.inkflow.module.project.entity.CreationPhase;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 心理描写技能
 * 增强 Agent 的心理描写能力
 * 
 * Requirements: 4.3
 */
@Component
public class PsychologySkill extends AbstractSkillSlot {
    
    private static final String ID = "psychology-skill";
    private static final String NAME = "心理描写";
    private static final String DESCRIPTION = "增强心理描写能力，深入刻画角色内心世界";
    private static final int DEFAULT_PRIORITY = 70;
    
    public PsychologySkill() {
        super(DEFAULT_PRIORITY);
        // 添加触发关键词
        addTriggerKeyword("心理");
        addTriggerKeyword("想法");
        addTriggerKeyword("感受");
        addTriggerKeyword("内心");
        addTriggerKeyword("情感");
        addTriggerKeyword("思考");
        addTriggerKeyword("回忆");
    }
    
    @Override
    public String getId() {
        return ID;
    }
    
    @Override
    public String getName() {
        return NAME;
    }
    
    @Override
    public String getDescription() {
        return DESCRIPTION;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public Set<Class<? extends BaseAgent<?, ?>>> getApplicableAgents() {
        return Set.of((Class<? extends BaseAgent<?, ?>>) (Class<?>) WriterAgent.class);
    }
    
    @Override
    public Set<CreationPhase> getApplicablePhases() {
        return Set.of(CreationPhase.WRITING, CreationPhase.REVISION);
    }
    
    @Override
    public String generatePromptFragment(SkillContext context) {
        return """
            **心理描写指南**
            
            1. 内心独白
               - 使用第一人称或自由间接引语
               - 展现角色的思维过程
               - 揭示隐藏的动机和恐惧
            
            2. 情感层次
               - 避免单一情感，展现复杂性
               - 情感应有变化和发展
               - 矛盾情感增加真实感
            
            3. 潜意识表达
               - 通过梦境、幻想展现潜意识
               - 使用象征和隐喻
               - 身体反应反映心理状态
            
            4. 记忆与联想
               - 当前事件触发过去记忆
               - 联想揭示角色背景
               - 记忆碎片增加神秘感
            
            5. 心理防御机制
               - 展现角色的自我保护
               - 否认、投射、合理化等
               - 防御机制的崩溃带来转折
            
            6. 成长与变化
               - 心理描写应体现角色弧线
               - 关键事件改变心理状态
               - 内心成长与外在行动呼应
            """;
    }
}
