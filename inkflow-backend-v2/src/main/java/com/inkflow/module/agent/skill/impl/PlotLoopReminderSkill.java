package com.inkflow.module.agent.skill.impl;

import com.inkflow.module.agent.core.BaseAgent;
import com.inkflow.module.agent.impl.WriterAgent;
import com.inkflow.module.agent.skill.AbstractSkillSlot;
import com.inkflow.module.agent.skill.SkillContext;
import com.inkflow.module.project.entity.CreationPhase;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 伏笔提醒技能
 * 在写作过程中提醒作者注意伏笔的埋设和回收
 * 
 * Requirements: 4.3
 */
@Component
public class PlotLoopReminderSkill extends AbstractSkillSlot {
    
    private static final String ID = "plotloop-reminder-skill";
    private static final String NAME = "伏笔提醒";
    private static final String DESCRIPTION = "在写作过程中提醒伏笔的埋设和回收时机";
    private static final int DEFAULT_PRIORITY = 85;
    
    public PlotLoopReminderSkill() {
        super(DEFAULT_PRIORITY);
        // 添加触发关键词
        addTriggerKeyword("伏笔");
        addTriggerKeyword("埋线");
        addTriggerKeyword("回收");
        addTriggerKeyword("铺垫");
        addTriggerKeyword("暗示");
        addTriggerKeyword("悬念");
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
            **伏笔管理指南**
            
            1. 伏笔埋设原则
               - 自然融入情节，不刻意
               - 首次出现时不引起过多注意
               - 与当前场景有合理关联
            
            2. 伏笔类型
               - 物品伏笔：关键道具的提前出现
               - 人物伏笔：角色特征或行为的暗示
               - 事件伏笔：未来事件的预兆
               - 对话伏笔：看似无意的话语
            
            3. 回收时机
               - 不要太快回收，保持悬念
               - 不要太晚回收，读者可能遗忘
               - 重要伏笔可多次提及强化
            
            4. 回收方式
               - 直接揭示：明确点明伏笔含义
               - 间接呼应：让读者自己发现关联
               - 反转利用：伏笔指向意外方向
            
            5. 伏笔检查
               - 写作时注意已埋设的伏笔
               - 检查是否有遗漏未回收的伏笔
               - 确保伏笔与回收逻辑一致
            
            6. 注意事项
               - 避免伏笔过多导致混乱
               - 主线伏笔优先回收
               - 支线伏笔可适当留白
            """;
    }
}
