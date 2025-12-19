package com.inkflow.module.agent.workflow.chain;

/**
 * 链步骤类型
 *
 */
public enum ChainStepType {
    /**
     * Agent 执行步骤
     */
    AGENT,
    
    /**
     * 用户交互步骤（暂停等待用户选择）
     */
    USER_INTERACTION
}
