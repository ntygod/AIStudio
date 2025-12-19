package com.inkflow.module.agent.routing;

import com.inkflow.module.agent.core.Intent;
import com.inkflow.module.agent.dto.ChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Fast Path 过滤器
 * 用于跳过 ThinkingAgent 意图分析，直接路由到目标 Agent
 * Fast Path 触发条件：
 * 1. 请求包含 intentHint 参数
 * 2. 消息以命令前缀开头（/write, /plan 等）
 *
 */
@Slf4j
@Component
public class FastPathFilter {
    
    /**
     * 尝试 Fast Path 路由
     * 
     * @param request 聊天请求
     * @return 如果可以 Fast Path，返回意图；否则返回空
     */
    public Optional<FastPathResult> tryFastPath(ChatRequest request) {
        // 1. 检查 intentHint 参数
        if (request.hasFastPathHint()) {
            Intent intent = request.intentHint();
            log.info("[FastPath] 使用 intentHint 直接路由: intent={}, targetAgent={}", 
                    intent, intent.getTargetAgentName());
            return Optional.of(FastPathResult.fromIntentHint(intent));
        }
        
        // 2. 检查命令前缀
        if (request.hasCommandPrefix()) {
            Intent intent = request.parseCommandIntent();
            if (intent != null) {
                log.info("[FastPath] 使用命令前缀路由: prefix={}, intent={}, targetAgent={}", 
                        extractCommandPrefix(request.message()), intent, intent.getTargetAgentName());
                return Optional.of(FastPathResult.fromCommandPrefix(intent, extractCommandPrefix(request.message())));
            }
        }
        
        // 无法 Fast Path
        log.debug("[FastPath] 无法 Fast Path，需要 ThinkingAgent 分析");
        return Optional.empty();
    }
    
    /**
     * 提取命令前缀
     */
    private String extractCommandPrefix(String message) {
        if (message == null) return null;
        int spaceIndex = message.indexOf(' ');
        return spaceIndex > 0 ? message.substring(0, spaceIndex) : message;
    }
}
