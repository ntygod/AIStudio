package com.inkflow.module.ratelimit.repository;

import com.inkflow.module.ratelimit.entity.RateLimitRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RateLimitRuleRepository extends JpaRepository<RateLimitRule, UUID> {

    List<RateLimitRule> findAllByEnabledTrueOrderByPriorityDesc();

    @Query("SELECT r FROM RateLimitRule r WHERE r.enabled = true ORDER BY r.priority DESC")
    List<RateLimitRule> findAllActiveRulesOrderedByPriority();

    List<RateLimitRule> findByEndpointPatternContaining(String pattern);

    boolean existsByEndpointPatternAndHttpMethod(String endpointPattern, String httpMethod);
}
