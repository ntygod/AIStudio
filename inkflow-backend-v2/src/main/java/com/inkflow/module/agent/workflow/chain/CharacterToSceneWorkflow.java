package com.inkflow.module.agent.workflow.chain;

import com.inkflow.module.agent.context.ContextBus;
import com.inkflow.module.agent.core.Intent;
import com.inkflow.module.agent.dto.ChatRequest;
import com.inkflow.module.agent.impl.CharacterAgent;
import com.inkflow.module.agent.impl.WriterAgent;
import com.inkflow.module.agent.orchestration.AgentOrchestrator;
import com.inkflow.module.agent.skill.PromptInjector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 角色到场景工作流
 * 先使用 CharacterAgent 设计角色详情，然后使用 WriterAgent 生成角色出场场景
 * 
 * 执行链: CharacterAgent → WriterAgent
 * 
 * @author zsg
 */
@Slf4j
@Component
public class CharacterToSceneWorkflow extends AbstractChainWorkflow {
    
    private final CharacterAgent characterAgent;
    private final WriterAgent writerAgent;
    
    public CharacterToSceneWorkflow(
            AgentOrchestrator orchestrator,
            PromptInjector promptInjector,
            ContextBus contextBus,
            CharacterAgent characterAgent,
            WriterAgent writerAgent) {
        super(orchestrator, promptInjector, contextBus);
        this.characterAgent = characterAgent;
        this.writerAgent = writerAgent;
    }
    
    @Override
    public String getName() {
        return "角色到场景";
    }
    
    @Override
    public List<Intent> getSupportedIntents() {
        return List.of(Intent.CHARACTER_TO_SCENE);
    }
    
    /**
     * 定义链式执行步骤
     * 
     * 步骤:
     * 1. CharacterAgent - 设计角色详情
     * 2. WriterAgent - 生成角色出场场景
     * 
     * 无用户交互，直接顺序执行
     */
    @Override
    protected List<ChainStep> getChainSteps(ChatRequest request) {
        return List.of(
            ChainStep.agent(characterAgent, "设计角色详情"),
            ChainStep.agent(writerAgent, "生成角色出场场景")
        );
    }
    
    // 无用户交互，使用默认实现（requiresUserInteraction() 返回 false）
}
