package com.inkflow.module.ai_bridge.context;

import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * 请求上下文持有器
 * 使用 Java 22 ScopedValue 实现跨 Virtual Threads 的上下文传递
 * 
 * ScopedValue 相比 ThreadLocal 的优势:
 * 1. 自动继承到子 Virtual Threads
 * 2. 不可变，线程安全
 * 3. 作用域明确，自动清理
 * 
 * Requirements: 7.1, 7.2, 7.3
 *
 * @author zsg
 * @date 2025/12/17
 */
public final class RequestContextHolder {

    /**
     * ScopedValue 实例，存储请求上下文
     */
    public static final ScopedValue<RequestContext> CONTEXT = ScopedValue.newInstance();

    private RequestContextHolder() {
        // 工具类，禁止实例化
    }

    /**
     * 请求上下文记录
     * 包含请求ID、用户ID、项目ID
     */
    public record RequestContext(
        String requestId,
        UUID userId,
        UUID projectId
    ) {
        public RequestContext {
            if (requestId == null || requestId.isBlank()) {
                throw new IllegalArgumentException("requestId cannot be null or blank");
            }
            if (userId == null) {
                throw new IllegalArgumentException("userId cannot be null");
            }
            // projectId 可以为 null（某些场景下用户可能没有选择项目）
        }

        /**
         * 创建带新 requestId 的上下文副本
         */
        public RequestContext withRequestId(String newRequestId) {
            return new RequestContext(newRequestId, this.userId, this.projectId);
        }

        /**
         * 创建带新 projectId 的上下文副本
         */
        public RequestContext withProjectId(UUID newProjectId) {
            return new RequestContext(this.requestId, this.userId, newProjectId);
        }
    }

    /**
     * 获取当前请求上下文
     * 
     * @return 当前上下文
     * @throws IllegalStateException 如果没有绑定上下文
     */
    public static RequestContext current() {
        return CONTEXT.orElseThrow(() -> 
            new IllegalStateException("No request context bound. " +
                "Ensure the operation is executed within RequestContextHolder.run() or call()"));
    }

    /**
     * 尝试获取当前请求上下文
     * 
     * @return 当前上下文，如果没有绑定则返回 null
     */
    public static RequestContext currentOrNull() {
        return CONTEXT.orElse(null);
    }

    /**
     * 检查是否有上下文绑定
     */
    public static boolean isBound() {
        return CONTEXT.isBound();
    }

    /**
     * 获取当前用户ID
     * 
     * @return 当前用户ID
     * @throws IllegalStateException 如果没有绑定上下文
     */
    public static UUID currentUserId() {
        return current().userId();
    }

    /**
     * 获取当前项目ID
     * 
     * @return 当前项目ID，可能为 null
     * @throws IllegalStateException 如果没有绑定上下文
     */
    public static UUID currentProjectId() {
        return current().projectId();
    }

    /**
     * 获取当前请求ID
     * 
     * @return 当前请求ID
     * @throws IllegalStateException 如果没有绑定上下文
     */
    public static String currentRequestId() {
        return current().requestId();
    }

    /**
     * 在指定上下文中执行操作（无返回值）
     * ScopedValue 会自动传递到子 Virtual Threads
     * 
     * @param context 请求上下文
     * @param runnable 要执行的操作
     */
    public static void run(RequestContext context, Runnable runnable) {
        ScopedValue.runWhere(CONTEXT, context, runnable);
    }

    /**
     * 在指定上下文中执行操作（有返回值）
     * ScopedValue 会自动传递到子 Virtual Threads
     * 
     * @param context 请求上下文
     * @param callable 要执行的操作
     * @return 操作结果
     * @throws Exception 如果操作抛出异常
     */
    public static <T> T call(RequestContext context, Callable<T> callable) throws Exception {
        return ScopedValue.callWhere(CONTEXT, context, callable);
    }

    /**
     * 创建新的请求上下文
     * 
     * @param userId 用户ID
     * @param projectId 项目ID（可为 null）
     * @return 新的请求上下文
     */
    public static RequestContext createContext(UUID userId, UUID projectId) {
        return new RequestContext(UUID.randomUUID().toString(), userId, projectId);
    }

    /**
     * 创建新的请求上下文（指定 requestId）
     * 
     * @param requestId 请求ID
     * @param userId 用户ID
     * @param projectId 项目ID（可为 null）
     * @return 新的请求上下文
     */
    public static RequestContext createContext(String requestId, UUID userId, UUID projectId) {
        return new RequestContext(requestId, userId, projectId);
    }
}
