package com.inkflow.module.agent.workflow.chain;

import com.inkflow.module.agent.context.ContextBus;
import com.inkflow.module.agent.core.Intent;
import com.inkflow.module.agent.dto.ChatRequest;
import com.inkflow.module.agent.impl.WorldBuilderAgent;
import com.inkflow.module.agent.impl.WriterAgent;
import com.inkflow.module.agent.orchestration.AgentOrchestrator;
import com.inkflow.module.agent.orchestration.chain.ChainExecutionContext;
import com.inkflow.module.agent.skill.PromptInjector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 头脑风暴 + 扩写工作流
 * 先使用 WorldBuilderAgent 生成多个创意方案，用户选择后使用 WriterAgent 扩写
 * 执行链: WorldBuilderAgent → [用户选择] → WriterAgent
 * 
 * @author zsg
 */
@Slf4j
@Component
public class BrainstormExpandWorkflow extends AbstractChainWorkflow {
    
    private final WorldBuilderAgent worldBuilderAgent;
    private final WriterAgent writerAgent;
    
    public BrainstormExpandWorkflow(
            AgentOrchestrator orchestrator,
            PromptInjector promptInjector,
            ContextBus contextBus,
            WorldBuilderAgent worldBuilderAgent,
            WriterAgent writerAgent) {
        super(orchestrator, promptInjector, contextBus);
        this.worldBuilderAgent = worldBuilderAgent;
        this.writerAgent = writerAgent;
    }
    
    @Override
    public String getName() {
        return "头脑风暴扩写";
    }
    
    @Override
    public List<Intent> getSupportedIntents() {
        return List.of(Intent.BRAINSTORM_AND_EXPAND);
    }

    
    /**
     * 定义链式执行步骤
     * 
     * 步骤:
     * 1. WorldBuilderAgent - 生成多个创意方案
     * 2. 用户交互 - 用户选择方案
     * 3. WriterAgent - 扩写选中的方案
     */
    @Override
    protected List<ChainStep> getChainSteps(ChatRequest request) {
        return List.of(
            ChainStep.agent(worldBuilderAgent, "生成多个创意方案"),
            ChainStep.userInteraction("用户选择方案"),
            ChainStep.agent(writerAgent, "扩写选中的方案")
        );
    }
    
    /**
     * 启用用户交互
     */
    @Override
    protected boolean requiresUserInteraction() {
        return true;
    }
    
    /**
     * 在第一个 Agent（WorldBuilderAgent）后暂停等待用户选择
     */
    @Override
    protected int getUserInteractionAfterStep() {
        return 0;
    }
    
    /**
     * 构建交互选项
     * 解析 WorldBuilderAgent 的输出，提取创意方案供用户选择
     * 
     * @param context 链式执行上下文
     * @return 交互选项的 JSON 字符串
     */
    @Override
    protected String buildInteractionOptions(ChainExecutionContext context) {
        if (context == null) {
            return """
                {
                    "type": "selection",
                    "message": "请选择一个创意方案进行扩写",
                    "options": []
                }
                """;
        }
        
        String brainstormOutput = context.getFinalResult();
        
        // 返回结构化的交互选项
        // 前端可以解析 content 字段，提取具体的方案选项
        return String.format("""
            {
                "type": "selection",
                "message": "请选择一个创意方案进行扩写，或输入您的修改意见",
                "content": %s,
                "allowCustomInput": true,
                "customInputPlaceholder": "输入您的修改意见或选择编号..."
            }
            """, escapeJsonValue(brainstormOutput));
    }
    
    /**
     * 简单的 JSON 值转义
     */
    private String escapeJsonValue(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            + "\"";
    }
}
