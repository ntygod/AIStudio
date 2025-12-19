package com.inkflow.module.agent.skill.impl;

import com.inkflow.module.agent.core.BaseAgent;
import com.inkflow.module.agent.impl.WriterAgent;
import com.inkflow.module.agent.skill.AbstractSkillSlot;
import com.inkflow.module.agent.skill.SkillContext;
import com.inkflow.module.project.entity.CreationPhase;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 对话生成技能
 * 增强 Agent 的对话写作能力
 * 
 * Requirements: 4.3
 */
@Component
public class DialogueSkill extends AbstractSkillSlot {
    
    private static final String ID = "dialogue-skill";
    private static final String NAME = "对话生成";
    private static final String DESCRIPTION = "增强对话写作能力，使角色对话更加生动自然";
    private static final int DEFAULT_PRIORITY = 80;
    
    public DialogueSkill() {
        super(DEFAULT_PRIORITY);
        // 添加触发关键词
        addTriggerKeyword("对话");
        addTriggerKeyword("说");
        addTriggerKeyword("问");
        addTriggerKeyword("答");
        addTriggerKeyword("交谈");
        addTriggerKeyword("聊天");
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
            **对话写作指南**
            
            1. 角色声音区分
               - 每个角色应有独特的说话方式和用词习惯
               - 根据角色背景、性格、教育程度调整语言风格
               - 避免所有角色说话方式雷同
            
            2. 对话节奏控制
               - 短句用于紧张、激动的场景
               - 长句用于沉思、解释的场景
               - 适当使用省略号、破折号表达情绪
            
            3. 潜台词运用
               - 角色不必直接说出想法
               - 通过言外之意传达真实情感
               - 利用对话推动情节和揭示性格
            
            4. 对话标签多样化
               - 避免过度使用"说"
               - 使用动作描写代替对话标签
               - 适时省略对话标签
            
            5. 冲突与张力
               - 对话中体现角色间的矛盾
               - 通过对话升级或缓解冲突
               - 保持对话的戏剧性
            """;
    }
}
