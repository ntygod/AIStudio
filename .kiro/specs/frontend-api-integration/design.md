# Design Document: Frontend API Integration

## Overview

本设计文档描述 InkFlow UI V2 前端与 inkflow-backend-v2 后端 API 的集成架构。采用分层架构设计，包括 API 客户端层、状态管理层、服务层和 UI 层。使用 TypeScript 确保类型安全，使用 React Context + Zustand 进行状态管理。

## Architecture

```mermaid
graph TB
    subgraph UI Layer
        LoginPage[LoginPage]
        EditorPage[EditorPage]
        SettingsPage[SettingsPage]
        Components[UI Components]
    end
    
    subgraph Service Layer
        AuthService[AuthService]
        ProjectService[ProjectService]
        ContentService[ContentService]
        CharacterService[CharacterService]
        WikiService[WikiService]
        ChatService[ChatService]
        PlotLoopService[PlotLoopService]
    end
    
    subgraph State Layer
        AuthStore[AuthStore]
        ProjectStore[ProjectStore]
        ContentStore[ContentStore]
        AssetStore[AssetStore]
        ChatStore[ChatStore]
    end
    
    subgraph API Layer
        ApiClient[ApiClient]
        SSEClient[SSEClient]
        TokenManager[TokenManager]
    end
    
    subgraph Backend
        Backend[InkFlow Backend API]
    end
    
    UI Layer --> Service Layer
    Service Layer --> State Layer
    Service Layer --> API Layer
    API Layer --> Backend
```

## Components and Interfaces

### 1. API Client Layer

#### ApiClient (`src/api/client.ts`)

```typescript
interface ApiClientConfig {
  baseUrl: string;
  timeout: number;
  onUnauthorized: () => void;
}

interface ApiResponse<T> {
  data: T;
  status: number;
  headers: Headers;
}

interface ApiError {
  code: string;
  message: string;
  details?: Record<string, unknown>;
}

class ApiClient {
  private baseUrl: string;
  private tokenManager: TokenManager;
  
  constructor(config: ApiClientConfig);
  
  async get<T>(path: string, params?: Record<string, string>): Promise<ApiResponse<T>>;
  async post<T>(path: string, body?: unknown): Promise<ApiResponse<T>>;
  async put<T>(path: string, body?: unknown): Promise<ApiResponse<T>>;
  async patch<T>(path: string, body?: unknown): Promise<ApiResponse<T>>;
  async delete<T>(path: string): Promise<ApiResponse<T>>;
}
```

#### TokenManager (`src/api/token-manager.ts`)

```typescript
interface TokenPair {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
}

class TokenManager {
  private tokens: TokenPair | null;
  private refreshPromise: Promise<TokenPair> | null;
  
  setTokens(tokens: TokenPair): void;
  getAccessToken(): string | null;
  getRefreshToken(): string | null;
  isExpiringSoon(thresholdMs: number): boolean;
  clear(): void;
  
  async refreshIfNeeded(): Promise<string | null>;
}
```

#### SSEClient (`src/api/sse-client.ts`)

```typescript
interface SSEEvent {
  event: string;
  data: string;
}

interface SSEClientOptions {
  onContent: (content: string) => void;
  onThought?: (thought: ThoughtEvent) => void;
  onTool?: (tool: ToolEvent) => void;
  onDone: () => void;
  onError: (error: Error) => void;
}

class SSEClient {
  private controller: AbortController | null;
  
  async connect(url: string, body: unknown, options: SSEClientOptions): Promise<void>;
  abort(): void;
}
```

### 2. State Management Layer

#### AuthStore (`src/stores/auth-store.ts`)

```typescript
interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
}

interface AuthActions {
  login(email: string, password: string): Promise<void>;
  register(email: string, password: string, nickname: string): Promise<void>;
  logout(): Promise<void>;
  validateSession(): Promise<void>;
  clearError(): void;
}

type AuthStore = AuthState & AuthActions;
```

#### ProjectStore (`src/stores/project-store.ts`)

```typescript
interface ProjectState {
  projects: Project[];
  currentProject: Project | null;
  isLoading: boolean;
  error: string | null;
  pagination: PaginationInfo;
}

interface ProjectActions {
  fetchProjects(page?: number): Promise<void>;
  fetchProject(id: string): Promise<void>;
  createProject(data: CreateProjectRequest): Promise<Project>;
  updateProject(id: string, data: UpdateProjectRequest): Promise<void>;
  deleteProject(id: string): Promise<void>;
  setCurrentProject(project: Project | null): void;
  updatePhase(id: string, phase: CreationPhase): Promise<void>;
}

type ProjectStore = ProjectState & ProjectActions;
```

#### ContentStore (`src/stores/content-store.ts`)

```typescript
interface ContentState {
  volumes: Volume[];
  chapters: Map<string, Chapter[]>; // volumeId -> chapters
  currentChapter: Chapter | null;
  isLoading: boolean;
  error: string | null;
}

interface ContentActions {
  fetchVolumes(projectId: string): Promise<void>;
  fetchChapters(projectId: string, volumeId: string): Promise<void>;
  createVolume(projectId: string, data: CreateVolumeRequest): Promise<Volume>;
  createChapter(projectId: string, data: CreateChapterRequest): Promise<Chapter>;
  updateChapter(projectId: string, chapterId: string, data: UpdateChapterRequest): Promise<void>;
  reorderChapters(projectId: string, orders: ReorderRequest[]): Promise<void>;
  setCurrentChapter(chapter: Chapter | null): void;
}

type ContentStore = ContentState & ContentActions;
```

#### AssetStore (`src/stores/asset-store.ts`)

```typescript
interface AssetState {
  characters: Character[];
  wikiEntries: WikiEntry[];
  plotLoops: PlotLoop[];
  relationshipGraph: RelationshipGraph | null;
  isLoading: boolean;
  error: string | null;
}

interface AssetActions {
  fetchCharacters(projectId: string): Promise<void>;
  fetchWikiEntries(projectId: string): Promise<void>;
  fetchPlotLoops(projectId: string): Promise<void>;
  fetchRelationshipGraph(projectId: string): Promise<void>;
  createCharacter(data: CreateCharacterRequest): Promise<Character>;
  createWikiEntry(data: CreateWikiEntryRequest): Promise<WikiEntry>;
  createPlotLoop(data: CreatePlotLoopRequest): Promise<PlotLoop>;
  updateCharacter(id: string, data: UpdateCharacterRequest): Promise<void>;
  updateWikiEntry(id: string, data: UpdateWikiEntryRequest): Promise<void>;
  resolvePlotLoop(id: string): Promise<void>;
}

type AssetStore = AssetState & AssetActions;
```

#### ChatStore (`src/stores/chat-store.ts`)

```typescript
interface ChatState {
  messages: Message[];
  isStreaming: boolean;
  currentThoughts: ThoughtEvent[];
  activeSkills: string[];
  agentState: AgentState;
  sessionId: string | null;
  error: string | null;
}

interface ChatActions {
  sendMessage(content: string, options?: ChatOptions): Promise<void>;
  sendSceneRequest(request: SceneRequest): Promise<void>;
  appendContent(content: string): void;
  addThought(thought: ThoughtEvent): void;
  setAgentState(state: AgentState): void;
  toggleSkill(skillId: string): void;
  clearMessages(): void;
  abortStream(): void;
}

type ChatStore = ChatState & ChatActions;
```

### 3. Service Layer

#### AuthService (`src/services/auth-service.ts`)

```typescript
interface LoginRequest {
  email: string;
  password: string;
}

interface RegisterRequest {
  email: string;
  password: string;
  nickname: string;
}

interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: User;
}

class AuthService {
  constructor(apiClient: ApiClient, tokenManager: TokenManager);
  
  async login(request: LoginRequest): Promise<TokenResponse>;
  async register(request: RegisterRequest): Promise<TokenResponse>;
  async logout(refreshToken: string): Promise<void>;
  async refreshToken(refreshToken: string): Promise<TokenResponse>;
  async getCurrentUser(): Promise<User>;
}
```

#### ChatService (`src/services/chat-service.ts`)

```typescript
interface ChatRequest {
  message: string;
  projectId: string;
  sessionId?: string;
  currentPhase?: CreationPhase;
  skills?: string[];
}

interface SceneRequest extends ChatRequest {
  sceneType: string;
  chapterId?: string;
  characterIds?: string[];
  consistencyEnabled?: boolean;
}

class ChatService {
  constructor(sseClient: SSEClient, tokenManager: TokenManager);
  
  streamChat(request: ChatRequest, callbacks: SSEClientOptions): void;
  streamSceneCreation(request: SceneRequest, callbacks: SSEClientOptions): void;
  abort(): void;
}
```

#### ProjectExportService (`src/services/project-export-service.ts`)

```typescript
class ProjectExportService {
  constructor(apiClient: ApiClient);
  
  /**
   * 导出项目为 JSON 文件并触发下载
   */
  async exportProject(projectId: string): Promise<void>;
  
  /**
   * 导入项目从 JSON 文件
   */
  async importProject(file: File): Promise<Project>;
  
  /**
   * 验证导入文件格式
   */
  validateExportData(data: unknown): data is ExportData;
}
```

## Data Models (前后端对照)

本节详细列出前端需要的数据结构与后端提供的数据结构对比，标注需要调整的地方。

### 认证相关

#### LoginRequest
| 前端字段 | 后端字段 | 类型 | 说明 |
|---------|---------|------|------|
| identifier | identifier | string | ✅ 一致 |
| password | password | string | ✅ 一致 |
| deviceInfo | deviceInfo | string? | ✅ 一致 |

#### RegisterRequest
| 前端字段 | 后端字段 | 类型 | 说明 |
|---------|---------|------|------|
| username | username | string | ✅ 一致 |
| email | email | string | ✅ 一致 |
| password | password | string | ✅ 一致 |
| displayName | displayName | string? | ✅ 一致 |

#### TokenResponse
| 前端字段 | 后端字段 | 类型 | 说明 |
|---------|---------|------|------|
| accessToken | accessToken | string | ✅ 一致 |
| refreshToken | refreshToken | string | ✅ 一致 |
| tokenType | tokenType | string | ✅ 一致 |
| expiresIn | expiresIn | number | ✅ 一致（秒） |
| user | user | UserDto | ✅ 一致 |

#### UserDto
| 前端字段 | 后端字段 | 类型 | 说明 |
|---------|---------|------|------|
| id | id | string (UUID) | ✅ 一致 |
| username | username | string | ✅ 一致 |
| email | email | string | ✅ 一致 |
| displayName | displayName | string? | ✅ 一致 |
| avatarUrl | avatarUrl | string? | ✅ 一致 |
| status | status | UserStatus | ✅ 一致 |
| emailVerified | emailVerified | boolean | ✅ 一致 |
| createdAt | createdAt | string (ISO) | ✅ 一致 |
| lastLoginAt | lastLoginAt | string (ISO) | ✅ 一致 |

```typescript
// 前端类型定义
type UserStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED';

interface User {
  id: string;
  username: string;
  email: string;
  displayName?: string;
  avatarUrl?: string;
  status: UserStatus;
  emailVerified: boolean;
  createdAt: string;
  lastLoginAt?: string;
}

interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}

interface LoginRequest {
  identifier: string;
  password: string;
  deviceInfo?: string;
}

interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  displayName?: string;
}
```

### 项目相关

#### ProjectDto
| 前端字段 | 后端字段 | 类型 | 说明 |
|---------|---------|------|------|
| id | id | string (UUID) | ✅ 一致 |
| userId | userId | string (UUID) | ✅ 一致 |
| title | title | string | ✅ 一致 |
| description | description | string? | ✅ 一致 |
| coverUrl | coverUrl | string? | ✅ 一致 |
| status | status | ProjectStatus | ✅ 一致 |
| creationPhase | creationPhase | CreationPhase | ✅ 一致 |
| metadata | metadata | object | ✅ 一致 |
| worldSettings | worldSettings | object | ✅ 一致 |
| createdAt | createdAt | string (ISO) | ✅ 一致 |
| updatedAt | updatedAt | string (ISO) | ✅ 一致 |
| wordCount | ❌ 缺失 | number | ⚠️ 前端需要，后端未提供 |

**后端调整建议**: 在 ProjectDto 中添加 `wordCount` 字段，可从关联的 Chapter 聚合计算。

```typescript
// 前端类型定义
type CreationPhase = 'PLANNING' | 'WORLDBUILDING' | 'OUTLINING' | 'WRITING' | 'EDITING';
type ProjectStatus = 'ACTIVE' | 'ARCHIVED' | 'DELETED';

interface Project {
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
  wordCount?: number; // 需要后端补充
}

interface CreateProjectRequest {
  title: string;
  description?: string;
  coverUrl?: string;
  metadata?: Record<string, unknown>;
}
```

### 内容结构

#### VolumeDto
| 前端字段 | 后端字段 | 类型 | 说明 |
|---------|---------|------|------|
| id | id | string (UUID) | ✅ 一致 |
| projectId | projectId | string (UUID) | ✅ 一致 |
| title | title | string | ✅ 一致 |
| description | description | string? | ✅ 一致 |
| orderIndex | orderIndex | number | ✅ 一致 |
| chapterCount | chapterCount | number | ✅ 一致 |
| createdAt | createdAt | string (ISO) | ✅ 一致 |
| updatedAt | updatedAt | string (ISO) | ✅ 一致 |
| wordCount | ❌ 缺失 | number | ⚠️ 前端需要，后端未提供 |

**后端调整建议**: 在 VolumeDto 中添加 `wordCount` 字段。

#### ChapterDto
| 前端字段 | 后端字段 | 类型 | 说明 |
|---------|---------|------|------|
| id | id | string (UUID) | ✅ 一致 |
| projectId | projectId | string (UUID) | ✅ 一致 |
| volumeId | volumeId | string (UUID) | ✅ 一致 |
| title | title | string | ✅ 一致 |
| summary | summary | string? | ✅ 一致 |
| orderIndex | orderIndex | number | ✅ 一致 |
| status | status | ChapterStatus | ✅ 一致 |
| wordCount | wordCount | number | ✅ 一致 |
| metadata | metadata | object | ✅ 一致 |
| createdAt | createdAt | string (ISO) | ✅ 一致 |
| updatedAt | updatedAt | string (ISO) | ✅ 一致 |
| content | ❌ 缺失 | string? | ⚠️ 前端编辑器需要，后端未在列表中返回 |

**后端调整建议**: 提供单独的章节内容获取接口，或在详情接口中返回 content。

```typescript
// 前端类型定义
type ChapterStatus = 'DRAFT' | 'WRITING' | 'REVIEW' | 'COMPLETE';

interface Volume {
  id: string;
  projectId: string;
  title: string;
  description?: string;
  orderIndex: number;
  chapterCount: number;
  wordCount?: number; // 需要后端补充
  createdAt: string;
  updatedAt: string;
}

interface Chapter {
  id: string;
  projectId: string;
  volumeId: string;
  title: string;
  summary?: string;
  content?: string; // 详情接口返回
  orderIndex: number;
  status: ChapterStatus;
  wordCount: number;
  metadata?: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}
```

### 角色相关

#### CharacterDto
| 前端字段 | 后端字段 | 类型 | 说明 |
|---------|---------|------|------|
| id | id | string (UUID) | ✅ 一致 |
| projectId | projectId | string (UUID) | ✅ 一致 |
| name | name | string | ✅ 一致 |
| role | role | string | ✅ 一致 |
| description | description | string? | ✅ 一致 |
| personality | personality | object | ✅ 一致 |
| relationships | relationships | array | ✅ 一致 |
| status | status | string | ✅ 一致 |
| isActive | isActive | boolean | ✅ 一致 |
| archetype | archetype | string? | ✅ 一致 |
| createdAt | createdAt | string (ISO) | ✅ 一致 |
| updatedAt | updatedAt | string (ISO) | ✅ 一致 |
| traits | ❌ 缺失 | string[] | ⚠️ 前端 UI 使用，可从 personality 提取 |

**前端适配**: 从 `personality` 对象中提取 traits 数组。

#### RelationshipGraphDto
| 前端字段 | 后端字段 | 类型 | 说明 |
|---------|---------|------|------|
| nodes | nodes | GraphNode[] | ✅ 一致 |
| edges | edges | GraphEdge[] | ✅ 一致 |

```typescript
// 前端类型定义
interface Character {
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

interface CharacterRelationship {
  targetId: string;
  type: string;
  description?: string;
}

interface RelationshipGraph {
  nodes: GraphNode[];
  edges: GraphEdge[];
}

interface GraphNode {
  id: string;
  name: string;
  role: string;
  status: string;
  archetype?: string;
  isActive: boolean;
}

interface GraphEdge {
  source: string;
  target: string;
  type: string;
  description?: string;
  strength?: number;
  bidirectional: boolean;
}
```

### Wiki 相关

#### WikiEntryDto
| 前端字段 | 后端字段 | 类型 | 说明 |
|---------|---------|------|------|
| id | id | string (UUID) | ✅ 一致 |
| projectId | projectId | string (UUID) | ✅ 一致 |
| title | title | string | ✅ 一致 |
| type | type | string | ✅ 一致 |
| content | content | string | ✅ 一致 |
| aliases | aliases | string[] | ✅ 一致 |
| tags | tags | string[] | ✅ 一致 |
| timeVersion | timeVersion | string? | ✅ 一致 |
| createdAt | createdAt | string (ISO) | ✅ 一致 |
| updatedAt | updatedAt | string (ISO) | ✅ 一致 |

```typescript
// 前端类型定义 - 完全匹配后端
interface WikiEntry {
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
```

### PlotLoop 相关

#### PlotLoopDto
| 前端字段 | 后端字段 | 类型 | 说明 |
|---------|---------|------|------|
| id | id | string (UUID) | ✅ 一致 |
| projectId | projectId | string (UUID) | ✅ 一致 |
| title | title | string | ✅ 一致 |
| description | description | string | ✅ 一致 |
| status | status | PlotLoopStatus | ✅ 一致 |
| introChapterId | introChapterId | string? | ✅ 一致 |
| introChapterOrder | introChapterOrder | number? | ✅ 一致 |
| resolutionChapterId | resolutionChapterId | string? | ✅ 一致 |
| resolutionChapterOrder | resolutionChapterOrder | number? | ✅ 一致 |
| abandonReason | abandonReason | string? | ✅ 一致 |
| createdAt | createdAt | string (ISO) | ✅ 一致 |
| updatedAt | updatedAt | string (ISO) | ✅ 一致 |

```typescript
// 前端类型定义 - 完全匹配后端
type PlotLoopStatus = 'OPEN' | 'RESOLVED' | 'ABANDONED';

interface PlotLoop {
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
```

### Chat 相关

#### ChatRequestDto
| 前端字段 | 后端字段 | 类型 | 说明 |
|---------|---------|------|------|
| projectId | projectId | string (UUID) | ✅ 一致 |
| message | message | string | ✅ 一致 |
| phase | phase | string? | ✅ 一致 |
| sessionId | sessionId | string? | ✅ 一致 |
| chapterId | chapterId | string? | ✅ 一致 |
| characterIds | characterIds | string[]? | ✅ 一致 |
| sceneType | sceneType | string? | ✅ 一致 |
| targetWordCount | targetWordCount | number? | ✅ 一致 |
| consistency | consistency | boolean? | ✅ 一致 |
| ragEnabled | ragEnabled | boolean? | ✅ 一致 |

#### ChatResponseDto (非流式)
| 前端字段 | 后端字段 | 类型 | 说明 |
|---------|---------|------|------|
| content | content | string | ✅ 一致 |
| sessionId | sessionId | string | ✅ 一致 |
| phase | phase | string? | ✅ 一致 |

#### SSE Events (流式)
| 事件类型 | 数据格式 | 说明 |
|---------|---------|------|
| content | string | 内容片段 |
| thought | ThoughtEvent JSON | 思考过程 |
| tool | ToolEvent JSON | 工具调用 |
| done | "[DONE]" | 完成标记 |
| error | string | 错误信息 |

```typescript
// 前端类型定义
interface ChatRequest {
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
}

interface ChatResponse {
  content: string;
  sessionId: string;
  phase?: string;
}

// 前端本地消息类型
interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
  artifacts?: Artifact[];
}

interface ThoughtEvent {
  id: string;
  type: 'thinking' | 'rag' | 'skill' | 'tool';
  agent: string;
  message: string;
  confidence?: number;
}

interface Artifact {
  type: 'character' | 'wiki' | 'plotloop' | 'content';
  id: string;
  title: string;
  preview: string;
}
```

### 导入导出相关

后端已有完整的导入导出数据结构，前端需要匹配：

```typescript
// 导出数据结构（后端返回）
interface ExportData {
  metadata: ExportMetadata;
  project: ExportProjectDto;
}

interface ExportMetadata {
  version: string;        // "2.0"
  exportedAt: string;     // ISO datetime
  source: string;         // "InkFlow 2.0"
}

interface ExportProjectDto {
  title: string;
  description?: string;
  coverUrl?: string;
  status: string;
  creationPhase: string;
  metadata?: Record<string, unknown>;
  worldSettings?: Record<string, unknown>;
  volumes: ExportVolumeDto[];
}

interface ExportVolumeDto {
  title: string;
  description?: string;
  orderIndex: number;
  chapters: ExportChapterDto[];
}

interface ExportChapterDto {
  title: string;
  summary?: string;
  orderIndex: number;
  status: string;
  metadata?: Record<string, unknown>;
  blocks: ExportStoryBlockDto[];
}

interface ExportStoryBlockDto {
  blockType: string;
  content: string;
  rank: string;
  metadata?: Record<string, unknown>;
}
```

## 后端 API 调整计划

### 1. API 路径统一

将所有 Controller 路径统一添加 `/api` 前缀：

| 当前路径 | 调整后路径 | Controller |
|---------|-----------|------------|
| `/characters/*` | `/api/characters/*` | CharacterController |
| `/wiki/*` | `/api/wiki/*` | WikiEntryController |
| `/plotloops/*` | `/api/plotloops/*` | PlotLoopController |

### 2. DTO 字段补充

#### ProjectDto 添加 wordCount

```java
// ProjectDto.java
public record ProjectDto(
    UUID id,
    UUID userId,
    String title,
    String description,
    String coverUrl,
    ProjectStatus status,
    CreationPhase creationPhase,
    Map<String, Object> metadata,
    Map<String, Object> worldSettings,
    Long wordCount,  // 新增
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static ProjectDto fromEntity(Project project, Long wordCount) {
        return new ProjectDto(
            project.getId(),
            project.getUserId(),
            project.getTitle(),
            project.getDescription(),
            project.getCoverUrl(),
            project.getStatus(),
            project.getCreationPhase(),
            project.getMetadata(),
            project.getWorldSettings(),
            wordCount,
            project.getCreatedAt(),
            project.getUpdatedAt()
        );
    }
}
```

#### VolumeDto 添加 wordCount

```java
// VolumeDto.java
public record VolumeDto(
    UUID id,
    UUID projectId,
    String title,
    String description,
    int orderIndex,
    int chapterCount,
    Long wordCount,  // 新增
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
```

### 3. 章节内容接口

添加获取章节内容的专用接口：

```java
// ChapterController.java
@GetMapping("/{chapterId}/content")
@Operation(summary = "获取章节内容", description = "获取章节的完整内容用于编辑")
public ResponseEntity<ChapterContentDto> getChapterContent(
        @PathVariable UUID projectId,
        @PathVariable UUID chapterId,
        @AuthenticationPrincipal UserPrincipal user) {
    ChapterContentDto content = chapterService.getContent(projectId, chapterId, user.getId());
    return ResponseEntity.ok(content);
}

@PutMapping("/{chapterId}/content")
@Operation(summary = "保存章节内容", description = "保存章节内容")
public ResponseEntity<ChapterContentDto> saveChapterContent(
        @PathVariable UUID projectId,
        @PathVariable UUID chapterId,
        @Valid @RequestBody SaveContentRequest request,
        @AuthenticationPrincipal UserPrincipal user) {
    ChapterContentDto content = chapterService.saveContent(projectId, chapterId, user.getId(), request);
    return ResponseEntity.ok(content);
}

// ChapterContentDto.java
public record ChapterContentDto(
    UUID id,
    UUID chapterId,
    String content,
    int wordCount,
    LocalDateTime updatedAt
) {}

// SaveContentRequest.java
public record SaveContentRequest(
    @NotNull String content
) {}
```

### 4. StoryBlock 内容管理

章节内容通过 StoryBlock 管理，需要确保前端可以：
1. 获取章节下所有 StoryBlock
2. 创建/更新/删除 StoryBlock
3. 重排序 StoryBlock

现有 StoryBlockController 已提供这些接口，路径为 `/api/projects/{projectId}/chapters/{chapterId}/blocks`。

### 5. 项目导入导出接口

后端已有 ExportService 和 ImportService，需要添加 Controller 接口：

```java
// ProjectController.java 添加导出接口
@GetMapping("/{id}/export")
@Operation(summary = "导出项目", description = "导出项目为 JSON 文件")
public ResponseEntity<Resource> exportProject(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable UUID id) {
    String json = exportService.exportToJson(id, user.getId());
    
    ByteArrayResource resource = new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8));
    
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"project-export.json\"")
        .contentType(MediaType.APPLICATION_JSON)
        .contentLength(resource.contentLength())
        .body(resource);
}

// ProjectController.java 添加导入接口
@PostMapping("/import")
@Operation(summary = "导入项目", description = "从 JSON 文件导入项目")
public ResponseEntity<ProjectDto> importProject(
        @AuthenticationPrincipal UserPrincipal user,
        @RequestBody String json) {
    UUID projectId = importService.importFromJson(json, user.getId());
    ProjectDto project = projectService.getProject(projectId, user.getId());
    return ResponseEntity.status(HttpStatus.CREATED).body(project);
}
```

## 后端调整优先级

| 优先级 | 调整项 | 影响范围 |
|--------|--------|----------|
| P0 | API 路径统一 | 所有前端调用 |
| P0 | 章节内容接口 | 编辑器核心功能 |
| P1 | ProjectDto wordCount | 项目列表显示 |
| P1 | VolumeDto wordCount | 分卷列表显示 |
| P1 | 导入导出接口 | 项目备份和迁移 |

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: JWT Token Attachment
*For any* API request made through ApiClient when a valid access token exists in TokenManager, the request SHALL include the token in the Authorization header with "Bearer" prefix.
**Validates: Requirements 1.2**

### Property 2: Error Response Transformation
*For any* API error response with status >= 400, the ApiClient SHALL transform it into a standardized ApiError object containing code and message fields.
**Validates: Requirements 1.5**

### Property 3: SSE Content Accumulation
*For any* sequence of SSE content events received during a chat stream, the ChatStore SHALL accumulate all content in order, resulting in the complete message when done event is received.
**Validates: Requirements 7.2**

### Property 4: Store Cache Timestamp
*For any* successful API data fetch, the corresponding store SHALL record a timestamp, and subsequent fetches within the cache validity period SHALL return cached data without API call.
**Validates: Requirements 9.2**

## Error Handling

### API Error Handling

```typescript
// Error types
enum ErrorCode {
  NETWORK_ERROR = 'NETWORK_ERROR',
  UNAUTHORIZED = 'UNAUTHORIZED',
  FORBIDDEN = 'FORBIDDEN',
  NOT_FOUND = 'NOT_FOUND',
  VALIDATION_ERROR = 'VALIDATION_ERROR',
  SERVER_ERROR = 'SERVER_ERROR',
  TIMEOUT = 'TIMEOUT',
}

// Error handler
class ErrorHandler {
  static handle(error: unknown): ApiError {
    if (error instanceof Response) {
      return this.handleHttpError(error);
    }
    if (error instanceof TypeError) {
      return { code: ErrorCode.NETWORK_ERROR, message: '网络连接失败' };
    }
    return { code: ErrorCode.SERVER_ERROR, message: '未知错误' };
  }
  
  private static handleHttpError(response: Response): ApiError {
    switch (response.status) {
      case 401: return { code: ErrorCode.UNAUTHORIZED, message: '请重新登录' };
      case 403: return { code: ErrorCode.FORBIDDEN, message: '无权限访问' };
      case 404: return { code: ErrorCode.NOT_FOUND, message: '资源不存在' };
      case 422: return { code: ErrorCode.VALIDATION_ERROR, message: '数据验证失败' };
      default: return { code: ErrorCode.SERVER_ERROR, message: '服务器错误' };
    }
  }
}
```

### Offline Support

```typescript
interface OfflineManager {
  isOnline: boolean;
  pendingMutations: QueuedMutation[];
  
  queueMutation(mutation: QueuedMutation): void;
  replayMutations(): Promise<void>;
  onOnline(callback: () => void): void;
  onOffline(callback: () => void): void;
}

interface QueuedMutation {
  id: string;
  type: 'CREATE' | 'UPDATE' | 'DELETE';
  resource: string;
  payload: unknown;
  timestamp: number;
}
```

## Testing Strategy

### Unit Testing

使用 Vitest 进行单元测试：

- **ApiClient**: 测试请求构建、响应解析、错误处理
- **TokenManager**: 测试 token 存储、过期检测、刷新逻辑
- **Stores**: 测试状态更新、action 执行、缓存行为
- **Services**: 测试 API 调用封装、数据转换

### Property-Based Testing

使用 fast-check 进行属性测试：

- **Property 1**: 生成随机 API 请求，验证 Authorization header
- **Property 2**: 生成随机错误响应，验证转换结果
- **Property 3**: 生成随机 SSE 事件序列，验证内容累积
- **Property 4**: 生成随机 fetch 操作序列，验证缓存行为

### Integration Testing

使用 MSW (Mock Service Worker) 进行集成测试：

- 模拟后端 API 响应
- 测试完整的用户流程
- 测试 SSE 流式响应处理
- 测试离线/在线切换

### Test Configuration

```typescript
// vitest.config.ts
export default defineConfig({
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
    },
  },
});
```

```typescript
// Property test example with fast-check
import * as fc from 'fast-check';

describe('ApiClient', () => {
  it('should attach JWT token to all requests', () => {
    fc.assert(
      fc.property(
        fc.record({
          method: fc.constantFrom('GET', 'POST', 'PUT', 'DELETE'),
          path: fc.string().filter(s => s.startsWith('/')),
          token: fc.string().filter(s => s.length > 0),
        }),
        ({ method, path, token }) => {
          tokenManager.setTokens({ accessToken: token, refreshToken: '', expiresAt: Date.now() + 3600000 });
          const request = apiClient.buildRequest(method, path);
          expect(request.headers.get('Authorization')).toBe(`Bearer ${token}`);
        }
      ),
      { numRuns: 100 }
    );
  });
});
```
