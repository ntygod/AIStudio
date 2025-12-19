/**
 * API 模块导出
 */

export { TokenManager, tokenManager } from './token-manager';
export { 
  ApiClient, 
  ErrorCode, 
  initApiClient, 
  getApiClient,
  type ApiClientConfig 
} from './client';
export { 
  SSEClient, 
  initSSEClient, 
  getSSEClient,
  SSE_EVENT_TYPES,
  type SSEClientOptions,
  type SSEEvent,
  type SSEEventType,
  // 一致性相关事件类型 (Requirements: 9.1, 9.3)
  type PreflightResultEvent,
  type PreflightWarning,
  type ConsistencyWarningEvent,
  type ConsistencyCheckCompleteEvent,
  type Severity,
  type EntityType,
  type WarningType,
  // 演进相关事件类型 (Requirements: 9.2)
  type EvolutionUpdateEvent,
  type SnapshotCreatedEvent,
  type ChangeType,
  type EvolutionEntityType,
} from './sse-client';
