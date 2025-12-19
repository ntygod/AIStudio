package com.inkflow.module.agent.dto;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * 统一聊天请求 DTO
 * 支持普通聊天和场景创作
 */
public record ChatRequestDto(
    @NotNull(message = "项目ID不能为空")
    @Parameter(description = "项目ID", required = true)
    UUID projectId,
    
    @NotBlank(message = "消息内容不能为空")
    @Parameter(description = "用户消息", required = true)
    String message,
    
    @Parameter(description = "创作阶段（可选，不传则自动推断）")
    String phase,
    
    @Parameter(description = "会话ID（可选，不传则自动生成）")
    String sessionId,
    
    // ========== 场景创作相关（可选） ==========
    
    @Parameter(description = "章节ID（场景创作时使用）")
    UUID chapterId,
    
    @Parameter(description = "参与角色ID列表（场景创作时使用）")
    List<UUID> characterIds,
    
    @Parameter(description = "场景类型（对话、动作、描写等）")
    String sceneType,
    
    @Parameter(description = "目标字数")
    Integer targetWordCount,
    
    // ========== 选项 ==========
    
    @Parameter(description = "是否启用一致性检查（默认 false）")
    Boolean consistency,
    
    @Parameter(description = "是否启用 RAG 检索（默认 true）")
    Boolean ragEnabled
) {
    /**
     * 是否为场景创作请求
     */
    public boolean isSceneCreation() {
        return sceneType != null || chapterId != null || characterIds != null;
    }
    
    /**
     * 获取一致性检查开关（默认 false）
     */
    public boolean consistencyEnabled() {
        return consistency != null && consistency;
    }
    
    /**
     * 获取 RAG 开关（默认 true）
     */
    public boolean ragSearchEnabled() {
        return ragEnabled == null || ragEnabled;
    }
}
