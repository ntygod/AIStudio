package com.inkflow.module.agent.skill.impl;

import com.inkflow.module.agent.core.BaseAgent;
import com.inkflow.module.agent.impl.WriterAgent;
import com.inkflow.module.agent.skill.AbstractSkillSlot;
import com.inkflow.module.agent.skill.SkillContext;
import com.inkflow.module.project.entity.CreationPhase;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 动作描写技能
 * 增强 Agent 的动作编排和空间描写能力
 * 迁移自 ai_bridge 模块的 ChoreographerAgent
 * 
 * Requirements: 8.3
 */
@Component
public class ActionSkill extends AbstractSkillSlot {
    
    private static final String ID = "action-skill";
    private static final String NAME = "动作描写";
    private static final String DESCRIPTION = "增强动作编排能力，设计场景中的动作、空间布局和视觉效果";
    private static final int DEFAULT_PRIORITY = 75;
    
    public ActionSkill() {
        super(DEFAULT_PRIORITY);
        // 添加触发关键词
        addTriggerKeyword("动作");
        addTriggerKeyword("打斗");
        addTriggerKeyword("战斗");
        addTriggerKeyword("追逐");
        addTriggerKeyword("姿态");
        addTriggerKeyword("空间");
        addTriggerKeyword("布局");
        addTriggerKeyword("位置");
        addTriggerKeyword("移动");
        addTriggerKeyword("跑");
        addTriggerKeyword("跳");
        addTriggerKeyword("攻击");
        addTriggerKeyword("防御");
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
            **动作描写指南**
            
            1. 动作设计原则
               - 动作要符合角色的身体条件和能力
               - 考虑角色的性格特点影响动作风格
               - 动作应有明确的目的和动机
               - 避免超出合理范围的夸张动作
            
            2. 空间布局要点
               - 清晰描述场景的空间结构
               - 标明角色之间的相对位置
               - 利用环境元素增强场景感
               - 空间描述要便于读者想象
            
            3. 动作节奏控制
               - 快节奏：短句、动词密集、紧张感
               - 慢节奏：长句、细节描写、氛围营造
               - 节奏变化：张弛有度，避免单调
               - 关键动作可适当放慢描写
            
            4. 视觉效果呈现
               - 使用具体的动作动词
               - 描写动作的轨迹和力度
               - 加入声音、光影等感官细节
               - 通过旁观者视角增强画面感
            
            5. 动作连贯性
               - 动作之间要有逻辑衔接
               - 注意身体部位的协调
               - 考虑动作的物理可行性
               - 避免"瞬移"式的位置变化
            
            6. 打斗场景技巧
               - 攻防节奏要清晰
               - 展现双方的策略和应变
               - 伤害和疲劳要有累积
               - 环境互动增加变数
            """;
    }
}
