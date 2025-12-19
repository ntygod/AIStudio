package com.inkflow.module.session.service;

import com.inkflow.module.progress.service.CreationProgress;
import com.inkflow.module.project.entity.CreationPhase;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 会话恢复信息
 *
 * @author zsg
 * @date 2025/12/17
 */
@Data
@Builder
public class SessionResumeInfo {
    
    /**
     * 会话ID
     */
    private UUID sessionId;
    
    /**
     * 用户ID
     */
    private UUID userId;
    
    /**
     * 项目ID
     */
    private UUID projectId;
    
    /**
     * 上次的创作阶段
     */
    private CreationPhase lastPhase;
    
    /**
     * 上次的操作（最后一条用户消息）
     */
    private String lastAction;
    
    /**
     * 上次活动时间
     */
    private LocalDateTime lastActivityTime;
    
    /**
     * 当前创作进度
     */
    private CreationProgress progress;
    
    /**
     * 待处理任务
     */
    private String pendingTasks;
}
