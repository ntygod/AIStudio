package com.inkflow.module.progress.service;

import com.inkflow.module.project.entity.CreationPhase;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * 创作进度数据
 * 追踪实体数量（已移除不实用的阶段完成度计算功能）
 * 
 * Requirements: 12.1-12.4
 *
 * @author zsg
 * @date 2025/12/17
 */
@Data
@Builder
public class CreationProgress {
    
    /**
     * 项目ID
     */
    private UUID projectId;
    
    /**
     * 当前创作阶段
     */
    private CreationPhase currentPhase;
    
    /**
     * 角色数量
     */
    private long characterCount;
    
    /**
     * 设定条目数量
     */
    private long wikiEntryCount;
    
    /**
     * 分卷数量
     */
    private long volumeCount;
    
    /**
     * 章节数量
     */
    private long chapterCount;
    
    /**
     * 总字数
     */
    private long wordCount;
    
    /**
     * 伏笔总数
     */
    private long plotLoopCount;
    
    /**
     * 开放伏笔数量
     */
    private long openPlotLoops;
    
    /**
     * 已闭合伏笔数量
     */
    private long closedPlotLoops;
    
    /**
     * 获取伏笔闭合率
     */
    public double getPlotLoopClosureRate() {
        if (plotLoopCount == 0) return 1.0;
        return (double) closedPlotLoops / plotLoopCount;
    }
    
    /**
     * 获取阶段显示名称
     */
    public String getCurrentPhaseDisplayName() {
        return currentPhase != null ? currentPhase.getDisplayName() : "未知";
    }
    
    /**
     * 获取阶段描述
     */
    public String getCurrentPhaseDescription() {
        return currentPhase != null ? currentPhase.getDescription() : "";
    }
}
