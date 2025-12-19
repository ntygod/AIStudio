package com.inkflow.module.ai_bridge.tool;

import com.inkflow.module.ai_bridge.chat.DynamicChatModelFactory;
import com.inkflow.module.rag.service.HybridSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 创意生成工具
 * 支持多种生成类型：章节内容、角色背景、大纲、世界观、故事块
 * 
 * <p>生成类型：
 * <ul>
 *   <li>CHAPTER_CONTENT - 章节正文内容</li>
 *   <li>CHARACTER_BACKGROUND - 角色背景故事</li>
 *   <li>OUTLINE - 故事大纲</li>
 *   <li>WORLD_STRUCTURE - 世界观设定</li>
 *   <li>STORY_BLOCK - 故事块（场景片段）</li>
 * </ul>
 *
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreativeGenTool {

    private final DynamicChatModelFactory chatModelFactory;
    private final HybridSearchService hybridSearchService;

    /**
     * 生成类型枚举
     */
    public enum GenerationType {
        CHAPTER_CONTENT("章节内容", "生成小说章节的正文内容"),
        CHARACTER_BACKGROUND("角色背景", "生成角色的背景故事和设定"),
        OUTLINE("故事大纲", "生成故事的整体大纲或章节大纲"),
        WORLD_STRUCTURE("世界观", "生成世界观、力量体系、地理环境等设定"),
        STORY_BLOCK("故事块", "生成单个场景或片段");

        private final String displayName;
        private final String description;

        GenerationType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }


    /**
     * 生成章节内容
     * 根据大纲和上下文生成章节正文
     */
    @Tool(description = "生成章节内容：根据章节大纲和相关设定生成小说正文。" +
                        "会自动检索相关角色、设定作为上下文。")
    public String generateChapterContent(
            @ToolParam(description = "项目ID") String projectId,
            @ToolParam(description = "章节大纲或节拍，描述本章要写的内容") String outline,
            @ToolParam(description = "字数要求，如 2000") int wordCount,
            @ToolParam(description = "额外要求或风格指导", required = false) String additionalRequirements) {

        log.info("生成章节内容: projectId={}, wordCount={}", projectId, wordCount);
        return generate(projectId, GenerationType.CHAPTER_CONTENT, outline, wordCount, additionalRequirements);
    }

    /**
     * 生成角色背景
     * 根据角色基本信息生成详细背景故事
     */
    @Tool(description = "生成角色背景：根据角色基本信息生成详细的背景故事、性格特点、成长经历。")
    public String generateCharacterBackground(
            @ToolParam(description = "项目ID") String projectId,
            @ToolParam(description = "角色基本信息，如名字、身份、特点等") String characterInfo,
            @ToolParam(description = "背景故事字数要求") int wordCount,
            @ToolParam(description = "额外要求", required = false) String additionalRequirements) {

        log.info("生成角色背景: projectId={}", projectId);
        return generate(projectId, GenerationType.CHARACTER_BACKGROUND, characterInfo, wordCount, additionalRequirements);
    }

    /**
     * 生成故事大纲
     * 根据故事概念生成结构化大纲
     */
    @Tool(description = "生成故事大纲：根据故事概念和核心冲突生成结构化的故事大纲。")
    public String generateOutline(
            @ToolParam(description = "项目ID") String projectId,
            @ToolParam(description = "故事概念、核心冲突、主要角色等") String concept,
            @ToolParam(description = "大纲详细程度：brief(简要), detailed(详细), chapter(章节级)") String detailLevel,
            @ToolParam(description = "额外要求", required = false) String additionalRequirements) {

        log.info("生成故事大纲: projectId={}, detailLevel={}", projectId, detailLevel);
        String prompt = concept + "\n详细程度: " + detailLevel;
        return generate(projectId, GenerationType.OUTLINE, prompt, 0, additionalRequirements);
    }

    /**
     * 生成世界观设定
     * 根据基础概念生成世界观、力量体系等
     */
    @Tool(description = "生成世界观设定：根据基础概念生成世界观、力量体系、地理环境、社会结构等设定。")
    public String generateWorldStructure(
            @ToolParam(description = "项目ID") String projectId,
            @ToolParam(description = "世界观基础概念，如类型、时代、特色等") String concept,
            @ToolParam(description = "要生成的设定类型：power_system(力量体系), geography(地理), society(社会), all(全部)") String settingType,
            @ToolParam(description = "额外要求", required = false) String additionalRequirements) {

        log.info("生成世界观设定: projectId={}, settingType={}", projectId, settingType);
        String prompt = concept + "\n设定类型: " + settingType;
        return generate(projectId, GenerationType.WORLD_STRUCTURE, prompt, 0, additionalRequirements);
    }

    /**
     * 生成故事块
     * 生成单个场景或片段
     */
    @Tool(description = "生成故事块：生成单个场景或片段，如对话、动作场景、心理描写等。")
    public String generateStoryBlock(
            @ToolParam(description = "项目ID") String projectId,
            @ToolParam(description = "场景描述，包括参与角色、场景类型、情节要点") String sceneDescription,
            @ToolParam(description = "字数要求") int wordCount,
            @ToolParam(description = "额外要求", required = false) String additionalRequirements) {

        log.info("生成故事块: projectId={}, wordCount={}", projectId, wordCount);
        return generate(projectId, GenerationType.STORY_BLOCK, sceneDescription, wordCount, additionalRequirements);
    }


    /**
     * 通用生成方法
     * 集成 RAG 上下文检索
     */
    private String generate(
            String projectIdStr,
            GenerationType type,
            String prompt,
            int wordCount,
            String additionalRequirements) {

        try {
            UUID projectId = UUID.fromString(projectIdStr);

            // 1. 检索相关上下文
            String ragContext = retrieveContext(projectId, prompt, type);

            // 2. 构建系统提示词
            String systemPrompt = buildSystemPrompt(type, wordCount, additionalRequirements);

            // 3. 构建用户提示词
            String userPrompt = buildUserPrompt(type, prompt, ragContext);

            // 4. 调用模型生成
            ChatModel chatModel = chatModelFactory.getDefaultModel();
            ChatClient client = ChatClient.builder(chatModel).build();

            String response = client.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            log.info("生成完成: type={}, 响应长度={}", type, response != null ? response.length() : 0);
            return response;

        } catch (IllegalArgumentException e) {
            log.error("无效的项目ID: {}", projectIdStr);
            return "错误：无效的项目ID格式";
        } catch (Exception e) {
            log.error("生成失败: type={}, error={}", type, e.getMessage(), e);
            return "生成失败：" + e.getMessage();
        }
    }

    /**
     * 检索相关上下文
     */
    private String retrieveContext(UUID projectId, String prompt, GenerationType type) {
        try {
            // 根据生成类型决定检索策略
            int limit = switch (type) {
                case CHAPTER_CONTENT, STORY_BLOCK -> 10;
                case CHARACTER_BACKGROUND -> 5;
                case OUTLINE, WORLD_STRUCTURE -> 8;
            };

            return hybridSearchService.buildContextForGeneration(projectId, prompt, limit)
                    .block();
        } catch (Exception e) {
            log.warn("检索上下文失败，继续生成: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt(GenerationType type, int wordCount, String additionalRequirements) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是一个专业的小说创作助手。");

        // 根据类型添加特定指导
        switch (type) {
            case CHAPTER_CONTENT -> {
                prompt.append("你的任务是根据大纲生成高质量的小说章节内容。\n");
                prompt.append("要求：\n");
                prompt.append("1. 文笔流畅，描写生动\n");
                prompt.append("2. 角色对话自然，符合人物性格\n");
                prompt.append("3. 场景描写细腻，有画面感\n");
                prompt.append("4. 情节推进合理，节奏把控得当\n");
                if (wordCount > 0) {
                    prompt.append("5. 字数要求：约 ").append(wordCount).append(" 字\n");
                }
            }
            case CHARACTER_BACKGROUND -> {
                prompt.append("你的任务是为角色创作详细的背景故事。\n");
                prompt.append("要求：\n");
                prompt.append("1. 背景故事要有深度，能解释角色的性格成因\n");
                prompt.append("2. 包含关键的人生经历和转折点\n");
                prompt.append("3. 与世界观设定保持一致\n");
                prompt.append("4. 为角色的动机和行为提供合理解释\n");
                if (wordCount > 0) {
                    prompt.append("5. 字数要求：约 ").append(wordCount).append(" 字\n");
                }
            }
            case OUTLINE -> {
                prompt.append("你的任务是创作结构化的故事大纲。\n");
                prompt.append("要求：\n");
                prompt.append("1. 结构清晰，层次分明\n");
                prompt.append("2. 包含主线和重要支线\n");
                prompt.append("3. 标注关键转折点和高潮\n");
                prompt.append("4. 考虑伏笔和悬念的布置\n");
            }
            case WORLD_STRUCTURE -> {
                prompt.append("你的任务是创作世界观设定。\n");
                prompt.append("要求：\n");
                prompt.append("1. 设定要有内在逻辑，自洽一致\n");
                prompt.append("2. 考虑设定对剧情的影响\n");
                prompt.append("3. 留有扩展空间\n");
                prompt.append("4. 避免过于复杂或矛盾的设定\n");
            }
            case STORY_BLOCK -> {
                prompt.append("你的任务是创作单个场景或片段。\n");
                prompt.append("要求：\n");
                prompt.append("1. 场景完整，有开头有结尾\n");
                prompt.append("2. 描写细腻，情感到位\n");
                prompt.append("3. 与整体风格保持一致\n");
                if (wordCount > 0) {
                    prompt.append("4. 字数要求：约 ").append(wordCount).append(" 字\n");
                }
            }
        }

        // 添加额外要求
        if (additionalRequirements != null && !additionalRequirements.isBlank()) {
            prompt.append("\n额外要求：").append(additionalRequirements).append("\n");
        }

        return prompt.toString();
    }

    /**
     * 构建用户提示词
     */
    private String buildUserPrompt(GenerationType type, String prompt, String ragContext) {
        StringBuilder userPrompt = new StringBuilder();

        // 添加 RAG 上下文
        if (ragContext != null && !ragContext.isBlank()) {
            userPrompt.append("【相关设定和上下文】\n");
            userPrompt.append(ragContext);
            userPrompt.append("\n\n");
        }

        // 添加任务描述
        userPrompt.append("【").append(type.getDisplayName()).append("任务】\n");
        userPrompt.append(prompt);

        return userPrompt.toString();
    }
}
