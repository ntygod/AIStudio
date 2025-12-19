package com.inkflow.module.session.controller;

import com.inkflow.module.ai_bridge.memory.ChatMemoryFactory;
import com.inkflow.module.auth.entity.User;
import com.inkflow.module.auth.security.UserPrincipal;
import com.inkflow.module.session.dto.SessionDto;
import com.inkflow.module.session.service.SessionManagementService;
import com.inkflow.module.session.service.SessionResumeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 会话管理 API
 *
 * @author zsg
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
@Tag(name = "Session Management", description = "会话管理接口")
public class SessionController {

    private final SessionManagementService sessionService;
    private final SessionResumeService sessionResumeService;
    private final ChatMemoryFactory chatMemoryFactory;

    /**
     * 获取当前用户的所有活跃会话
     */
    @GetMapping
    public ResponseEntity<List<SessionDto>> getActiveSessions(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(sessionService.getActiveSessions(principal.getId()));
    }

    /**
     * 获取指定会话详情
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionDto> getSession(@PathVariable UUID sessionId) {
        return sessionService.getSession(sessionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 终止指定会话
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> terminateSession(@PathVariable UUID sessionId) {
        sessionService.terminateSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 终止当前用户的所有其他会话
     */
    @DeleteMapping("/others")
    @Operation(summary = "终止其他会话", description = "终止当前用户的所有其他登录会话")
    public ResponseEntity<Map<String, Integer>> terminateOtherSessions(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader("X-Session-Id") UUID currentSessionId) {
        int count = sessionService.terminateAllSessions(principal.getId(), currentSessionId);
        return ResponseEntity.ok(Map.of("terminated", count));
    }

    /**
     * 获取会话恢复提示
     * 当用户返回项目时，检查是否有可恢复的会话并生成提示
     */
    @GetMapping("/resume/{projectId}")
    @Operation(summary = "获取会话恢复提示", description = "检查上次会话并生成恢复提示")
    public SessionResumeResponse getResumePrompt(
            @AuthenticationPrincipal User user,
            @PathVariable UUID projectId) {
        
        UUID userId = user != null ? user.getId() : null;
        
        if (userId == null) {
            return new SessionResumeResponse(false, "欢迎使用 InkFlow！请告诉我您想创作什么样的故事？", null);
        }
        
        log.info("获取会话恢复提示: userId={}, projectId={}", userId, projectId);
        
        var resumeInfo = sessionResumeService.checkForPreviousSession(userId, projectId);
        
        if (resumeInfo.isEmpty()) {
            String welcomePrompt = sessionResumeService.generateResumePrompt(userId, projectId);
            return new SessionResumeResponse(false, welcomePrompt, null);
        }
        
        String resumePrompt = sessionResumeService.generateResumePrompt(userId, projectId);
        return new SessionResumeResponse(true, resumePrompt, resumeInfo.get().getSessionId());
    }

    /**
     * 清除对话历史
     */
    @DeleteMapping("/conversations/{conversationId}")
    @Operation(summary = "清除对话历史", description = "清除指定对话的历史记录")
    public ResponseEntity<Void> clearConversation(@PathVariable String conversationId) {
        log.info("清除对话历史: {}", conversationId);
        chatMemoryFactory.clearMemory(conversationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 会话恢复响应
     */
    public record SessionResumeResponse(
        @Parameter(description = "是否有可恢复的会话")
        boolean hasSession,
        
        @Parameter(description = "恢复/欢迎提示")
        String prompt,
        
        @Parameter(description = "上次会话ID（如果有）")
        UUID sessionId
    ) {}
}
