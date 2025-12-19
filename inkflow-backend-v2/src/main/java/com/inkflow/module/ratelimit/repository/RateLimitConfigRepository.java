package com.inkflow.module.ratelimit.repository;

import com.inkflow.module.ratelimit.entity.RateLimitConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RateLimitConfigRepository extends JpaRepository<RateLimitConfig, UUID> {

    Optional<RateLimitConfig> findByUserId(UUID userId);

    Optional<RateLimitConfig> findByUserIdAndEnabledTrue(UUID userId);

    List<RateLimitConfig> findAllByEnabledTrue();

    boolean existsByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
