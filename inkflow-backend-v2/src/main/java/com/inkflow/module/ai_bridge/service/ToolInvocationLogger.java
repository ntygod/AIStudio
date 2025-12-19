package com.inkflow.module.ai_bridge.service;

import com.inkflow.module.ai_bridge.entity.ToolInvocationLog;
import com.inkflow.module.ai_bridge.event.ToolExecutionEvent;
import com.inkflow.module.ai_bridge.repository.ToolInvocationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Tool 调用日志服务
 * 监听 ToolExecutionEvent 并持久化日志
 * 
 * Requirements: 17.1, 17.2, 17.3, 17.4
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolInvocationLogger {

    private final ToolInvocationLogRepository repository;

    @Value("${inkflow.tool-logging.retention-days:30}")
    private int retentionDays;

    @Value("${inkflow.tool-logging.include-stack-trace:false}")
    private boolean includeStackTrace;

    /**
     * 记录成功的 Tool 调用
     */
    @Transactional
    public void logSuccess(
            String requestId,
            String toolName,
            Map<String, Object> parameters,
            Long durationMs,
            String resultSummary,
            UUID userId,
            UUID projectId) {
        
        try {
            ToolInvocationLog logEntry = ToolInvocationLog.success(
                requestId, toolName, parameters, durationMs, resultSummary, userId, projectId
            );
            repository.save(logEntry);
            log.debug("Logged successful tool invocation: {} ({}ms)", toolName, durationMs);
        } catch (Exception e) {
            log.error("Failed to log tool invocation: {}", e.getMessage());
        }
    }

    /**
     * 记录失败的 Tool 调用
     */
    @Transactional
    public void logFailure(
            String requestId,
            String toolName,
            Map<String, Object> parameters,
            Long durationMs,
            Exception exception,
            UUID userId,
            UUID projectId) {
        
        try {
            String stackTrace = null;
            if (includeStackTrace && exception != null) {
                StringWriter sw = new StringWriter();
                exception.printStackTrace(new PrintWriter(sw));
                stackTrace = sw.toString();
                // 截断过长的堆栈
                if (stackTrace.length() > 10000) {
                    stackTrace = stackTrace.substring(0, 10000) + "\n...[truncated]";
                }
            }

            ToolInvocationLog logEntry = ToolInvocationLog.failure(
                requestId, toolName, parameters, durationMs,
                exception != null ? exception.getMessage() : "Unknown error",
                stackTrace, userId, projectId
            );
            repository.save(logEntry);
            log.debug("Logged failed tool invocation: {} - {}", toolName, exception != null ? exception.getMessage() : "Unknown");
        } catch (Exception e) {
            log.error("Failed to log tool failure: {}", e.getMessage());
        }
    }

    /**
     * 监听 Tool 执行事件（仅处理 END 事件）
     * 使用异步处理避免阻塞主流程
     */
    @Async
    @EventListener
    public void onToolExecutionEvent(ToolExecutionEvent event) {
        // 只记录 END 事件
        if (event.getPhase() != ToolExecutionEvent.Phase.END) {
            return;
        }

        if (event.isSuccess()) {
            logSuccess(
                event.getRequestId(),
                event.getToolName(),
                event.getParameters(),
                event.getDurationMs(),
                event.getResultSummary(),
                event.getUserId(),
                event.getProjectId()
            );
        } else {
            logFailure(
                event.getRequestId(),
                event.getToolName(),
                event.getParameters(),
                event.getDurationMs(),
                new RuntimeException(event.getErrorMessage()),
                event.getUserId(),
                event.getProjectId()
            );
        }
    }

    /**
     * 定时清理过期日志
     * 每天凌晨 3 点执行
     * Requirements: 17.4
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupExpiredLogs() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(retentionDays);
        int deletedCount = repository.deleteByCreatedAtBefore(cutoffTime);
        log.info("Cleaned up {} expired tool invocation logs (older than {} days)", 
            deletedCount, retentionDays);
    }

    /**
     * 手动触发日志清理
     */
    @Transactional
    public int cleanupLogs(int days) {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(days);
        int deletedCount = repository.deleteByCreatedAtBefore(cutoffTime);
        log.info("Manually cleaned up {} tool invocation logs (older than {} days)", 
            deletedCount, days);
        return deletedCount;
    }
}
