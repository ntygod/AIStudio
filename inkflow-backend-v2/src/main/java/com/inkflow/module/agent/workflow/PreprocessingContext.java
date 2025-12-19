package com.inkflow.module.agent.workflow;

import com.inkflow.module.rag.dto.SearchResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 预处理上下文
 * 请求级别的上下文，包含 RAG 检索结果、角色状态、风格样本等
 * 与 SessionContext 的区别：
 * - SessionContext: 会话级，跨请求持久化，存储最近实体和工作内存
 * - PreprocessingContext: 请求级，单次请求有效，存储预处理结果
 *
 */
public record PreprocessingContext(
    List<SearchResult> ragResults,
    Map<UUID, CharacterState> characterStates,
    String styleContext,
    Map<String, Object> additionalContext
) {
    
    /**
     * 创建空的预处理上下文
     */
    public static PreprocessingContext empty() {
        return new PreprocessingContext(List.of(), Map.of(), "", Map.of());
    }
    
    /**
     * 转换为可注入到 Prompt 的字符串
     */
    public String toContextString() {
        StringBuilder sb = new StringBuilder();
        
        if (ragResults != null && !ragResults.isEmpty()) {
            sb.append("## 相关设定\n");
            for (SearchResult r : ragResults) {
                String typeLabel = switch (r.getSourceType()) {
                    case "character" -> "[角色]";
                    case "wiki_entry" -> "[设定]";
                    case "story_block" -> "[章节]";
                    default -> "[" + r.getSourceType() + "]";
                };
                sb.append("- ").append(typeLabel).append(" ").append(r.getContent()).append("\n");
            }
        }

        if (characterStates != null && !characterStates.isEmpty()) {
            sb.append("\n## 角色当前状态\n");
            characterStates.forEach((id, state) -> 
                sb.append("- ").append(state.name()).append(": ").append(state.currentState()).append("\n"));
        }
        
        if (styleContext != null && !styleContext.isBlank()) {
            sb.append("\n## 风格参考\n").append(styleContext);
        }
        
        return sb.toString();
    }
    
    /**
     * 检查是否为空上下文
     */
    public boolean isEmpty() {
        return (ragResults == null || ragResults.isEmpty())
            && (characterStates == null || characterStates.isEmpty())
            && (styleContext == null || styleContext.isBlank())
            && (additionalContext == null || additionalContext.isEmpty());
    }
    
    /**
     * 创建带 RAG 结果的上下文
     */
    public PreprocessingContext withRagResults(List<SearchResult> results) {
        return new PreprocessingContext(results, this.characterStates, this.styleContext, this.additionalContext);
    }
    
    /**
     * 创建带角色状态的上下文
     */
    public PreprocessingContext withCharacterStates(Map<UUID, CharacterState> states) {
        return new PreprocessingContext(this.ragResults, states, this.styleContext, this.additionalContext);
    }
    
    /**
     * 创建带风格上下文的上下文
     */
    public PreprocessingContext withStyleContext(String style) {
        return new PreprocessingContext(this.ragResults, this.characterStates, style, this.additionalContext);
    }
}
