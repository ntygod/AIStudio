# Implementation Plan

## AI服务商与功能模型配置分离实现计划

> 注意：项目尚未上线，不需要考虑兼容性，可以直接删除旧代码和旧表结构。

- [x] 1. 创建新的数据结构





  - [x] 1.1 创建数据库迁移脚本


    - 删除旧的 user_api_keys 表（或重命名备份）
    - 创建 provider_configs 表
    - 创建 functional_model_configs 表
    - _Requirements: 5.1_
  - [x] 1.2 创建 ProviderType 枚举


    - 定义 GEMINI, DEEPSEEK, OPENAI, CUSTOM 四种类型
    - _Requirements: 1.4_
  - [x] 1.3 创建 ProviderConfig 实体类


    - 只包含 providerType, encryptedKey, keyHint, baseUrl, isConfigured
    - _Requirements: 1.1, 1.3_
  - [x] 1.4 创建 FunctionalModelConfig 实体类


    - 包含 scene, providerType, model
    - _Requirements: 2.3, 2.4_
  - [ ]* 1.5 编写属性测试：服务商配置不包含模型信息
    - **Property 1: 服务商配置不包含模型信息**
    - **Validates: Requirements 1.1**

- [x] 2. 实现服务商配置服务





  - [x] 2.1 删除旧的 ApiKeyService 和 ApiKey 实体


    - 删除 ApiKey.java
    - 删除 ApiKeyRepository.java
    - 删除 ApiKeyService.java
    - 删除 ApiKeyController.java
    - 删除相关 DTO
    - _Requirements: 7.1_
  - [x] 2.2 创建 ProviderConfigRepository


    - _Requirements: 1.3, 1.5_
  - [x] 2.3 创建 ProviderConfigService


    - saveConfig（只保存 API Key 和 Base URL）
    - getAllConfigs
    - getConfiguredProviders
    - getConnectionInfo
    - deleteConfig（带引用检查）
    - _Requirements: 1.1, 1.2, 1.3, 1.5, 5.4_
  - [ ]* 2.4 编写属性测试：服务商配置验证和状态一致性
    - **Property 2, 3, 4**
    - **Validates: Requirements 1.2, 1.3, 1.4**

- [x] 3. 实现功能模型配置服务






  - [x] 3.1 创建 FunctionalModelConfigRepository

    - _Requirements: 2.3_

  - [x] 3.2 创建 FunctionalModelConfigService

    - saveConfig
    - getAllConfigs
    - getConfigByScene
    - getSupportedModels（各服务商支持的模型列表）
    - _Requirements: 2.1, 2.2, 2.3, 2.4_
  - [ ]* 3.3 编写属性测试：已配置服务商过滤和配置完整性
    - **Property 5, 6, 7**
    - **Validates: Requirements 2.1, 2.3, 2.4**

- [x] 4. Checkpoint - 确保所有测试通过





  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. 实现AI配置解析器





  - [x] 5.1 创建 ServiceSceneMapping 配置


    - 定义业务服务到场景的映射
    - _Requirements: 6.1-6.5_
  - [x] 5.2 创建 AIConfigResolver 服务


    - resolveConfig（根据场景获取完整配置）
    - getSceneForService（根据服务类型获取场景）
    - 错误处理（场景未配置、服务商未配置）
    - _Requirements: 3.1-3.5_
  - [ ]* 5.3 编写属性测试：配置解析和错误处理
    - **Property 8, 9, 10, 11, 15**
    - **Validates: Requirements 3.2-3.5, 6.1-6.4**

- [x] 6. 修改 AIService 集成新配置系统





  - [x] 6.1 修改 AIService 使用 AIConfigResolver


    - 删除旧的配置获取逻辑
    - 根据场景自动获取配置
    - _Requirements: 3.1-3.3, 7.3, 7.4_
  - [x] 6.2 更新各业务服务的AI调用


    - 确保传递正确的场景信息
    - 删除不再需要的 provider 参数传递
    - _Requirements: 6.1-6.4_

- [x] 7. Checkpoint - 确保所有测试通过
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. 创建API控制器
  - [x] 8.1 创建 ProviderConfigController
    - POST /api/provider-configs
    - GET /api/provider-configs
    - GET /api/provider-configs/configured
    - DELETE /api/provider-configs/{providerType}
    - GET /api/provider-configs/{providerType}/validate
    - _Requirements: 1.1, 1.3, 1.5, 5.4_
  - [x] 8.2 创建 FunctionalModelConfigController
    - POST /api/functional-model-configs
    - GET /api/functional-model-configs
    - GET /api/functional-model-configs/{scene}
    - GET /api/functional-model-configs/models/{providerType}
    - PUT /api/functional-model-configs/batch
    - DELETE /api/functional-model-configs/{scene}
    - _Requirements: 2.1-2.4_
  - [x] 8.3 创建 DTO 类
    - DTOs already exist from previous tasks
    - _Requirements: 1.1, 2.3_

- [x] 9. 实现配置一致性检查


  - [x] 9.1 创建 ConfigConsistencyService

    - 检查功能模型配置引用的服务商是否都已配置
    - _Requirements: 5.5_
  - [ ]* 9.2 编写属性测试：配置一致性
    - **Property 12, 13, 14**
    - **Validates: Requirements 5.2, 5.4, 5.5**

- [x] 10. 清理旧代码


  - [x] 10.1 删除旧的 apikey 模块相关代码

    - 删除 ApiKeyConfigValidator
    - 删除相关测试文件
    - _Requirements: 7.1_
  - [x] 10.2 更新 SecurityConfig

    - 更新 API 路径配置
    - _Requirements: 7.1_

- [x] 11. Checkpoint - 确保所有测试通过



  - Ensure all tests pass, ask the user if questions arise.

- [x] 12. 前端适配
  - [x] 12.1 更新前端服务商配置组件
    - 创建 providerConfigService.ts - 服务商配置 API 服务
    - 创建 AISettingsPanel.tsx - 新版 AI 配置面板组件
    - 服务商配置只显示 API Key 和 Base URL，不涉及模型选择
    - _Requirements: 4.1, 4.2_
  - [x] 12.2 更新前端功能模型配置组件
    - 创建 functionalModelConfigService.ts - 功能模型配置 API 服务
    - 服务商下拉框只显示已配置的服务商
    - 选择服务商后动态加载模型列表
    - _Requirements: 4.3, 4.4, 4.5_
  - [x] 12.3 更新前端 API 调用
    - 更新 AppSettings.tsx 添加新版 AI 配置区域
    - 创建 AISettingsDialog.tsx - 独立的 AI 配置对话框
    - 更新 services/api/index.ts 导出新服务
    - _Requirements: 4.1-4.5_

- [x] 13. Final Checkpoint - 确保所有测试通过
  - Backend tests pass: AIServiceSceneIntegrationTest (4 tests), Provider/AIConfig tests
  - Frontend services created and TypeScript compiles without errors
  - All new API services properly exported
