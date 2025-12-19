# Requirements Document

## Introduction

本文档定义了 InkFlow Backend V2 中四个部分实现模块的完善需求。根据模块审计报告，以下模块需要补充功能：

1. **ratelimit** - 缺少用户级别限流配置和动态限流规则
2. **session** - 缺少完整的会话管理和会话持久化
3. **progress** - 缺少进度持久化和进度统计 API
4. **consistency** - 缺少 CDC 触发机制和冲突解决支持

## Glossary

- **RateLimitService**: 限流服务，使用 Token Bucket 算法控制请求频率
- **SessionResumeService**: 会话恢复服务，检测上次会话并生成恢复提示
- **CreationProgressService**: 创作进度服务，计算阶段完成度和追踪实体数量
- **ProactiveConsistencyService**: 主动一致性检查服务，实现防抖、限流和静默警告存储
- **CDC**: Change Data Capture，数据变更捕获
- **Token Bucket**: 令牌桶算法，一种限流算法
- **Debounce**: 防抖，批量更新后等待一段时间再触发操作

## Requirements

### Requirement 1: 用户级别限流配置

**User Story:** As a system administrator, I want to configure different rate limits for different users, so that I can provide better service to premium users while protecting the system from abuse.

#### Acceptance Criteria

1. WHEN an administrator creates a rate limit configuration for a user THEN the system SHALL store the configuration with user ID, bucket capacity, refill rate, and window duration
2. WHEN a user makes a request THEN the system SHALL apply the user-specific rate limit configuration if one exists
3. WHEN no user-specific configuration exists THEN the system SHALL apply the default rate limit configuration
4. WHEN an administrator updates a rate limit configuration THEN the system SHALL apply the new configuration immediately without restart
5. WHEN an administrator deletes a rate limit configuration THEN the system SHALL revert to the default rate limit for that user

### Requirement 2: 动态限流规则

**User Story:** As a system administrator, I want to define rate limit rules based on API endpoints, so that I can apply different limits to different operations.

#### Acceptance Criteria

1. WHEN an administrator creates an endpoint-specific rate limit rule THEN the system SHALL store the rule with endpoint pattern, HTTP method, bucket capacity, and refill rate
2. WHEN a request matches an endpoint-specific rule THEN the system SHALL apply that rule instead of the default
3. WHEN multiple rules match a request THEN the system SHALL apply the most specific rule based on path matching
4. WHEN an administrator lists all rate limit rules THEN the system SHALL return all configured rules with their current status
5. WHEN the system detects high load THEN the system SHALL provide metrics for rate limit hits and remaining tokens

### Requirement 3: 会话持久化

**User Story:** As a user, I want my session state to be preserved across server restarts, so that I can continue my work without losing context.

#### Acceptance Criteria

1. WHEN a user session is created THEN the system SHALL persist the session state to Redis with a configurable TTL
2. WHEN a user returns after server restart THEN the system SHALL restore the session state from Redis
3. WHEN a session expires THEN the system SHALL automatically clean up the session data from Redis
4. WHEN a user explicitly logs out THEN the system SHALL remove the session data from Redis immediately
5. WHEN retrieving session state THEN the system SHALL include last activity time, current phase, and pending tasks

### Requirement 4: 完整会话管理

**User Story:** As a user, I want to manage my active sessions, so that I can see where I'm logged in and terminate sessions if needed.

#### Acceptance Criteria

1. WHEN a user requests their active sessions THEN the system SHALL return a list of all active sessions with device info and last activity time
2. WHEN a user terminates a specific session THEN the system SHALL invalidate that session immediately
3. WHEN a user terminates all sessions THEN the system SHALL invalidate all sessions except the current one
4. WHEN a session is inactive for more than 24 hours THEN the system SHALL mark it as expired
5. WHEN a user logs in from a new device THEN the system SHALL create a new session and notify the user of the new login

### Requirement 5: 进度持久化

**User Story:** As a user, I want my creation progress to be saved automatically, so that I can track my progress over time.

#### Acceptance Criteria

1. WHEN a user's creation progress changes THEN the system SHALL persist the progress snapshot to the database
2. WHEN a user requests their progress history THEN the system SHALL return progress snapshots with timestamps
3. WHEN calculating progress THEN the system SHALL include character count, wiki entry count, chapter count, word count, and plot loop status
4. WHEN a project is deleted THEN the system SHALL cascade delete all associated progress records
5. WHEN retrieving progress THEN the system SHALL calculate phase completion percentage based on configurable thresholds

### Requirement 6: 进度统计 API

**User Story:** As a user, I want to view detailed statistics about my creation progress, so that I can understand my writing patterns and productivity.

#### Acceptance Criteria

1. WHEN a user requests progress statistics THEN the system SHALL return current progress, phase completion, and suggested next phase
2. WHEN a user requests progress trends THEN the system SHALL return daily/weekly/monthly progress changes
3. WHEN a user requests phase transition history THEN the system SHALL return all phase changes with timestamps and reasons
4. WHEN a user requests word count statistics THEN the system SHALL return total words, words per chapter, and daily word count
5. WHEN a user requests entity statistics THEN the system SHALL return counts for characters, wiki entries, plot loops, and their relationships

### Requirement 7: CDC 触发机制

**User Story:** As a system, I want to automatically trigger consistency checks when data changes, so that inconsistencies are detected early.

#### Acceptance Criteria

1. WHEN a character entity is created or updated THEN the system SHALL trigger a consistency check for related entities
2. WHEN a wiki entry is created or updated THEN the system SHALL trigger a consistency check for referencing entities
3. WHEN a plot loop status changes THEN the system SHALL trigger a consistency check for the associated chapter
4. WHEN multiple entities are updated in quick succession THEN the system SHALL debounce the checks to avoid redundant processing
5. WHEN a consistency check is triggered THEN the system SHALL respect the rate limit of one check per project per 5 minutes

### Requirement 8: 冲突解决支持

**User Story:** As a user, I want to be notified of consistency issues and have tools to resolve them, so that my story remains coherent.

#### Acceptance Criteria

1. WHEN a consistency warning is created THEN the system SHALL store it with severity level, affected entities, and suggested resolution
2. WHEN a user views their project THEN the system SHALL display unresolved warnings count in the UI
3. WHEN a user requests warning details THEN the system SHALL return the warning with context and resolution options
4. WHEN a user resolves a warning THEN the system SHALL mark it as resolved and record the resolution method
5. WHEN a user dismisses a warning THEN the system SHALL mark it as dismissed and exclude it from future notifications

