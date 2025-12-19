package com.inkflow;

import com.inkflow.module.auth.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * InkFlow 2.0 - AI原生小说创作平台
 * 
 * 技术栈:
 * - Java 25 LTS (Virtual Threads, Structured Concurrency)
 * - Spring Boot 4.0.1
 * - Spring AI 2.0
 * - PostgreSQL 18 + pgvector
 * - Redis Stack 7.4+
 * 
 * 注意: DeepSeek/OpenAI 等 LLM 的自动配置已禁用
 * API Key 由用户在前端配置，存储在数据库中，运行时动态创建 ChatModel
 */
@SpringBootApplication(exclude = {
    org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration.class,
    org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration.class
})
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties({JwtProperties.class})
public class InkFlowApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(InkFlowApplication.class, args);
    }
}
