package com.inkflow.module.agent.orchestration;

import com.inkflow.module.agent.context.ContextBus;
import com.inkflow.module.agent.context.ContextEvent;
import com.inkflow.module.agent.core.CapableAgent;
import com.inkflow.module.agent.dto.ChatRequest;
import com.inkflow.module.agent.orchestration.chain.ChainExecutionContext;
import com.inkflow.module.agent.orchestration.chain.ChainExecutionException;
import com.inkflow.module.agent.orchestration.dto.AgentOutput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Agent 编排器
 * 负责 Agent 的并行执行、结果聚合和链式执行
 * 使用 Virtual Threads 实现高效并发
 *
 * @author zsg
 * @date 2025/12/15
 */
@Slf4j
@Component
public class AgentOrchestrator {

    private final ContextBus contextBus;
    private final ApplicationEventPublisher eventPublisher;
    
    public AgentOrchestrator(ContextBus contextBus, ApplicationEventPublisher eventPublisher) {
        this.contextBus = contextBus;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Virtual Thread 执行器
     */
    private static final java.util.concurrent.ExecutorService VIRTUAL_EXECUTOR =
            Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 默认超时时间
     */
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    /**
     * 最大重试次数
     */
    private static final int MAX_RETRIES = 3;

    // ==================== 基础执行方法 ====================

    /**
     * 直接执行指定 Agent（流式）
     */
    public Flux<String> executeAgent(CapableAgent<ChatRequest, ?> agent, ChatRequest request) {
        return executeWithRetry(agent, request, MAX_RETRIES);
    }


    // ==================== 并行执行方法 ====================

    /**
     * 并行执行多个 Agent
     * 使用 Virtual Threads + CompletableFuture
     *
     */
    public <T> CompletableFuture<List<T>> executeParallel(
            List<CapableAgent<ChatRequest, T>> agents,
            ChatRequest request) {
        
        log.info("并行执行 {} 个 Agent", agents.size());
        
        List<CompletableFuture<T>> futures = agents.stream()
                .map(agent -> CompletableFuture.supplyAsync(
                        () -> executeWithRetrySync(agent, request, MAX_RETRIES),
                        VIRTUAL_EXECUTOR
                ))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList()));
    }

    /**
     * 并行执行多个任务（Supplier 形式）
     *
     */
    @SafeVarargs
    public final <T> List<T> executeParallel(Supplier<T>... tasks) {
        log.info("并行执行 {} 个任务", tasks.length);

        List<CompletableFuture<T>> futures = new java.util.ArrayList<>();
        for (Supplier<T> task : tasks) {
            futures.add(CompletableFuture.supplyAsync(task, VIRTUAL_EXECUTOR));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<T> results = new java.util.ArrayList<>();
        for (CompletableFuture<T> future : futures) {
            try {
                results.add(future.join());
            } catch (Exception e) {
                log.error("任务执行失败", e);
                results.add(null);
            }
        }

        return results;
    }

    /**
     * 并行执行两个任务（类型安全）
     *
     */
    public <T1, T2> ParallelResult2<T1, T2> executeParallel2(
            Supplier<T1> task1,
            Supplier<T2> task2) {

        log.debug("启动2个并行任务");

        var future1 = CompletableFuture.supplyAsync(task1, VIRTUAL_EXECUTOR);
        var future2 = CompletableFuture.supplyAsync(task2, VIRTUAL_EXECUTOR);

        CompletableFuture.allOf(future1, future2).join();

        return new ParallelResult2<>(future1.join(), future2.join());
    }

    /**
     * 并行执行三个任务（类型安全）
     *
     */
    public <T1, T2, T3> ParallelResult3<T1, T2, T3> executeParallel3(
            Supplier<T1> task1,
            Supplier<T2> task2,
            Supplier<T3> task3) {

        log.debug("启动3个并行任务");

        var future1 = CompletableFuture.supplyAsync(task1, VIRTUAL_EXECUTOR);
        var future2 = CompletableFuture.supplyAsync(task2, VIRTUAL_EXECUTOR);
        var future3 = CompletableFuture.supplyAsync(task3, VIRTUAL_EXECUTOR);

        CompletableFuture.allOf(future1, future2, future3).join();

        return new ParallelResult3<>(future1.join(), future2.join(), future3.join());
    }

    /**
     * 并行执行并聚合结果
     */
    public <T, R> CompletableFuture<R> executeParallelAndAggregate(
            List<CapableAgent<ChatRequest, T>> agents,
            ChatRequest request,
            Function<List<T>, R> aggregator) {
        
        return executeParallel(agents, request)
                .thenApply(aggregator);
    }

    // ==================== 竞争执行方法 ====================

    /**
     * 竞争执行 - 任一成功即返回
     * 
     * Requirements: 3.1
     */
    public <T> CompletableFuture<T> executeAny(
            List<CapableAgent<ChatRequest, T>> agents,
            ChatRequest request) {
        
        log.info("竞争执行 {} 个 Agent", agents.size());

        List<CompletableFuture<T>> futures = agents.stream()
                .map(agent -> CompletableFuture.supplyAsync(
                        () -> executeWithRetrySync(agent, request, MAX_RETRIES),
                        VIRTUAL_EXECUTOR
                ))
                .toList();

        return CompletableFuture.anyOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(result -> (T) result);
    }

    /**
     * 竞争执行（Supplier 形式）
     */
    @SafeVarargs
    public final <T> T executeAny(Supplier<T>... tasks) {
        log.debug("启动竞速模式，任务数: {}", tasks.length);

        List<CompletableFuture<T>> futures = new java.util.ArrayList<>();
        for (Supplier<T> task : tasks) {
            futures.add(CompletableFuture.supplyAsync(task, VIRTUAL_EXECUTOR));
        }

        @SuppressWarnings("unchecked")
        T result = (T) CompletableFuture.anyOf(futures.toArray(new CompletableFuture[0])).join();

        return result;
    }


    // ==================== 链式执行方法 ====================

    /**
     * 链式执行多个 Agent（流式）
     * 前一个 Agent 的输出作为后一个的输入
     */
    public Flux<String> executeChain(
            List<CapableAgent<ChatRequest, String>> agents,
            ChatRequest initialRequest) {
        
        if (agents.isEmpty()) {
            return Flux.empty();
        }

        log.info("链式执行 {} 个 Agent", agents.size());

        return Flux.defer(() -> {
            ChatRequest currentRequest = initialRequest;
            Flux<String> result = Flux.empty();

            for (int i = 0; i < agents.size(); i++) {
                CapableAgent<ChatRequest, String> agent = agents.get(i);
                final int index = i;
                final ChatRequest req = currentRequest;

                result = result.concatWith(
                        Flux.defer(() -> {
                            log.info("链式执行第 {} 个 Agent: {}", index + 1, agent.getName());
                            return executeWithRetry(agent, req, MAX_RETRIES);
                        })
                );
            }

            return result;
        });
    }

    /**
     * 链式执行多个 Agent（带上下文）
     * 使用 ChainExecutionContext 管理执行状态
     * 
     * @param initialInput 初始输入
     * @param agents Agent 列表（按执行顺序）
     * @return 链式执行上下文，包含所有输出
     * @throws ChainExecutionException 如果任何 Agent 执行失败
     */
    public ChainExecutionContext executeChainWithContext(
            String initialInput,
            List<CapableAgent<ChatRequest, String>> agents,
            ChatRequest baseRequest) {
        
        ChainExecutionContext context = new ChainExecutionContext();
        log.info("启动链式执行（带上下文），Agent数: {}", agents.size());
        
        String currentInput = initialInput;
        
        for (int i = 0; i < agents.size(); i++) {
            CapableAgent<ChatRequest, String> agent = agents.get(i);
            
            // 检查是否已中断
            if (context.isAborted()) {
                log.warn("链式执行已中断，跳过剩余Agent: {}", agent.getName());
                break;
            }
            
            log.info("执行Agent [{}/{}]: {}", i + 1, agents.size(), agent.getName());
            
            try {
                // 构建当前请求
                ChatRequest currentRequest = new ChatRequest(
                    currentInput,
                    baseRequest.projectId(),
                    baseRequest.sessionId(),
                    baseRequest.currentPhase(),
                    baseRequest.intentHint(),
                    baseRequest.metadata()
                );
                
                // 执行 Agent（同步收集流式输出）
                String output = agent.stream(currentRequest)
                        .timeout(DEFAULT_TIMEOUT)
                        .collectList()
                        .map(chunks -> String.join("", chunks))
                        .block();
                
                // 记录输出
                AgentOutput agentOutput = AgentOutput.success(agent.getName(), output);
                context.addOutput(agentOutput);
                
                // 使用当前输出构建下一个输入
                currentInput = context.buildNextInput(initialInput);
                
                log.info("Agent {} 执行成功", agent.getName());
                publishCompletionEvent(baseRequest.sessionId(), agent.getName());
                
            } catch (Exception e) {
                log.error("Agent {} 执行失败", agent.getName(), e);
                
                // 记录失败输出
                AgentOutput failedOutput = AgentOutput.failure(agent.getName(), e);
                context.addOutput(failedOutput);
                
                // 中断执行
                context.abort("Agent " + agent.getName() + " 执行失败: " + e.getMessage());
                publishFailureEvent(baseRequest.sessionId(), agent.getName(), e);
                
                // 抛出异常
                throw new ChainExecutionException(agent.getName(), i, context, e);
            }
        }
        
        log.info("链式执行完成: {}", context.getSummary());
        return context;
    }

    /**
     * 异步链式执行（带上下文）
     */
    public CompletableFuture<ChainExecutionContext> executeChainWithContextAsync(
            String initialInput,
            List<CapableAgent<ChatRequest, String>> agents,
            ChatRequest baseRequest) {
        
        return CompletableFuture.supplyAsync(
            () -> executeChainWithContext(initialInput, agents, baseRequest),
            VIRTUAL_EXECUTOR
        );
    }


    // ==================== 重试和辅助方法 ====================

    /**
     * 带重试的执行（流式）
     * 
     * Requirements: 3.3
     */
    private Flux<String> executeWithRetry(
            CapableAgent<ChatRequest, ?> agent,
            ChatRequest request,
            int maxRetries) {
        
        return agent.stream(request)
                .timeout(DEFAULT_TIMEOUT)
                .onErrorResume(e -> {
                    if (maxRetries > 0) {
                        log.warn("Agent {} 执行失败，重试中... 剩余重试次数: {}", 
                                agent.getName(), maxRetries - 1);
                        return Mono.delay(calculateBackoff(MAX_RETRIES - maxRetries))
                                .flatMapMany(v -> executeWithRetry(agent, request, maxRetries - 1));
                    } else {
                        log.error("Agent {} 执行失败，已达最大重试次数", agent.getName());
                        publishFailureEvent(request.sessionId(), agent.getName(), e);
                        return Flux.error(e);
                    }
                })
                .doOnComplete(() -> publishCompletionEvent(request.sessionId(), agent.getName()));
    }

    /**
     * 带重试的同步执行
     * 
     * Requirements: 3.3
     */
    private <T> T executeWithRetrySync(
            CapableAgent<ChatRequest, T> agent,
            ChatRequest request,
            int maxRetries) {
        
        Exception lastException = null;
        
        for (int i = 0; i <= maxRetries; i++) {
            try {
                T result = agent.execute(request);
                publishCompletionEvent(request.sessionId(), agent.getName());
                return result;
            } catch (Exception e) {
                lastException = e;
                if (i < maxRetries) {
                    log.warn("Agent {} 执行失败，重试中... 剩余重试次数: {}", 
                            agent.getName(), maxRetries - i);
                    try {
                        Thread.sleep(calculateBackoff(i).toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("重试被中断", ie);
                    }
                }
            }
        }
        
        log.error("Agent {} 执行失败，已达最大重试次数", agent.getName());
        publishFailureEvent(request.sessionId(), agent.getName(), lastException);
        throw new RuntimeException("Agent 执行失败: " + agent.getName(), lastException);
    }

    /**
     * 计算指数退避时间
     * 
     * Requirements: 3.3
     */
    private Duration calculateBackoff(int retryCount) {
        // 指数退避: 1s, 2s, 4s, ...
        return Duration.ofSeconds((long) Math.pow(2, retryCount));
    }

    /**
     * 发布完成事件
     * 
     * Requirements: 3.4
     */
    private void publishCompletionEvent(String sessionId, String agentName) {
        if (sessionId != null) {
            contextBus.publish(sessionId, ContextEvent.agentCompleted(
                    agentName, "执行完成", System.currentTimeMillis()
            ));
        }
    }

    /**
     * 发布失败事件
     */
    private void publishFailureEvent(String sessionId, String agentName, Throwable error) {
        if (sessionId != null) {
            contextBus.publish(sessionId, ContextEvent.custom(
                    agentName,
                    ContextEvent.EventType.AGENT_FAILED.name(),
                    error.getMessage(),
                    Map.of("errorType", error.getClass().getSimpleName())
            ));
        }
    }

    // ==================== 类型安全结果容器 ====================

    /**
     * 两个结果的容器
     * 
     * Requirements: 8.2
     */
    public record ParallelResult2<T1, T2>(T1 result1, T2 result2) {}

    /**
     * 三个结果的容器
     * 
     * Requirements: 8.2
     */
    public record ParallelResult3<T1, T2, T3>(T1 result1, T2 result2, T3 result3) {}
}
