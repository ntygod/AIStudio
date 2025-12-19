package com.inkflow.module.ai_bridge.chat;

import com.inkflow.module.project.entity.CreationPhase;
import com.inkflow.module.provider.dto.ProviderConnectionInfo;
import com.inkflow.module.provider.entity.ProviderType;
import com.inkflow.module.provider.entity.UserProviderConfig;
import com.inkflow.module.provider.service.UserProviderConfigCacheService;
import com.inkflow.module.provider.service.UserProviderConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态ChatModel工厂
 * 支持多AI提供商的动态切换
 *
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@Component
public class DynamicChatModelFactory {

    private final UserProviderConfigCacheService userConfigCacheService;
    private final UserProviderConfigService userProviderConfigService;

    @Value("${spring.ai.openai.api-key:}")
    private String openaiApiKey;

    @Value("${spring.ai.openai.base-url:https://api.openai.com}")
    private String openaiBaseUrl;

    @Value("${spring.ai.deepseek.api-key:}")
    private String deepseekApiKey;

    @Value("${spring.ai.deepseek.base-url:https://api.deepseek.com}")
    private String deepseekBaseUrl;

    /**
     * 缓存已创建的ChatModel实例
     */
    private final Map<String, ChatModel> modelCache = new ConcurrentHashMap<>();

    public DynamicChatModelFactory(UserProviderConfigCacheService userConfigCacheService,
                                   UserProviderConfigService userProviderConfigService) {
        this.userConfigCacheService = userConfigCacheService;
        this.userProviderConfigService = userProviderConfigService;
    }

    /**
     * 支持的AI提供商
     */
    public enum Provider {
        OPENAI("openai"),
        DEEPSEEK("deepseek");

        private final String code;

        Provider(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }

        public static Provider fromCode(String code) {
            for (Provider p : values()) {
                if (p.code.equalsIgnoreCase(code)) {
                    return p;
                }
            }
            throw new IllegalArgumentException("未知的AI提供商: " + code);
        }
    }

    /**
     * 获取默认的ChatModel
     */
    public ChatModel getDefaultModel() {
        // 优先使用DeepSeek
        if (isProviderConfigured(Provider.DEEPSEEK)) {
            return getModel(Provider.DEEPSEEK, "deepseek-chat");
        }
        if (isProviderConfigured(Provider.OPENAI)) {
            return getModel(Provider.OPENAI, "gpt-4-turbo");
        }
        throw new IllegalStateException("未配置任何AI提供商");
    }

    /**
     * 根据提供商和模型名获取ChatModel
     */
    public ChatModel getModel(Provider provider, String modelName) {
        String cacheKey = provider.getCode() + ":" + modelName;
        return modelCache.computeIfAbsent(cacheKey, k -> createModel(provider, modelName));
    }

    /**
     * 根据提供商代码和模型名获取ChatModel
     */
    public ChatModel getModel(String providerCode, String modelName) {
        return getModel(Provider.fromCode(providerCode), modelName);
    }

    /**
     * 检查提供商是否已配置
     */
    public boolean isProviderConfigured(Provider provider) {
        return switch (provider) {
            case OPENAI -> openaiApiKey != null && !openaiApiKey.isBlank();
            case DEEPSEEK -> deepseekApiKey != null && !deepseekApiKey.isBlank();
        };
    }

    /**
     * 创建ChatModel实例
     */
    private ChatModel createModel(Provider provider, String modelName) {
        log.info("创建ChatModel: provider={}, model={}", provider, modelName);

        return switch (provider) {
            case OPENAI -> createOpenAiModel(modelName);
            case DEEPSEEK -> createDeepSeekModel(modelName);
        };
    }

    /**
     * 创建OpenAI ChatModel
     */
    private ChatModel createOpenAiModel(String modelName) {
        if (!isProviderConfigured(Provider.OPENAI)) {
            throw new IllegalStateException("OpenAI API Key未配置");
        }

        OpenAiApi api = OpenAiApi.builder()
                .apiKey(openaiApiKey)
                .baseUrl(openaiBaseUrl)
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(modelName)
                .temperature(0.7)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }

    /**
     * 创建DeepSeek ChatModel
     */
    private ChatModel createDeepSeekModel(String modelName) {
        if (!isProviderConfigured(Provider.DEEPSEEK)) {
            throw new IllegalStateException("DeepSeek API Key未配置");
        }

        DeepSeekApi api = DeepSeekApi.builder()
                .apiKey(deepseekApiKey)
                .baseUrl(deepseekBaseUrl)
                .build();

        DeepSeekChatOptions options = DeepSeekChatOptions.builder()
                .model(modelName)
                .temperature(0.7)
                .build();

        return DeepSeekChatModel.builder()
                .deepSeekApi(api)
                .defaultOptions(options)
                .build();
    }

    /**
     * 获取用户的ChatModel
     * 根据用户配置返回对应的模型
     * 
     * 优先级：
     * 1. 用户级配置 (UserProviderConfig) - 用户的默认偏好
     * 2. 用户提供商配置 (AIProviderConfig) - 用户配置的具体提供商
     * 3. 系统默认模型
     * 
     * @param userId 用户ID
     * @return ChatModel 实例
     */
    public ChatModel getChatModel(UUID userId) {
        if (userId == null) {
            log.debug("用户ID为空，返回默认模型");
            return getDefaultModel();
        }

        // 1. 首先尝试从用户级配置获取偏好提供商
        Optional<UserProviderConfig> userConfig = userProviderConfigService.getUserConfig(userId);
        if (userConfig.isPresent()) {
            UserProviderConfig config = userConfig.get();
            try {
                ChatModel model = createModelFromUserConfig(config);
                if (model != null && isModelAvailable(model)) {
                    log.debug("使用用户 {} 的偏好提供商: {}", userId, config.getPreferredProvider());
                    return model;
                }
            } catch (Exception e) {
                log.warn("用户 {} 的偏好提供商 {} 不可用，尝试其他配置: {}", 
                        userId, config.getPreferredProvider(), e.getMessage());
            }
        }

        // 2. 尝试从用户提供商配置获取
        Optional<ProviderConnectionInfo> connectionInfo = userConfigCacheService.getDefaultConnectionInfo(userId);
        if (connectionInfo.isPresent()) {
            ProviderConnectionInfo info = connectionInfo.get();
            try {
                ChatModel model = createModelFromConnectionInfo(info);
                if (model != null && isModelAvailable(model)) {
                    log.debug("使用用户 {} 配置的提供商: {}", userId, info.providerType());
                    return model;
                }
            } catch (Exception e) {
                log.warn("用户 {} 配置的提供商 {} 不可用，降级到默认模型: {}", 
                        userId, info.providerType(), e.getMessage());
            }
        }

        // 3. 降级到默认模型
        log.debug("用户 {} 未配置有效提供商，使用系统默认模型", userId);
        return getDefaultModel();
    }

    /**
     * 从用户级配置创建 ChatModel
     * 
     * @param config 用户级配置
     * @return ChatModel 实例，如果无法创建则返回 null
     */
    private ChatModel createModelFromUserConfig(UserProviderConfig config) {
        ProviderType providerType = config.getPreferredProvider();
        String modelName = config.getPreferredModel();
        
        String cacheKey = "userPref:" + providerType.name() + ":" + (modelName != null ? modelName : "default");
        return modelCache.computeIfAbsent(cacheKey, k -> {
            return switch (providerType) {
                case OPENAI -> {
                    if (!isProviderConfigured(Provider.OPENAI)) {
                        log.warn("用户偏好 OpenAI 但系统未配置 OpenAI API Key");
                        yield null;
                    }
                    yield createOpenAiModel(modelName != null ? modelName : "gpt-4-turbo");
                }
                case DEEPSEEK -> {
                    if (!isProviderConfigured(Provider.DEEPSEEK)) {
                        log.warn("用户偏好 DeepSeek 但系统未配置 DeepSeek API Key");
                        yield null;
                    }
                    yield createDeepSeekModel(modelName != null ? modelName : "deepseek-chat");
                }
                default -> {
                    log.warn("不支持的提供商类型: {}", providerType);
                    yield null;
                }
            };
        });
    }

    /**
     * 从连接信息创建 ChatModel
     */
    private ChatModel createModelFromConnectionInfo(ProviderConnectionInfo info) {
        String cacheKey = "user:" + info.providerType().name() + ":" + info.defaultModel();
        return modelCache.computeIfAbsent(cacheKey, k -> {
            return switch (info.providerType()) {
                case OPENAI -> createOpenAiModelWithKey(info.apiKey(), info.baseUrl(), info.defaultModel());
                case DEEPSEEK -> createDeepSeekModelWithKey(info.apiKey(), info.baseUrl(), info.defaultModel());
                default -> {
                    log.warn("不支持的提供商类型: {}", info.providerType());
                    yield null;
                }
            };
        });
    }

    /**
     * 使用指定的 API Key 创建 OpenAI 模型
     */
    private ChatModel createOpenAiModelWithKey(String apiKey, String baseUrl, String modelName) {
        OpenAiApi api = OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl != null ? baseUrl : "https://api.openai.com")
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(modelName != null ? modelName : "gpt-4-turbo")
                .temperature(0.7)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }

    /**
     * 使用指定的 API Key 创建 DeepSeek 模型
     */
    private ChatModel createDeepSeekModelWithKey(String apiKey, String baseUrl, String modelName) {
        DeepSeekApi api = DeepSeekApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl != null ? baseUrl : "https://api.deepseek.com")
                .build();

        DeepSeekChatOptions options = DeepSeekChatOptions.builder()
                .model(modelName != null ? modelName : "deepseek-chat")
                .temperature(0.7)
                .build();

        return DeepSeekChatModel.builder()
                .deepSeekApi(api)
                .defaultOptions(options)
                .build();
    }

    /**
     * 检查模型是否可用
     * 简单的可用性检查
     */
    private boolean isModelAvailable(ChatModel model) {
        // 基本检查：模型不为空即认为可用
        // 实际的健康检查可以在调用时进行
        return model != null;
    }

    /**
     * 根据创作阶段获取合适的模型
     * 不同阶段可能需要不同能力的模型
     * 
     * Requirements: 8.1-8.6
     * 
     * @param userId 用户ID
     * @param phase 创作阶段
     * @return ChatModel 实例
     */
    public ChatModel getChatModelForPhase(UUID userId, CreationPhase phase) {
        // 根据阶段选择模型
        return switch (phase) {
            // 需要深度推理的阶段，优先使用推理模型
            case OUTLINE -> {
                ChatModel reasoningModel = getReasoningModel(userId);
                yield reasoningModel != null ? reasoningModel : getDefaultModel();
            }
            // 写作阶段需要创意能力
            case WRITING -> {
                if (isProviderConfigured(Provider.DEEPSEEK)) {
                    yield getModel(Provider.DEEPSEEK, "deepseek-chat");
                }
                yield getDefaultModel();
            }
            // 其他阶段使用默认模型
            default -> getDefaultModel();
        };
    }

    /**
     * 获取默认ChatModel
     */
    public ChatModel getDefaultChatModel() {
        return getDefaultModel();
    }

    /**
     * 获取用户的推理模型
     * 用于深度思考任务
     * 
     * <p>DeepSeek 思考模式注意事项：
     * <ul>
     * <li>deepseek-reasoner 的 Tool Calls 需要回传 reasoning_content</li>
     * <li>当前 Spring AI 不支持，因此推理模型不应用于 Tool Calling 场景</li>
     * <li>如果需要 Tool Calling，会自动降级到 deepseek-chat</li>
     * </ul>
     * 
     * Requirements: 7.1, 8.5, 11.4
     * 
     * @param userId 用户ID
     * @return 推理模型实例的 Optional，如果不可用则返回 Optional.empty()
     */
    public Optional<ChatModel> getReasoningModelOptional(UUID userId) {
        // 1. 首先检查用户级配置中的推理模型设置
        if (userId != null) {
            Optional<UserProviderConfig> userConfig = userProviderConfigService.getUserConfig(userId);
            if (userConfig.isPresent() && userConfig.get().hasReasoningConfig()) {
                UserProviderConfig config = userConfig.get();
                try {
                    ChatModel model = createReasoningModelFromUserConfig(config);
                    if (model != null) {
                        log.debug("使用用户 {} 配置的推理模型: provider={}, model={}", 
                                userId, config.getReasoningProvider(), config.getReasoningModel());
                        return Optional.of(model);
                    }
                } catch (Exception e) {
                    log.warn("用户 {} 配置的推理模型不可用: {}", userId, e.getMessage());
                }
            }
        }

        // 2. 推理模型优先使用 DeepSeek R1
        if (isProviderConfigured(Provider.DEEPSEEK)) {
            try {
                // 注意：deepseek-reasoner 不支持 Tool Calling
                // 仅用于纯文本推理场景
                return Optional.of(createDeepSeekReasoningModel());
            } catch (Exception e) {
                log.warn("获取 deepseek-reasoner 失败，降级到 deepseek-chat: {}", e.getMessage());
                return Optional.of(getModel(Provider.DEEPSEEK, "deepseek-chat"));
            }
        }
        
        // 3. 降级到 OpenAI
        if (isProviderConfigured(Provider.OPENAI)) {
            return Optional.of(getModel(Provider.OPENAI, "gpt-4-turbo"));
        }
        
        return Optional.empty();
    }

    /**
     * 从用户配置创建推理模型
     */
    private ChatModel createReasoningModelFromUserConfig(UserProviderConfig config) {
        ProviderType providerType = config.getEffectiveReasoningProvider();
        String modelName = config.getReasoningModel();
        
        return switch (providerType) {
            case DEEPSEEK -> {
                if (!isProviderConfigured(Provider.DEEPSEEK)) {
                    yield null;
                }
                yield createDeepSeekModel(modelName != null ? modelName : "deepseek-reasoner");
            }
            case OPENAI -> {
                if (!isProviderConfigured(Provider.OPENAI)) {
                    yield null;
                }
                yield createOpenAiModel(modelName != null ? modelName : "gpt-4-turbo");
            }
            default -> null;
        };
    }

    /**
     * 获取推理模型（返回 Optional）
     * 
     * Requirements: 7.1
     * 
     * @return 推理模型实例的 Optional，如果不可用则返回 Optional.empty()
     */
    public Optional<ChatModel> getReasoningModel() {
        return getReasoningModelOptional(null);
    }

    /**
     * 获取用户的推理模型（兼容旧API）
     * @deprecated 使用 {@link #getReasoningModelOptional(UUID)} 代替
     */
    @Deprecated
    public ChatModel getReasoningModel(UUID userId) {
        return getReasoningModelOptional(userId).orElse(null);
    }

    /**
     * 创建 DeepSeek 推理模型（不降级）
     * 用于纯文本生成，不涉及 Tool Calling
     */
    private ChatModel createDeepSeekReasoningModel() {
        log.info("创建 DeepSeek 推理模型: deepseek-reasoner");

        DeepSeekApi api = DeepSeekApi.builder()
                .apiKey(deepseekApiKey)
                .baseUrl(deepseekBaseUrl)
                .build();

        DeepSeekChatOptions options = DeepSeekChatOptions.builder()
                .model("deepseek-reasoner")
                .build();

        return DeepSeekChatModel.builder()
                .deepSeekApi(api)
                .defaultOptions(options)
                .build();
    }

    /**
     * 检查是否是 DeepSeek 思考模式模型
     * 思考模式模型包括：reasoner, r1 及其变体
     * 
     * @param model 模型名称
     * @return 是否是思考模式模型
     */
    public boolean isThinkingModeModel(String model) {
        if (model == null || model.isBlank()) {
            return false;
        }
        String lowerModel = model.toLowerCase().trim();
        return lowerModel.contains("reasoner")
                || lowerModel.contains("-r1")
                || lowerModel.equals("r1")
                || lowerModel.startsWith("r1-");
    }

    /**
     * 获取默认推理模型
     */
    public ChatModel getDefaultReasoningModel() {
        return getReasoningModel(null);
    }

    /**
     * 清除模型缓存
     */
    public void clearCache() {
        modelCache.clear();
        log.info("ChatModel缓存已清除");
    }

    /**
     * 获取已缓存的模型数量
     */
    public int getCachedModelCount() {
        return modelCache.size();
    }
}
