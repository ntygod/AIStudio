# Implementation Plan

## Phase 1: 核心布局与状态管理

- [x] 1. 创建 UI 状态管理基础设施





  - [x] 1.1 创建 UIStore 管理布局状态


    - 实现 leftSidebarCollapsed、rightPanelCollapsed、zenMode、mobileActivePanel 状态
    - 实现 localStorage 持久化
    - _Requirements: 1.2, 1.3, 1.5_
  - [ ]* 1.2 编写 UIStore 属性测试
    - **Property 1: Sidebar collapse preserves content state**
    - **Property 2: LocalStorage persistence round-trip**
    - **Validates: Requirements 1.2, 1.3, 1.5**

  - [x] 1.3 增强 MainLayout 组件

    - 添加响应式断点检测
    - 实现移动端面板切换
    - _Requirements: 14.1, 14.2_
  - [ ]* 1.4 编写 MainLayout 响应式属性测试
    - **Property 24: Responsive layout breakpoint transitions**
    - **Validates: Requirements 14.1, 14.2**

- [ ] 2. Checkpoint - 确保所有测试通过
  - Ensure all tests pass, ask the user if questions arise.

## Phase 2: 左侧边栏功能完善

- [-] 3. 增强 PhaseSwitcher 组件




  - [x] 3.1 实现阶段切换与后端同步


    - 添加阶段切换 API 调用
    - 添加视觉指示器
    - _Requirements: 2.1, 2.2_
  - [ ]* 3.2 编写阶段切换属性测试
    - **Property 3: Phase transition updates state correctly**
    - **Validates: Requirements 2.2**

- [x] 4. 增强 ProjectTree 组件





  - [x] 4.1 完善卷/章节渲染逻辑


    - 确保所有卷和章节正确渲染
    - 显示字数统计
    - _Requirements: 2.3, 2.4_
  - [ ]* 4.2 编写 ProjectTree 属性测试
    - **Property 4: Project tree renders all volumes and chapters**
    - **Validates: Requirements 2.3**

  - [x] 4.3 实现章节选择与编辑器联动

    - 章节选择加载内容到编辑器
    - _Requirements: 2.5_
  - [ ]* 4.4 编写章节选择属性测试
    - **Property 5: Chapter selection loads correct content**
    - **Validates: Requirements 2.5**

- [x] 5. 增强 AssetDrawer 组件





  - [x] 5.1 完善资产详情展示


    - 点击资产显示详情模态框
    - _Requirements: 2.6, 2.7_
  - [ ]* 5.2 编写资产点击属性测试
    - **Property 6: Asset click displays correct details**
    - **Validates: Requirements 2.7**

  - [x] 5.3 添加关系图谱入口

    - 添加"查看关系图"按钮
    - _Requirements: 13.1_

  - [x] 5.4 添加演进时间线入口

    - 添加"查看演进"按钮
    - _Requirements: 6.1_

- [x] 6. 创建 ProgressStats 组件





  - [x] 6.1 实现进度统计面板


    - 显示总字数、今日字数、每日目标
    - 显示周活动图表
    - _Requirements: 7.1, 7.2, 7.3_
  - [ ]* 6.2 编写进度统计属性测试
    - **Property 7: Progress statistics calculation accuracy**
    - **Validates: Requirements 2.8, 7.2, 7.5**

  - [-] 6.3 实现每日目标完成庆祝动画

    - _Requirements: 7.4_

- [x] 7. Checkpoint - 确保所有测试通过





  - Ensure all tests pass, ask the user if questions arise.

## Phase 3: 中央编辑器增强

- [x] 8. 增强 TipTapEditor 组件


  - [x] 8.1 完善面包屑导航


    - 显示当前卷 > 章节路径
    - _Requirements: 3.1_
  - [ ]* 8.2 编写面包屑属性测试
    - **Property 8: Breadcrumb shows correct path**
    - **Validates: Requirements 3.1**
  - [x] 8.3 实现浮动工具栏


    - 文本选择时显示 AI 增强选项
    - _Requirements: 3.3, 3.4_
  - [ ]* 8.4 编写浮动工具栏属性测试
    - **Property 9: Text selection triggers floating toolbar**
    - **Validates: Requirements 3.3**
  - [x] 8.5 实现自动保存功能

    - 30 秒自动保存
    - Ctrl+S 立即保存
    - 显示保存状态指示器
    - _Requirements: 3.5, 3.6_
  - [x] 8.6 实现字数统计显示

    - 编辑器底部显示字数和字符数
    - _Requirements: 3.7_
  - [ ]* 8.7 编写字数统计属性测试
    - **Property 10: Word count accuracy**
    - **Validates: Requirements 3.7**

- [x] 9. 创建 VersionHistory 组件


  - [x] 9.1 实现版本历史面板


    - 显示快照列表
    - _Requirements: 8.1, 8.2_
  - [x] 9.2 实现差异对比视图

    - 选择快照显示与当前内容的差异
    - _Requirements: 8.3_
  - [ ]* 9.3 编写差异对比属性测试
    - **Property 20: Version history diff accuracy**
    - **Validates: Requirements 8.3**
  - [x] 9.4 实现快照恢复功能

    - _Requirements: 8.4_
  - [ ]* 9.5 编写快照恢复属性测试
    - **Property 21: Snapshot restore replaces content exactly**
    - **Validates: Requirements 8.4**
  - [x] 9.6 实现 AI 编辑前自动快照

    - _Requirements: 8.5_

- [x] 10. Checkpoint - 确保所有测试通过



  - Ensure all tests pass, ask the user if questions arise.

## Phase 4: 右侧 AI Copilot 面板增强

- [x] 11. 增强 AgentStatus 组件

  - [x] 11.1 完善状态指示器
    - 显示 agent 类型和状态
    - 添加 error 状态支持
    - 添加 tooltip 显示 agent 描述和状态说明
    - 添加错误横幅显示错误信息
    - _Requirements: 4.1_
  - [ ]* 11.2 编写状态指示器属性测试
    - **Property 11: Agent state indicator correctness**
    - **Validates: Requirements 4.1**

- [x] 12. 增强 SkillSelector 组件

  - [x] 12.1 完善技能切换逻辑
    - 技能状态更新（已实现 toggleSkill）
    - 包含在后续 AI 请求中（ChatRequest 添加 skills 字段）
    - _Requirements: 4.3, 4.4_
  - [ ]* 12.2 编写技能切换属性测试
    - **Property 12: Skill toggle updates state**
    - **Validates: Requirements 4.4**

- [x] 13. 增强 ArtifactCard 组件


  - [x] 13.1 完善应用功能
    - 点击应用插入内容到光标位置
    - 添加 cursorPosition 到 content-store
    - 添加 insertAtCursor 方法
    - TipTapEditor 跟踪光标位置
    - _Requirements: 4.7, 4.8_
  - [ ]* 13.2 编写应用功能属性测试
    - **Property 13: Artifact apply inserts at cursor**
    - **Validates: Requirements 4.8**

- [x] 14. 创建 TokenUsageIndicator 组件


  - [x] 14.1 实现使用量指示器


    - 显示今日使用量和配额
    - _Requirements: 10.1, 10.2_
  - [ ]* 14.2 编写使用量显示属性测试
    - **Property 27: Token usage display accuracy**
    - **Validates: Requirements 10.2**
  - [x] 14.3 实现使用量详情面板


    - 显示按操作类型分解
    - 显示周趋势图表
    - _Requirements: 10.3, 10.4_
  - [x] 14.4 实现超额警告


    - 使用量超过 80% 显示警告
    - _Requirements: 10.5_
  - [ ]* 14.5 编写超额警告属性测试
    - **Property 28: Usage warning threshold trigger**
    - **Validates: Requirements 10.5**

- [x] 15. Checkpoint - 确保所有测试通过





  - Ensure all tests pass, ask the user if questions arise.

## Phase 5: 一致性警告系统

- [x] 16. 创建一致性警告 UI



  - [x] 16.1 创建 ConsistencyWarningIndicator 组件

    - 显示未解决警告数量
    - 有警告时高亮显示
    - _Requirements: 5.1, 5.2_
  - [ ]* 16.2 编写警告指示器属性测试
    - **Property 14: Warning indicator count accuracy**
    - **Validates: Requirements 5.1, 5.2**

  - [x] 16.3 创建 ConsistencyWarningPanel 组件

    - 显示所有活动警告
    - 显示严重程度、描述、受影响实体、建议解决方案
    - _Requirements: 5.3, 5.4_

  - [x] 16.4 实现解决警告功能

    - _Requirements: 5.5_
  - [ ]* 16.5 编写解决警告属性测试
    - **Property 15: Warning resolve updates count**
    - **Validates: Requirements 5.5**

  - [x] 16.6 实现忽略警告功能

    - _Requirements: 5.6_
  - [ ]* 16.7 编写忽略警告属性测试
    - **Property 16: Warning dismiss hides without resolving**
    - **Validates: Requirements 5.6**

- [x] 17. Checkpoint - 确保所有测试通过



  - Ensure all tests pass, ask the user if questions arise.

## Phase 6: 演进时间线系统

- [x] 18. 创建演进时间线 UI





  - [x] 18.1 创建 EvolutionTimeline 组件


    - 显示垂直时间线
    - 显示时间戳、变更描述、受影响属性
    - _Requirements: 6.2, 6.3_
  - [ ]* 18.2 编写时间线渲染属性测试
    - **Property 17: Evolution timeline renders all snapshots**
    - **Validates: Requirements 6.2, 6.3**
  - [x] 18.3 创建 StateCompareDialog 组件


    - 显示前后状态对比
    - _Requirements: 6.4_
  - [ ]* 18.4 编写状态对比属性测试
    - **Property 18: State comparison shows correct diff**
    - **Validates: Requirements 6.4**
  - [x] 18.5 实现手动创建快照功能


    - _Requirements: 6.5, 6.6_
  - [ ]* 18.6 编写快照创建属性测试
    - **Property 19: Snapshot creation captures current state**
    - **Validates: Requirements 6.6**

- [ ] 19. Checkpoint - 确保所有测试通过
  - Ensure all tests pass, ask the user if questions arise.

## Phase 7: 关系图谱可视化

- [x] 20. 创建关系图谱 UI







  - [x] 20.1 创建 RelationshipGraph 组件


    - 使用 D3.js 或 React Flow 渲染图谱
    - 角色作为节点，关系作为边
    - _Requirements: 13.1, 13.2_
  - [ ]* 20.2 编写图谱渲染属性测试
    - **Property 23: Relationship graph renders all nodes and edges**
    - **Validates: Requirements 13.2**
  - [x] 20.3 实现节点悬停高亮


    - _Requirements: 13.3_

  - [x] 20.4 实现边点击显示详情

    - _Requirements: 13.4_

  - [x] 20.5 实现缩放和平移交互

    - _Requirements: 13.5_

- [ ] 21. Checkpoint - 确保所有测试通过
  - Ensure all tests pass, ask the user if questions arise.

## Phase 8: 设置页面功能

- [x] 22. 增强设置页面





  - [x] 22.1 创建写作风格管理面板


    - 显示当前风格配置
    - 上传样本文本分析
    - 手动调整参数
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5_

  - [x] 22.2 创建 AI Provider 配置面板

    - 显示可用提供商和连接状态
    - 添加/验证 API Key
    - 设置默认提供商
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5_
  - [x] 22.3 创建导入导出功能


    - 导出项目为 JSON
    - 导入项目文件
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5_
  - [ ]* 22.4 编写导入导出往返属性测试
    - **Property 22: Export/Import round-trip consistency**
    - **Validates: Requirements 12.2, 12.5**

- [x] 23. Checkpoint - 确保所有测试通过






  - Ensure all tests pass, ask the user if questions arise.

## Phase 9: 离线支持

- [x] 24. 实现离线支持







  - [x] 24.1 创建 OfflineStore 和 OfflineManager


    - 检测网络状态
    - 管理离线队列
    - _Requirements: 15.1_

  - [x] 24.2 实现 IndexedDB 缓存
    - 离线时缓存编辑器变更
    - _Requirements: 15.2_
  - [ ]* 24.3 编写离线缓存属性测试
    - **Property 25: Offline cache preserves changes**
    - **Validates: Requirements 15.2, 15.3**
  - [x] 24.4 实现同步功能


    - 连接恢复时同步缓存变更
    - _Requirements: 15.3_
  - [x] 24.5 实现冲突解决对话框


    - _Requirements: 15.4_
  - [x] 24.6 创建同步状态指示器




    - _Requirements: 15.5_
  - [ ]* 24.7 编写同步状态属性测试
    - **Property 26: Sync status indicator accuracy**
    - **Validates: Requirements 15.5**

- [x] 25. Final Checkpoint - 确保所有测试通过












  - Ensure all tests pass, ask the user if questions arise.
