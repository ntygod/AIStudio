package com.inkflow.module.agent.workflow.chain;

import com.inkflow.module.agent.core.CapableAgent;
import com.inkflow.module.agent.dto.ChatRequest;

/**
 * 链步骤
 * 定义链式工作流中的单个步骤
 *
 */
public record ChainStep(
    ChainStepType type,
    CapableAgent<ChatRequest, String> agent,
    String description
) {
    /**
     * 创建 Agent 执行步骤
     * 
     * @param agent Agent 实例
     * @param description 步骤描述
     * @return ChainStep
     */
    public static ChainStep agent(CapableAgent<ChatRequest, String> agent, String description) {
        return new ChainStep(ChainStepType.AGENT, agent, description);
    }
    
    /**
     * 创建用户交互步骤
     * 
     * @param description 交互描述
     * @return ChainStep
     */
    public static ChainStep userInteraction(String description) {
        return new ChainStep(ChainStepType.USER_INTERACTION, null, description);
    }
    
    /**
     * 检查是否为 Agent 步骤
     */
    public boolean isAgentStep() {
        return type == ChainStepType.AGENT;
    }
    
    /**
     * 检查是否为用户交互步骤
     */
    public boolean isUserInteractionStep() {
        return type == ChainStepType.USER_INTERACTION;
    }
}
