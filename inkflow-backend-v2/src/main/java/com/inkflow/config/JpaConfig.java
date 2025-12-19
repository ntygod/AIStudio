package com.inkflow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * JPA配置类
 * 
 * 启用JPA审计功能，自动填充创建时间和更新时间
 * 启用事务管理
 */
@Configuration
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "com.inkflow")
@EnableTransactionManagement
public class JpaConfig {
    // JPA配置通过application.yml完成
    // 此类主要用于启用注解功能
}
