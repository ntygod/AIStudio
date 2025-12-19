# Requirements Document

## Introduction

本文档定义 InkFlow 2.0 完整 UI 布局方案的需求规格。基于当前已实现的三栏布局（左侧边栏、中央编辑器、右侧 AI 面板），需要补充缺失的功能组件，优化布局结构，并确保所有后端功能在前端有对应的交互入口。

当前 UI 截图分析：
- 左侧：项目树（卷/章节）+ 资产抽屉（人物、世界观、伏笔）
- 中央：TipTap 富文本编辑器
- 右侧：AI Copilot 面板（Agent 状态、技能选择器、聊天界面）

缺失的关键功能：
1. 一致性警告系统的 UI 展示
2. 角色演进时间线
3. 创作进度统计
4. 章节快照/版本历史
5. 写作风格管理
6. Token 使用量监控
7. AI Provider 配置
8. 项目导入/导出
9. 关系图谱可视化

## Glossary

- **InkFlow_System**: InkFlow 2.0 AI 辅助小说创作平台的前端系统
- **MainLayout**: 主布局组件，包含左侧边栏、中央编辑器、右侧面板的三栏结构
- **LeftSidebar**: 左侧边栏，包含项目导航、资产管理等功能
- **RightPanel**: 右侧面板，包含 AI Copilot、一致性检查、演进追踪等功能
- **Editor**: 中央编辑区域，包含富文本编辑器和相关工具栏
- **ConsistencyWarning**: 一致性警告，系统检测到的设定冲突或逻辑矛盾
- **EvolutionTimeline**: 演进时间线，追踪角色/设定随时间的变化
- **CreationPhase**: 创作阶段，包括 WELCOME、INITIALIZATION、PLOTTING、DRAFTING、MAINTENANCE
- **SkillSlot**: 技能槽，AI 写作增强技能的配置单元
- **ArtifactCard**: 产物卡片，AI 生成的结构化内容展示组件

## Requirements

### Requirement 1: 主布局结构优化

**User Story:** As a 小说作者, I want 一个清晰的三栏布局界面, so that 我可以同时查看项目结构、编辑内容和使用 AI 助手。

#### Acceptance Criteria

1. THE InkFlow_System SHALL display a three-column layout with left sidebar (280px default), center editor (flexible), and right panel (400px default)
2. WHEN a user clicks the collapse button on the left sidebar THEN the InkFlow_System SHALL animate the sidebar to 0px width and preserve content state
3. WHEN a user clicks the collapse button on the right panel THEN the InkFlow_System SHALL animate the panel to 0px width and preserve AI conversation state
4. WHEN zen mode is activated THEN the InkFlow_System SHALL hide both sidebars and center the editor with maximum 4xl width
5. THE InkFlow_System SHALL persist sidebar collapse states in localStorage across sessions

### Requirement 2: 左侧边栏功能完善

**User Story:** As a 小说作者, I want 在左侧边栏管理我的项目结构和创作资产, so that 我可以快速导航和组织我的小说内容。

#### Acceptance Criteria

1. THE InkFlow_System SHALL display a phase switcher at the top of the left sidebar showing current creation phase with visual indicator
2. WHEN a user selects a different creation phase THEN the InkFlow_System SHALL update the phase state and notify the backend
3. THE InkFlow_System SHALL display a project tree showing volumes and chapters with word count statistics
4. WHEN a user expands a volume THEN the InkFlow_System SHALL fetch and display chapters for that volume
5. WHEN a user selects a chapter THEN the InkFlow_System SHALL load the chapter content into the editor
6. THE InkFlow_System SHALL display an asset drawer with collapsible sections for characters, wiki entries, and plot loops
7. WHEN a user clicks on an asset item THEN the InkFlow_System SHALL display the asset details in a modal or side panel
8. THE InkFlow_System SHALL display a creation progress indicator showing word count, chapter completion, and daily writing goals

### Requirement 3: 中央编辑器增强

**User Story:** As a 小说作者, I want 一个功能丰富的编辑器, so that 我可以高效地撰写和编辑我的小说内容。

#### Acceptance Criteria

1. THE InkFlow_System SHALL display a breadcrumb navigation showing current volume and chapter path
2. THE InkFlow_System SHALL provide a TipTap-based rich text editor with formatting toolbar
3. WHEN a user selects text in the editor THEN the InkFlow_System SHALL display a floating toolbar with AI enhancement options
4. WHEN a user triggers AI enhancement on selected text THEN the InkFlow_System SHALL send the text to the AI service and display suggestions
5. THE InkFlow_System SHALL auto-save editor content every 30 seconds and display save status indicator
6. WHEN a user presses Ctrl+S THEN the InkFlow_System SHALL immediately save the current content
7. THE InkFlow_System SHALL display word count and character count in the editor footer
8. WHEN zen mode is toggled THEN the InkFlow_System SHALL hide all UI elements except the editor content area

### Requirement 4: 右侧 AI Copilot 面板

**User Story:** As a 小说作者, I want 一个智能的 AI 助手面板, so that 我可以获得创作建议和自动化辅助。

#### Acceptance Criteria

1. THE InkFlow_System SHALL display an agent status indicator showing current AI agent type and state (online/thinking/error)
2. WHEN the AI is processing a request THEN the InkFlow_System SHALL display a thought chain showing reasoning steps
3. THE InkFlow_System SHALL display a skill selector with toggleable skill slots for writing enhancement
4. WHEN a user toggles a skill THEN the InkFlow_System SHALL update the skill state and include it in subsequent AI requests
5. THE InkFlow_System SHALL display a chat interface for conversational interaction with the AI
6. WHEN a user sends a message THEN the InkFlow_System SHALL stream the AI response in real-time using SSE
7. WHEN the AI generates structured content THEN the InkFlow_System SHALL display it as an artifact card with apply/dismiss actions
8. WHEN a user clicks apply on an artifact card THEN the InkFlow_System SHALL insert the content into the editor at cursor position

### Requirement 5: 一致性警告系统 UI

**User Story:** As a 小说作者, I want 看到系统检测到的设定冲突和逻辑矛盾, so that 我可以及时修正错误保持故事一致性。

#### Acceptance Criteria

1. THE InkFlow_System SHALL display a consistency warning indicator in the right panel header showing unresolved warning count
2. WHEN warnings exist THEN the InkFlow_System SHALL highlight the indicator with a warning color
3. WHEN a user clicks the warning indicator THEN the InkFlow_System SHALL expand a warning panel showing all active warnings
4. THE InkFlow_System SHALL display each warning with severity level, description, affected entities, and suggested resolution
5. WHEN a user clicks resolve on a warning THEN the InkFlow_System SHALL mark the warning as resolved and update the count
6. WHEN a user clicks dismiss on a warning THEN the InkFlow_System SHALL hide the warning without resolving the underlying issue
7. THE InkFlow_System SHALL poll for new warnings every 60 seconds when the project is active

### Requirement 6: 角色演进时间线

**User Story:** As a 小说作者, I want 追踪角色和设定随时间的变化, so that 我可以管理复杂的角色发展弧线。

#### Acceptance Criteria

1. THE InkFlow_System SHALL provide an evolution timeline view accessible from the asset drawer
2. WHEN a user selects an entity for evolution tracking THEN the InkFlow_System SHALL display a vertical timeline of state changes
3. THE InkFlow_System SHALL display each timeline node with timestamp, change description, and affected attributes
4. WHEN a user clicks on a timeline node THEN the InkFlow_System SHALL display a state comparison dialog showing before/after values
5. THE InkFlow_System SHALL allow users to manually create evolution snapshots with custom descriptions
6. WHEN a user creates a snapshot THEN the InkFlow_System SHALL capture the current entity state and add it to the timeline

### Requirement 7: 创作进度统计面板

**User Story:** As a 小说作者, I want 查看我的创作进度统计, so that 我可以了解写作效率并保持动力。

#### Acceptance Criteria

1. THE InkFlow_System SHALL display a progress statistics panel in the left sidebar footer
2. THE InkFlow_System SHALL show total word count, today's word count, and daily goal progress
3. THE InkFlow_System SHALL display a weekly writing activity chart showing daily word counts
4. WHEN a user completes their daily goal THEN the InkFlow_System SHALL display a celebration animation
5. THE InkFlow_System SHALL show chapter completion percentage for the current volume

### Requirement 8: 章节快照与版本历史

**User Story:** As a 小说作者, I want 查看和恢复章节的历史版本, so that 我可以回溯到之前的内容状态。

#### Acceptance Criteria

1. THE InkFlow_System SHALL provide a version history panel accessible from the editor toolbar
2. WHEN a user opens version history THEN the InkFlow_System SHALL display a list of snapshots with timestamps and descriptions
3. WHEN a user selects a snapshot THEN the InkFlow_System SHALL display a diff view comparing with current content
4. WHEN a user clicks restore on a snapshot THEN the InkFlow_System SHALL replace current content with the snapshot content
5. THE InkFlow_System SHALL auto-create snapshots before major AI-assisted edits

### Requirement 9: 写作风格管理

**User Story:** As a 小说作者, I want 管理和应用写作风格配置, so that AI 生成的内容能匹配我的写作风格。

#### Acceptance Criteria

1. THE InkFlow_System SHALL provide a style management panel in the settings page
2. THE InkFlow_System SHALL display current style profile with sample text and style attributes
3. WHEN a user uploads sample text THEN the InkFlow_System SHALL analyze and extract style characteristics
4. THE InkFlow_System SHALL allow users to manually adjust style parameters
5. WHEN a user saves style changes THEN the InkFlow_System SHALL persist the style profile and apply it to subsequent AI requests

### Requirement 10: Token 使用量监控

**User Story:** As a 小说作者, I want 监控我的 AI Token 使用量, so that 我可以控制成本并优化使用。

#### Acceptance Criteria

1. THE InkFlow_System SHALL display a token usage indicator in the right panel header
2. THE InkFlow_System SHALL show today's token usage and remaining quota
3. WHEN a user clicks the usage indicator THEN the InkFlow_System SHALL display a detailed usage breakdown by operation type
4. THE InkFlow_System SHALL display a usage trend chart showing daily consumption over the past week
5. WHEN usage exceeds 80% of daily quota THEN the InkFlow_System SHALL display a warning notification

### Requirement 11: AI Provider 配置

**User Story:** As a 小说作者, I want 配置不同的 AI 服务提供商, so that 我可以选择最适合我需求的 AI 模型。

#### Acceptance Criteria

1. THE InkFlow_System SHALL provide an AI provider configuration panel in the settings page
2. THE InkFlow_System SHALL display available providers with connection status
3. WHEN a user adds a new provider THEN the InkFlow_System SHALL validate the API key and display connection result
4. THE InkFlow_System SHALL allow users to set default provider for different operation types
5. WHEN a provider connection fails THEN the InkFlow_System SHALL display an error message and suggest troubleshooting steps

### Requirement 12: 项目导入导出

**User Story:** As a 小说作者, I want 导入和导出我的项目数据, so that 我可以备份数据或在不同设备间迁移。

#### Acceptance Criteria

1. THE InkFlow_System SHALL provide export functionality in the project settings
2. WHEN a user triggers export THEN the InkFlow_System SHALL generate a JSON file containing all project data
3. THE InkFlow_System SHALL provide import functionality for previously exported projects
4. WHEN a user imports a project file THEN the InkFlow_System SHALL validate the file format and display import preview
5. WHEN import is confirmed THEN the InkFlow_System SHALL create a new project with the imported data

### Requirement 13: 关系图谱可视化

**User Story:** As a 小说作者, I want 可视化查看角色之间的关系, so that 我可以更好地理解和管理复杂的人物关系网络。

#### Acceptance Criteria

1. THE InkFlow_System SHALL provide a relationship graph view accessible from the asset drawer
2. THE InkFlow_System SHALL display characters as nodes and relationships as edges with labels
3. WHEN a user hovers over a node THEN the InkFlow_System SHALL highlight connected nodes and display character summary
4. WHEN a user clicks on an edge THEN the InkFlow_System SHALL display relationship details and evolution history
5. THE InkFlow_System SHALL support zoom and pan interactions for navigating large graphs

### Requirement 14: 响应式布局适配

**User Story:** As a 小说作者, I want 在不同设备上使用 InkFlow, so that 我可以随时随地进行创作。

#### Acceptance Criteria

1. WHEN viewport width is less than 768px THEN the InkFlow_System SHALL switch to single-column mobile layout
2. WHEN viewport width is between 768px and 1024px THEN the InkFlow_System SHALL switch to two-column tablet layout
3. THE InkFlow_System SHALL provide swipe gestures for switching between panels on mobile devices
4. THE InkFlow_System SHALL preserve all functionality across different viewport sizes

### Requirement 15: 离线支持与状态同步

**User Story:** As a 小说作者, I want 在离线时继续创作, so that 网络问题不会中断我的写作流程。

#### Acceptance Criteria

1. THE InkFlow_System SHALL display an offline indicator when network connection is lost
2. WHEN offline THEN the InkFlow_System SHALL cache editor changes locally using IndexedDB
3. WHEN connection is restored THEN the InkFlow_System SHALL sync cached changes with the server
4. IF sync conflicts occur THEN the InkFlow_System SHALL display a conflict resolution dialog
5. THE InkFlow_System SHALL indicate sync status with visual feedback (synced/syncing/pending)
