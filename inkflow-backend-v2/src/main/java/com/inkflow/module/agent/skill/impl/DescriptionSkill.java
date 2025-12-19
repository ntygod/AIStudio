package com.inkflow.module.agent.skill.impl;

import com.inkflow.module.agent.core.BaseAgent;
import com.inkflow.module.agent.impl.WriterAgent;
import com.inkflow.module.agent.skill.AbstractSkillSlot;
import com.inkflow.module.agent.skill.SkillContext;
import com.inkflow.module.project.entity.CreationPhase;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 环境描写技能
 * 增强 Agent 的环境和场景描写能力
 * 
 * Requirements: 4.3
 */
@Component
public class DescriptionSkill extends AbstractSkillSlot {
    
    private static final String ID = "description-skill";
    private static final String NAME = "环境描写";
    private static final String DESCRIPTION = "增强环境描写能力，营造沉浸式的场景氛围";
    private static final int DEFAULT_PRIORITY = 65;
    
    public DescriptionSkill() {
        super(DEFAULT_PRIORITY);
        // 添加触发关键词
        addTriggerKeyword("环境");
        addTriggerKeyword("场景");
        addTriggerKeyword("描写");
        addTriggerKeyword("景色");
        addTriggerKeyword("氛围");
        addTriggerKeyword("背景");
        addTriggerKeyword("地点");
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
            **环境描写指南**
            
            1. 多感官描写
               - 视觉：颜色、光影、形状
               - 听觉：声音、音乐、寂静
               - 嗅觉：气味、香气、恶臭
               - 触觉：温度、质感、湿度
               - 味觉：适当场景中使用
            
            2. 氛围营造
               - 环境反映情节氛围
               - 天气配合情感基调
               - 细节暗示即将发生的事
            
            3. 动态描写
               - 环境不是静止的背景
               - 描写变化和运动
               - 角色与环境的互动
            
            4. 选择性聚焦
               - 不必描写所有细节
               - 聚焦于有意义的元素
               - 通过角色视角过滤
            
            5. 象征与隐喻
               - 环境元素承载象征意义
               - 自然景观反映内心状态
               - 建筑空间暗示社会关系
            
            6. 世界观一致性
               - 环境描写符合已建立的设定
               - 体现世界的独特性
               - 细节增强可信度
            """;
    }
}
