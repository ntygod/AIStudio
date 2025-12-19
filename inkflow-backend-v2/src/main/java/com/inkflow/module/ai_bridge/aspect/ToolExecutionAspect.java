package com.inkflow.module.ai_bridge.aspect;

import com.inkflow.module.ai_bridge.context.RequestContextHolder;
import com.inkflow.module.ai_bridge.context.RequestContextHolder.RequestContext;
import com.inkflow.module.ai_bridge.event.ToolExecutionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tool 执行切面
 * 拦截所有 @Tool 注解的方法，发布执行事件
 * 
 * Requirements: 6.1, 6.2
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@Aspect
@Component
@Order(1)
@RequiredArgsConstructor
public class ToolExecutionAspect {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 拦截所有 @Tool 注解的方法
     */
    @Around("@annotation(tool)")
    public Object aroundToolExecution(ProceedingJoinPoint joinPoint, Tool tool) throws Throwable {
        String toolName = extractToolName(joinPoint, tool);
        Map<String, Object> parameters = extractParameters(joinPoint);
        
        // 获取请求上下文（可能为空）
        RequestContext context = RequestContextHolder.currentOrNull();
        String requestId = context != null ? context.requestId() : UUID.randomUUID().toString();
        UUID userId = context != null ? context.userId() : null;
        UUID projectId = context != null ? context.projectId() : null;

        // 发布 START 事件
        publishStartEvent(requestId, toolName, parameters, userId, projectId);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 执行 Tool 方法
            Object result = joinPoint.proceed();
            
            long durationMs = System.currentTimeMillis() - startTime;
            String resultSummary = summarizeResult(result);
            
            // 发布成功 END 事件
            publishSuccessEvent(requestId, toolName, resultSummary, durationMs, userId, projectId);
            
            log.debug("Tool [{}] executed successfully in {}ms", toolName, durationMs);
            return result;
            
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            
            // 发布失败 END 事件
            publishFailureEvent(requestId, toolName, e.getMessage(), durationMs, userId, projectId);
            
            log.error("Tool [{}] failed after {}ms: {}", toolName, durationMs, e.getMessage());
            throw e;
        }
    }

    /**
     * 提取 Tool 名称
     */
    private String extractToolName(ProceedingJoinPoint joinPoint, Tool tool) {
        // 优先使用 @Tool 注解的 name 属性
        String name = tool.name();
        if (name != null && !name.isBlank()) {
            return name;
        }
        // 否则使用方法名
        return joinPoint.getSignature().getName();
    }

    /**
     * 提取方法参数
     */
    private Map<String, Object> extractParameters(ProceedingJoinPoint joinPoint) {
        Map<String, Object> params = new HashMap<>();
        
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] args = joinPoint.getArgs();
        
        for (int i = 0; i < parameters.length && i < args.length; i++) {
            String paramName = parameters[i].getName();
            Object paramValue = args[i];
            
            // 对敏感或大型参数进行脱敏/截断
            params.put(paramName, sanitizeParameter(paramValue));
        }
        
        return params;
    }

    /**
     * 参数脱敏/截断
     */
    private Object sanitizeParameter(Object value) {
        if (value == null) {
            return null;
        }
        
        String str = value.toString();
        // 截断过长的参数值
        if (str.length() > 200) {
            return str.substring(0, 200) + "...[truncated]";
        }
        return str;
    }

    /**
     * 生成结果摘要
     */
    private String summarizeResult(Object result) {
        if (result == null) {
            return "null";
        }
        
        String str = result.toString();
        // 截断过长的结果
        if (str.length() > 500) {
            return str.substring(0, 500) + "...[truncated]";
        }
        return str;
    }

    /**
     * 发布 START 事件
     */
    private void publishStartEvent(
            String requestId,
            String toolName,
            Map<String, Object> parameters,
            UUID userId,
            UUID projectId) {
        try {
            ToolExecutionEvent event = ToolExecutionEvent.start(
                this, requestId, toolName, parameters, userId, projectId
            );
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.warn("Failed to publish tool start event: {}", e.getMessage());
        }
    }

    /**
     * 发布成功 END 事件
     */
    private void publishSuccessEvent(
            String requestId,
            String toolName,
            String resultSummary,
            Long durationMs,
            UUID userId,
            UUID projectId) {
        try {
            ToolExecutionEvent event = ToolExecutionEvent.success(
                this, requestId, toolName, resultSummary, durationMs, userId, projectId
            );
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.warn("Failed to publish tool success event: {}", e.getMessage());
        }
    }

    /**
     * 发布失败 END 事件
     */
    private void publishFailureEvent(
            String requestId,
            String toolName,
            String errorMessage,
            Long durationMs,
            UUID userId,
            UUID projectId) {
        try {
            ToolExecutionEvent event = ToolExecutionEvent.failure(
                this, requestId, toolName, errorMessage, durationMs, userId, projectId
            );
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.warn("Failed to publish tool failure event: {}", e.getMessage());
        }
    }
}
