# Implementation Plan

## Phase 1: 后端 API 调整

- [x] 1. 统一 API 路径前缀


  - [x] 1.1 修改 CharacterController 路径为 /api/characters


    - 更新 @RequestMapping 注解
    - _Requirements: 后端调整计划 1_

  - [x] 1.2 修改 WikiEntryController 路径为 /api/wiki

    - 更新 @RequestMapping 注解
    - _Requirements: 后端调整计划 1_

  - [x] 1.3 修改 PlotLoopController 路径为 /api/plotloops

    - 更新 @RequestMapping 注解
    - _Requirements: 后端调整计划 1_

- [x] 2. 补充 DTO 字段



  - [x] 2.1 ProjectDto 添加 wordCount 字段

    - 修改 ProjectDto record
    - 修改 ProjectService 计算字数
    - _Requirements: 后端调整计划 2_

  - [x] 2.2 VolumeDto 添加 wordCount 字段

    - 修改 VolumeDto record
    - 修改 VolumeService 计算字数
    - _Requirements: 后端调整计划 2_

- [x] 3. 章节内容接口



  - [x] 3.1 创建 ChapterContentDto

    - 定义 content、wordCount、updatedAt 字段
    - _Requirements: 后端调整计划 3_

  - [x] 3.2 实现获取章节内容接口

    - GET /api/projects/{projectId}/chapters/{chapterId}/content
    - 聚合 StoryBlock 内容返回
    - _Requirements: 后端调整计划 3_

  - [x] 3.3 实现保存章节内容接口
    - PUT /api/projects/{projectId}/chapters/{chapterId}/content
    - 支持内容保存和字数更新
    - _Requirements: 后端调整计划 3_

- [x] 4. 项目导入导出接口




  - [x] 4.1 添加导出接口到 ProjectController

    - GET /api/projects/{id}/export
    - 返回 JSON 文件下载
    - _Requirements: 新增_

  - [x] 4.2 添加导入接口到 ProjectController
    - POST /api/projects/import
    - 接收 JSON 文件上传
    - _Requirements: 新增_

- [x] 5. Checkpoint - 后端 API 调整完成
  - Ensure all tests pass, ask the user if questions arise.

## Phase 2: 前端基础设施

- [-] 6. 项目配置


  - [x] 5.1 安装必要依赖

    - 添加 zustand、axios、@tanstack/react-query
    - 配置 TypeScript 路径别名
    - _Requirements: 1.1_

  - [x] 5.2 配置环境变量

    - 创建 .env.development 和 .env.production
    - 配置 VITE_API_BASE_URL
    - _Requirements: 1.1_

- [-] 7. API 客户端层


  - [x] 6.1 实现 TokenManager

    - Token 存储（localStorage）
    - 过期检测
    - 刷新逻辑
    - _Requirements: 1.2, 1.3, 1.4_
  - [x]* 6.2 Write property test for JWT token attachment


    - **Property 1: JWT Token Attachment**
    - **Validates: Requirements 1.2**

  - [x] 6.3 实现 ApiClient

    - 基于 fetch 封装
    - 自动附加 Authorization header
    - 错误响应转换
    - _Requirements: 1.2, 1.5_

  - [x]* 6.4 Write property test for error transformation

    - **Property 2: Error Response Transformation**
    - **Validates: Requirements 1.5**

  - [x] 6.5 实现 SSEClient

    - SSE 连接管理
    - 事件解析
    - 中断处理
    - _Requirements: 7.1, 7.3, 7.4_

- [-] 8. Checkpoint - API 客户端完成

  - Ensure all tests pass, ask the user if questions arise.

## Phase 3: 状态管理层

- [-] 9. 认证状态管理


  - [x] 8.1 实现 AuthStore

    - 用户状态、登录、登出、会话验证
    - Token 自动刷新
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_
  - [ ]* 8.2 Write unit tests for AuthStore
    - 测试登录、登出、刷新流程
    - _Requirements: 2.1, 2.2, 2.3_


- [-] 10. 项目状态管理

  - [x] 9.1 实现 ProjectStore

    - 项目列表、当前项目、CRUD 操作
    - 缓存和分页
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 9.2_
  - [x]* 9.2 Write property test for cache timestamp


    - **Property 4: Store Cache Timestamp**
    - **Validates: Requirements 9.2**



- [ ] 11. 内容状态管理
  - [x] 10.1 实现 ContentStore

    - 卷、章节管理
    - 当前编辑章节
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_
  - [ ]* 10.2 Write unit tests for ContentStore
    - 测试卷/章节 CRUD
    - _Requirements: 4.1, 4.2, 4.3_



- [ ] 12. 资产状态管理
  - [x] 11.1 实现 AssetStore

    - 角色、Wiki、PlotLoop 管理
    - 关系图谱
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 6.1, 6.2, 6.3, 6.4, 6.5, 8.1, 8.2, 8.3, 8.4_
  - [ ]* 11.2 Write unit tests for AssetStore
    - 测试资产 CRUD
    - _Requirements: 5.1, 6.1, 8.1_



- [ ] 13. 聊天状态管理
  - [x] 12.1 实现 ChatStore

    - 消息列表、流式响应、思考链
    - 技能管理
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_
  - [x]* 12.2 Write property test for SSE content accumulation


    - **Property 3: SSE Content Accumulation**
    - **Validates: Requirements 7.2**

- [x] 14. Checkpoint - 状态管理完成


  - Ensure all tests pass, ask the user if questions arise.

## Phase 4: UI 集成

- [x] 15. 认证页面集成



  - [x] 14.1 集成 LoginPage


    - 连接 AuthStore
    - 表单验证和错误显示
    - _Requirements: 2.1, 2.2, 10.1, 10.3_

  - [x] 14.2 添加注册功能

    - 注册表单
    - 密码强度验证
    - _Requirements: 2.1_

- [x] 16. 项目管理集成
  - [x] 16.1 创建项目列表页


    - 显示用户项目
    - 分页和搜索
    - _Requirements: 3.1, 10.1_

  - [x] 16.2 创建项目创建/编辑对话框

    - 项目表单
    - 封面上传
    - _Requirements: 3.2, 3.4_

  - [x] 16.3 添加导入导出按钮




    - 导出按钮：下载 JSON 文件
    - 导入按钮：上传 JSON 文件
    - _Requirements: 新增_

- [x] 17. 侧边栏集成
  - [x] 16.1 集成 ProjectTree




    - 连接 ContentStore
    - 卷/章节树形结构
    - _Requirements: 4.1, 4.3_
  - [x] 16.2 集成 AssetDrawer


    - 连接 AssetStore
    - 角色、Wiki、PlotLoop 列表
    - _Requirements: 5.1, 6.1, 8.1_
  - [x] 16.3 集成 PhaseSwitcher


    - 连接 ProjectStore
    - 阶段切换
    - _Requirements: 3.5_

- [x] 18. 编辑器集成
  - [x] 17.1 集成 TipTapEditor


    - 加载章节内容
    - 自动保存
    - _Requirements: 4.3, 后端调整计划 3_
  - [x] 17.2 实现内容自动保存

    - 防抖保存
    - 保存状态指示
    - _Requirements: 10.1, 10.2_

- [x] 19. Copilot 集成
  - [x] 18.1 集成 ChatInterface


    - 连接 ChatStore
    - 消息发送和显示
    - _Requirements: 7.1, 7.2_
  - [x] 18.2 集成 ThoughtChain


    - 显示思考过程
    - _Requirements: 7.2_
  - [x] 18.3 集成 SkillSelector


    - 技能切换
    - _Requirements: 7.5_
  - [x] 18.4 集成 ArtifactCard



    - 显示生成的资产
    - 应用到编辑器
    - _Requirements: 7.2_

- [x] 20. Checkpoint - UI 集成完成
  - Ensure all tests pass, ask the user if questions arise.

## Phase 5: 错误处理与优化

- [x] 21. 错误处理
  - [x] 20.1 实现全局错误处理
    - Toast 通知 (`src/lib/toast.ts`)
    - 错误边界 (`src/app/components/error/ErrorBoundary.tsx`)
    - 全局错误处理 Hook (`src/hooks/useGlobalErrorHandler.ts`)
    - 集成到 App.tsx
    - _Requirements: 10.2, 10.3_
  - [x] 20.2 实现加载状态
    - Skeleton 加载组件 (`src/app/components/loading/LoadingStates.tsx`)
    - 包含: ProjectListSkeleton, ProjectTreeSkeleton, AssetDrawerSkeleton, EditorSkeleton, ChatMessageSkeleton, ThoughtChainSkeleton
    - 通用组件: LoadingSpinner, LoadingOverlay, SectionLoading, EmptyState
    - _Requirements: 10.1_

- [x] 22. 离线支持（可选）
  - [x]* 21.1 实现 OfflineManager (`src/lib/offline-manager.ts`)
    - 网络状态检测 (online/offline events)
    - 操作队列 (QueuedMutation with retry support)
    - 自动重放机制 (replayMutations)
    - Zustand 持久化存储
    - _Requirements: 10.4, 10.5_
  - [x]* 21.2 实现离线指示器 (`src/app/components/status/OfflineIndicator.tsx`)
    - OfflineIndicator: 紧凑/完整两种模式
    - OfflineBanner: 顶部横幅提示
    - NetworkStatusIcon: 工具栏图标
    - 离线状态显示
    - 重连提示
    - _Requirements: 10.4_

- [x] 23. Final Checkpoint - 全部完成
  - All 22 tests pass
  - Phase 5 错误处理与优化完成
