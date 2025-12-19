package com.inkflow.module.ratelimit;

import com.inkflow.module.ratelimit.dto.RateLimitConfigDto;
import com.inkflow.module.ratelimit.dto.RateLimitRuleDto;
import com.inkflow.module.ratelimit.service.RateLimitConfigService;
import com.inkflow.module.ratelimit.service.RateLimitMetricsService;
import com.inkflow.module.ratelimit.service.RateLimitRuleService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * 增强版限流过滤器
 * 支持用户级别配置和端点级别规则
 */
@Component
@Order(1)
public class RateLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    // 默认配置
    private static final int DEFAULT_MAX_REQUESTS = 60;
    private static final int DEFAULT_BUCKET_CAPACITY = 100;
    private static final int DEFAULT_REFILL_RATE = 10;
    private static final Duration DEFAULT_WINDOW = Duration.ofMinutes(1);

    private final RateLimitService rateLimitService;
    private final RateLimitConfigService configService;
    private final RateLimitRuleService ruleService;
    private final RateLimitMetricsService metricsService;

    public RateLimitFilter(RateLimitService rateLimitService,
                           RateLimitConfigService configService,
                           RateLimitRuleService ruleService,
                           RateLimitMetricsService metricsService) {
        this.rateLimitService = rateLimitService;
        this.configService = configService;
        this.ruleService = ruleService;
        this.metricsService = metricsService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 跳过静态资源和健康检查
        String path = httpRequest.getRequestURI();
        if (shouldSkip(path)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            // 获取客户端标识和用户ID
            String clientKey = getClientKey(httpRequest);
            UUID userId = getUserId(httpRequest);
            String method = httpRequest.getMethod();

            // 确定限流参数（优先级：端点规则 > 用户配置 > 默认）
            RateLimitParams params = resolveRateLimitParams(path, method, userId);

            // 检查限流
            boolean allowed = rateLimitService.tryAcquire(clientKey, 1, params.capacity, params.refillRate);

            // 记录指标
            metricsService.recordHit(clientKey, allowed);

            if (!allowed) {
                log.warn("Rate limit exceeded for client: {}, path: {}", clientKey, path);

                httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write("""
                    {"error": "Too many requests", "message": "请求过于频繁，请稍后再试"}
                    """);

                // 添加限流相关的响应头
                httpResponse.setHeader("X-RateLimit-Limit", String.valueOf(params.capacity));
                httpResponse.setHeader("X-RateLimit-Remaining", "0");
                httpResponse.setHeader("Retry-After", String.valueOf(params.windowSeconds));
                return;
            }

            // 添加限流信息到响应头
            int remaining = rateLimitService.getRemainingTokens(clientKey);
            httpResponse.setHeader("X-RateLimit-Limit", String.valueOf(params.capacity));
            httpResponse.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        } catch (Exception e) {
            // 限流检查失败时，允许请求通过，避免影响正常业务
            log.warn("Rate limit check failed, allowing request: {}", e.getMessage());
        }

        chain.doFilter(request, response);
    }

    /**
     * 解析限流参数
     */
    private RateLimitParams resolveRateLimitParams(String path, String method, UUID userId) {
        // 1. 首先检查端点规则
        Optional<RateLimitRuleDto> rule = ruleService.findMatchingRule(path, method);
        if (rule.isPresent()) {
            RateLimitRuleDto r = rule.get();
            return new RateLimitParams(r.getBucketCapacity(), r.getRefillRate(), 60);
        }

        // 2. 然后检查用户配置
        if (userId != null) {
            Optional<RateLimitConfigDto> config = configService.getConfigForUser(userId);
            if (config.isPresent()) {
                RateLimitConfigDto c = config.get();
                return new RateLimitParams(c.getBucketCapacity(), c.getRefillRate(), c.getWindowSeconds());
            }
        }

        // 3. 使用默认配置
        return new RateLimitParams(DEFAULT_BUCKET_CAPACITY, DEFAULT_REFILL_RATE, 60);
    }

    /**
     * 获取用户ID
     */
    private UUID getUserId(HttpServletRequest request) {
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            try {
                return UUID.fromString(userIdHeader);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 限流参数
     */
    private record RateLimitParams(int capacity, int refillRate, int windowSeconds) {}

    /**
     * 获取客户端标识
     */
    private String getClientKey(HttpServletRequest request) {
        // 优先使用用户ID（如果已认证）
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            return "user:" + userId;
        }

        // 使用IP地址
        String ip = getClientIp(request);
        return "ip:" + ip;
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            return ip.split(",")[0].trim();
        }

        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) {
            return ip;
        }

        return request.getRemoteAddr();
    }

    /**
     * 判断是否跳过限流
     * 注意: context-path=/api 已经在请求到达这里之前被处理，所以路径不包含 /api 前缀
     */
    private boolean shouldSkip(String path) {
        return path.startsWith("/actuator") ||
                path.startsWith("/swagger") ||
                path.startsWith("/v3/api-docs") ||
                path.startsWith("/api/auth") ||  // 认证端点不限流 (带 context-path)
                path.startsWith("/auth") ||       // 认证端点不限流 (不带 context-path)
                path.equals("/health") ||
                path.endsWith(".css") ||
                path.endsWith(".js") ||
                path.endsWith(".ico");
    }
}
