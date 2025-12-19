package com.inkflow.module.provider.entity;

/**
 * AI 服务商类型枚举
 */
public enum ProviderType {
    OPENAI("OpenAI", "https://api.openai.com/v1"),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com"),
    OLLAMA("Ollama", "http://localhost:11434"),
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta"),
    CLAUDE("Anthropic Claude", "https://api.anthropic.com/v1");

    private final String displayName;
    private final String defaultBaseUrl;

    ProviderType(String displayName, String defaultBaseUrl) {
        this.displayName = displayName;
        this.defaultBaseUrl = defaultBaseUrl;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDefaultBaseUrl() {
        return defaultBaseUrl;
    }
}
