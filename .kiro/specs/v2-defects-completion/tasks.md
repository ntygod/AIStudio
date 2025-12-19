# Implementation Plan

- [x] 1. 用户级AI模型配置实现







  - [x] 1.1 创建 UserProviderConfig 实体和 Repository


    - 创建 `UserProviderConfig.java` 实体类
    - 创建 `UserProviderConfigRepository.java` 接口
    - 创建数据库迁移脚本 `V11__user_provider_config.sql`
    - _Requirements: 1.1, 1.2_

  - [ ]* 1.2 编写属性测试 - 用户配置检索一致性
    - **Property 1: User Config Retrieval Consistency**
    - **Validates: Requirements 1.1, 1.2, 1.3**



  - [ ] 1.3 实现 UserProviderConfigService
    - 实现 `getUserConfig(UUID userId)` 方法
    - 实现 5 分钟缓存逻辑
    - 实现配置保存和缓存失效
    - _Requirements: 1.1, 1.2, 1.4_

  - [ ]* 1.4 编写属性测试 - 用户配置缓存一致性
    - **Property 2: User Config Cache Consistency**


    - **Validates: Requirements 1.4**

  - [ ] 1.5 增强 DynamicChatModelFactory
    - 修改 `getChatModel(UUID userId)` 使用 UserProviderConfigService
    - 实现提供商不可用时的降级逻辑
    - 修改 `getReasoningModel()` 返回 Optional
    - _Requirements: 1.1, 1.2, 1.3, 7.1_

- [ ] 2. Checkpoint - 确保所有测试通过
  - Ensure all tests pass, ask the user if questions arise.

- [x] 3. 一致性规则检查实现





  - [x] 3.1 创建 RuleCheckerService


    - 创建 `RuleCheckerService.java` 服务类
    - 定义规则检查接口
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

  - [ ]* 3.2 编写属性测试 - 角色名称唯一性检测
    - **Property 3: Character Name Uniqueness Detection**
    - **Validates: Requirements 2.1**


  - [x] 3.3 实现角色规则检查

    - 实现名称唯一性检查
    - 实现必填字段验证
    - _Requirements: 2.1, 2.2_

  - [ ]* 3.4 编写属性测试 - 必填字段验证
    - **Property 4: Required Field Validation**
    - **Validates: Requirements 2.2**

  - [ ]* 3.5 编写属性测试 - Wiki标题唯一性检测
    - **Property 5: WikiEntry Title Uniqueness Detection**
    - **Validates: Requirements 2.3**

  - [x] 3.6 实现 WikiEntry 规则检查


    - 实现标题唯一性检查
    - 实现引用完整性检查
    - _Requirements: 2.3, 2.4_

  - [ ]* 3.7 编写属性测试 - 引用完整性检查
    - **Property 6: Reference Integrity Check**
    - **Validates: Requirements 2.4**

  - [ ]* 3.8 编写属性测试 - 双向关系一致性
    - **Property 7: Bidirectional Relationship Consistency**
    - **Validates: Requirements 2.5**


  - [x] 3.9 实现关系规则检查

    - 实现双向一致性检查
    - _Requirements: 2.5_


  - [x] 3.10 集成 RuleCheckerService 到 ProactiveConsistencyService

    - 修改 `performRuleBasedCheck()` 调用 RuleCheckerService
    - 添加 AI 检查配置开关
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_

- [ ] 4. Checkpoint - 确保所有测试通过
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. 版本化嵌入清理实现





  - [x] 5.1 添加 Repository 删除方法


    - 在 `KnowledgeChunkRepository` 添加 `deleteBySourceIdAndVersionLessThanAndIsActiveFalse()` 方法
    - _Requirements: 3.1, 3.2_

  - [ ]* 5.2 编写属性测试 - 版本清理正确性
    - **Property 8: Version Cleanup Correctness**
    - **Validates: Requirements 3.1, 3.2, 3.4**

  - [x] 5.3 实现 cleanupOldVersions 方法


    - 实现实际的删除逻辑
    - 添加删除记录数日志
    - _Requirements: 3.1, 3.2, 3.3, 3.4_

- [x] 6. Agent 链式执行输出传递





  - [x] 6.1 创建 ChainExecutionContext


    - 创建 `ChainExecutionContext.java` 类
    - 实现输出收集和请求构建逻辑
    - _Requirements: 4.1, 4.2_

  - [ ]* 6.2 编写属性测试 - 链式执行输出传递
    - **Property 9: Chain Execution Output Passing**
    - **Validates: Requirements 4.1, 4.2, 4.3**

  - [ ]* 6.3 编写属性测试 - 链式执行错误处理
    - **Property 10: Chain Execution Error Handling**
    - **Validates: Requirements 4.4**


  - [x] 6.4 修改 AgentOrchestrator 链式执行逻辑

    - 使用 ChainExecutionContext 管理执行状态
    - 实现输出传递到下一个 Agent
    - 实现错误处理和中断逻辑
    - _Requirements: 4.1, 4.2, 4.3, 4.4_

- [ ] 7. Checkpoint - 确保所有测试通过
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. 完整项目导出/导入功能






  - [x] 8.1 创建 EntitySerializerService

    - 创建 `EntitySerializerService.java` 服务类
    - 实现 Character 序列化/反序列化
    - 实现 WikiEntry 序列化/反序列化
    - 实现 PlotLoop 序列化/反序列化
    - 实现 CharacterRelationship 序列化/反序列化
    - 实现 CharacterArchetype 序列化/反序列化
    - _Requirements: 5.1-5.6, 6.1-6.6_

  - [ ]* 8.2 编写属性测试 - 导出-导入往返一致性
    - **Property 11: Export-Import Round Trip**
    - **Validates: Requirements 5.1-5.6, 6.1-6.7**


  - [x] 8.3 增强 ExportService

    - 添加 Character 导出
    - 添加 WikiEntry 导出
    - 添加 PlotLoop 导出
    - 添加 CharacterRelationship 导出
    - 添加 CharacterArchetype 导出
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_


  - [x] 8.4 增强 ImportService

    - 添加 Character 导入
    - 添加 WikiEntry 导入
    - 添加 PlotLoop 导入
    - 添加 CharacterRelationship 导入
    - 添加 CharacterArchetype 导入
    - 实现无效引用跳过逻辑
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7_

- [ ] 9. Checkpoint - 确保所有测试通过
  - Ensure all tests pass, ask the user if questions arise.

- [x] 10. 空返回值安全处理





  - [ ]* 10.1 编写属性测试 - 空安全 Optional 返回
    - **Property 12: Null Safety - Optional Returns**
    - **Validates: Requirements 7.1-7.6**

  - [x] 10.2 修复 DynamicChatModelFactory 空返回


    - 修改 `getReasoningModel()` 返回 Optional
    - _Requirements: 7.1_

  - [x] 10.3 修复 ThinkingAgent 空返回


    - 修改 `extractThinking()` 返回空字符串
    - 修改 `extractAnswer()` 返回原始内容
    - _Requirements: 7.2, 7.3_

  - [x] 10.4 修复 Agent retrieveContext 空返回


    - 修改 CharacterAgent, ConsistencyAgent, PlannerAgent, WorldBuilderAgent, WriterAgent 的 `retrieveContext()` 返回空字符串
    - _Requirements: 7.4_

  - [x] 10.5 修复 WriterAgent retrieveStyle 空返回

    - 修改 `retrieveStyle()` 返回默认风格配置
    - _Requirements: 7.5_

  - [x] 10.6 修复 DeepReasoningTool 空返回


    - 修改 `getReasoningModel()` 抛出描述性异常
    - _Requirements: 7.6_

- [x] 11. 外部服务配置验证





  - [x] 11.1 实现 RerankerService 启动验证


    - 添加 `@PostConstruct` 健康检查
    - 实现端点不可达时的禁用逻辑
    - _Requirements: 8.1, 8.2_


  - [x] 11.2 实现 EmbeddingService 启动验证

    - 添加 EmbeddingModel bean 可用性检查
    - 实现 bean 不存在时的启动异常
    - _Requirements: 8.3, 8.4_

- [ ] 12. Checkpoint - 确保所有测试通过
  - Ensure all tests pass, ask the user if questions arise.

- [x] 13. 移除创作进度功能






  - [x] 13.1 移除 CreationProgressService 相关代码

    - 删除 `calculatePhaseCompletion()` 方法
    - 删除相关 DTO 和端点
    - 更新依赖代码
    - _Requirements: 12.1, 12.2, 12.3, 12.4_

- [x] 14. JSON 序列化异常处理





  - [ ]* 14.1 编写属性测试 - JSON 序列化错误处理
    - **Property 13: JSON Serialization Error Handling**
    - **Validates: Requirements 13.1-13.3**

  - [x] 14.2 修复 EvolutionAnalysisService.toJson()


    - 修改异常处理返回包含错误信息的 JSON
    - 添加异常日志记录
    - _Requirements: 13.1, 13.3_


  - [x] 14.3 修复 ConsistencyCheckService.toJson()

    - 修改异常处理返回包含错误信息的 JSON
    - 添加异常日志记录
    - _Requirements: 13.2, 13.3_

- [x] 15. 预检详细逻辑检查





  - [ ]* 15.1 编写属性测试 - 预检逻辑检查检测
    - **Property 14: Preflight Logic Check Detection**

    - **Validates: Requirements 14.1-14.5**

  - [x] 15.2 实现 PreflightService.checkDetailedLogic()

    - 实现时间线一致性检查
    - 实现地点一致性检查
    - 实现角色状态一致性检查
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 14.5_

- [x] 16. Final Checkpoint - 确保所有测试通过





  - Ensure all tests pass, ask the user if questions arise.
