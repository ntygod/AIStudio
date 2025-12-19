# Design Document: InkFlow UI 完整布局方案

## Overview

本设计文档定义 InkFlow 2.0 前端 UI 的完整布局架构，基于 React + TypeScript + Tailwind CSS + shadcn/ui 技术栈。设计目标是提供一个功能完整、响应式、支持离线的小说创作界面。

### 设计原则

1. **渐进式复杂度**: 新用户看到简洁界面，高级功能按需展示
2. **上下文感知**: UI 根据创作阶段自动调整
3. **非侵入式 AI**: AI 辅助功能不打断创作流程
4. **数据优先**: 所有状态变更优先持久化，支持离线

## Architecture

### 整体布局结构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           Header Bar (可选)                              │
├──────────────┬────────────────────────────────┬─────────────────────────┤
│              │                                │                         │
│   Left       │        Center Editor           │      Right Panel        │
│   Sidebar    │                                │                         │
│   (280px)    │        (flexible)              │       (400px)           │
│              │                                │                         │
│ ┌──────────┐ │  ┌────────────────────────┐   │  ┌───────────────────┐  │
│ │ Phase    │ │  │ Breadcrumb + Toolbar   │   │  │ Agent Status      │  │
│ │ Switcher │ │  ├────────────────────────┤   │  ├───────────────────┤  │
│ ├──────────┤ │  │                        │   │  │ Warning Indicator │  │
│ │ Project  │ │  │                        │   │  ├───────────────────┤  │
│ │ Tree     │ │  │    TipTap Editor       │   │  │ Thought Chain     │  │
│ │          │ │  │                        │   │  ├───────────────────┤  │
│ ├──────────┤ │  │                        │   │  │ Artifact Cards    │  │
│ │ Asset    │ │  │                        │   │  ├───────────────────┤  │
│ │ Drawer   │ │  │                        │   │  │ Skill Selector    │  │
│ │          │ │  ├────────────────────────┤   │  ├───────────────────┤  │
│ ├──────────┤ │  │ Word Count | Save      │   │  │ Chat Interface    │  │
│ │ Progress │ │  └────────────────────────┘   │  │                   │  │
│ │ Stats    │ │                                │  │                   │  │
│ └──────────┘ │                                │  └───────────────────┘  │
├──────────────┴────────────────────────────────┴─────────────────────────┤
│                        Status Bar (离线/同步状态)                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 响应式断点

| 断点 | 宽度 | 布局 |
|------|------|------|
| Mobile | < 768px | 单栏，底部导航切换 |
| Tablet | 768px - 1024px | 双栏，左侧边栏 + 编辑器 |
| Desktop | > 1024px | 三栏完整布局 |

### 状态管理架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        Zustand Stores                           │
├─────────────┬─────────────┬─────────────┬─────────────┬────────┤
│ AuthStore   │ ProjectStore│ ContentStore│ ChatStore   │AssetStore│
├─────────────┼─────────────┼─────────────┼─────────────┼────────┤
│ConsistencyStore│EvolutionStore│ SettingsStore│OfflineStore│UIStore│
└─────────────┴─────────────┴─────────────┴─────────────┴────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Service Layer                              │
├─────────────┬─────────────┬─────────────┬─────────────┬────────┤
│ AuthService │ProjectService│ContentService│ChatService │AssetService│
├─────────────┼─────────────┼─────────────┼─────────────┼────────┤
│ConsistencyService│EvolutionService│StyleService│OfflineManager│SSEClient│
└─────────────┴─────────────┴─────────────┴─────────────┴────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      API Client Layer                           │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐ │
│  │ HTTP Client │  │ SSE Client  │  │ IndexedDB (Offline)     │ │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

## Components and Interfaces

### 1. MainLayout 组件

```typescript
interface MainLayoutProps {
  leftSidebar: ReactNode;
  editor: ReactNode;
  rightSidebar: ReactNode;
  zenMode?: boolean;
  mobileView?: 'left' | 'editor' | 'right';
}

interface LayoutState {
  leftCollapsed: boolean;
  rightCollapsed: boolean;
  zenMode: boolean;
  mobileActivePanel: 'left' | 'editor' | 'right';
}
```

### 2. LeftSidebar 组件结构

```typescript
// PhaseSwitcher
interface PhaseSwitcherProps {
  currentPhase: CreationPhase;
  onPhaseChange: (phase: CreationPhase) => void;
}

// ProjectTree
interface ProjectTreeProps {
  volumes: Volume[];
  onChapterSelect: (volumeId: string, chapterId: string) => void;
  selectedChapterId?: string;
  integrated?: boolean;
}

// AssetDrawer
interface AssetDrawerProps {
  characters: Character[];
  wikiEntries: WikiEntry[];
  plotLoops: PlotLoop[];
  onAssetClick: (type: AssetType, id: string) => void;
  onViewRelationGraph: () => void;
  onViewEvolutionTimeline: (entityId: string) => void;
}

// ProgressStats
interface ProgressStatsProps {
  totalWordCount: number;
  todayWordCount: number;
  dailyGoal: number;
  weeklyActivity: DailyWordCount[];
  volumeCompletion: number;
}
```

### 3. Editor 组件结构

```typescript
// TipTapEditor
interface TipTapEditorProps {
  content: string;
  onChange: (content: string) => void;
  onZenToggle: () => void;
  zenMode: boolean;
  breadcrumb: string;
  onTextSelect: (text: string, position: SelectionPosition) => void;
}

// FloatingToolbar
interface FloatingToolbarProps {
  selectedText: string;
  position: { x: number; y: number };
  onEnhance: (type: EnhanceType) => void;
  onClose: () => void;
}

// VersionHistory
interface VersionHistoryProps {
  snapshots: ChapterSnapshot[];
  currentContent: string;
  onRestore: (snapshotId: string) => void;
  onCompare: (snapshotId: string) => void;
}
```

### 4. RightPanel 组件结构

```typescript
// AgentStatus
interface AgentStatusProps {
  agent: AgentType;
  state: AgentState;
}

// ConsistencyWarningIndicator
interface ConsistencyWarningIndicatorProps {
  warningCount: number;
  onClick: () => void;
}

// ConsistencyWarningPanel
interface ConsistencyWarningPanelProps {
  warnings: ConsistencyWarning[];
  onResolve: (warningId: string) => void;
  onDismiss: (warningId: string) => void;
}

// ThoughtChain
interface ThoughtChainProps {
  events: ThoughtEvent[];
  isThinking: boolean;
}

// SkillSelector
interface SkillSelectorProps {
  skills: Skill[];
  onToggle: (skillId: string) => void;
}

// ChatInterface
interface ChatInterfaceProps {
  messages: Message[];
  onSend: (message: string) => void;
  isLoading: boolean;
}

// ArtifactCard
interface ArtifactCardProps {
  type: ArtifactType;
  title: string;
  description: string;
  content: string;
  tags?: string[];
  onApply: () => void;
  onDismiss: () => void;
}

// TokenUsageIndicator
interface TokenUsageIndicatorProps {
  todayUsage: number;
  dailyQuota: number;
  onClick: () => void;
}
```

### 5. 模态框/对话框组件

```typescript
// EvolutionTimeline
interface EvolutionTimelineProps {
  entityId: string;
  entityType: 'character' | 'wiki';
  snapshots: StateSnapshot[];
  onCreateSnapshot: (description: string) => void;
  onCompare: (snapshotId: string) => void;
}

// StateCompareDialog
interface StateCompareDialogProps {
  beforeState: EntityState;
  afterState: EntityState;
  onClose: () => void;
}

// RelationshipGraph
interface RelationshipGraphProps {
  characters: Character[];
  relationships: CharacterRelationship[];
  onNodeClick: (characterId: string) => void;
  onEdgeClick: (relationshipId: string) => void;
}

// ImportExportDialog
interface ImportExportDialogProps {
  mode: 'import' | 'export';
  onExport: () => Promise<Blob>;
  onImport: (file: File) => Promise<ImportPreview>;
  onConfirmImport: () => void;
}
```

## Data Models

### UI State Models

```typescript
// 布局状态
interface UIState {
  leftSidebarCollapsed: boolean;
  rightPanelCollapsed: boolean;
  zenMode: boolean;
  mobileActivePanel: 'left' | 'editor' | 'right';
  theme: ThemeMode;
  activeModal: ModalType | null;
}

// 编辑器状态
interface EditorState {
  content: string;
  selectedText: string | null;
  selectionPosition: SelectionPosition | null;
  isDirty: boolean;
  lastSavedAt: Date | null;
  wordCount: number;
  characterCount: number;
}

// 一致性警告
interface ConsistencyWarning {
  id: string;
  projectId: string;
  severity: 'low' | 'medium' | 'high';
  type: string;
  description: string;
  affectedEntities: AffectedEntity[];
  suggestedResolution: string;
  status: 'active' | 'resolved' | 'dismissed';
  createdAt: Date;
}

// 演进快照
interface StateSnapshot {
  id: string;
  entityId: string;
  entityType: 'character' | 'wiki';
  state: Record<string, any>;
  description: string;
  createdAt: Date;
  chapterId?: string;
}

// 创作进度
interface CreationProgress {
  totalWordCount: number;
  todayWordCount: number;
  dailyGoal: number;
  weeklyActivity: DailyWordCount[];
  volumeCompletion: number;
  streakDays: number;
}

// Token 使用
interface TokenUsage {
  todayUsage: number;
  dailyQuota: number;
  weeklyTrend: DailyUsage[];
  breakdownByOperation: OperationUsage[];
}

// 离线状态
interface OfflineState {
  isOnline: boolean;
  pendingChanges: PendingChange[];
  syncStatus: 'synced' | 'syncing' | 'pending' | 'conflict';
  lastSyncAt: Date | null;
}
```

### API Response Models

```typescript
// 一致性警告响应
interface ConsistencyWarningResponse {
  warnings: ConsistencyWarning[];
  totalCount: number;
  unresolvedCount: number;
}

// 演进时间线响应
interface EvolutionTimelineResponse {
  entityId: string;
  entityType: string;
  snapshots: StateSnapshot[];
  changeRecords: ChangeRecord[];
}

// 进度统计响应
interface ProgressStatsResponse {
  totalWordCount: number;
  todayWordCount: number;
  weeklyActivity: DailyWordCount[];
  volumeStats: VolumeStats[];
}

// Token 使用响应
interface TokenUsageResponse {
  todayUsage: number;
  dailyQuota: number;
  weeklyTrend: DailyUsage[];
  breakdown: OperationUsage[];
}
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Sidebar collapse preserves content state
*For any* sidebar (left or right) with any content state, collapsing and expanding the sidebar should preserve the exact content state including scroll position and selection.
**Validates: Requirements 1.2, 1.3**

### Property 2: LocalStorage persistence round-trip
*For any* UI state (sidebar collapse, theme, zen mode), saving to localStorage and reloading should restore the exact same state.
**Validates: Requirements 1.5**

### Property 3: Phase transition updates state correctly
*For any* valid phase transition, the phase state should update and the backend should receive the correct phase value.
**Validates: Requirements 2.2**

### Property 4: Project tree renders all volumes and chapters
*For any* project data with volumes and chapters, the project tree should render all items with correct word counts and hierarchy.
**Validates: Requirements 2.3**

### Property 5: Chapter selection loads correct content
*For any* chapter selection, the editor content should match the selected chapter's content exactly.
**Validates: Requirements 2.5**

### Property 6: Asset click displays correct details
*For any* asset type (character, wiki, plot) and item, clicking should display the correct details for that specific item.
**Validates: Requirements 2.7**

### Property 7: Progress statistics calculation accuracy
*For any* project data, the progress statistics (total word count, today's count, volume completion) should be calculated correctly.
**Validates: Requirements 2.8, 7.2, 7.5**

### Property 8: Breadcrumb shows correct path
*For any* selected chapter, the breadcrumb should display the correct volume > chapter path.
**Validates: Requirements 3.1**

### Property 9: Text selection triggers floating toolbar
*For any* text selection in the editor, the floating toolbar should appear at the correct position with appropriate options.
**Validates: Requirements 3.3**

### Property 10: Word count accuracy
*For any* editor content, the displayed word count and character count should match the actual content.
**Validates: Requirements 3.7**

### Property 11: Agent state indicator correctness
*For any* agent state (online, thinking, error), the indicator should display the correct visual representation.
**Validates: Requirements 4.1**

### Property 12: Skill toggle updates state
*For any* skill toggle action, the skill state should update correctly and be reflected in subsequent AI requests.
**Validates: Requirements 4.4**

### Property 13: Artifact apply inserts at cursor
*For any* artifact content and cursor position, applying the artifact should insert content at the exact cursor position.
**Validates: Requirements 4.8**

### Property 14: Warning indicator count accuracy
*For any* set of warnings, the indicator should display the correct count of unresolved warnings and highlight when count > 0.
**Validates: Requirements 5.1, 5.2**

### Property 15: Warning resolve updates count
*For any* warning resolve action, the warning should be marked resolved and the count should decrease by 1.
**Validates: Requirements 5.5**

### Property 16: Warning dismiss hides without resolving
*For any* warning dismiss action, the warning should be hidden from the list but remain unresolved in the backend.
**Validates: Requirements 5.6**

### Property 17: Evolution timeline renders all snapshots
*For any* entity with evolution data, the timeline should render all snapshots in chronological order with correct data.
**Validates: Requirements 6.2, 6.3**

### Property 18: State comparison shows correct diff
*For any* two snapshots, the comparison dialog should show the correct before/after values for all changed attributes.
**Validates: Requirements 6.4**

### Property 19: Snapshot creation captures current state
*For any* snapshot creation, the captured state should exactly match the entity's current state at creation time.
**Validates: Requirements 6.6**

### Property 20: Version history diff accuracy
*For any* snapshot and current content, the diff view should accurately show all differences.
**Validates: Requirements 8.3**

### Property 21: Snapshot restore replaces content exactly
*For any* snapshot restore action, the editor content should exactly match the snapshot content.
**Validates: Requirements 8.4**

### Property 22: Export/Import round-trip consistency
*For any* project data, exporting to JSON and importing should produce a project with identical data.
**Validates: Requirements 12.2, 12.5**

### Property 23: Relationship graph renders all nodes and edges
*For any* character and relationship data, the graph should render all characters as nodes and all relationships as edges with correct labels.
**Validates: Requirements 13.2**

### Property 24: Responsive layout breakpoint transitions
*For any* viewport width, the layout should match the expected column configuration (1/2/3 columns based on breakpoints).
**Validates: Requirements 14.1, 14.2**

### Property 25: Offline cache preserves changes
*For any* offline edit, the changes should be cached in IndexedDB and retrievable after reconnection.
**Validates: Requirements 15.2, 15.3**

### Property 26: Sync status indicator accuracy
*For any* sync state (synced, syncing, pending, conflict), the indicator should display the correct visual state.
**Validates: Requirements 15.5**

### Property 27: Token usage display accuracy
*For any* usage data, the displayed usage and quota should match the actual values from the backend.
**Validates: Requirements 10.2**

### Property 28: Usage warning threshold trigger
*For any* usage exceeding 80% of quota, a warning notification should be displayed.
**Validates: Requirements 10.5**

## Error Handling

### 网络错误处理

```typescript
// 统一错误处理
interface ErrorHandler {
  handleNetworkError: (error: NetworkError) => void;
  handleAuthError: (error: AuthError) => void;
  handleValidationError: (error: ValidationError) => void;
  handleServerError: (error: ServerError) => void;
}

// 错误恢复策略
const errorRecoveryStrategies = {
  network: {
    retry: { maxAttempts: 3, backoff: 'exponential' },
    fallback: 'offline-mode',
    notification: 'toast'
  },
  auth: {
    action: 'redirect-to-login',
    clearState: true
  },
  validation: {
    action: 'show-inline-error',
    highlight: true
  },
  server: {
    retry: { maxAttempts: 1 },
    notification: 'toast',
    fallback: 'cached-data'
  }
};
```

### 离线错误处理

```typescript
// 离线队列管理
interface OfflineQueue {
  enqueue: (operation: PendingOperation) => void;
  dequeue: () => PendingOperation | null;
  peek: () => PendingOperation | null;
  clear: () => void;
  getAll: () => PendingOperation[];
}

// 冲突解决
interface ConflictResolver {
  detectConflict: (local: Data, remote: Data) => boolean;
  resolveConflict: (local: Data, remote: Data, strategy: ConflictStrategy) => Data;
  showConflictDialog: (conflicts: Conflict[]) => Promise<Resolution[]>;
}
```

## Testing Strategy

### 测试框架

- **单元测试**: Vitest + React Testing Library
- **属性测试**: fast-check
- **E2E 测试**: Playwright (可选)

### 单元测试覆盖

1. **组件渲染测试**: 验证组件正确渲染
2. **交互测试**: 验证用户交互行为
3. **状态管理测试**: 验证 store 状态变更
4. **服务层测试**: 验证 API 调用和数据转换

### 属性测试策略

使用 fast-check 进行属性测试，每个属性测试运行 100 次迭代。

```typescript
// 示例：侧边栏折叠状态保持
import * as fc from 'fast-check';

/**
 * Feature: ui-layout-redesign, Property 1: Sidebar collapse preserves content state
 * Validates: Requirements 1.2, 1.3
 */
test('sidebar collapse preserves content state', () => {
  fc.assert(
    fc.property(
      fc.record({
        scrollPosition: fc.integer({ min: 0, max: 10000 }),
        selectedItem: fc.option(fc.string()),
        expandedSections: fc.array(fc.string())
      }),
      (contentState) => {
        // Setup sidebar with content state
        const { result } = renderHook(() => useSidebarState(contentState));
        
        // Collapse
        act(() => result.current.collapse());
        expect(result.current.isCollapsed).toBe(true);
        
        // Expand
        act(() => result.current.expand());
        expect(result.current.isCollapsed).toBe(false);
        
        // Verify state preserved
        expect(result.current.contentState).toEqual(contentState);
      }
    ),
    { numRuns: 100 }
  );
});

/**
 * Feature: ui-layout-redesign, Property 22: Export/Import round-trip consistency
 * Validates: Requirements 12.2, 12.5
 */
test('export/import round-trip preserves project data', () => {
  fc.assert(
    fc.property(
      projectDataArbitrary,
      async (projectData) => {
        // Export
        const exported = await exportService.exportProject(projectData);
        
        // Import
        const imported = await importService.importProject(exported);
        
        // Verify round-trip
        expect(imported.title).toBe(projectData.title);
        expect(imported.volumes.length).toBe(projectData.volumes.length);
        expect(imported.characters.length).toBe(projectData.characters.length);
      }
    ),
    { numRuns: 100 }
  );
});
```

### 测试数据生成器

```typescript
// 项目数据生成器
const projectDataArbitrary = fc.record({
  id: fc.uuid(),
  title: fc.string({ minLength: 1, maxLength: 100 }),
  volumes: fc.array(volumeArbitrary, { minLength: 0, maxLength: 10 }),
  characters: fc.array(characterArbitrary, { minLength: 0, maxLength: 50 }),
  wikiEntries: fc.array(wikiEntryArbitrary, { minLength: 0, maxLength: 100 }),
  plotLoops: fc.array(plotLoopArbitrary, { minLength: 0, maxLength: 30 })
});

// 一致性警告生成器
const consistencyWarningArbitrary = fc.record({
  id: fc.uuid(),
  severity: fc.constantFrom('low', 'medium', 'high'),
  type: fc.string(),
  description: fc.string({ minLength: 1 }),
  status: fc.constantFrom('active', 'resolved', 'dismissed')
});

// 演进快照生成器
const stateSnapshotArbitrary = fc.record({
  id: fc.uuid(),
  entityId: fc.uuid(),
  entityType: fc.constantFrom('character', 'wiki'),
  state: fc.dictionary(fc.string(), fc.jsonValue()),
  description: fc.string(),
  createdAt: fc.date()
});
```
