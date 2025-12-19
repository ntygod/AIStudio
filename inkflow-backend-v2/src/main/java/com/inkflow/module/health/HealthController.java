package com.inkflow.module.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查控制器
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "健康检查", description = "系统健康状态API")
public class HealthController {

    private final DataSource dataSource;
    private final RedisTemplate<String, String> redisTemplate;

    public HealthController(DataSource dataSource, RedisTemplate<String, String> redisTemplate) {
        this.dataSource = dataSource;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping
    @Operation(summary = "健康检查", description = "检查系统各组件健康状态")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now().toString());

        // 检查数据库
        health.put("database", checkDatabase());

        // 检查Redis
        health.put("redis", checkRedis());

        // 判断整体状态
        boolean allUp = health.values().stream()
                .filter(v -> v instanceof Map)
                .map(v -> (Map<?, ?>) v)
                .allMatch(m -> "UP".equals(m.get("status")));

        health.put("status", allUp ? "UP" : "DEGRADED");

        return ResponseEntity.ok(health);
    }

    @GetMapping("/live")
    @Operation(summary = "存活检查", description = "Kubernetes liveness probe")
    public ResponseEntity<Map<String, String>> liveness() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    @GetMapping("/ready")
    @Operation(summary = "就绪检查", description = "Kubernetes readiness probe")
    public ResponseEntity<Map<String, Object>> readiness() {
        Map<String, Object> ready = new HashMap<>();
        
        boolean dbReady = checkDatabase().get("status").equals("UP");
        boolean redisReady = checkRedis().get("status").equals("UP");
        
        ready.put("database", dbReady);
        ready.put("redis", redisReady);
        ready.put("status", dbReady && redisReady ? "UP" : "DOWN");

        if (dbReady && redisReady) {
            return ResponseEntity.ok(ready);
        } else {
            return ResponseEntity.status(503).body(ready);
        }
    }

    private Map<String, String> checkDatabase() {
        Map<String, String> result = new HashMap<>();
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(5)) {
                result.put("status", "UP");
                result.put("database", conn.getMetaData().getDatabaseProductName());
            } else {
                result.put("status", "DOWN");
                result.put("error", "Connection not valid");
            }
        } catch (Exception e) {
            result.put("status", "DOWN");
            result.put("error", e.getMessage());
        }
        return result;
    }

    private Map<String, String> checkRedis() {
        Map<String, String> result = new HashMap<>();
        try {
            String pong = redisTemplate.getConnectionFactory()
                    .getConnection()
                    .ping();
            if ("PONG".equalsIgnoreCase(pong)) {
                result.put("status", "UP");
            } else {
                result.put("status", "DOWN");
                result.put("error", "Unexpected response: " + pong);
            }
        } catch (Exception e) {
            result.put("status", "DOWN");
            result.put("error", e.getMessage());
        }
        return result;
    }
}
