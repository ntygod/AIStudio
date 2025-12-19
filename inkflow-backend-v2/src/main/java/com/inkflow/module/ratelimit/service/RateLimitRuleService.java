package com.inkflow.module.ratelimit.service;

import com.inkflow.module.ratelimit.dto.RateLimitRuleDto;
import com.inkflow.module.ratelimit.dto.RateLimitRuleRequest;
import com.inkflow.module.ratelimit.entity.RateLimitRule;
import com.inkflow.module.ratelimit.repository.RateLimitRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 端点级别限流规则服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitRuleService {

    private final RateLimitRuleRepository ruleRepository;
    
    // 缓存已排序的规则列表，用于快速匹配
    private final CopyOnWriteArrayList<RateLimitRule> cachedRules = new CopyOnWriteArrayList<>();

    /**
     * 创建限流规则
     */
    @Transactional
    public RateLimitRuleDto createRule(RateLimitRuleRequest request) {
        RateLimitRule rule = RateLimitRule.builder()
                .endpointPattern(request.getEndpointPattern())
                .httpMethod(request.getHttpMethod())
                .bucketCapacity(request.getBucketCapacity())
                .refillRate(request.getRefillRate())
                .priority(request.getPriority())
                .enabled(request.isEnabled())
                .description(request.getDescription())
                .build();

        rule = ruleRepository.save(rule);
        refreshCache();
        log.info("创建限流规则: pattern={}, priority={}", request.getEndpointPattern(), request.getPriority());
        
        return toDto(rule);
    }

    /**
     * 更新限流规则
     */
    @Transactional
    public RateLimitRuleDto updateRule(UUID ruleId, RateLimitRuleRequest request) {
        RateLimitRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("限流规则不存在: " + ruleId));

        rule.setEndpointPattern(request.getEndpointPattern());
        rule.setHttpMethod(request.getHttpMethod());
        rule.setBucketCapacity(request.getBucketCapacity());
        rule.setRefillRate(request.getRefillRate());
        rule.setPriority(request.getPriority());
        rule.setEnabled(request.isEnabled());
        rule.setDescription(request.getDescription());

        rule = ruleRepository.save(rule);
        refreshCache();
        log.info("更新限流规则: id={}", ruleId);
        
        return toDto(rule);
    }

    /**
     * 删除限流规则
     */
    @Transactional
    public void deleteRule(UUID ruleId) {
        ruleRepository.deleteById(ruleId);
        refreshCache();
        log.info("删除限流规则: id={}", ruleId);
    }

    /**
     * 查找匹配的规则（返回优先级最高的）
     */
    public Optional<RateLimitRuleDto> findMatchingRule(String path, String method) {
        if (cachedRules.isEmpty()) {
            refreshCache();
        }
        
        return cachedRules.stream()
                .filter(rule -> rule.matches(path, method))
                .findFirst()
                .map(this::toDto);
    }

    /**
     * 获取所有规则
     */
    public List<RateLimitRuleDto> getAllRules() {
        return ruleRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * 刷新规则缓存
     */
    public void refreshCache() {
        cachedRules.clear();
        cachedRules.addAll(ruleRepository.findAllActiveRulesOrderedByPriority());
        log.debug("刷新限流规则缓存，加载 {} 条规则", cachedRules.size());
    }

    private RateLimitRuleDto toDto(RateLimitRule rule) {
        return RateLimitRuleDto.builder()
                .id(rule.getId())
                .endpointPattern(rule.getEndpointPattern())
                .httpMethod(rule.getHttpMethod())
                .bucketCapacity(rule.getBucketCapacity())
                .refillRate(rule.getRefillRate())
                .priority(rule.getPriority())
                .enabled(rule.isEnabled())
                .description(rule.getDescription())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }
}
