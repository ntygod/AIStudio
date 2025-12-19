package com.inkflow.module.consistency.service;

import com.inkflow.module.consistency.entity.ConsistencyWarning;
import com.inkflow.module.consistency.repository.ConsistencyWarningRepository;
import com.inkflow.module.evolution.dto.InconsistencyReport;
import com.inkflow.module.evolution.service.ConsistencyCheckService;
import com.inkflow.module.evolution.entity.EntityType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 主动式一致性检查服务
 * 实现防抖、限流和静默警告存储
 * 
 * Requirements: 7.4, 7.5, 9.1-9.9, 2.1-2.6
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@Service
public class ProactiveConsistencyService {

    private final ConsistencyCheckService consistencyCheckService;
    private final ConsistencyWarningRepository warningRepository;
    private final RuleCheckerService ruleCheckerService;

    // 限流：每个项目每5分钟最多1次检查 (可配置)
    private final Duration rateLimitDuration;
    
    // 防抖：批量更新后等待2秒再触发检查 (可配置)
    private final Duration debounceDuration;
    
    // AI检查开关
    private final boolean aiCheckEnabled;

    // 记录每个项目的最后检查时间
    private final Map<UUID, LocalDateTime> lastCheckTime = new ConcurrentHashMap<>();
    
    // 记录每个项目的待处理更新（用于防抖）
    private final Map<UUID, Set<EntityUpdate>> pendingUpdates = new ConcurrentHashMap<>();
    
    // 记录每个项目的防抖定时器
    private final Map<UUID, ScheduledFuture<?>> debounceTimers = new ConcurrentHashMap<>();
    
    // 记录每个项目在防抖窗口内的更新计数（用于测试验证）
    private final Map<UUID, AtomicInteger> debounceUpdateCounts = new ConcurrentHashMap<>();
    
    // 记录每个项目的检查执行次数（用于测试验证）
    private final Map<UUID, AtomicInteger> checkExecutionCounts = new ConcurrentHashMap<>();
    
    // 调度器用于防抖定时
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    public ProactiveConsistencyService(
            ConsistencyCheckService consistencyCheckService,
            ConsistencyWarningRepository warningRepository,
            RuleCheckerService ruleCheckerService,
            @Value("${inkflow.consistency.debounce-seconds:2}") int debounceSeconds,
            @Value("${inkflow.consistency.rate-limit-minutes:5}") int rateLimitMinutes,
            @Value("${inkflow.consistency.ai-check-enabled:false}") boolean aiCheckEnabled) {
        this.consistencyCheckService = consistencyCheckService;
        this.warningRepository = warningRepository;
        this.ruleCheckerService = ruleCheckerService;
        this.debounceDuration = Duration.ofSeconds(debounceSeconds);
        this.rateLimitDuration = Duration.ofMinutes(rateLimitMinutes);
        this.aiCheckEnabled = aiCheckEnabled;
    }

    /**
     * 触发一致性检查（带防抖）
     * 当实体更新时调用此方法
     * 
     * Requirements: 7.4 - 防抖机制
     */
    public void triggerCheck(UUID projectId, UUID entityId, EntityType entityType, String entityName) {
        // 添加到待处理更新
        pendingUpdates.computeIfAbsent(projectId, k -> ConcurrentHashMap.newKeySet())
                .add(new EntityUpdate(entityId, entityType, entityName));
        
        // 增加防抖窗口内的更新计数
        debounceUpdateCounts.computeIfAbsent(projectId, k -> new AtomicInteger(0)).incrementAndGet();
        
        // 取消之前的定时器（如果存在）
        ScheduledFuture<?> existingTimer = debounceTimers.get(projectId);
        if (existingTimer != null && !existingTimer.isDone()) {
            existingTimer.cancel(false);
        }
        
        // 设置新的防抖定时器
        ScheduledFuture<?> newTimer = scheduler.schedule(
                () -> executeCheckWithRateLimit(projectId),
                debounceDuration.toMillis(),
                TimeUnit.MILLISECONDS
        );
        debounceTimers.put(projectId, newTimer);
        
        log.debug("Debounce timer set for project {}, will execute in {}ms", 
                projectId, debounceDuration.toMillis());
    }

    /**
     * 执行检查（带限流）
     * 
     * Requirements: 7.5 - 每个项目每5分钟最多1次检查
     */
    private void executeCheckWithRateLimit(UUID projectId) {
        // 检查限流
        if (!canCheck(projectId)) {
            log.debug("项目 {} 检查被限流，距离上次检查不足 {} 分钟", 
                    projectId, rateLimitDuration.toMinutes());
            return;
        }
        
        // 执行检查
        executeCheck(projectId);
    }

    /**
     * 执行一致性检查
     */
    @Transactional
    public void executeCheck(UUID projectId) {
        // 获取并清空待处理更新
        Set<EntityUpdate> updates = pendingUpdates.remove(projectId);
        if (updates == null || updates.isEmpty()) {
            return;
        }
        
        // 重置防抖计数
        debounceUpdateCounts.remove(projectId);

        log.info("开始一致性检查: projectId={}, 更新数量={}", projectId, updates.size());

        // 记录检查时间
        lastCheckTime.put(projectId, LocalDateTime.now());
        
        // 增加检查执行次数
        checkExecutionCounts.computeIfAbsent(projectId, k -> new AtomicInteger(0)).incrementAndGet();

        // 执行规则检查
        List<ConsistencyWarning> warnings = new ArrayList<>();
        
        for (EntityUpdate update : updates) {
            try {
                List<ConsistencyWarning> entityWarnings = checkEntity(projectId, update);
                warnings.addAll(entityWarnings);
            } catch (Exception e) {
                log.error("检查实体失败: {}", update, e);
            }
        }

        // 保存警告（静默存储，不打扰用户）
        if (!warnings.isEmpty()) {
            warningRepository.saveAll(warnings);
            log.info("发现 {} 个一致性问题，已静默存储", warnings.size());
        }
    }

    /**
     * 检查单个实体
     * 
     * Requirements: 2.1-2.6
     */
    private List<ConsistencyWarning> checkEntity(UUID projectId, EntityUpdate update) {
        // 首先使用规则检查（低成本）
        List<ConsistencyWarning> ruleWarnings = performRuleBasedCheck(projectId, update);
        
        // 如果AI检查已启用，在规则检查后执行AI增强检查
        // Requirements: 2.6 - IF AI consistency check is enabled in configuration 
        // THEN the ProactiveConsistencyService SHALL perform AI-enhanced analysis after rule-based checks
        if (aiCheckEnabled && !ruleWarnings.isEmpty()) {
            log.debug("AI检查已启用，执行AI增强分析: projectId={}, entityId={}", projectId, update.entityId());
            // AI检查可以在这里扩展实现
            // List<ConsistencyWarning> aiWarnings = performAICheck(projectId, update);
            // ruleWarnings.addAll(aiWarnings);
        }
        
        return ruleWarnings;
    }

    /**
     * 规则检查（低成本）
     * 使用 RuleCheckerService 执行基于规则的一致性检查
     * 
     * Requirements: 2.1, 2.2, 2.3, 2.4, 2.5
     */
    private List<ConsistencyWarning> performRuleBasedCheck(UUID projectId, EntityUpdate update) {
        List<ConsistencyWarning> warnings = new ArrayList<>();
        
        // 根据实体类型执行不同的规则检查
        if (update.entityType() == null || update.entityId() == null) {
            return warnings;
        }
        
        try {
            warnings.addAll(ruleCheckerService.checkAllRules(projectId, update.entityType(), update.entityId()));
        } catch (Exception e) {
            log.error("规则检查失败: projectId={}, entityId={}, entityType={}", 
                    projectId, update.entityId(), update.entityType(), e);
        }
        
        return warnings;
    }
    
    /**
     * 检查AI检查是否启用
     * 
     * Requirements: 2.6
     */
    public boolean isAICheckEnabled() {
        return aiCheckEnabled;
    }

    /**
     * 检查是否可以执行检查（限流）
     * 
     * Requirements: 7.5 - 每个项目每5分钟最多1次检查
     */
    public boolean canCheck(UUID projectId) {
        LocalDateTime lastCheck = lastCheckTime.get(projectId);
        if (lastCheck == null) {
            return true;
        }
        return Duration.between(lastCheck, LocalDateTime.now()).compareTo(rateLimitDuration) > 0;
    }
    
    /**
     * 获取项目在防抖窗口内的更新计数
     * 用于测试验证防抖效果
     */
    public int getDebounceUpdateCount(UUID projectId) {
        AtomicInteger count = debounceUpdateCounts.get(projectId);
        return count != null ? count.get() : 0;
    }
    
    /**
     * 获取项目的检查执行次数
     * 用于测试验证限流效果
     */
    public int getCheckExecutionCount(UUID projectId) {
        AtomicInteger count = checkExecutionCounts.get(projectId);
        return count != null ? count.get() : 0;
    }
    
    /**
     * 获取项目的最后检查时间
     */
    public Optional<LocalDateTime> getLastCheckTime(UUID projectId) {
        return Optional.ofNullable(lastCheckTime.get(projectId));
    }
    
    /**
     * 重置项目的限流状态（用于测试）
     */
    public void resetRateLimitState(UUID projectId) {
        lastCheckTime.remove(projectId);
        checkExecutionCounts.remove(projectId);
    }
    
    /**
     * 获取防抖持续时间
     */
    public Duration getDebounceDuration() {
        return debounceDuration;
    }
    
    /**
     * 获取限流持续时间
     */
    public Duration getRateLimitDuration() {
        return rateLimitDuration;
    }

    /**
     * 获取项目的待处理警告
     */
    @Transactional(readOnly = true)
    public List<ConsistencyWarning> getPendingWarnings(UUID projectId) {
        return warningRepository.findByProjectIdAndResolvedFalseOrderByCreatedAtDesc(projectId);
    }

    /**
     * 获取项目的所有警告
     */
    @Transactional(readOnly = true)
    public List<ConsistencyWarning> getAllWarnings(UUID projectId) {
        return warningRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    /**
     * 标记警告为已解决
     */
    @Transactional
    public void resolveWarning(UUID warningId) {
        warningRepository.findById(warningId).ifPresent(warning -> {
            warning.setResolved(true);
            warning.setResolvedAt(LocalDateTime.now());
            warningRepository.save(warning);
        });
    }

    /**
     * 批量标记警告为已解决
     */
    @Transactional
    public void resolveWarnings(List<UUID> warningIds) {
        warningIds.forEach(this::resolveWarning);
    }

    /**
     * 清理已解决的旧警告
     */
    @Transactional
    public int cleanupResolvedWarnings(int retentionDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        return warningRepository.deleteResolvedOlderThan(cutoff);
    }

    /**
     * 强制执行检查（忽略限流，用于用户主动请求）
     */
    @Transactional
    public List<ConsistencyWarning> forceCheck(UUID projectId, List<ConsistencyCheckService.EntityReference> entities) {
        log.info("强制执行一致性检查: projectId={}", projectId);
        
        List<ConsistencyWarning> warnings = new ArrayList<>();
        
        for (var ref : entities) {
            EntityUpdate update = new EntityUpdate(ref.entityId(), ref.entityType(), ref.entityName());
            warnings.addAll(checkEntity(projectId, update));
        }
        
        if (!warnings.isEmpty()) {
            warningRepository.saveAll(warnings);
        }
        
        return warnings;
    }

    /**
     * 实体更新记录
     */
    public record EntityUpdate(
            UUID entityId,
            EntityType entityType,
            String entityName
    ) {}
}
