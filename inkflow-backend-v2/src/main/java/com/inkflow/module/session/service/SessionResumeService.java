package com.inkflow.module.session.service;

import com.inkflow.module.conversation.entity.ConversationHistory;
import com.inkflow.module.conversation.repository.ConversationHistoryRepository;
import com.inkflow.module.progress.service.CreationProgress;
import com.inkflow.module.progress.service.CreationProgressService;
import com.inkflow.module.project.entity.CreationPhase;
import com.inkflow.module.project.entity.Project;
import com.inkflow.module.project.repository.ProjectRepository;
import com.inkflow.module.session.entity.UserSession;
import com.inkflow.module.session.repository.UserSessionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 会话恢复服务
 * 检测上次会话，生成恢复提示
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionResumeService {

    private final ConversationHistoryRepository conversationRepository;
    private final ProjectRepository projectRepository;
    private final CreationProgressService progressService;
    private final UserSessionRepository sessionRepository;
    private final SessionPersistenceService persistenceService;

    // 会话过期时间（小时）
    private static final int SESSION_EXPIRY_HOURS = 24;

    /**
     * 服务启动时恢复活跃会话到 Redis
     *用户返回后服务重启时从 Redis 恢复会话状态
     */
    @PostConstruct
    public void restoreSessionsOnStartup() {
        log.info("开始恢复活跃会话到 Redis...");
        
        try {
            // 获取所有活跃且未过期的会话
            List<UserSession> activeSessions = sessionRepository.findAll().stream()
                    .filter(UserSession::isActive)
                    .filter(s -> !s.isExpired())
                    .toList();
            
            AtomicInteger restoredCount = new AtomicInteger(0);
            AtomicInteger skippedCount = new AtomicInteger(0);
            
            activeSessions.forEach(session -> {
                try {
                    // 检查 Redis 中是否已存在
                    if (!persistenceService.existsInRedis(session.getId())) {
                        // 持久化到 Redis
                        persistenceService.persistToRedis(session);
                        restoredCount.incrementAndGet();
                    } else {
                        skippedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.warn("恢复会话失败: sessionId={}, error={}", session.getId(), e.getMessage());
                }
            });
            
            log.info("会话恢复完成: 恢复={}, 跳过={}, 总活跃会话={}", 
                    restoredCount.get(), skippedCount.get(), activeSessions.size());
        } catch (Exception e) {
            log.error("会话恢复过程出错", e);
        }
    }

    /**
     * 恢复指定用户的会话状态
     * 恢复会话状态包括最后活动时间、当前阶段和待处理任务
     * 
     * @param userId 用户ID
     * @return 恢复的会话数量
     */
    @Transactional(readOnly = true)
    public int restoreUserSessions(UUID userId) {
        List<UserSession> userSessions = sessionRepository.findByUserIdAndActiveTrue(userId);
        int restoredCount = 0;
        
        for (UserSession session : userSessions) {
            if (!session.isExpired()) {
                // 尝试从 Redis 恢复，如果不存在则重新持久化
                Optional<SessionPersistenceService.SessionData> redisData = 
                        persistenceService.restoreFromRedis(session.getId());
                
                if (redisData.isEmpty()) {
                    persistenceService.persistToRedis(session);
                    restoredCount++;
                }
            }
        }
        
        log.debug("恢复用户会话: userId={}, count={}", userId, restoredCount);
        return restoredCount;
    }

    /**
     * 获取会话状态（优先从 Redis 获取）
     * 检索会话状态时包括最后活动时间、当前阶段和待处理任务
     * 
     * @param sessionId 会话ID
     * @return 会话状态信息
     */
    @Transactional(readOnly = true)
    public Optional<SessionStateInfo> getSessionState(UUID sessionId) {
        // 优先从 Redis 获取
        Optional<SessionPersistenceService.SessionData> redisData = 
                persistenceService.restoreFromRedis(sessionId);
        
        if (redisData.isPresent()) {
            SessionPersistenceService.SessionData data = redisData.get();
            return Optional.of(buildSessionStateFromRedis(data));
        }
        
        // 回退到数据库
        return sessionRepository.findById(sessionId)
                .filter(UserSession::isActive)
                .filter(s -> !s.isExpired())
                .map(this::buildSessionStateFromEntity);
    }

    /**
     * 同步会话状态到 Redis
     * 
     * @param sessionId 会话ID
     */
    @Transactional(readOnly = true)
    public void syncSessionToRedis(UUID sessionId) {
        sessionRepository.findById(sessionId)
                .filter(UserSession::isActive)
                .filter(s -> !s.isExpired())
                .ifPresent(persistenceService::persistToRedis);
    }

    /**
     * 从 Redis 数据构建会话状态信息
     */
    private SessionStateInfo buildSessionStateFromRedis(SessionPersistenceService.SessionData data) {
        CreationPhase phase = data.currentPhase() != null 
                ? CreationPhase.valueOf(data.currentPhase()) 
                : null;
        
        LocalDateTime lastActivity = data.lastActivityAt() != null 
                ? LocalDateTime.parse(data.lastActivityAt()) 
                : null;
        
        // 获取待处理任务
        String pendingTasks = null;
        if (data.currentProjectId() != null) {
            try {
                CreationProgress progress = progressService.getProgress(data.currentProjectId());
                if (progress != null && progress.getOpenPlotLoops() > 0) {
                    pendingTasks = "有 " + progress.getOpenPlotLoops() + " 个伏笔待回收";
                }
            } catch (Exception e) {
                log.debug("获取项目进度失败: {}", e.getMessage());
            }
        }
        
        return SessionStateInfo.builder()
                .sessionId(data.id())
                .userId(data.userId())
                .currentProjectId(data.currentProjectId())
                .currentPhase(phase)
                .lastActivityTime(lastActivity)
                .pendingTasks(pendingTasks)
                .active(data.active())
                .build();
    }

    /**
     * 从实体构建会话状态信息
     */
    private SessionStateInfo buildSessionStateFromEntity(UserSession session) {
        // 获取待处理任务
        String pendingTasks = null;
        if (session.getCurrentProjectId() != null) {
            try {
                CreationProgress progress = progressService.getProgress(session.getCurrentProjectId());
                if (progress != null && progress.getOpenPlotLoops() > 0) {
                    pendingTasks = "有 " + progress.getOpenPlotLoops() + " 个伏笔待回收";
                }
            } catch (Exception e) {
                log.debug("获取项目进度失败: {}", e.getMessage());
            }
        }
        
        return SessionStateInfo.builder()
                .sessionId(session.getId())
                .userId(session.getUserId())
                .currentProjectId(session.getCurrentProjectId())
                .currentPhase(session.getCurrentPhase())
                .lastActivityTime(session.getLastActivityAt())
                .pendingTasks(pendingTasks)
                .active(session.isActive())
                .build();
    }

    /**
     * 检查是否有可恢复的会话
     */
    @Transactional(readOnly = true)
    public Optional<SessionResumeInfo> checkForPreviousSession(UUID userId, UUID projectId) {
        // 查找最近的会话
        Optional<UUID> latestSessionId = conversationRepository.findLatestSessionId(userId, projectId);
        
        if (latestSessionId.isEmpty()) {
            return Optional.empty();
        }

        // 获取会话的最近消息
        List<ConversationHistory> recentMessages = conversationRepository.findRecentBySessionId(
                latestSessionId.get(), PageRequest.of(0, 5));

        if (recentMessages.isEmpty()) {
            return Optional.empty();
        }

        // 检查会话是否过期
        ConversationHistory lastMessage = recentMessages.get(recentMessages.size() - 1);
        if (isSessionExpired(lastMessage.getCreatedAt())) {
            return Optional.empty();
        }

        // 构建恢复信息
        return Optional.of(buildResumeInfo(userId, projectId, latestSessionId.get(), recentMessages));
    }

    /**
     * 生成恢复提示
     */
    @Transactional(readOnly = true)
    public String generateResumePrompt(UUID userId, UUID projectId) {
        Optional<SessionResumeInfo> resumeInfo = checkForPreviousSession(userId, projectId);
        
        if (resumeInfo.isEmpty()) {
            return generateWelcomePrompt(projectId);
        }

        SessionResumeInfo info = resumeInfo.get();
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("欢迎回来！");
        
        // 添加上次活动信息
        if (info.getLastPhase() != null) {
            prompt.append("上次您在【").append(info.getLastPhase().getDisplayName()).append("】阶段。");
        }
        
        if (info.getLastAction() != null && !info.getLastAction().isBlank()) {
            prompt.append("最后的操作是：").append(truncate(info.getLastAction(), 50)).append("。");
        }
        
        // 添加进度信息
        if (info.getProgress() != null) {
            prompt.append("\n\n当前进度：")
                    .append("角色 ").append(info.getProgress().getCharacterCount()).append(" 个，")
                    .append("设定 ").append(info.getProgress().getWikiEntryCount()).append(" 条，")
                    .append("章节 ").append(info.getProgress().getChapterCount()).append(" 章，")
                    .append("字数 ").append(info.getProgress().getWordCount()).append(" 字。");
        }
        
        // 添加待处理任务
        if (info.getPendingTasks() != null && !info.getPendingTasks().isBlank()) {
            prompt.append("\n\n待处理：").append(info.getPendingTasks());
        }
        
        prompt.append("\n\n您想继续之前的工作，还是开始新的话题？");
        
        return prompt.toString();
    }

    /**
     * 生成欢迎提示（无历史会话时）
     */
    private String generateWelcomePrompt(UUID projectId) {
        Project project = projectRepository.findById(projectId).orElse(null);
        
        if (project == null) {
            return "欢迎使用 InkFlow！请告诉我您想创作什么样的故事？";
        }

        CreationProgress progress = progressService.getProgress(projectId);
        CreationPhase phase = progress.getCurrentPhase();
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("欢迎来到【").append(project.getTitle()).append("】项目！");
        prompt.append("当前处于【").append(phase.getDisplayName()).append("】阶段。");
        
        // 根据阶段给出引导
        String guidance = switch (phase) {
            case IDEA -> "让我们从收集灵感开始，您想创作什么类型的故事？";
            case WORLDBUILDING -> "现在来构建世界观，您想设定什么样的世界背景？";
            case CHARACTER -> "是时候创建角色了，您的主角是什么样的人？";
            case OUTLINE -> "让我们规划故事大纲，您想讲述什么样的故事主线？";
            case WRITING -> "开始正式创作吧，您想从哪一章开始写？";
            case REVISION -> "进入修订阶段，让我们检查一下有没有需要完善的地方。";
            case COMPLETED -> "恭喜完成创作！您想回顾或修改哪个部分？";
        };
        
        prompt.append("\n\n").append(guidance);
        
        return prompt.toString();
    }

    /**
     * 构建恢复信息
     */
    private SessionResumeInfo buildResumeInfo(UUID userId, UUID projectId, 
            UUID sessionId, List<ConversationHistory> recentMessages) {
        
        // 获取最后一条用户消息作为最后操作
        String lastAction = recentMessages.stream()
                .filter(m -> "user".equalsIgnoreCase(m.getRole()))
                .reduce((first, second) -> second)
                .map(ConversationHistory::getContent)
                .orElse(null);

        // 获取最后的阶段
        CreationPhase lastPhase = recentMessages.stream()
                .filter(m -> m.getCreationPhase() != null)
                .reduce((first, second) -> second)
                .map(ConversationHistory::getCreationPhase)
                .orElse(null);

        // 获取当前进度
        CreationProgress progress = null;
        try {
            progress = progressService.getProgress(projectId);
        } catch (Exception e) {
            log.warn("获取项目进度失败: {}", e.getMessage());
        }

        // 检查待处理任务（如开放的伏笔）
        String pendingTasks = null;
        if (progress != null && progress.getOpenPlotLoops() > 0) {
            pendingTasks = "有 " + progress.getOpenPlotLoops() + " 个伏笔待回收";
        }

        return SessionResumeInfo.builder()
                .sessionId(sessionId)
                .userId(userId)
                .projectId(projectId)
                .lastPhase(lastPhase)
                .lastAction(lastAction)
                .lastActivityTime(recentMessages.get(recentMessages.size() - 1).getCreatedAt())
                .progress(progress)
                .pendingTasks(pendingTasks)
                .build();
    }

    /**
     * 检查会话是否过期
     */
    private boolean isSessionExpired(LocalDateTime lastActivity) {
        if (lastActivity == null) return true;
        Duration duration = Duration.between(lastActivity, LocalDateTime.now());
        return duration.toHours() > SESSION_EXPIRY_HOURS;
    }

    /**
     * 截断字符串
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}
