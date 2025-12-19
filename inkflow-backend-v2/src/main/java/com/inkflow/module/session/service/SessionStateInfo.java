package com.inkflow.module.session.service;

import com.inkflow.module.project.entity.CreationPhase;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 会话状态信息
 * 检索会话状态时包括最后活动时间、当前阶段和待处理任务
 *
 * @author zsg
 * @date 2025/12/17
 */
@Data
@Builder
public class SessionStateInfo {
    
    /**
     * 会话ID
     */
    private UUID sessionId;
    
    /**
     * 用户ID
     */
    private UUID userId;
    
    /**
     * 当前项目ID
     */
    private UUID currentProjectId;
    
    /**
     * 当前创作阶段
     */
    private CreationPhase currentPhase;
    
    /**
     * 最后活动时间
     */
    private LocalDateTime lastActivityTime;
    
    /**
     * 待处理任务
     */
    private String pendingTasks;
    
    /**
     * 会话是否活跃
     */
    private boolean active;
}
