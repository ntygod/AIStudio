package com.inkflow.module.agent.service;

import com.inkflow.module.agent.core.Intent;
import com.inkflow.module.agent.dto.ChatRequest;
import com.inkflow.module.agent.dto.ChatRequestDto;
import com.inkflow.module.ai_bridge.service.PhaseInferenceService;
import com.inkflow.module.project.entity.CreationPhase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 请求适配服务
 * 负责将外部 DTO 转换为内部 ChatRequest
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequestAdapterService {

    private final PhaseInferenceService phaseInferenceService;

    /**
     * 适配请求（统一入口）
     * 根据请求内容自动判断是普通聊天还是场景创作
     */
    public ChatRequest adapt(ChatRequestDto dto, UUID userId) {
        if (dto.isSceneCreation()) {
            return adaptSceneRequest(dto, userId);
        }
        return adaptChatRequest(dto, userId);
    }

    /**
     * 适配普通聊天请求
     */
    private ChatRequest adaptChatRequest(ChatRequestDto dto, UUID userId) {
        CreationPhase phase = resolvePhase(dto.projectId(), dto.message(), dto.phase());
        String sessionId = resolveSessionId(userId, dto.projectId(), dto.sessionId());
        Map<String, Object> metadata = buildMetadata(dto, userId);
        Intent intentHint = parseIntentHint(dto.message());
        
        return new ChatRequest(
            dto.message(),
            dto.projectId(),
            sessionId,
            phase,
            intentHint,
            metadata
        );
    }

    /**
     * 适配场景创作请求
     */
    private ChatRequest adaptSceneRequest(ChatRequestDto dto, UUID userId) {
        String enhancedPrompt = buildEnhancedPrompt(dto);
        Map<String, Object> metadata = buildMetadata(dto, userId);
        String sessionId = dto.sessionId() != null ? dto.sessionId() 
            : "scene_" + UUID.randomUUID().toString().substring(0, 8);
        
        return new ChatRequest(
            enhancedPrompt,
            dto.projectId(),
            sessionId,
            CreationPhase.WRITING,
            Intent.WRITE_CONTENT,
            metadata
        );
    }

    // ========== 私有方法 ==========

    private CreationPhase resolvePhase(UUID projectId, String message, String phaseStr) {
        if (phaseStr != null && !phaseStr.isBlank()) {
            try {
                return CreationPhase.valueOf(phaseStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("无效的阶段值: {}, 将自动推断", phaseStr);
            }
        }
        return phaseInferenceService.inferPhase(projectId, message);
    }

    private String resolveSessionId(UUID userId, UUID projectId, String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionId;
        }
        String userPart = userId != null ? userId.toString().substring(0, 8) : "anonymous";
        String projectPart = projectId != null ? projectId.toString().substring(0, 8) : "default";
        return "session_" + userPart + "_" + projectPart;
    }

    private Map<String, Object> buildMetadata(ChatRequestDto dto, UUID userId) {
        Map<String, Object> metadata = new HashMap<>();
        if (userId != null) metadata.put("userId", userId.toString());
        if (dto.projectId() != null) metadata.put("projectId", dto.projectId().toString());
        metadata.put("source", "agent-controller");
        
        // 选项
        metadata.put("consistency", dto.consistencyEnabled());
        metadata.put("ragEnabled", dto.ragSearchEnabled());
        
        // 场景相关
        if (dto.chapterId() != null) metadata.put("chapterId", dto.chapterId().toString());
        if (dto.characterIds() != null && !dto.characterIds().isEmpty()) {
            metadata.put("characterIds", dto.characterIds().stream().map(UUID::toString).toList());
        }
        if (dto.sceneType() != null) metadata.put("sceneType", dto.sceneType());
        
        return metadata;
    }

    private Intent parseIntentHint(String message) {
        if (message == null || message.isBlank()) return null;
        
        String trimmed = message.trim().toLowerCase();
        if (trimmed.startsWith("/write")) return Intent.WRITE_CONTENT;
        if (trimmed.startsWith("/plan")) return Intent.PLAN_OUTLINE;
        if (trimmed.startsWith("/check")) return Intent.CHECK_CONSISTENCY;
        if (trimmed.startsWith("/name")) return Intent.GENERATE_NAME;
        if (trimmed.startsWith("/world")) return Intent.PLAN_WORLD;
        if (trimmed.startsWith("/character")) return Intent.PLAN_CHARACTER;
        return null;
    }

    private String buildEnhancedPrompt(ChatRequestDto dto) {
        StringBuilder builder = new StringBuilder(dto.message());
        
        if (dto.sceneType() != null) {
            builder.insert(0, "【场景类型: " + dto.sceneType() + "】\n");
        }
        if (dto.targetWordCount() != null && dto.targetWordCount() > 0) {
            builder.append("\n\n【目标字数: ").append(dto.targetWordCount()).append("字】");
        }
        return builder.toString();
    }
}
