package com.inkflow.module.agent.workflow;

import com.inkflow.module.agent.core.Intent;
import com.inkflow.module.agent.dto.ChatRequest;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 工作流接口
 * 定义工作流的基本行为：名称、支持的意图、执行方法
 *
 */
public interface Workflow {
    
    /**
     * 获取工作流名称
     * 
     * @return 工作流名称
     */
    String getName();
    
    /**
     * 获取支持的意图列表
     * 
     * @return 支持的意图列表
     */
    List<Intent> getSupportedIntents();
    
    /**
     * 执行工作流
     * 
     * @param request 聊天请求
     * @return SSE 事件流
     */
    Flux<ServerSentEvent<String>> execute(ChatRequest request);
    
    /**
     * 检查是否支持指定意图
     * 
     * @param intent 意图
     * @return 是否支持
     */
    default boolean supports(Intent intent) {
        return getSupportedIntents().contains(intent);
    }
    
    /**
     * 获取工作流类型
     * 
     * @return 工作流类型
     */
    default WorkflowType getType() {
        return WorkflowType.SIMPLE_AGENT;
    }
}
