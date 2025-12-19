package com.inkflow.module.agent.routing;

import com.inkflow.module.agent.core.Intent;

/**
 * Fast Path 结果
 * 
 * Requirements: 2.1-2.5
 *
 * @param canSkip 是否可以跳过 ThinkingAgent
 * @param intent 识别的意图
 * @param targetAgent 目标 Agent 名称
 * @param source Fast Path 来源
 * @param commandPrefix 命令前缀（如果有）
 */
public record FastPathResult(
    boolean canSkip,
    Intent intent,
    String targetAgent,
    FastPathSource source,
    String commandPrefix
) {
    
    /**
     * 从 intentHint 创建结果
     */
    public static FastPathResult fromIntentHint(Intent intent) {
        return new FastPathResult(
            true,
            intent,
            intent.getTargetAgentName(),
            FastPathSource.INTENT_HINT,
            null
        );
    }
    
    /**
     * 从命令前缀创建结果
     */
    public static FastPathResult fromCommandPrefix(Intent intent, String prefix) {
        return new FastPathResult(
            true,
            intent,
            intent.getTargetAgentName(),
            FastPathSource.COMMAND_PREFIX,
            prefix
        );
    }
    
    /**
     * Fast Path 来源
     */
    public enum FastPathSource {
        /**
         * 来自 intentHint 参数
         */
        INTENT_HINT,
        
        /**
         * 来自命令前缀
         */
        COMMAND_PREFIX
    }
}
