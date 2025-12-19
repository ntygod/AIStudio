package com.inkflow.module.ai_bridge.tool;

import com.inkflow.module.rag.service.HybridSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 风格检索工具
 * 检索用户的写作风格样本，用于生成时参考
 * 
 * <p>功能：
 * <ul>
 *   <li>检索相似风格样本</li>
 *   <li>构建风格引导提示词</li>
 *   <li>分析写作风格特点</li>
 * </ul>
 * 
 * Requirements: 13.1-13.5
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StyleRetrieveTool {

    private final HybridSearchService hybridSearchService;

    @Value("${inkflow.style.min-samples:3}")
    private int minSamples;

    @Value("${inkflow.style.max-samples:5}")
    private int maxSamples;

    /**
     * 检索风格样本
     * 根据上下文检索相似的写作风格样本
     */
    @Tool(description = "检索风格样本：根据当前写作上下文检索用户的写作风格样本，用于生成时参考。" +
                        "返回风格提示词片段，可直接用于指导生成。")
    public String retrieveStyleSamples(
            @ToolParam(description = "项目ID") String projectId,
            @ToolParam(description = "当前写作上下文，用于相似度匹配") String context,
            @ToolParam(description = "返回样本数量，默认3-5个", required = false) Integer limit) {

        log.info("检索风格样本: projectId={}, contextLength={}", projectId,
                context != null ? context.length() : 0);

        try {
            UUID projectUuid = UUID.fromString(projectId);
            int sampleLimit = limit != null ? Math.min(limit, maxSamples) : minSamples;

            // 检索相似内容作为风格参考
            String styleContext = hybridSearchService
                    .buildContextForGeneration(projectUuid, context, sampleLimit)
                    .block();

            if (styleContext == null || styleContext.isBlank()) {
                return buildNoSamplesResponse();
            }

            return buildStylePrompt(styleContext);

        } catch (IllegalArgumentException e) {
            log.error("无效的项目ID: {}", projectId);
            return "错误：无效的项目ID格式";
        } catch (Exception e) {
            log.error("检索风格样本失败: {}", e.getMessage(), e);
            return "";
        }
    }

    /**
     * 获取写作风格指南
     * 分析项目已有内容，提取写作风格特点
     */
    @Tool(description = "获取写作风格指南：分析项目已有内容，提取写作风格特点，生成风格指导。")
    public String getStyleGuide(
            @ToolParam(description = "项目ID") String projectId,
            @ToolParam(description = "要分析的内容类型：dialogue(对话), description(描写), action(动作), all(全部)", required = false) String contentType) {

        log.info("获取写作风格指南: projectId={}, contentType={}", projectId, contentType);

        try {
            UUID projectUuid = UUID.fromString(projectId);
            String type = contentType != null ? contentType : "all";

            // 检索项目内容
            String query = switch (type.toLowerCase()) {
                case "dialogue" -> "对话 说道 问道 回答";
                case "description" -> "描写 景色 环境 氛围";
                case "action" -> "动作 战斗 奔跑 攻击";
                default -> "章节内容";
            };

            String samples = hybridSearchService
                    .buildContextForGeneration(projectUuid, query, maxSamples)
                    .block();

            if (samples == null || samples.isBlank()) {
                return buildNoContentResponse(type);
            }

            return buildStyleGuide(samples, type);

        } catch (Exception e) {
            log.error("获取风格指南失败: {}", e.getMessage(), e);
            return "获取风格指南失败，请稍后重试。";
        }
    }

    /**
     * 匹配写作风格
     * 检查新内容是否与项目风格一致
     */
    @Tool(description = "匹配写作风格：检查新生成的内容是否与项目已有风格一致，给出调整建议。")
    public String matchStyle(
            @ToolParam(description = "项目ID") String projectId,
            @ToolParam(description = "需要检查的新内容") String newContent) {

        log.info("匹配写作风格: projectId={}, contentLength={}", projectId,
                newContent != null ? newContent.length() : 0);

        if (newContent == null || newContent.isBlank()) {
            return "请提供需要检查的内容。";
        }

        try {
            UUID projectUuid = UUID.fromString(projectId);

            // 检索相似内容
            String existingContent = hybridSearchService
                    .buildContextForGeneration(projectUuid, newContent, minSamples)
                    .block();

            if (existingContent == null || existingContent.isBlank()) {
                return "项目中暂无足够的参考内容，无法进行风格匹配。";
            }

            return buildStyleMatchResult(existingContent, newContent);

        } catch (Exception e) {
            log.error("风格匹配失败: {}", e.getMessage(), e);
            return "风格匹配失败，请稍后重试。";
        }
    }

    /**
     * 构建风格提示词
     */
    private String buildStylePrompt(String styleContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("【写作风格参考】\n");
        prompt.append("以下是项目中已有的写作内容，请参考其风格进行创作：\n\n");
        prompt.append(styleContext);
        prompt.append("\n\n请模仿以上内容的：\n");
        prompt.append("1. 叙述节奏和语气\n");
        prompt.append("2. 对话风格和用词习惯\n");
        prompt.append("3. 描写手法和细节程度\n");
        prompt.append("4. 段落结构和过渡方式\n");
        return prompt.toString();
    }

    /**
     * 构建风格指南
     */
    private String buildStyleGuide(String samples, String contentType) {
        StringBuilder guide = new StringBuilder();
        guide.append("【写作风格指南】\n\n");

        String typeDesc = switch (contentType.toLowerCase()) {
            case "dialogue" -> "对话风格";
            case "description" -> "描写风格";
            case "action" -> "动作描写风格";
            default -> "整体写作风格";
        };

        guide.append("基于项目已有内容分析的").append(typeDesc).append("特点：\n\n");
        guide.append("【参考样本】\n");
        guide.append(samples);
        guide.append("\n\n【风格建议】\n");
        guide.append("请在创作时保持与以上样本一致的风格特点。\n");

        return guide.toString();
    }

    /**
     * 构建风格匹配结果
     */
    private String buildStyleMatchResult(String existingContent, String newContent) {
        StringBuilder result = new StringBuilder();
        result.append("【风格匹配分析】\n\n");
        result.append("【项目已有风格参考】\n");
        result.append(truncate(existingContent, 500));
        result.append("\n\n【待检查内容】\n");
        result.append(truncate(newContent, 300));
        result.append("\n\n【建议】\n");
        result.append("请对比以上内容，确保新内容在以下方面与项目风格保持一致：\n");
        result.append("- 叙述视角和人称\n");
        result.append("- 句式长度和节奏\n");
        result.append("- 用词风格和语气\n");
        result.append("- 描写详略程度\n");
        return result.toString();
    }

    /**
     * 无样本时的响应
     */
    private String buildNoSamplesResponse() {
        return "项目中暂无足够的写作样本用于风格参考。建议先创作一些内容，系统会自动学习您的写作风格。";
    }

    /**
     * 无内容时的响应
     */
    private String buildNoContentResponse(String contentType) {
        String typeDesc = switch (contentType.toLowerCase()) {
            case "dialogue" -> "对话";
            case "description" -> "描写";
            case "action" -> "动作";
            default -> "相关";
        };
        return String.format("项目中暂无足够的%s内容用于分析风格。", typeDesc);
    }

    /**
     * 截断文本
     */
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
