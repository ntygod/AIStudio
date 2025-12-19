/**
 * API 相关类型定义
 */

// ============ 通用类型 ============

export interface ApiResponse<T> {
  data: T;
  status: number;
  headers: Headers;
}

export interface ApiError {
  code: string;
  message: string;
  details?: Record<string, unknown>;
}

export interface PaginationInfo {
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ============ 认证相关 ============

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
}

export type UserStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED';

export interface User {
  id: string;
  username: string;
  email: string;
  displayName?: string;
  avatarUrl?: string;
  bio?: string;
  status: UserStatus;
  emailVerified: boolean;
  createdAt: string;
  lastLoginAt?: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}

export interface LoginRequest {
  identifier: string;
  password: string;
  deviceInfo?: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  displayName?: string;
}

// ============ 项目相关 ============

// 创作阶段 - 与后端 CreationPhase 枚举保持一致
export type CreationPhase = 
  | 'IDEA'          // 灵感收集
  | 'WORLDBUILDING' // 世界构建
  | 'CHARACTER'     // 角色设计
  | 'OUTLINE'       // 大纲规划
  | 'WRITING'       // 正式写作
  | 'REVISION'      // 修订完善
  | 'COMPLETED';    // 创作完成
export type ProjectStatus = 'ACTIVE' | 'ARCHIVED' | 'DELETED';

export interface Project {
  id: string;
  userId: string;
  title: string;
  description?: string;
  coverUrl?: string;
  status: ProjectStatus;
  creationPhase: CreationPhase;
  metadata?: Record<string, unknown>;
  worldSettings?: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
  wordCount?: number;
}

export interface CreateProjectRequest {
  title: string;
  description?: string;
  coverUrl?: string;
  metadata?: Record<string, unknown>;
}

export interface UpdateProjectRequest {
  title?: string;
  description?: string;
  coverUrl?: string;
  status?: ProjectStatus;
  metadata?: Record<string, unknown>;
  worldSettings?: Record<string, unknown>;
}

// ============ 内容结构 ============

export type ChapterStatus = 'DRAFT' | 'WRITING' | 'REVIEW' | 'COMPLETE';

export interface Volume {
  id: string;
  projectId: string;
  title: string;
  description?: string;
  orderIndex: number;
  chapterCount: number;
  wordCount?: number;
  createdAt: string;
  updatedAt: string;
}

export interface Chapter {
  id: string;
  projectId: string;
  volumeId: string;
  title: string;
  summary?: string;
  content?: string;
  orderIndex: number;
  status: ChapterStatus;
  wordCount: number;
  metadata?: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}

export interface ChapterContent {
  id: string;
  chapterId: string;
  content: string;
  wordCount: number;
  updatedAt: string;
  version?: number;
}

export interface CreateVolumeRequest {
  title: string;
  description?: string;
}

export interface CreateChapterRequest {
  volumeId: string;
  title: string;
  summary?: string;
}

export interface UpdateChapterRequest {
  title?: string;
  summary?: string;
  status?: ChapterStatus;
  metadata?: Record<string, unknown>;
}

export interface SaveContentRequest {
  content: string;
}

export interface ReorderRequest {
  id: string;
  orderIndex: number;
}

// ============ 角色相关 ============

export interface CharacterRelationship {
  targetId: string;
  type: string;
  description?: string;
}

export interface Character {
  id: string;
  projectId: string;
  name: string;
  role: string;
  description?: string;
  personality?: Record<string, unknown>;
  relationships: CharacterRelationship[];
  status: string;
  isActive: boolean;
  archetype?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCharacterRequest {
  projectId: string;
  name: string;
  role: string;
  description?: string;
  personality?: Record<string, unknown>;
  archetype?: string;
}

export interface UpdateCharacterRequest {
  name?: string;
  role?: string;
  description?: string;
  personality?: Record<string, unknown>;
  status?: string;
  isActive?: boolean;
  archetype?: string;
}

export interface GraphNode {
  id: string;
  name: string;
  role: string;
  status: string;
  archetype?: string;
  isActive: boolean;
}

export interface GraphEdge {
  source: string;
  target: string;
  type: string;
  description?: string;
  strength?: number;
  bidirectional: boolean;
}

export interface RelationshipGraph {
  nodes: GraphNode[];
  edges: GraphEdge[];
}

// ============ Wiki 相关 ============

export interface WikiEntry {
  id: string;
  projectId: string;
  title: string;
  type: string;
  content: string;
  aliases: string[];
  tags: string[];
  timeVersion?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateWikiEntryRequest {
  projectId: string;
  title: string;
  type: string;
  content: string;
  aliases?: string[];
  tags?: string[];
  timeVersion?: string;
}

export interface UpdateWikiEntryRequest {
  title?: string;
  type?: string;
  content?: string;
  aliases?: string[];
  tags?: string[];
  timeVersion?: string;
}

// ============ PlotLoop 相关 ============

export type PlotLoopStatus = 'OPEN' | 'RESOLVED' | 'ABANDONED';

export interface PlotLoop {
  id: string;
  projectId: string;
  title: string;
  description: string;
  status: PlotLoopStatus;
  introChapterId?: string;
  introChapterOrder?: number;
  resolutionChapterId?: string;
  resolutionChapterOrder?: number;
  abandonReason?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreatePlotLoopRequest {
  projectId: string;
  title: string;
  description: string;
  introChapterId?: string;
}

export interface UpdatePlotLoopRequest {
  title?: string;
  description?: string;
  status?: PlotLoopStatus;
  resolutionChapterId?: string;
  abandonReason?: string;
}

// ============ Chat 相关 ============

export interface ChatRequest {
  projectId: string;
  message: string;
  phase?: string;
  sessionId?: string;
  chapterId?: string;
  characterIds?: string[];
  sceneType?: string;
  targetWordCount?: number;
  consistency?: boolean;
  ragEnabled?: boolean;
  /** 激活的技能 ID 列表 (Requirements: 4.4) */
  skills?: string[];
}

export interface ChatResponse {
  content: string;
  sessionId: string;
  phase?: string;
}

export interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
  artifacts?: Artifact[];
}

export interface ThoughtEvent {
  id: string;
  type: 'thinking' | 'rag' | 'skill' | 'tool';
  agent: string;
  message: string;
  confidence?: number;
}

export interface ToolEvent {
  id: string;
  name: string;
  status: 'started' | 'completed' | 'failed';
  input?: Record<string, unknown>;
  output?: string;
}

export interface Artifact {
  type: 'character' | 'wiki' | 'plotloop' | 'content';
  id: string;
  title: string;
  preview: string;
}

// ============ 导入导出 ============

export interface ExportMetadata {
  version: string;
  exportedAt: string;
  source: string;
}

export interface ExportData {
  metadata: ExportMetadata;
  project: ExportProjectDto;
}

export interface ExportProjectDto {
  title: string;
  description?: string;
  coverUrl?: string;
  status: string;
  creationPhase: string;
  metadata?: Record<string, unknown>;
  worldSettings?: Record<string, unknown>;
  volumes: ExportVolumeDto[];
}

export interface ExportVolumeDto {
  title: string;
  description?: string;
  orderIndex: number;
  chapters: ExportChapterDto[];
}

export interface ExportChapterDto {
  title: string;
  summary?: string;
  orderIndex: number;
  status: string;
  metadata?: Record<string, unknown>;
  blocks: ExportStoryBlockDto[];
}

export interface ExportStoryBlockDto {
  blockType: string;
  content: string;
  rank: string;
  metadata?: Record<string, unknown>;
}

// ============ 章节快照/版本历史 ============

export type SnapshotTrigger = 'MANUAL' | 'AUTO_SAVE' | 'AI_EDIT' | 'RESTORE';

export interface ChapterSnapshot {
  id: string;
  chapterId: string;
  content: string;
  wordCount: number;
  description?: string;
  trigger: SnapshotTrigger;
  createdAt: string;
}

export interface CreateSnapshotRequest {
  description?: string;
  trigger?: SnapshotTrigger;
}

export interface SnapshotDiff {
  additions: number;
  deletions: number;
  changes: DiffChange[];
}

export interface DiffChange {
  type: 'add' | 'remove' | 'equal';
  value: string;
}
