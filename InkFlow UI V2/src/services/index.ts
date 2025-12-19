/**
 * 服务导出
 */

export { AuthService, authService } from './auth-service';
export { ProjectService, projectService } from './project-service';
export { ContentService, contentService } from './content-service';
export { AssetService, assetService } from './asset-service';
export { ChatService, chatService, type SceneRequest, type ChatCallbacks } from './chat-service';
export { 
  ConsistencyService, 
  consistencyService,
  type ConsistencyWarning,
  type WarningCount,
  type EntityType,
  type WarningType,
  type Severity,
} from './consistency-service';
export {
  EvolutionService,
  evolutionService,
  type EvolutionEntityType,
  type ChangeType,
  type StateSnapshot,
  type EvolutionTimeline,
  type StateChange,
  type StateCompareResult,
  type ChangeRecord,
} from './evolution-service';
export {
  ProgressService,
  progressService,
  type CreationProgress,
  type WordCountStatistics,
  type TrendDataPoint,
  type ProgressTrend,
  type ProgressStatistics,
  type DailyWordCount,
} from './progress-service';
export {
  StyleService,
  styleService,
  type StyleStats,
  type StyleSample,
  type SaveStyleSampleRequest,
  type EditRatioResult,
} from './style-service';
export {
  ProviderService,
  providerService,
  PROVIDER_INFO,
  type ProviderType,
  type ProviderConfig,
  type SaveProviderConfigRequest,
} from './provider-service';
export {
  ImportExportService,
  importExportService,
  type ImportPreview,
  type ImportResult,
} from './import-export-service';
