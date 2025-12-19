# Implementation Plan

- [x] 1. 创建数据库 CDC 触发器
  - [x] 1.1 创建 Flyway 迁移脚本 V12__cdc_triggers.sql
    - 创建通用通知函数 `notify_entity_change()`
    - 创建 characters 表触发器
    - 创建 wiki_entries 表触发器
    - 创建 plot_loops 表触发器
    - 创建 character_relationships 表触发器
    - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 2. 实现角色变更事件监听
  - [x] 2.1 创建 CharacterChangedEvent 事件类
    - 定义 projectId, characterId, characterName, operation, currentState, previousState 字段
    - 定义 Operation 枚举 (CREATE, UPDATE, DELETE)
    - _Requirements: 2.1_

  - [x] 2.2 创建 CharacterChangeListener 监听器
    - 注入 ProactiveConsistencyService, EvolutionAnalysisService, ConsistencyWarningService
    - 实现 @TransactionalEventListener 方法处理 CharacterChangedEvent
    - 触发一致性检查
    - 创建演进快照
    - 处理删除时的清理逻辑
    - _Requirements: 2.1, 2.2, 2.3, 2.4_

  - [ ]* 2.3 编写属性测试：实体变更触发快照创建
    - **Property 1: Entity changes trigger snapshot creation**
    - **Validates: Requirements 2.1, 2.3**

  - [x] 2.4 修改 CharacterService 发布变更事件
    - 在 create/update/delete 方法中发布 CharacterChangedEvent
    - _Requirements: 2.1, 2.2, 2.3, 2.4_

- [x] 3. 实现伏笔变更事件监听
  - [x] 3.1 创建 PlotLoopChangedEvent 事件类
    - 定义 projectId, plotLoopId, status, operation 字段
    - _Requirements: 3.1_

  - [x] 3.2 创建 PlotLoopChangeListener 监听器
    - 实现状态变更时触发一致性检查
    - 实现解决时验证章节存在性
    - 创建演进快照
    - _Requirements: 3.1, 3.2, 3.3_

  - [x] 3.3 修改 PlotLoopService 发布变更事件
    - 在 create/update/delete 方法中发布 PlotLoopChangedEvent
    - _Requirements: 3.1, 3.2, 3.3_

- [ ] 4. Checkpoint - 确保所有测试通过
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. 集成 ContentGenerationWorkflow
  - [x] 5.1 添加 PreflightService 依赖到 ContentGenerationWorkflow
    - 注入 PreflightService 和 StateRetrievalService
    - _Requirements: 4.1_

  - [x] 5.2 实现预检逻辑
    - 在 executeWorkflow 开始时调用 preflightService.preflight()
    - 检查 context.isConsistencyEnabled() 决定是否执行
    - 发送 SSE 事件通知预检结果
    - _Requirements: 4.1, 4.5, 9.3_

  - [x] 5.3 实现角色状态获取
    - 使用 StateRetrievalService 获取角色在当前章节的状态
    - 将状态信息注入到生成上下文中
    - _Requirements: 4.2_

  - [x] 5.4 实现后检逻辑
    - 在内容生成完成后触发一致性检查
    - 发送 SSE 事件通知检查结果
    - _Requirements: 4.3, 9.1_

  - [ ]* 5.5 编写属性测试：工作流一致性检查生命周期
    - **Property 3: Workflow consistency check lifecycle**
    - **Validates: Requirements 4.1, 4.3**

  - [ ]* 5.6 编写属性测试：禁用一致性时跳过检查
    - **Property 4: Consistency disabled skips checks**
    - **Validates: Requirements 4.5**

- [x] 6. 增强 ConsistencyAgent
  - [x] 6.1 注入 ProactiveConsistencyService 和 ConsistencyWarningService
    - 替换现有的简单检查逻辑
    - _Requirements: 5.1_

  - [x] 6.2 实现完整的规则检查调用
    - 调用 RuleCheckerService.checkAllRules()
    - 处理检查结果
    - _Requirements: 5.1, 5.4_

  - [x] 6.3 实现警告存储逻辑
    - 将检测到的问题通过 ConsistencyWarningService 存储
    - _Requirements: 5.2_

  - [x] 6.4 实现结构化报告返回
    - 按严重程度分组警告
    - 返回包含 errors, warnings, info 的报告
    - _Requirements: 5.3_

  - [ ]* 6.5 编写属性测试：实体变更触发一致性检查
    - **Property 2: Entity changes trigger consistency checks**
    - **Validates: Requirements 2.2, 3.1, 5.1**

- [ ] 7. Checkpoint - 确保所有测试通过
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. 增强演进快照服务
  - [x] 8.1 添加 createSnapshotForEntity 便捷方法到 EvolutionAnalysisService
    - 简化从事件监听器调用的接口
    - _Requirements: 6.1_

  - [x] 8.2 验证关键帧间隔逻辑
    - 确保每 10 个增量后创建关键帧
    - _Requirements: 6.2, 6.4_

  - [ ]* 8.3 编写属性测试：状态快照往返一致性
    - **Property 5: State snapshot round-trip consistency**
    - **Validates: Requirements 6.2, 6.3**

  - [ ]* 8.4 编写属性测试：关键帧间隔强制执行
    - **Property 6: Keyframe interval enforcement**
    - **Validates: Requirements 6.2, 6.4**

- [x] 9. 实现 SSE 事件发送
  - [x] 9.1 定义 SSE 事件类型常量
    - 添加 PREFLIGHT_RESULT, CONSISTENCY_WARNING, EVOLUTION_UPDATE 等常量
    - _Requirements: 9.1, 9.2, 9.3_

  - [x] 9.2 在 SSEEventBuilder 中添加便捷方法
    - 添加 preflightResult(), consistencyWarning(), evolutionUpdate() 方法
    - _Requirements: 9.1, 9.2, 9.3_

  - [ ]* 9.3 编写属性测试：系统事件触发 SSE 发送
    - **Property 7: SSE event emission on system events**
    - **Validates: Requirements 9.1, 9.2, 9.3**

- [x] 10. 实现前端一致性警告展示





  - [x] 10.1 创建 consistency-service.ts


    - 实现 getWarningCount(), getWarnings(), resolveWarning(), dismissWarning() 方法
    - _Requirements: 7.1, 7.4, 7.5_

  - [x] 10.2 创建 consistency-store.ts


    - 管理警告状态
    - 实现加载、解决、忽略操作
    - _Requirements: 7.1, 7.4, 7.5_

  - [x] 10.3 创建 ConsistencyWarningIndicator 组件


    - 在侧边栏显示警告数量徽章
    - 点击打开警告面板
    - _Requirements: 7.2, 7.3_

  - [x] 10.4 创建 ConsistencyWarningPanel 组件



    - 显示警告列表，按严重程度分组
    - 支持解决和忽略操作
    - _Requirements: 7.3, 7.4, 7.5_

- [x] 11. 实现前端演进时间线展示





  - [x] 11.1 创建 evolution-service.ts


    - 实现 getSnapshots(), getStateAtChapter(), compareStates() 方法
    - _Requirements: 8.1, 8.3, 8.4_

  - [x] 11.2 创建 evolution-store.ts


    - 管理演进时间线状态
    - _Requirements: 8.1_

  - [x] 11.3 创建 EvolutionTimeline 组件


    - 显示角色状态变化时间线
    - 支持点击查看详情
    - _Requirements: 8.2, 8.3_

  - [x] 11.4 创建 StateCompareDialog 组件


    - 对比两个快照的差异
    - 高亮显示变化字段
    - _Requirements: 8.4_



- [x] 12. 集成前端 SSE 事件处理



  - [x] 12.1 在 sse-client.ts 中添加新事件类型处理


    - 处理 preflight_result, consistency_warning, evolution_update 事件
    - _Requirements: 9.1, 9.2, 9.3_

  - [x] 12.2 在 ChatInterface 中显示一致性警告


    - 在聊天流中显示警告消息
    - _Requirements: 4.4, 9.1_

- [x] 13. 实现实体删除清理
  - [x] 13.1 在 CharacterChangeListener 中实现删除清理
    - 删除关联的 ConsistencyWarning
    - 删除关联的 EvolutionTimeline 和 StateSnapshot
    - _Requirements: 2.4_

  - [ ]* 13.2 编写属性测试：实体删除时清理关联数据
    - **Property 8: Entity deletion cleanup**
    - **Validates: Requirements 2.4**

- [ ] 14. Final Checkpoint - 确保所有测试通过
  - Ensure all tests pass, ask the user if questions arise.

