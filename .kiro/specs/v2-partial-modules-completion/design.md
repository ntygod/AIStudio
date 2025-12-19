# Design Document: V2 Partial Modules Completion

## Overview

本设计文档描述了 InkFlow Backend V2 中四个部分实现模块的完善方案。这些模块已有基础实现，需要补充高级功能以达到生产就绪状态。

### 目标模块

1. **ratelimit** - 添加用户级别配置和动态规则
2. **session** - 添加持久化和完整会话管理
3. **progress** - 添加持久化和统计 API
4. **consistency** - 添加 CDC 触发和冲突解决

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           API Gateway Layer                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│  RateLimitFilter (enhanced)                                                  │
│  ├── UserRateLimitConfig lookup                                             │
│  ├── EndpointRateLimitRule matching                                         │
│  └── Metrics collection                                                      │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Service Layer                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐             │
│  │ RateLimitService│  │ SessionService  │  │ ProgressService │             │
│  │ (enhanced)      │  │ (new)           │  │ (enhanced)      │             │
│  │                 │  │                 │  │                 │             │
│  │ • User configs  │  │ • Persistence   │  │ • Snapshots     │             │
│  │ • Endpoint rules│  │ • Management    │  │ • Statistics    │             │
│  │ • Metrics       │  │ • Multi-device  │  │ • Trends        │             │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘             │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────┐           │
│  │ ConsistencyService (enhanced)                                │           │
│  │                                                              │           │
│  │ • CDC Event Listener (PostgreSQL LISTEN/NOTIFY)             │           │
│  │ • Debounced check scheduling                                 │           │
│  │ • Warning management with resolution tracking                │           │
│  └─────────────────────────────────────────────────────────────┘           │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Data Layer                                         │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │ PostgreSQL  │  │ Redis       │  │ PostgreSQL  │  │ PostgreSQL  │        │
│  │             │  │             │  │             │  │             │        │
│  │ rate_limit_ │  │ session:*   │  │ progress_   │  │ consistency_│        │
│  │ configs     │  │ rate_limit:*│  │ snapshots   │  │ warnings    │        │
│  │ rate_limit_ │  │             │  │ phase_      │  │             │        │
│  │ rules       │  │             │  │ transitions │  │             │        │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘        │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Components and Interfaces

### 1. RateLimit Module Enhancement

#### New Entities

```java
@Entity
@Table(name = "rate_limit_configs")
public class RateLimitConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "bucket_capacity", nullable = false)
    private int bucketCapacity;
    
    @Column(name = "refill_rate", nullable = false)
    private int refillRate;
    
    @Column(name = "window_seconds", nullable = false)
    private int windowSeconds;
    
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

@Entity
@Table(name = "rate_limit_rules")
public class RateLimitRule {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "endpoint_pattern", nullable = false)
    private String endpointPattern;  // e.g., "/api/chat/**"
    
    @Column(name = "http_method")
    private String httpMethod;  // GET, POST, etc. or null for all
    
    @Column(name = "bucket_capacity", nullable = false)
    private int bucketCapacity;
    
    @Column(name = "refill_rate", nullable = false)
    private int refillRate;
    
    @Column(name = "priority", nullable = false)
    private int priority;  // Higher = more specific
    
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
}
```

#### Enhanced Service Interface

```java
public interface RateLimitConfigService {
    RateLimitConfig createConfig(UUID userId, RateLimitConfigRequest request);
    RateLimitConfig updateConfig(UUID configId, RateLimitConfigRequest request);
    void deleteConfig(UUID configId);
    Optional<RateLimitConfig> getConfigForUser(UUID userId);
    List<RateLimitConfig> getAllConfigs();
}

public interface RateLimitRuleService {
    RateLimitRule createRule(RateLimitRuleRequest request);
    RateLimitRule updateRule(UUID ruleId, RateLimitRuleRequest request);
    void deleteRule(UUID ruleId);
    Optional<RateLimitRule> findMatchingRule(String path, String method);
    List<RateLimitRule> getAllRules();
}

public interface RateLimitMetricsService {
    void recordHit(String key, boolean allowed);
    RateLimitMetrics getMetrics(String key);
    Map<String, RateLimitMetrics> getAllMetrics();
}
```

### 2. Session Module Enhancement

#### New Entities

```java
@Entity
@Table(name = "user_sessions")
public class UserSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "device_info")
    private String deviceInfo;
    
    @Column(name = "ip_address")
    private String ipAddress;
    
    @Column(name = "current_project_id")
    private UUID currentProjectId;
    
    @Column(name = "current_phase")
    @Enumerated(EnumType.STRING)
    private CreationPhase currentPhase;
    
    @Column(name = "last_activity_at", nullable = false)
    private LocalDateTime lastActivityAt;
    
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
    
    private LocalDateTime createdAt;
}
```

#### Service Interface

```java
public interface SessionManagementService {
    UserSession createSession(UUID userId, SessionCreateRequest request);
    Optional<UserSession> getSession(UUID sessionId);
    void updateActivity(UUID sessionId);
    void terminateSession(UUID sessionId);
    void terminateAllSessions(UUID userId, UUID exceptSessionId);
    List<UserSession> getActiveSessions(UUID userId);
    void cleanupExpiredSessions();
}

public interface SessionPersistenceService {
    void persistToRedis(UserSession session);
    Optional<UserSession> restoreFromRedis(UUID sessionId);
    void removeFromRedis(UUID sessionId);
    void updateTTL(UUID sessionId, Duration ttl);
}
```

### 3. Progress Module Enhancement

#### New Entities

```java
@Entity
@Table(name = "progress_snapshots")
public class ProgressSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    
    @Column(name = "phase")
    @Enumerated(EnumType.STRING)
    private CreationPhase phase;
    
    @Column(name = "phase_completion")
    private int phaseCompletion;
    
    @Column(name = "character_count")
    private long characterCount;
    
    @Column(name = "wiki_entry_count")
    private long wikiEntryCount;
    
    @Column(name = "chapter_count")
    private long chapterCount;
    
    @Column(name = "word_count")
    private long wordCount;
    
    @Column(name = "open_plot_loops")
    private long openPlotLoops;
    
    @Column(name = "snapshot_at", nullable = false)
    private LocalDateTime snapshotAt;
}

@Entity
@Table(name = "phase_transitions")
public class PhaseTransition {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    
    @Column(name = "from_phase")
    @Enumerated(EnumType.STRING)
    private CreationPhase fromPhase;
    
    @Column(name = "to_phase", nullable = false)
    @Enumerated(EnumType.STRING)
    private CreationPhase toPhase;
    
    @Column(name = "reason")
    private String reason;
    
    @Column(name = "transitioned_at", nullable = false)
    private LocalDateTime transitionedAt;
}
```

#### Enhanced Service Interface

```java
public interface ProgressPersistenceService {
    ProgressSnapshot saveSnapshot(UUID projectId);
    List<ProgressSnapshot> getHistory(UUID projectId, int limit);
    List<ProgressSnapshot> getHistoryBetween(UUID projectId, LocalDateTime start, LocalDateTime end);
    void deleteByProjectId(UUID projectId);
}

public interface ProgressStatisticsService {
    ProgressStatistics getStatistics(UUID projectId);
    ProgressTrend getTrend(UUID projectId, TrendPeriod period);
    List<PhaseTransition> getPhaseHistory(UUID projectId);
    WordCountStatistics getWordCountStats(UUID projectId);
    EntityStatistics getEntityStats(UUID projectId);
}
```

### 4. Consistency Module Enhancement

#### Enhanced Entity

```java
@Entity
@Table(name = "consistency_warnings")
public class ConsistencyWarning {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    
    @Column(name = "severity", nullable = false)
    @Enumerated(EnumType.STRING)
    private WarningSeverity severity;
    
    @Column(name = "warning_type", nullable = false)
    private String warningType;
    
    @Column(name = "message", nullable = false)
    private String message;
    
    @Column(name = "affected_entity_id")
    private UUID affectedEntityId;
    
    @Column(name = "affected_entity_type")
    @Enumerated(EnumType.STRING)
    private EntityType affectedEntityType;
    
    @Column(name = "related_entity_ids", columnDefinition = "jsonb")
    private List<UUID> relatedEntityIds;
    
    @Column(name = "suggested_resolution")
    private String suggestedResolution;
    
    @Column(name = "resolved", nullable = false)
    private boolean resolved = false;
    
    @Column(name = "dismissed", nullable = false)
    private boolean dismissed = false;
    
    @Column(name = "resolution_method")
    private String resolutionMethod;
    
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
    
    private LocalDateTime createdAt;
}

public enum WarningSeverity {
    INFO, WARNING, ERROR, CRITICAL
}
```

#### CDC Listener

```java
@Component
public class ConsistencyCDCListener {
    // PostgreSQL LISTEN/NOTIFY integration
    void onCharacterChange(EntityChangeEvent event);
    void onWikiEntryChange(EntityChangeEvent event);
    void onPlotLoopChange(EntityChangeEvent event);
}
```

#### Enhanced Service Interface

```java
public interface ConsistencyWarningService {
    ConsistencyWarning createWarning(ConsistencyWarningRequest request);
    Optional<ConsistencyWarning> getWarning(UUID warningId);
    List<ConsistencyWarning> getUnresolvedWarnings(UUID projectId);
    long getUnresolvedCount(UUID projectId);
    void resolveWarning(UUID warningId, String resolutionMethod);
    void dismissWarning(UUID warningId);
    void bulkResolve(List<UUID> warningIds, String resolutionMethod);
}
```

## Data Models

### Database Schema (V10__partial_modules_completion.sql)

```sql
-- Rate Limit Configs
CREATE TABLE rate_limit_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    bucket_capacity INT NOT NULL DEFAULT 100,
    refill_rate INT NOT NULL DEFAULT 10,
    window_seconds INT NOT NULL DEFAULT 60,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

-- Rate Limit Rules
CREATE TABLE rate_limit_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_pattern VARCHAR(255) NOT NULL,
    http_method VARCHAR(10),
    bucket_capacity INT NOT NULL DEFAULT 100,
    refill_rate INT NOT NULL DEFAULT 10,
    priority INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_rate_limit_rules_pattern ON rate_limit_rules(endpoint_pattern);

-- User Sessions
CREATE TABLE user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_info VARCHAR(500),
    ip_address VARCHAR(45),
    current_project_id UUID REFERENCES projects(id) ON DELETE SET NULL,
    current_phase VARCHAR(50),
    last_activity_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_expires_at ON user_sessions(expires_at);

-- Progress Snapshots
CREATE TABLE progress_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    phase VARCHAR(50),
    phase_completion INT DEFAULT 0,
    character_count BIGINT DEFAULT 0,
    wiki_entry_count BIGINT DEFAULT 0,
    chapter_count BIGINT DEFAULT 0,
    word_count BIGINT DEFAULT 0,
    open_plot_loops BIGINT DEFAULT 0,
    snapshot_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_progress_snapshots_project_id ON progress_snapshots(project_id);
CREATE INDEX idx_progress_snapshots_snapshot_at ON progress_snapshots(snapshot_at);

-- Phase Transitions
CREATE TABLE phase_transitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    from_phase VARCHAR(50),
    to_phase VARCHAR(50) NOT NULL,
    reason TEXT,
    transitioned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_phase_transitions_project_id ON phase_transitions(project_id);

-- Enhance consistency_warnings table
ALTER TABLE consistency_warnings 
ADD COLUMN IF NOT EXISTS severity VARCHAR(20) DEFAULT 'WARNING',
ADD COLUMN IF NOT EXISTS warning_type VARCHAR(100),
ADD COLUMN IF NOT EXISTS related_entity_ids JSONB,
ADD COLUMN IF NOT EXISTS suggested_resolution TEXT,
ADD COLUMN IF NOT EXISTS dismissed BOOLEAN DEFAULT false,
ADD COLUMN IF NOT EXISTS resolution_method VARCHAR(100);

-- CDC Triggers for consistency checks
CREATE OR REPLACE FUNCTION notify_entity_change() RETURNS TRIGGER AS $$
BEGIN
    PERFORM pg_notify('entity_changes', json_build_object(
        'table', TG_TABLE_NAME,
        'operation', TG_OP,
        'id', NEW.id,
        'project_id', NEW.project_id
    )::text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_character_change
AFTER INSERT OR UPDATE ON characters
FOR EACH ROW EXECUTE FUNCTION notify_entity_change();

CREATE TRIGGER trigger_wiki_entry_change
AFTER INSERT OR UPDATE ON wiki_entries
FOR EACH ROW EXECUTE FUNCTION notify_entity_change();

CREATE TRIGGER trigger_plot_loop_change
AFTER INSERT OR UPDATE ON plot_loops
FOR EACH ROW EXECUTE FUNCTION notify_entity_change();
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

Based on the prework analysis, the following properties have been identified after removing redundancies:

### Property 1: User rate limit config persistence
*For any* valid user rate limit configuration, saving it and then retrieving it should return an equivalent configuration with all fields preserved (user ID, bucket capacity, refill rate, window duration).
**Validates: Requirements 1.1**

### Property 2: User-specific rate limit application
*For any* user with a custom rate limit configuration, requests from that user should be limited according to their specific configuration, not the default.
**Validates: Requirements 1.2, 1.3**

### Property 3: Rate limit config update immediacy
*For any* rate limit configuration update, subsequent requests should immediately use the new configuration values without requiring a restart.
**Validates: Requirements 1.4**

### Property 4: Endpoint rule matching specificity
*For any* set of overlapping endpoint rate limit rules, the most specific rule (highest priority) should be applied to matching requests.
**Validates: Requirements 2.2, 2.3**

### Property 5: Session persistence round-trip
*For any* user session, persisting to Redis and restoring should return an equivalent session with all state preserved (last activity, current phase, pending tasks).
**Validates: Requirements 3.1, 3.2, 3.5**

### Property 6: Session termination completeness
*For any* session termination request, the session should be immediately invalidated and removed from both database and Redis.
**Validates: Requirements 3.4, 4.2**

### Property 7: Bulk session termination preserves current
*For any* "terminate all sessions" request, all sessions except the current one should be invalidated.
**Validates: Requirements 4.3**

### Property 8: Progress snapshot completeness
*For any* progress snapshot, it should contain all required metrics (character count, wiki entry count, chapter count, word count, plot loop status).
**Validates: Requirements 5.1, 5.3**

### Property 9: Progress history ordering
*For any* progress history request, snapshots should be returned in chronological order with accurate timestamps.
**Validates: Requirements 5.2**

### Property 10: Progress cascade deletion
*For any* project deletion, all associated progress snapshots and phase transitions should be deleted.
**Validates: Requirements 5.4**

### Property 11: CDC debounce effectiveness
*For any* sequence of rapid entity updates within the debounce window, only one consistency check should be triggered.
**Validates: Requirements 7.4**

### Property 12: Consistency check rate limiting
*For any* project, consistency checks should be rate-limited to at most one check per 5 minutes.
**Validates: Requirements 7.5**

### Property 13: Warning resolution state transition
*For any* warning resolution, the warning should be marked as resolved with the resolution method recorded, and excluded from unresolved warnings list.
**Validates: Requirements 8.4, 8.5**

## Error Handling

### Rate Limit Module
- Invalid configuration values: Return 400 Bad Request with validation details
- Configuration not found: Return 404 Not Found
- Redis unavailable: Fall back to local rate limiting with warning log

### Session Module
- Session not found: Return 404 Not Found
- Session expired: Return 401 Unauthorized with "session_expired" error code
- Redis unavailable: Fall back to database-only session management

### Progress Module
- Project not found: Return 404 Not Found
- Statistics calculation failure: Return partial results with error flag

### Consistency Module
- CDC listener failure: Log error and continue, retry on next change
- Warning creation failure: Log error, do not block the triggering operation

## Testing Strategy

### Dual Testing Approach

This implementation uses both unit tests and property-based tests:

- **Unit tests**: Verify specific examples, edge cases, and integration points
- **Property-based tests**: Verify universal properties using jqwik framework

### Property-Based Testing Framework

Using **jqwik 1.9.0** as specified in the tech stack.

### Test Structure

```
src/test/java/com/inkflow/module/
├── ratelimit/
│   ├── RateLimitConfigServiceTest.java          # Unit tests
│   └── RateLimitConfigPropertyTest.java         # Property tests
├── session/
│   ├── SessionManagementServiceTest.java        # Unit tests
│   └── SessionPersistencePropertyTest.java      # Property tests
├── progress/
│   ├── ProgressPersistenceServiceTest.java      # Unit tests
│   └── ProgressSnapshotPropertyTest.java        # Property tests
└── consistency/
    ├── ConsistencyWarningServiceTest.java       # Unit tests
    └── ConsistencyCDCPropertyTest.java          # Property tests
```

### Property Test Annotations

Each property-based test must be tagged with:
```java
/**
 * **Feature: v2-partial-modules-completion, Property {number}: {property_text}**
 * **Validates: Requirements X.Y**
 */
@Property(tries = 100)
```

