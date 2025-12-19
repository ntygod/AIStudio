package com.inkflow.module.plotloop.entity;

/**
 * 伏笔状态枚举
 */
public enum PlotLoopStatus {
    /**
     * 开放状态 - 伏笔已埋下，等待回收
     */
    OPEN,
    
    /**
     * 紧急状态 - 超过10章未回收
     */
    URGENT,
    
    /**
     * 已关闭 - 伏笔已回收
     */
    CLOSED,
    
    /**
     * 已放弃 - 决定不再回收此伏笔
     */
    ABANDONED
}
