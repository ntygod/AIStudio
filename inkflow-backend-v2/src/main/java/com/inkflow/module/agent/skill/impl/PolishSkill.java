package com.inkflow.module.agent.skill.impl;

import com.inkflow.module.agent.core.BaseAgent;
import com.inkflow.module.agent.impl.WriterAgent;
import com.inkflow.module.agent.skill.AbstractSkillSlot;
import com.inkflow.module.agent.skill.SkillContext;
import com.inkflow.module.project.entity.CreationPhase;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 文本润色技能
 * 增强 Agent 的文本润色和优化能力
 * 
 * Requirements: 4.3
 */
@Component
public class PolishSkill extends AbstractSkillSlot {
    
    private static final String ID = "polish-skill";
    private static final String NAME = "文本润色";
    private static final String DESCRIPTION = "增强文本润色能力，优化语言表达和文笔质量";
    private static final int DEFAULT_PRIORITY = 60;
    
    public PolishSkill() {
        super(DEFAULT_PRIORITY);
        // 添加触发关键词
        addTriggerKeyword("润色");
        addTriggerKeyword("优化");
        addTriggerKeyword("修改");
        addTriggerKeyword("改进");
        addTriggerKeyword("文笔");
        addTriggerKeyword("语言");
        addTriggerKeyword("表达");
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
            **文本润色指南**
            
            1. 语言精炼
               - 删除冗余词语和重复表达
               - 用精确的词替代模糊的词
               - 避免过度使用形容词和副词
            
            2. 句式优化
               - 变化句子长度，避免单调
               - 适当使用排比、对仗等修辞
               - 调整句子结构增强节奏感
            
            3. 词汇丰富
               - 避免同一词语反复出现
               - 使用同义词增加变化
               - 选择最贴切的词语
            
            4. 风格一致
               - 保持全文风格统一
               - 符合已建立的叙事声音
               - 避免突兀的风格转换
            
            5. 节奏把控
               - 紧张场景用短句
               - 抒情场景可用长句
               - 段落长度适当变化
            
            6. 细节打磨
               - 检查标点符号使用
               - 确保时态一致
               - 修正语法错误
            
            7. 保留原意
               - 润色不改变原有含义
               - 保持作者的独特风格
               - 增强而非替代原有表达
            """;
    }
}
