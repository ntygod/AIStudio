package com.inkflow.module.agent.lazy;

import com.inkflow.module.agent.core.CapableAgent;
import com.inkflow.module.agent.core.ExecutionMode;
import com.inkflow.module.agent.core.Intent;
import com.inkflow.module.agent.dto.ChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 懒执行管理器
 * 管理工具类 Agent 的按需触发
 *
 */
@Slf4j
@Component
public class LazyExecutionManager {

    /**
     * 懒执行 Agent 集合
     */
    private final Map<String, CapableAgent<ChatRequest, ?>> lazyAgents = new ConcurrentHashMap<>();

    /**
     * 懒执行 Agent 对应的意图
     */
    private static final Set<Intent> LAZY_INTENTS = Set.of(
        Intent.GENERATE_NAME,
        Intent.SUMMARIZE,
        Intent.EXTRACT_ENTITY
    );

    /**
     * 注册懒执行 Agent
     */
    public void register(CapableAgent<ChatRequest, ?> agent) {
        if (agent.getExecutionMode() == ExecutionMode.LAZY) {
            lazyAgents.put(agent.getName(), agent);
            log.info("注册懒执行 Agent: {}", agent.getName());
        }
    }

    /**
     * 注销懒执行 Agent
     */
    public void unregister(String agentName) {
        lazyAgents.remove(agentName);
        log.info("注销懒执行 Agent: {}", agentName);
    }

    /**
     * 判断是否应该执行
     * 懒执行 Agent 只在明确请求时执行
     */
    public boolean shouldExecute(Intent intent) {
        return LAZY_INTENTS.contains(intent);
    }

    /**
     * 判断意图是否对应懒执行 Agent
     */
    public boolean isLazyIntent(Intent intent) {
        return LAZY_INTENTS.contains(intent);
    }

    /**
     * 获取懒执行 Agent
     */
    public Optional<CapableAgent<ChatRequest, ?>> getAgent(String agentName) {
        return Optional.ofNullable(lazyAgents.get(agentName));
    }

    /**
     * 根据意图获取懒执行 Agent
     */
    public Optional<CapableAgent<ChatRequest, ?>> getAgentForIntent(Intent intent) {
        return lazyAgents.values().stream()
            .filter(agent -> agent.supportsIntent(intent))
            .findFirst();
    }

    /**
     * 手动触发懒执行 Agent
     */
    @SuppressWarnings("unchecked")
    public <O> O triggerExecution(String agentName, ChatRequest request) {
        CapableAgent<ChatRequest, ?> agent = lazyAgents.get(agentName);
        if (agent == null) {
            throw new IllegalArgumentException("未找到懒执行 Agent: " + agentName);
        }
        
        log.info("手动触发懒执行 Agent: {}", agentName);
        return (O) agent.execute(request);
    }

    /**
     * 获取所有懒执行 Agent 名称
     */
    public List<String> getRegisteredAgentNames() {
        return List.copyOf(lazyAgents.keySet());
    }

    /**
     * 获取所有懒执行 Agent 的能力描述
     */
    public Map<String, String> getAgentCapabilities() {
        return lazyAgents.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().getDescription()
            ));
    }

    /**
     * 检查 Agent 是否已注册
     */
    public boolean isRegistered(String agentName) {
        return lazyAgents.containsKey(agentName);
    }
}
