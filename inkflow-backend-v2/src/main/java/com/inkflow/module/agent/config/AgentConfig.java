package com.inkflow.module.agent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 配置类
 */
@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentConfig {
    
}
