package com.inkflow.module.agent.orchestration.chain;

import com.inkflow.common.exception.BusinessException;
import lombok.Getter;

/**
 * 链式执行异常
 * 当 Agent 链式执行失败时抛出
 * 
 * Requirements: 3.3
 *
 * @author zsg
 * @date 2025/12/17
 */
@Getter
public class ChainExecutionException extends BusinessException {
    
    private static final String ERROR_CODE = "CHAIN_EXECUTION_ERROR";
    
    /**
     * 失败的 Agent 名称
     */
    private final String failedAgentName;
    
    /**
     * 失败的 Agent 索引
     */
    private final int failedIndex;
    
    /**
     * 执行上下文
     */
    private final ChainExecutionContext context;
    
    public ChainExecutionException(String agentName, int index, Throwable cause) {
        super(ERROR_CODE, "Agent链式执行失败: " + agentName + " at index " + index, cause);
        this.failedAgentName = agentName;
        this.failedIndex = index;
        this.context = null;
    }
    
    public ChainExecutionException(String agentName, int index, ChainExecutionContext context, Throwable cause) {
        super(ERROR_CODE, "Agent链式执行失败: " + agentName + " at index " + index, cause);
        this.failedAgentName = agentName;
        this.failedIndex = index;
        this.context = context;
    }
    
    public ChainExecutionException(String agentName, int index, String message) {
        super(ERROR_CODE, "Agent链式执行失败: " + agentName + " at index " + index + " - " + message);
        this.failedAgentName = agentName;
        this.failedIndex = index;
        this.context = null;
    }
    
    public ChainExecutionException(String agentName, int index, ChainExecutionContext context, String message) {
        super(ERROR_CODE, "Agent链式执行失败: " + agentName + " at index " + index + " - " + message);
        this.failedAgentName = agentName;
        this.failedIndex = index;
        this.context = context;
    }
    
    /**
     * 获取失败详情
     */
    public String getFailureDetails() {
        StringBuilder sb = new StringBuilder();
        sb.append("Agent链式执行失败\n");
        sb.append("失败Agent: ").append(failedAgentName).append("\n");
        sb.append("失败索引: ").append(failedIndex).append("\n");
        
        if (context != null) {
            sb.append("执行摘要: ").append(context.getSummary()).append("\n");
            sb.append("成功执行: ").append(context.getSuccessCount()).append("\n");
            sb.append("失败执行: ").append(context.getFailureCount()).append("\n");
        }
        
        if (getCause() != null) {
            sb.append("原因: ").append(getCause().getMessage()).append("\n");
        }
        
        return sb.toString();
    }
}
