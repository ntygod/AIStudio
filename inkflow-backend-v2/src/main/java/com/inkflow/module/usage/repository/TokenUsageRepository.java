package com.inkflow.module.usage.repository;

import com.inkflow.module.usage.entity.TokenUsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TokenUsageRepository extends JpaRepository<TokenUsageRecord, UUID> {

    List<TokenUsageRecord> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<TokenUsageRecord> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    @Query("""
        SELECT SUM(t.totalTokens) FROM TokenUsageRecord t 
        WHERE t.userId = :userId 
          AND t.createdAt >= :startDate
        """)
    Long sumTotalTokensByUserIdSince(@Param("userId") UUID userId, @Param("startDate") LocalDateTime startDate);

    @Query("""
        SELECT SUM(t.totalTokens) FROM TokenUsageRecord t 
        WHERE t.projectId = :projectId
        """)
    Long sumTotalTokensByProjectId(@Param("projectId") UUID projectId);

    @Query("""
        SELECT t.modelName, SUM(t.totalTokens) FROM TokenUsageRecord t 
        WHERE t.userId = :userId 
          AND t.createdAt >= :startDate 
        GROUP BY t.modelName
        """)
    List<Object[]> sumTokensByModelForUser(@Param("userId") UUID userId, @Param("startDate") LocalDateTime startDate);

    @Query("""
        SELECT SUM(t.cost) FROM TokenUsageRecord t 
        WHERE t.userId = :userId 
          AND t.createdAt >= :startDate
        """)
    Double sumCostByUserIdSince(@Param("userId") UUID userId, @Param("startDate") LocalDateTime startDate);

    @Query("""
        SELECT DATE(t.createdAt), SUM(t.totalTokens) FROM TokenUsageRecord t 
        WHERE t.userId = :userId 
          AND t.createdAt >= :startDate 
        GROUP BY DATE(t.createdAt) 
        ORDER BY DATE(t.createdAt)
        """)
    List<Object[]> dailyUsageByUser(@Param("userId") UUID userId, @Param("startDate") LocalDateTime startDate);
}
