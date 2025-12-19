package com.inkflow.module.agent.workflow.chain;

import com.inkflow.module.agent.context.ContextBus;
import com.inkflow.module.agent.core.Intent;
import com.inkflow.module.agent.dto.ChatRequest;
import com.inkflow.module.agent.impl.PlannerAgent;
import com.inkflow.module.agent.impl.WriterAgent;
import com.inkflow.module.agent.orchestration.AgentOrchestrator;
import com.inkflow.module.agent.skill.PromptInjector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 大纲到章节工作流
 * 先使用 PlannerAgent 规划章节大纲，然后使用 WriterAgent 生成章节内容
 * 
 * 执行链: PlannerAgent → WriterAgent
 * 
 * Requirements: 14.2
 * 
 * @author zsg
 */
@Slf4j
@Component
public class OutlineToChapterWorkflow extends AbstractChainWorkflow {
    
    private final PlannerAgent plannerAgent;
    private final WriterAgent writerAgent;
    
    public OutlineToChapterWorkflow(
            AgentOrchestrator orchestrator,
            PromptInjector promptInjector,
            ContextBus contextBus,
            PlannerAgent plannerAgent,
            WriterAgent writerAgent) {
        super(orchestrator, promptInjector, contextBus);
        this.plannerAgent = plannerAgent;
        this.writerAgent = writerAgent;
    }
    
    @Override
    public String getName() {
        return "大纲到章节";
    }
    
    @Override
    public List<Intent> getSupportedIntents() {
        return List.of(Intent.OUTLINE_TO_CHAPTER);
    }
    
    /**
     * 定义链式执行步骤
     * 
     * 步骤:
     * 1. PlannerAgent - 规划章节大纲
     * 2. WriterAgent - 生成章节内容
     * 
     * 无用户交互，直接顺序执行
     */
    @Override
    protected List<ChainStep> getChainSteps(ChatRequest request) {
        return List.of(
            ChainStep.agent(plannerAgent, "规划章节大纲"),
            ChainStep.agent(writerAgent, "生成章节内容")
        );
    }
    
    // 无用户交互，使用默认实现（requiresUserInteraction() 返回 false）
}
