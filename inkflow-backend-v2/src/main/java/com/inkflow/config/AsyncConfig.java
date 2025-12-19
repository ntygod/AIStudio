package com.inkflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 异步任务配置
 * 
 * 使用 Java 25 Virtual Threads 实现高并发异步处理
 * Virtual Threads 特点:
 * - 轻量级: 每个虚拟线程仅占用几KB内存
 * - 高并发: 支持百万级并发任务
 * - 自动调度: JVM自动管理虚拟线程的调度
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 虚拟线程执行器
     * 用于I/O密集型任务，如AI调用、数据库操作、网络请求等
     */
    @Bean(name = "virtualThreadExecutor")
    public Executor virtualThreadExecutor() {
        // 使用虚拟线程执行器 - Java 21+ 特性
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 嵌入向量生成专用执行器
     * 用于异步生成文本嵌入向量
     */
    @Bean(name = "embeddingExecutor")
    public Executor embeddingExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * AI任务执行器
     * 用于AI对话、内容生成等任务
     */
    @Bean(name = "aiTaskExecutor")
    public Executor aiTaskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 传统线程池执行器 (备用)
     * 用于CPU密集型任务，如复杂计算
     * 注意: 虚拟线程不适合CPU密集型任务
     */
    @Bean(name = "cpuBoundExecutor")
    public Executor cpuBoundExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // CPU密集型任务线程数 = CPU核心数
        int coreCount = Runtime.getRuntime().availableProcessors();
        executor.setCorePoolSize(coreCount);
        executor.setMaxPoolSize(coreCount * 2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("cpu-bound-");
        executor.initialize();
        return executor;
    }
}
