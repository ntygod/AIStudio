package com.inkflow.module.ai_bridge.tool;

import com.inkflow.module.rag.dto.SearchResult;
import com.inkflow.module.rag.service.HybridSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * RAG检索工具
 * 为AI提供知识库检索能力
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RAGSearchTool {

    private final HybridSearchService hybridSearchService;

    @Tool(description = "搜索小说设定和知识库，返回与查询相关的内容")
    public String searchKnowledge(
            @ToolParam(description = "项目ID") String projectId,
            @ToolParam(description = "搜索查询词") String query,
            @ToolParam(description = "返回结果数量，默认5", required = false) Integer topK) {

        log.info("RAG搜索: projectId={}, query={}, topK={}", projectId, query, topK);

        int limit = topK != null && topK > 0 ? topK : 5;

        try {
            List<SearchResult> results = hybridSearchService
                    .search(UUID.fromString(projectId), query, limit)
                    .block();

            if (results == null || results.isEmpty()) {
                return "未找到与\"" + query + "\"相关的内容";
            }

            return formatResults(results);
        } catch (Exception e) {
            log.error("RAG搜索失败", e);
            return "搜索失败：" + e.getMessage();
        }
    }

    @Tool(description = "按类型搜索知识库，如角色、百科、章节等")
    public String searchByType(
            @ToolParam(description = "项目ID") String projectId,
            @ToolParam(description = "内容类型：character, wiki_entry, story_block") String sourceType,
            @ToolParam(description = "搜索查询词") String query,
            @ToolParam(description = "返回结果数量，默认5", required = false) Integer topK) {

        log.info("按类型RAG搜索: projectId={}, type={}, query={}", projectId, sourceType, query);

        int limit = topK != null && topK > 0 ? topK : 5;

        try {
            List<SearchResult> results = hybridSearchService
                    .searchBySourceType(UUID.fromString(projectId), sourceType, query, limit)
                    .block();

            if (results == null || results.isEmpty()) {
                return "未找到类型为\"" + sourceType + "\"且与\"" + query + "\"相关的内容";
            }

            return formatResults(results);
        } catch (Exception e) {
            log.error("按类型RAG搜索失败", e);
            return "搜索失败：" + e.getMessage();
        }
    }

    @Tool(description = "搜索角色设定")
    public String searchCharacters(
            @ToolParam(description = "项目ID") String projectId,
            @ToolParam(description = "角色名或特征描述") String query) {

        return searchByType(projectId, "character", query, 5);
    }

    @Tool(description = "搜索世界观设定")
    public String searchWiki(
            @ToolParam(description = "项目ID") String projectId,
            @ToolParam(description = "设定关键词") String query) {

        return searchByType(projectId, "wiki_entry", query, 5);
    }

    @Tool(description = "搜索已写章节内容")
    public String searchChapters(
            @ToolParam(description = "项目ID") String projectId,
            @ToolParam(description = "内容关键词") String query) {

        return searchByType(projectId, "story_block", query, 5);
    }

    /**
     * 格式化搜索结果
     */
    private String formatResults(List<SearchResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("找到").append(results.size()).append("条相关内容：\n\n");

        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            sb.append(i + 1).append(". ");

            // 添加类型标签
            String typeLabel = switch (result.getSourceType()) {
                case "character" -> "[角色]";
                case "wiki_entry" -> "[设定]";
                case "story_block" -> "[章节]";
                default -> "[" + result.getSourceType() + "]";
            };
            sb.append(typeLabel).append(" ");

            // 添加名称（如果有）
            if (result.getMetadata() != null) {
                String name = (String) result.getMetadata().getOrDefault("name",
                        result.getMetadata().getOrDefault("title", ""));
                if (!name.isEmpty()) {
                    sb.append(name).append(": ");
                }
            }

            // 添加内容摘要
            String content = result.getContent();
            if (content != null) {
                if (content.length() > 200) {
                    content = content.substring(0, 200) + "...";
                }
                sb.append(content);
            }

            // 添加相似度
            if (result.getSimilarity() != null) {
                sb.append(" (相关度: ").append(String.format("%.2f", result.getSimilarity())).append(")");
            }

            sb.append("\n\n");
        }

        return sb.toString();
    }
}
