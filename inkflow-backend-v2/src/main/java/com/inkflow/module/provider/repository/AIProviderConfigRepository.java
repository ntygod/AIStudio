package com.inkflow.module.provider.repository;

import com.inkflow.module.provider.entity.AIProviderConfig;
import com.inkflow.module.provider.entity.ProviderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AIProviderConfigRepository extends JpaRepository<AIProviderConfig, UUID> {

    List<AIProviderConfig> findByUserId(UUID userId);

    Optional<AIProviderConfig> findByUserIdAndProviderType(UUID userId, ProviderType providerType);

    boolean existsByUserIdAndProviderType(UUID userId, ProviderType providerType);

    @Query("SELECT c.providerType FROM AIProviderConfig c WHERE c.userId = :userId AND c.isConfigured = true")
    List<ProviderType> findConfiguredProviderTypesByUserId(UUID userId);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM AIProviderConfig c " +
           "WHERE c.userId = :userId AND c.providerType = :providerType AND c.isConfigured = true")
    boolean existsConfiguredByUserIdAndProviderType(UUID userId, ProviderType providerType);

    Optional<AIProviderConfig> findByUserIdAndIsDefaultTrue(UUID userId);

    void deleteByUserIdAndProviderType(UUID userId, ProviderType providerType);
}
