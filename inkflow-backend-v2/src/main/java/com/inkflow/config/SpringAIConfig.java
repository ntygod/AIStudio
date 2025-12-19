package com.inkflow.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 配置类
 * 配置 EmbeddingModel Bean，支持本地 TEI 服务（兼容 OpenAI 格式）
 *
 * @author zsg
 * @date 2025/12/18
 */
@Slf4j
@Configuration
public class SpringAIConfig {

    @Value("${spring.ai.openai.base-url:http://localhost:8081}")
    private String baseUrl;

    @Value("${spring.ai.openai.api-key:sk-dummy}")
    private String apiKey;

    /**
     * 配置 EmbeddingModel Bean
     * 使用 OpenAI 兼容的 API（支持本地 TEI 服务）
     */
    @Bean
    @ConditionalOnMissingBean(EmbeddingModel.class)
    public EmbeddingModel embeddingModel() {
        log.info("配置 EmbeddingModel: baseUrl={}", baseUrl);
        
        // 创建 OpenAI API 客户端（兼容 TEI 服务）
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
        
        // 创建 EmbeddingModel
        return new OpenAiEmbeddingModel(openAiApi);
    }
}
