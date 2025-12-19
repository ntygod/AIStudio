# Implementation Plan

## 模块 1: AI 任务模型配置

- [ ] 1. 后端 - 任务模型配置
  - [ ] 1.1 创建 TaskModelConfig 实体和 Repository
    - 创建 `task_model_configs` 表的 JPA 实体
    - 创建 TaskType 枚举（CONTENT_GENERATION, OUTLINE_PLANNING, CHARACTER_DESIGN, WORLDBUILDING, CONSISTENCY_CHECK, STYLE_ANALYSIS, BRAINSTORM, REVISION）
    - 实现 Repository 接口
    - _Requirements: 1.1, 1.2_
  - [ ] 1.2 实现 TaskModelConfigService
    - 实现 getAllConfigs、saveConfig、deleteConfig 方法
    - 实现 getModelForTask 方法（带默认值回退）
    - _Requirements: 1.2, 1.3, 1.4_
  - [ ]* 1.3 编写属性测试 - 配置 Round-Trip
    - **Property 1: 任务模型配置 Round-Trip 一致性**
    - **Validates: Requirements 1.2**
  - [ ] 1.4 创建 TaskModelConfigController
    - 实现 REST API 端点
    - _Requirements: 1.1_
  - [ ] 1.5 集成到 DynamicChatModelFactory
    - 修改 DynamicChatModelFactory 支持按任务类型获取模型
    - _Requirements: 1.3, 1.4_
  - [ ]* 1.6 编写属性测试 - 模型路由正确性
    - **Property 2: 任务模型路由正确性**
    - **Validates: Requirements 1.3, 1.4**

- [ ] 2. 前端 - 任务模型配置面板
  - [ ] 2.1 创建 task-config-service.ts
    - 实现 API 调用服务
    - _Requirements: 1.1, 1.2_
  - [ ] 2.2 创建 TaskModelPanel 组件
    - 显示所有任务类型的模型配置
    - 支持为每个任务类型选择服务商和模型
    - _Requirements: 1.1, 1.2_
  - [ ] 2.3 集成到 SettingsPage
    - 在 AI 服务商设置中添加任务配置 Tab
    - _Requirements: 1.1_

- [ ] 3. Checkpoint - 确保所有测试通过
  - Ensure all tests pass, ask the user if questions arise.

## 模块 2: 账户安全功能

- [ ] 4. 后端 - 账户安全 API
  - [ ] 4.1 实现密码修改功能
    - 在 AuthService 添加 changePassword 方法
    - 验证当前密码、密码强度
    - _Requirements: 2.1, 2.2_
  - [ ]* 4.2 编写属性测试 - 密码更新 Round-Trip
    - **Property 3: 密码更新 Round-Trip**
    - **Validates: Requirements 2.2**
  - [ ] 4.3 实现会话管理功能
    - 扩展 RefreshToken 实体添加 user_agent、last_used_at
    - 实现 getActiveSessions、revokeSession、revokeAllOtherSessions
    - _Requirements: 2.3, 2.4, 2.5_
  - [ ]* 4.4 编写属性测试 - 会话管理一致性
    - **Property 4: 会话管理一致性**
    - **Validates: Requirements 2.3, 2.4, 2.5**
  - [ ] 4.5 创建 AccountSecurityController
    - 实现 REST API 端点
    - _Requirements: 2.1, 2.3_

- [ ] 5. 前端 - 账户安全面板
  - [ ] 5.1 创建 account-service.ts
    - 实现密码修改、会话管理 API 调用
    - _Requirements: 2.1, 2.3_
  - [ ] 5.2 创建 PasswordChangeForm 组件
    - 当前密码、新密码、确认密码表单
    - 密码强度指示器
    - _Requirements: 2.1, 2.2_
  - [ ] 5.3 创建 SessionManager 组件
    - 显示活跃会话列表
    - 支持登出单个/所有设备
    - _Requirements: 2.3, 2.4, 2.5_
  - [ ] 5.4 集成到 SettingsPage
    - 替换账户安全 Tab 的占位内容
    - _Requirements: 2.1_

- [ ] 6. Checkpoint - 确保所有测试通过
  - Ensure all tests pass, ask the user if questions arise.

## 模块 3: 导入导出格式扩展

- [ ] 7. 后端 - 导入导出扩展
  - [ ] 7.1 实现 TXT 导出功能
    - 按卷/章节顺序导出纯文本
    - _Requirements: 3.1_
  - [ ]* 7.2 编写属性测试 - TXT 导出内容完整性
    - **Property 6: TXT 导出内容完整性**
    - **Validates: Requirements 3.1**
  - [ ] 7.3 实现 ZIP 导出功能
    - 打包 JSON 格式的完整项目数据
    - _Requirements: 3.2_
  - [ ] 7.4 实现 TXT 导入功能
    - 解析文本，按分隔符创建章节
    - _Requirements: 3.3_
  - [ ] 7.5 实现 ZIP 导入功能
    - 解压并还原项目结构
    - _Requirements: 3.4_
  - [ ]* 7.6 编写属性测试 - 导出导入 Round-Trip
    - **Property 5: 导出导入 Round-Trip**
    - **Validates: Requirements 3.2, 3.4**
  - [ ]* 7.7 编写属性测试 - 无效文件错误处理
    - **Property 7: 无效文件导入错误处理**
    - **Validates: Requirements 3.5**
  - [ ] 7.8 扩展 ImportExportController
    - 添加 TXT/ZIP 导入导出端点
    - _Requirements: 3.1, 3.2, 3.3, 3.4_

- [ ] 8. 前端 - 导入导出面板扩展
  - [ ] 8.1 扩展 import-export-service.ts
    - 添加 TXT/ZIP 导入导出方法
    - _Requirements: 3.1, 3.2, 3.3, 3.4_
  - [ ] 8.2 更新 ImportExportPanel 组件
    - 添加格式选择（JSON/TXT/ZIP）
    - 添加文件上传组件
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [ ] 9. Checkpoint - 确保所有测试通过
  - Ensure all tests pass, ask the user if questions arise.

## 模块 4: 主界面卷章节编辑

- [ ] 10. 后端 - 卷章节管理 API 完善
  - [ ] 10.1 完善 Volume/Chapter CRUD API
    - 确保支持创建、重命名、删除、排序
    - _Requirements: 4.1, 4.2, 4.3_
  - [ ] 10.2 实现拖拽排序 API
    - 批量更新 order_index
    - _Requirements: 4.4_
  - [ ]* 10.3 编写属性测试 - 章节排序持久化
    - **Property 8: 章节排序持久化**
    - **Validates: Requirements 4.4**

- [ ] 11. 前端 - 卷章节编辑功能
  - [ ] 11.1 创建 VolumeChapterTree 组件
    - 树形结构展示卷和章节
    - 支持展开/折叠
    - _Requirements: 4.1, 4.2_
  - [ ] 11.2 实现新建卷/章节功能
    - 新建按钮和表单弹窗
    - _Requirements: 4.1, 4.2_
  - [ ] 11.3 实现右键上下文菜单
    - 重命名、删除、移动选项
    - _Requirements: 4.3_
  - [ ] 11.4 实现拖拽排序
    - 使用 dnd-kit 实现拖拽
    - _Requirements: 4.4_
  - [ ] 11.5 显示小说名称
    - 在页面顶部显示当前项目名称
    - _Requirements: 4.5_
  - [ ] 11.6 集成到 EditorPage
    - 替换现有的侧边栏卷章节列表
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

- [ ] 12. Checkpoint - 确保所有测试通过
  - Ensure all tests pass, ask the user if questions arise.

## 模块 5: 左侧边栏优化

- [ ] 13. 后端 - 上下文相关内容 API
  - [ ] 13.1 实现章节相关人物查询
    - 基于章节内容提取涉及的人物
    - _Requirements: 5.2_
  - [ ] 13.2 实现章节相关 Wiki 查询
    - 基于章节内容提取涉及的设定
    - _Requirements: 5.3_
  - [ ]* 13.3 编写属性测试 - 上下文相关内容排序
    - **Property 9: 上下文相关内容排序**
    - **Validates: Requirements 5.2, 5.3**

- [ ] 14. 前端 - 左侧边栏优化
  - [ ] 14.1 重命名"世界观"为"Wiki"
    - 更新所有相关文本和组件
    - _Requirements: 5.1_
  - [ ] 14.2 创建 ContextualSidebar 组件
    - 根据当前章节显示相关内容
    - 相关内容排在前面
    - _Requirements: 5.2, 5.3_
  - [ ] 14.3 添加"查看全部"入口
    - 链接到项目资料管理面板
    - _Requirements: 5.4_
  - [ ] 14.4 实现侧边抽屉展示
    - 点击条目显示详细信息
    - _Requirements: 5.5_
  - [ ] 14.5 集成到 EditorPage
    - 替换现有的侧边栏内容
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

- [ ] 15. Checkpoint - 确保所有测试通过
  - Ensure all tests pass, ask the user if questions arise.

## 模块 6: 项目资料管理

- [ ] 16. 前端 - 项目资料管理面板
  - [ ] 16.1 创建 ResourceManagerPanel 组件
    - Tab 切换不同资料类型
    - _Requirements: 6.1, 6.2_
  - [ ] 16.2 创建 ResourceList 组件
    - 通用列表展示
    - 支持搜索过滤
    - _Requirements: 6.2, 6.3_
  - [ ]* 16.3 编写属性测试 - 资料搜索完整性
    - **Property 10: 资料搜索完整性**
    - **Validates: Requirements 6.3**
  - [ ] 16.4 创建 ResourceEditor 组件
    - 通用编辑表单
    - 支持富文本编辑
    - _Requirements: 6.4, 6.5_
  - [ ] 16.5 实现项目信息编辑
    - 编辑项目标题、简介、创作阶段
    - _Requirements: 6.4, 6.5_
  - [ ] 16.6 添加资料管理入口
    - 在主界面添加入口按钮
    - _Requirements: 6.1_

- [ ] 17. Final Checkpoint - 确保所有测试通过
  - Ensure all tests pass, ask the user if questions arise.
