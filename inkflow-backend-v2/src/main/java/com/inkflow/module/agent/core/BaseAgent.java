package com.inkflow.module.agent.core;

import com.inkflow.module.ai_bridge.chat.DynamicChatModelFactory;
import com.inkflow.module.agent.event.AgentThoughtEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Flux;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * Agent 抽象基类
 * 使用 Java 22 Virtual Threads 实现异步执行
 * 支持能力声明
 *
 * @param <I> 输入类型
 * @param <O> 输出类型
 */
@Slf4j
public abstract class BaseAgent<I, O> implements CapableAgent<I, O> {

    protected final DynamicChatModelFactory chatModelFactory;
    protected final ApplicationEventPublisher eventPublisher;

    /**
     * Virtual Thread 执行器
     */
    protected static final java.util.concurrent.ExecutorService VIRTUAL_EXECUTOR =
            Executors.newVirtualThreadPerTaskExecutor();

    protected BaseAgent(
            DynamicChatModelFactory chatModelFactory,
            ApplicationEventPublisher eventPublisher) {
        this.chatModelFactory = chatModelFactory;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 获取系统提示词
     */
    protected abstract String getSystemPrompt();

    /**
     * 构建用户提示词
     */
    protected abstract String buildUserPrompt(I input);

    /**
     * 解析 AI 响应
     */
    protected abstract O parseResponse(String response, I input);

    @Override
    public String getDescription() {
        return getCapability().category().getDescription();
    }

    /**
     * 同步执行
     */
    @Override
    public O execute(I input) {
        publishThought("开始执行...");

        try {
            ChatModel model = chatModelFactory.getDefaultModel();
            ChatClient client = ChatClient.builder(model)
                    .defaultSystem(getSystemPrompt())
                    .build();

            String userPrompt = buildUserPrompt(input);
            publishThought("正在分析...");

            String response = client.prompt()
                    .user(userPrompt)
                    .call()
                    .content();

            publishThought("分析完成");
            return parseResponse(response, input);

        } catch (Exception e) {
            log.error("Agent 执行失败: {}", getName(), e);
            publishThought("执行失败: " + e.getMessage());
            throw new RuntimeException("Agent 执行失败: " + getName(), e);
        }
    }

    /**
     * 异步执行（使用 Virtual Threads）
     * Requirements: 23.3
     */
    @Override
    public CompletableFuture<O> executeAsync(I input) {
        return CompletableFuture.supplyAsync(() -> execute(input), VIRTUAL_EXECUTOR);
    }

    /**
     * 流式执行
     */
    @Override
    public Flux<String> stream(I input) {
        publishThought("开始流式生成...");

        ChatModel model = chatModelFactory.getDefaultModel();
        ChatClient client = ChatClient.builder(model)
                .defaultSystem(getSystemPrompt())
                .build();

        String userPrompt = buildUserPrompt(input);

        return client.prompt()
                .user(userPrompt)
                .stream()
                .content()
                .doOnComplete(() -> publishThought("生成完成"))
                .doOnError(e -> publishThought("生成失败: " + e.getMessage()));
    }

    /**
     * 发布思考过程事件
     */
    protected void publishThought(String thought) {
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new AgentThoughtEvent(this, getName(), thought));
        }
        log.debug("[{}] {}", getName(), thought);
    }
}
