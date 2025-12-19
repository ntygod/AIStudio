package com.inkflow.module.progress.service;

import lombok.Builder;
import lombok.Data;

/**
 * 阶段转换检查结果
 * 
 * Requirements: 15.3
 *
 * @author zsg
 * @date 2025/12/17
 */
@Data
@Builder
public class PhaseTransitionCheck {
    
    /**
     * 是否可以转换
     */
    private boolean canTransition;
    
    /**
     * 提示消息
     */
    private String message;
    
    /**
     * 缺失的前置条件
     */
    private String missingPrerequisites;
}
