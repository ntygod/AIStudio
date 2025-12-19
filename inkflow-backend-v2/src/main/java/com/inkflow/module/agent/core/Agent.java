package com.inkflow.module.agent.core;

import reactor.core.publisher.Flux;

import java.util.concurrent.CompletableFuture;

/**
 * Agent基础接口
 * 定义智能体的核心能力
 *
 * @param <I> 输入类型
 * @param <O> 输出类型
 * @author zsg
 * @date 2025/12/17
 */
public interface Agent<I, O> {

    /**
     * 获取Agent名称
     */
    String getName();

    /**
     * 获取Agent描述
     */
    String getDescription();

    /**
     * 同步执行
     */
    O execute(I input);

    /**
     * 异步执行（使用Virtual Threads）
     */
    CompletableFuture<O> executeAsync(I input);

    /**
     * 流式执行
     */
    Flux<String> stream(I input);
}
