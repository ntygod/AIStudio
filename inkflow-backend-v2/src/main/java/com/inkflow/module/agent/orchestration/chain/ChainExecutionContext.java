package com.inkflow.module.agent.orchestration.chain;

import com.inkflow.module.agent.orchestration.dto.AgentOutput;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 链式执行上下文
 * 管理 Agent 链式执行的状态和输出传递
 * 
 * Requirements: 3.2, 8.1
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
public class ChainExecutionContext {
    
    /**
     * 执行ID
     */
    @Getter
    private final UUID executionId;
    
    /**
     * 执行开始时间
     */
    @Getter
    private final LocalDateTime startedAt;
    
    /**
     * Agent 输出列表（按执行顺序）
     */
    private final List<AgentOutput> outputs = Collections.synchronizedList(new ArrayList<>());
    
    /**
     * 共享上下文数据
     */
    private final Map<String, Object> sharedContext = new ConcurrentHashMap<>();
    
    /**
     * 当前执行索引
     */
    @Getter
    private volatile int currentIndex = 0;
    
    /**
     * 是否已中断
     */
    @Getter
    private volatile boolean aborted = false;
    
    /**
     * 中断原因
     */
    @Getter
    private volatile String abortReason;

    /**
     * 创建新的执行上下文
     */
    public ChainExecutionContext() {
        this.executionId = UUID.randomUUID();
        this.startedAt = LocalDateTime.now();
    }
    
    /**
     * 添加 Agent 输出
     */
    public void addOutput(AgentOutput output) {
        if (aborted) {
            log.warn("尝试向已中断的执行上下文添加输出: executionId={}", executionId);
            return;
        }
        
        outputs.add(output);
        sharedContext.put(output.agentName() + "_output", output.content());
        currentIndex++;
        
        log.debug("添加Agent输出: agent={}, success={}, index={}", 
            output.agentName(), output.success(), currentIndex);
    }
    
    /**
     * 获取最后一个输出
     */
    public Optional<AgentOutput> getLastOutput() {
        if (outputs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(outputs.get(outputs.size() - 1));
    }
    
    /**
     * 获取指定 Agent 的输出
     */
    public Optional<AgentOutput> getOutputByAgent(String agentName) {
        return outputs.stream()
            .filter(o -> o.agentName().equals(agentName))
            .findFirst();
    }
    
    /**
     * 获取所有输出（不可变副本）
     */
    public List<AgentOutput> getAllOutputs() {
        return List.copyOf(outputs);
    }
    
    /**
     * 获取成功的输出数量
     */
    public long getSuccessCount() {
        return outputs.stream().filter(AgentOutput::success).count();
    }
    
    /**
     * 获取失败的输出数量
     */
    public long getFailureCount() {
        return outputs.stream().filter(o -> !o.success()).count();
    }
    
    /**
     * 设置共享上下文数据
     */
    public void setSharedData(String key, Object value) {
        sharedContext.put(key, value);
    }
    
    /**
     * 获取共享上下文数据
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getSharedData(String key) {
        return Optional.ofNullable((T) sharedContext.get(key));
    }
    
    /**
     * 获取共享上下文数据（带默认值）
     */
    @SuppressWarnings("unchecked")
    public <T> T getSharedDataOrDefault(String key, T defaultValue) {
        return (T) sharedContext.getOrDefault(key, defaultValue);
    }
    
    /**
     * 中断执行
     */
    public void abort(String reason) {
        this.aborted = true;
        this.abortReason = reason;
        log.warn("链式执行被中断: executionId={}, reason={}", executionId, reason);
    }
    
    /**
     * 构建下一个 Agent 的输入消息
     * 将前一个 Agent 的输出作为下一个 Agent 的输入
     */
    public String buildNextInput(String originalMessage) {
        Optional<AgentOutput> lastOutput = getLastOutput();
        
        if (lastOutput.isEmpty()) {
            return originalMessage;
        }
        
        AgentOutput previous = lastOutput.get();
        if (!previous.success() || previous.content() == null) {
            return originalMessage;
        }
        
        return String.format("""
            原始请求: %s
            
            前一个Agent(%s)的输出:
            %s
            
            请基于以上信息继续处理。
            """, originalMessage, previous.agentName(), previous.content());
    }
    
    /**
     * 获取执行摘要
     */
    public String getSummary() {
        return String.format(
            "ChainExecution[id=%s, agents=%d, success=%d, failed=%d, aborted=%s]",
            executionId.toString().substring(0, 8),
            outputs.size(),
            getSuccessCount(),
            getFailureCount(),
            aborted
        );
    }
    
    /**
     * 检查是否所有执行都成功
     */
    public boolean isAllSuccess() {
        return !aborted && !outputs.isEmpty() && outputs.stream().allMatch(AgentOutput::success);
    }
    
    /**
     * 获取最终结果内容
     */
    public String getFinalResult() {
        if (aborted) {
            return "执行被中断: " + abortReason;
        }
        
        Optional<AgentOutput> lastOutput = getLastOutput();
        if (lastOutput.isEmpty()) {
            return "没有执行结果";
        }
        
        AgentOutput output = lastOutput.get();
        if (output.success()) {
            return output.content();
        } else {
            return "执行失败: " + output.errorMessage();
        }
    }
}
