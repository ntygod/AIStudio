package com.inkflow.module.consistency.cdc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.module.consistency.service.ProactiveConsistencyService;
import com.inkflow.module.evolution.entity.EntityType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PostgreSQL CDC 监听器
 * 使用 LISTEN/NOTIFY 机制监听实体变更事件
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsistencyCDCListener {

    private final DataSource dataSource;
    private final ProactiveConsistencyService consistencyService;
    private final ObjectMapper objectMapper;

    @Value("${inkflow.cdc.enabled:true}")
    private boolean cdcEnabled;

    @Value("${inkflow.cdc.poll-interval-ms:100}")
    private int pollIntervalMs;

    private static final String CHANNEL_NAME = "entity_changes";
    
    private Connection listenerConnection;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        if (cdcEnabled) {
            try {
                setupListenerConnection();
                running.set(true);
                log.info("CDC listener initialized, listening on channel: {}", CHANNEL_NAME);
            } catch (Exception e) {
                log.error("Failed to initialize CDC listener", e);
            }
        } else {
            log.info("CDC listener is disabled");
        }
    }

    @PreDestroy
    public void cleanup() {
        running.set(false);
        closeListenerConnection();
        log.info("CDC listener stopped");
    }

    /**
     * 设置监听连接
     */
    private void setupListenerConnection() throws SQLException {
        listenerConnection = dataSource.getConnection();
        listenerConnection.setAutoCommit(true);
        
        // 订阅通知频道
        try (Statement stmt = listenerConnection.createStatement()) {
            stmt.execute("LISTEN " + CHANNEL_NAME);
        }
        
        log.debug("Subscribed to PostgreSQL notification channel: {}", CHANNEL_NAME);
    }

    /**
     * 关闭监听连接
     */
    private void closeListenerConnection() {
        if (listenerConnection != null) {
            try {
                listenerConnection.close();
            } catch (SQLException e) {
                log.warn("Error closing listener connection", e);
            }
        }
    }

    /**
     * 定时轮询通知
     * 使用固定延迟调度，确保不会重叠执行
     * 使用反射调用 PostgreSQL 特定方法以避免编译时依赖
     */
    @Scheduled(fixedDelayString = "${inkflow.cdc.poll-interval-ms:100}")
    public void pollNotifications() {
        if (!cdcEnabled || !running.get()) {
            return;
        }

        try {
            // 检查连接是否有效
            if (listenerConnection == null || listenerConnection.isClosed()) {
                log.warn("Listener connection lost, attempting to reconnect...");
                setupListenerConnection();
            }

            // 使用反射获取 PostgreSQL 连接和通知
            Object pgConn = listenerConnection.unwrap(Class.forName("org.postgresql.PGConnection"));
            
            // 调用 getNotifications(int timeout) 方法
            Method getNotificationsMethod = pgConn.getClass().getMethod("getNotifications", int.class);
            Object[] notifications = (Object[]) getNotificationsMethod.invoke(pgConn, pollIntervalMs);
            
            if (notifications != null && notifications.length > 0) {
                for (Object notification : notifications) {
                    handleNotification(notification);
                }
            }
            
        } catch (ClassNotFoundException e) {
            log.error("PostgreSQL driver not found, CDC listener disabled", e);
            running.set(false);
        } catch (SQLException e) {
            log.error("Error polling notifications", e);
            // 尝试重新建立连接
            closeListenerConnection();
            try {
                setupListenerConnection();
            } catch (SQLException reconnectError) {
                log.error("Failed to reconnect listener", reconnectError);
            }
        } catch (Exception e) {
            log.error("Error in CDC listener", e);
        }
    }

    /**
     * 处理单个通知（使用反射）
     */
    private void handleNotification(Object notification) {
        try {
            // 使用反射获取通知的 name 和 parameter
            Method getNameMethod = notification.getClass().getMethod("getName");
            Method getParameterMethod = notification.getClass().getMethod("getParameter");
            
            String channel = (String) getNameMethod.invoke(notification);
            String payload = (String) getParameterMethod.invoke(notification);
            
            log.debug("Received notification on channel {}: {}", channel, payload);
            
            EntityChangeEvent event = parsePayload(payload);
            if (event != null) {
                processEntityChange(event);
            }
        } catch (Exception e) {
            log.error("Error processing notification", e);
        }
    }

    /**
     * 解析通知负载
     */
    @SuppressWarnings("unchecked")
    private EntityChangeEvent parsePayload(String payload) {
        try {
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            
            String table = (String) data.get("table");
            String operation = (String) data.get("operation");
            UUID id = parseUUID(data.get("id"));
            UUID projectId = parseUUID(data.get("project_id"));
            LocalDateTime timestamp = LocalDateTime.now(); // 使用当前时间
            
            if (id == null || projectId == null) {
                log.warn("Invalid notification payload, missing id or project_id: {}", payload);
                return null;
            }
            
            return new EntityChangeEvent(table, operation, id, projectId, timestamp);
            
        } catch (Exception e) {
            log.error("Failed to parse notification payload: {}", payload, e);
            return null;
        }
    }

    /**
     * 解析 UUID
     */
    private UUID parseUUID(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID) {
            return (UUID) value;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 处理实体变更事件
     */
    private void processEntityChange(EntityChangeEvent event) {
        // 使用虚拟线程异步处理，避免阻塞轮询
        Thread.startVirtualThread(() -> {
            try {
                if (event.isCharacterChange()) {
                    onCharacterChange(event);
                } else if (event.isWikiEntryChange()) {
                    onWikiEntryChange(event);
                } else if (event.isPlotLoopChange()) {
                    onPlotLoopChange(event);
                }
            } catch (Exception e) {
                log.error("Error processing entity change: {}", event, e);
            }
        });
    }

    /**
     * 处理角色变更事件
     * Requirements: 7.1
     */
    private void onCharacterChange(EntityChangeEvent event) {
        log.info("Character change detected: {} - {}", event.operation(), event.id());
        
        if (!event.isDelete()) {
            consistencyService.triggerCheck(
                    event.projectId(),
                    event.id(),
                    EntityType.CHARACTER,
                    null // 名称将在检查时获取
            );
        }
    }

    /**
     * 处理 Wiki 条目变更事件
     * Requirements: 7.2
     */
    private void onWikiEntryChange(EntityChangeEvent event) {
        log.info("Wiki entry change detected: {} - {}", event.operation(), event.id());
        
        if (!event.isDelete()) {
            consistencyService.triggerCheck(
                    event.projectId(),
                    event.id(),
                    EntityType.WIKI_ENTRY,
                    null
            );
        }
    }

    /**
     * 处理伏笔变更事件
     * Requirements: 7.3
     */
    private void onPlotLoopChange(EntityChangeEvent event) {
        log.info("Plot loop change detected: {} - {}", event.operation(), event.id());
        
        // 伏笔状态变更需要检查关联章节的一致性
        if (!event.isDelete()) {
            // 对于伏笔，我们使用 RELATIONSHIP 类型来表示它与章节的关联
            consistencyService.triggerCheck(
                    event.projectId(),
                    event.id(),
                    EntityType.RELATIONSHIP,
                    null
            );
        }
    }

    /**
     * 检查 CDC 监听器是否正在运行
     */
    public boolean isRunning() {
        return running.get() && cdcEnabled;
    }

    /**
     * 手动重启监听器
     */
    public void restart() {
        cleanup();
        init();
    }
}
