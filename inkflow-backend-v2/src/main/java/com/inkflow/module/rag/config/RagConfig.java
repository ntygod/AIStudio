package com.inkflow.module.rag.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * RAG模块配置类
 * 启用RagProperties配置属性绑定
 * 
 * @author zsg
 * @date 2025/12/17
 */
@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagConfig {
    // 配置类，启用RagProperties属性绑定
}
