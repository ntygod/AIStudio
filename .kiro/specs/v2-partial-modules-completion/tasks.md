# Implementation Plan

- [x] 1. Database migration and base entities




  - [ ] 1.1 Create V10 database migration script
    - Create `rate_limit_configs` table with user_id, bucket_capacity, refill_rate, window_seconds
    - Create `rate_limit_rules` table with endpoint_pattern, http_method, priority
    - Create `user_sessions` table with device_info, ip_address, current_phase
    - Create `progress_snapshots` table with all progress metrics
    - Create `phase_transitions` table for history tracking
    - Add CDC triggers for entity_changes notification


    - _Requirements: 1.1, 2.1, 3.1, 5.1, 7.1_

  - [ ] 1.2 Create entity classes for new tables
    - Create `RateLimitConfig` entity
    - Create `RateLimitRule` entity
    - Create `UserSession` entity


    - Create `ProgressSnapshot` entity
    - Create `PhaseTransition` entity
    - _Requirements: 1.1, 2.1, 3.1, 5.1_

  - [x] 1.3 Create repository interfaces




    - Create `RateLimitConfigRepository`
    - Create `RateLimitRuleRepository`
    - Create `UserSessionRepository`
    - Create `ProgressSnapshotRepository`
    - Create `PhaseTransitionRepository`
    - _Requirements: 1.1, 2.1, 3.1, 5.1_



- [x] 2. RateLimit module enhancement





  - [x] 2.1 Implement RateLimitConfigService


    - Implement CRUD operations for user rate limit configs
    - Add cache layer for config lookup
    - _Requirements: 1.1, 1.4, 1.5_

  - [x]* 2.2 Write property test for rate limit config persistence


    - **Property 1: User rate limit config persistence**
    - **Validates: Requirements 1.1**

  - [x] 2.3 Implement RateLimitRuleService


    - Implement CRUD operations for endpoint rules
    - Implement rule matching with priority
    - _Requirements: 2.1, 2.2, 2.3_



  - [ ]* 2.4 Write property test for endpoint rule matching
    - **Property 4: Endpoint rule matching specificity**
    - **Validates: Requirements 2.2, 2.3**

  - [x] 2.5 Enhance RateLimitFilter


    - Integrate user config lookup




    - Integrate endpoint rule matching
    - Add metrics collection
    - _Requirements: 1.2, 1.3, 2.2, 2.5_

  - [ ]* 2.6 Write property test for user-specific rate limit application
    - **Property 2: User-specific rate limit application**
    - **Validates: Requirements 1.2, 1.3**


  - [x] 2.7 Create RateLimitController for admin API


    - Add endpoints for config management
    - Add endpoints for rule management
    - Add metrics endpoint
    - _Requirements: 1.1, 2.1, 2.4, 2.5_

- [ ] 3. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Session module enhancement




  - [x] 4.1 Implement SessionPersistenceService


    - Implement Redis persistence with TTL
    - Implement restore from Redis
    - Implement cleanup on logout
    - _Requirements: 3.1, 3.2, 3.3, 3.4_


  - [ ]* 4.2 Write property test for session persistence round-trip
    - **Property 5: Session persistence round-trip**
    - **Validates: Requirements 3.1, 3.2, 3.5**

  - [x] 4.3 Implement SessionManagementService

    - Implement session creation with device info
    - Implement session listing for user
    - Implement single session termination
    - Implement bulk session termination



    - _Requirements: 4.1, 4.2, 4.3, 4.4_

  - [ ]* 4.4 Write property test for session termination
    - **Property 6: Session termination completeness**
    - **Property 7: Bulk session termination preserves current**
    - **Validates: Requirements 3.4, 4.2, 4.3**

  - [x] 4.5 Enhance SessionResumeService


    - Integrate with SessionPersistenceService
    - Add session state restoration on startup
    - _Requirements: 3.2, 3.5_

  - [x] 4.6 Create SessionController


    - Add endpoint to list active sessions
    - Add endpoint to terminate session
    - Add endpoint to terminate all sessions
    - _Requirements: 4.1, 4.2, 4.3_

  - [x] 4.7 Add scheduled task for session cleanup


    - Implement expired session cleanup
    - Run every hour
    - _Requirements: 3.3, 4.4_

- [ ] 5. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Progress module enhancement





  - [x] 6.1 Implement ProgressPersistenceService


    - Implement snapshot saving
    - Implement history retrieval
    - Implement cascade deletion
    - _Requirements: 5.1, 5.2, 5.4_

  - [ ]* 6.2 Write property test for progress snapshot
    - **Property 8: Progress snapshot completeness**
    - **Property 9: Progress history ordering**
    - **Validates: Requirements 5.1, 5.2, 5.3**

  - [x] 6.3 Implement ProgressStatisticsService


    - Implement statistics calculation
    - Implement trend calculation (daily/weekly/monthly)
    - Implement word count statistics
    - Implement entity statistics
    - _Requirements: 6.1, 6.2, 6.4, 6.5_

  - [x] 6.4 Implement PhaseTransitionService


    - Record phase transitions with reason
    - Retrieve transition history
    - _Requirements: 6.3_

  - [x] 6.5 Enhance CreationProgressService


    - Integrate with ProgressPersistenceService
    - Auto-save snapshots on progress change
    - _Requirements: 5.1, 5.5_

  - [ ]* 6.6 Write property test for cascade deletion
    - **Property 10: Progress cascade deletion**
    - **Validates: Requirements 5.4**

  - [x] 6.7 Create ProgressController


    - Add endpoint for current progress
    - Add endpoint for progress history
    - Add endpoint for statistics
    - Add endpoint for trends
    - Add endpoint for phase history
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

- [ ] 7. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Consistency module enhancement





  - [x] 8.1 Implement CDC listener


    - Create PostgreSQL LISTEN/NOTIFY listener
    - Handle character change events
    - Handle wiki entry change events
    - Handle plot loop change events
    - _Requirements: 7.1, 7.2, 7.3_

  - [ ]* 8.2 Write property test for CDC debounce
    - **Property 11: CDC debounce effectiveness**
    - **Validates: Requirements 7.4**

  - [x] 8.3 Enhance ProactiveConsistencyService


    - Improve debounce mechanism
    - Add rate limiting per project
    - _Requirements: 7.4, 7.5_

  - [ ]* 8.4 Write property test for consistency check rate limiting
    - **Property 12: Consistency check rate limiting**
    - **Validates: Requirements 7.5**

  - [x] 8.5 Implement ConsistencyWarningService


    - Implement warning creation with severity
    - Implement warning resolution
    - Implement warning dismissal
    - Implement bulk operations
    - _Requirements: 8.1, 8.4, 8.5_

  - [ ]* 8.6 Write property test for warning resolution
    - **Property 13: Warning resolution state transition**
    - **Validates: Requirements 8.4, 8.5**

  - [x] 8.7 Create ConsistencyController


    - Add endpoint for unresolved warnings
    - Add endpoint for warning count
    - Add endpoint for warning details
    - Add endpoint for resolve/dismiss
    - _Requirements: 8.2, 8.3, 8.4, 8.5_

- [ ] 9. Final Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 10. Update documentation





  - [x] 10.1 Update V2_MODULE_AUDIT_REPORT.md


    - Mark ratelimit, session, progress, consistency as fully implemented
    - Update module status table
    - _Requirements: All_


  - [x] 10.2 Update README.md

    - Add new API endpoints documentation
    - Update module status section
    - _Requirements: All_

