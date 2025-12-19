package com.inkflow.module.ai_bridge.tool;

import com.inkflow.module.ai_bridge.chat.DynamicChatModelFactory;
import com.inkflow.module.ai_bridge.context.RequestContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 深度推理工具
 * 封装 DeepSeek R1 (deepseek-reasoner) 模型，供主模型调用
 * 
 * <p>主从架构设计：
 * <ul>
 *   <li>主模型 (V3/deepseek-chat): 负责 Tool Calling 和任务调度</li>
 *   <li>从模型 (R1/deepseek-reasoner): 负责深度思考，通过此 Tool 被调用</li>
 * </ul>
 * 
 * <p>使用场景：
 * <ul>
 *   <li>复杂剧情分析和逻辑推演</li>
 *   <li>角色心理深度分析</li>
 *   <li>伏笔和悬念的设计建议</li>
 *   <li>情节冲突检测和解决方案</li>
 * </ul>
 * 
 * Requirements: 11.1-11.6
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeepReasoningTool {

    private final DynamicChatModelFactory modelFactory;

    @Value("${inkflow.ai.deep-reasoning.enabled:true}")
    private boolean enabled;

    @Value("${inkflow.ai.deep-reasoning.fallback-to-main:true}")
    private boolean fallbackToMain;

    /**
     * 执行深度思考
     * 当遇到需要深度逻辑推演的复杂问题时调用
     */
    @Tool(description = "深度思考工具：当遇到复杂的剧情分析、逻辑推演、角色心理分析、伏笔设计等需要深度思考的任务时调用。" +
                        "此工具使用推理模型进行深度分析，适合处理需要多步推理的复杂问题。")
    public String deepThinking(
            @ToolParam(description = "需要深度思考的问题或任务，描述清楚你想要分析的内容")
            String query,
            @ToolParam(description = "相关上下文信息，如角色设定、剧情背景、已有内容等", required = false)
            String context,
            @ToolParam(description = "分析类型：plot(剧情分析), character(角色心理), foreshadowing(伏笔设计), conflict(冲突解决)", required = false)
            String analysisType) {

        // 从 ScopedValue 获取上下文
        UUID userId = null;
        UUID projectId = null;
        if (RequestContextHolder.isBound()) {
            var ctx = RequestContextHolder.current();
            userId = ctx.userId();
            projectId = ctx.projectId();
        }

        log.info("开始深度思考: userId={}, projectId={}, analysisType={}, query长度={}",
                userId, projectId, analysisType,
                query != null ? query.length() : 0);

        if (!enabled) {
            log.warn("深度推理功能已禁用");
            return "深度推理功能当前不可用。";
        }

        try {
            // 获取推理模型（如果不可用会抛出 ReasoningModelUnavailableException）
            ChatModel reasoningModel = getReasoningModel(userId);

            // 构建提示词
            String systemPrompt = buildSystemPrompt(analysisType);
            String userPrompt = buildUserPrompt(query, context, analysisType);

            // 调用推理模型
            ChatClient client = ChatClient.builder(reasoningModel).build();

            String response = client.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            log.info("深度思考完成: 响应长度={}", response != null ? response.length() : 0);
            return response != null ? response : "";

        } catch (ReasoningModelUnavailableException e) {
            // 推理模型不可用，尝试降级处理
            log.warn("推理模型不可用: {}", e.getMessage());
            return handleModelUnavailable(query, context, analysisType);
        } catch (Exception e) {
            log.error("深度思考失败: {}", e.getMessage(), e);
            return handleError(e, query, context, analysisType);
        }
    }


    /**
     * 分析剧情逻辑
     */
    @Tool(description = "分析剧情逻辑：检查剧情的合理性、因果关系、时间线一致性。")
    public String analyzePlotLogic(
            @ToolParam(description = "需要分析的剧情内容") String plotContent,
            @ToolParam(description = "相关背景设定", required = false) String background) {

        return deepThinking(
                "请分析以下剧情的逻辑合理性：\n" + plotContent,
                background,
                "plot"
        );
    }

    /**
     * 分析角色心理
     */
    @Tool(description = "分析角色心理：深入分析角色的心理状态、动机、行为逻辑。")
    public String analyzeCharacterPsychology(
            @ToolParam(description = "角色名称") String characterName,
            @ToolParam(description = "角色当前处境或行为") String situation,
            @ToolParam(description = "角色设定信息", required = false) String characterInfo) {

        String query = String.format("请深入分析角色 '%s' 在以下情境中的心理状态和行为动机：\n%s",
                characterName, situation);
        return deepThinking(query, characterInfo, "character");
    }

    /**
     * 设计伏笔
     */
    @Tool(description = "设计伏笔：根据剧情需要设计合理的伏笔和悬念。")
    public String designForeshadowing(
            @ToolParam(description = "需要铺设伏笔的剧情点") String plotPoint,
            @ToolParam(description = "伏笔要达成的效果") String intendedEffect,
            @ToolParam(description = "已有的剧情背景", required = false) String background) {

        String query = String.format("请为以下剧情点设计伏笔：\n剧情点：%s\n期望效果：%s",
                plotPoint, intendedEffect);
        return deepThinking(query, background, "foreshadowing");
    }

    /**
     * 获取推理模型
     * 
     * Requirements: 7.6
     * 
     * @param userId 用户ID（可为 null）
     * @return 推理模型实例
     * @throws ReasoningModelUnavailableException 当推理模型不可用时抛出描述性异常
     */
    private ChatModel getReasoningModel(UUID userId) {
        try {
            ChatModel model;
            if (userId != null) {
                model = modelFactory.getReasoningModel(userId);
            } else {
                // 如果没有用户上下文，尝试获取默认推理模型
                model = modelFactory.getDefaultReasoningModel();
            }
            
            if (model == null) {
                throw new ReasoningModelUnavailableException(
                    "推理模型不可用。请检查 AI 配置：" +
                    "1. 确保已配置 DeepSeek 或 OpenAI API Key；" +
                    "2. 确保网络连接正常；" +
                    "3. 如果使用用户级配置，请检查用户的 AI 提供商设置。"
                );
            }
            return model;
        } catch (ReasoningModelUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.warn("获取推理模型失败: {}", e.getMessage());
            throw new ReasoningModelUnavailableException(
                "获取推理模型时发生错误: " + e.getMessage() + 
                "。请检查 AI 配置或稍后重试。",
                e
            );
        }
    }

    /**
     * 推理模型不可用异常
     *
     */
    public static class ReasoningModelUnavailableException extends RuntimeException {
        public ReasoningModelUnavailableException(String message) {
            super(message);
        }

        public ReasoningModelUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 处理模型不可用的情况
     */
    private String handleModelUnavailable(String query, String context, String analysisType) {
        if (!fallbackToMain) {
            return "深度推理模型当前不可用，请稍后重试或在 AI 设置中配置推理模型。";
        }

        log.info("推理模型不可用，降级到主模型");

        // 降级到主模型
        try {
            UUID userId = RequestContextHolder.isBound() ? RequestContextHolder.currentUserId() : null;
            ChatModel mainModel = userId != null
                    ? modelFactory.getChatModel(userId)
                    : modelFactory.getDefaultChatModel();

            if (mainModel == null) {
                return "AI 模型当前不可用，请检查配置。";
            }

            String systemPrompt = buildSystemPrompt(analysisType) +
                    "\n注意：当前使用的是通用模型，分析深度可能有限。";
            String userPrompt = buildUserPrompt(query, context, analysisType);

            ChatClient client = ChatClient.builder(mainModel).build();
            return client.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

        } catch (Exception e) {
            log.error("降级到主模型也失败: {}", e.getMessage());
            return "AI 服务当前不可用，请稍后重试。";
        }
    }

    /**
     * 处理错误
     */
    private String handleError(Exception e, String query, String context, String analysisType) {
        if (fallbackToMain) {
            log.info("推理出错，尝试降级到主模型");
            return handleModelUnavailable(query, context, analysisType);
        }
        return "深度思考过程中出现错误，请稍后重试。";
    }

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt(String analysisType) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个专业的小说创作深度分析专家。");

        if (analysisType != null) {
            switch (analysisType.toLowerCase()) {
                case "plot" -> prompt.append("""
                    
                    你的任务是分析剧情的逻辑合理性。
                    分析要点：
                    1. 因果关系是否合理
                    2. 时间线是否一致
                    3. 角色行为是否符合设定
                    4. 是否存在逻辑漏洞
                    5. 给出具体的改进建议
                    """);
                case "character" -> prompt.append("""
                    
                    你的任务是深入分析角色心理。
                    分析要点：
                    1. 角色的核心动机
                    2. 心理状态的变化
                    3. 行为背后的逻辑
                    4. 性格一致性
                    5. 成长弧线建议
                    """);
                case "foreshadowing" -> prompt.append("""
                    
                    你的任务是设计伏笔和悬念。
                    设计要点：
                    1. 伏笔要自然，不突兀
                    2. 与后续剧情有机结合
                    3. 考虑读者的阅读体验
                    4. 提供多个备选方案
                    5. 说明每个方案的优缺点
                    """);
                case "conflict" -> prompt.append("""
                    
                    你的任务是分析和解决剧情冲突。
                    分析要点：
                    1. 冲突的根源
                    2. 各方立场和动机
                    3. 可能的解决方案
                    4. 每个方案的后果
                    5. 推荐的最佳方案
                    """);
            }
        }

        prompt.append("\n请用中文回答，语言要专业但易懂。给出具体、可操作的建议。");
        return prompt.toString();
    }

    /**
     * 构建用户提示词
     */
    private String buildUserPrompt(String query, String context, String analysisType) {
        StringBuilder prompt = new StringBuilder();

        if (context != null && !context.isBlank()) {
            prompt.append("【相关背景信息】\n");
            prompt.append(context);
            prompt.append("\n\n");
        }

        prompt.append("【需要分析的问题】\n");
        prompt.append(query);

        return prompt.toString();
    }
}
