# Requirements Document

## Introduction

本文档定义了 InkFlow 2.0 UI 增强功能的需求，涵盖以下核心改进领域：
1. AI 模型场景化配置 - 支持不同场景使用不同的服务商和模型
2. 账户安全功能 - 密码修改、登录设备管理等
3. 导入导出格式扩展 - 支持 TXT、压缩包等更多格式
4. 主界面编辑功能 - 卷章节管理、小说信息展示
5. 左侧边栏优化 - Wiki 重命名、上下文相关内容展示

## Glossary

- **InkFlow System**: AI 原生小说创作平台的核心系统
- **Task Type (任务类型)**: AI 功能的任务类型，如内容生成、大纲规划、一致性检查、角色设计等
- **Provider (服务商)**: AI 模型提供商，如 OpenAI、DeepSeek、Ollama 等
- **Model (模型)**: 具体的 AI 模型，如 gpt-4-turbo、deepseek-chat 等
- **Volume (卷)**: 小说的大章节划分单位
- **Chapter (章节)**: 小说的基本内容单位
- **Wiki**: 小说世界观设定的知识库条目
- **Character (人物)**: 小说中的角色设定
- **Plot Loop (伏笔)**: 小说中的伏笔/悬念设定

## Requirements

### Requirement 1: AI 任务模型配置

**User Story:** As a 创作者, I want to 为不同类型的 AI 任务配置不同的服务商和模型, so that 我可以根据任务特点选择最合适的 AI 能力。

#### Acceptance Criteria

1. WHEN 用户访问 AI 服务商设置页面 THEN THE InkFlow System SHALL 显示任务类型-模型映射配置界面
2. WHEN 用户为某个任务类型选择服务商和模型 THEN THE InkFlow System SHALL 保存该任务类型的配置到数据库
3. WHEN 系统执行某类型的 AI 任务 THEN THE InkFlow System SHALL 使用该任务类型配置的服务商和模型
4. WHEN 某个任务类型未配置专属模型 THEN THE InkFlow System SHALL 使用默认服务商的默认模型
5. WHERE 用户配置了本地模型（如 Ollama）THEN THE InkFlow System SHALL 支持将敏感内容路由到本地模型

### Requirement 2: 账户安全功能

**User Story:** As a 用户, I want to 管理我的账户安全设置, so that 我可以保护我的账户和数据安全。

#### Acceptance Criteria

1. WHEN 用户点击修改密码 THEN THE InkFlow System SHALL 显示密码修改表单并验证当前密码
2. WHEN 用户提交新密码 THEN THE InkFlow System SHALL 验证密码强度并更新密码
3. WHEN 用户查看登录设备 THEN THE InkFlow System SHALL 显示所有活跃的登录会话列表
4. WHEN 用户选择登出某个设备 THEN THE InkFlow System SHALL 撤销该设备的刷新令牌
5. WHEN 用户选择登出所有设备 THEN THE InkFlow System SHALL 撤销除当前设备外的所有刷新令牌

### Requirement 3: 导入导出格式扩展

**User Story:** As a 创作者, I want to 使用多种格式导入导出我的小说, so that 我可以方便地备份、分享和在不同平台使用我的作品。

#### Acceptance Criteria

1. WHEN 用户选择导出为 TXT 格式 THEN THE InkFlow System SHALL 生成纯文本文件包含小说正文内容
2. WHEN 用户选择导出为 ZIP 格式 THEN THE InkFlow System SHALL 打包所有项目资源（正文、设定、人物、Wiki）
3. WHEN 用户选择导入 TXT 文件 THEN THE InkFlow System SHALL 解析文本并创建章节结构
4. WHEN 用户选择导入 ZIP 文件 THEN THE InkFlow System SHALL 解压并还原完整项目结构
5. WHERE 导入的文件格式不正确 THEN THE InkFlow System SHALL 显示明确的错误提示并中止导入

### Requirement 4: 主界面卷章节编辑功能

**User Story:** As a 创作者, I want to 在主界面直接管理卷和章节, so that 我可以高效地组织小说结构。

#### Acceptance Criteria

1. WHEN 用户点击新建卷按钮 THEN THE InkFlow System SHALL 显示卷创建表单并在确认后创建新卷
2. WHEN 用户点击新建章节按钮 THEN THE InkFlow System SHALL 在当前卷下创建新章节
3. WHEN 用户右键点击卷或章节 THEN THE InkFlow System SHALL 显示上下文菜单（重命名、删除、移动）
4. WHEN 用户拖拽章节 THEN THE InkFlow System SHALL 更新章节排序并持久化到数据库
5. WHEN 用户进入编辑页面 THEN THE InkFlow System SHALL 在页面顶部显示当前小说名称

### Requirement 5: 左侧边栏优化

**User Story:** As a 创作者, I want to 在左侧边栏看到与当前章节相关的人物和设定, so that 我可以快速参考相关信息进行创作。

#### Acceptance Criteria

1. WHEN 用户查看左侧边栏 THEN THE InkFlow System SHALL 将"世界观"标签显示为"Wiki"
2. WHEN 用户编辑某个章节 THEN THE InkFlow System SHALL 在人物列表中优先显示该章节涉及的人物
3. WHEN 用户编辑某个章节 THEN THE InkFlow System SHALL 在 Wiki 列表中优先显示该章节涉及的设定
4. WHEN 用户需要查看所有项目资料 THEN THE InkFlow System SHALL 提供入口访问完整的人物/Wiki/伏笔列表
5. WHEN 用户点击人物/Wiki/伏笔条目 THEN THE InkFlow System SHALL 以侧边抽屉形式展示详细信息

### Requirement 6: 项目资料管理入口

**User Story:** As a 创作者, I want to 有一个统一的入口管理所有项目资料, so that 我可以方便地查看和编辑完整的设定信息。

#### Acceptance Criteria

1. WHEN 用户点击项目资料入口 THEN THE InkFlow System SHALL 显示项目资料管理面板
2. WHEN 用户在资料管理面板中 THEN THE InkFlow System SHALL 提供人物、Wiki、伏笔的完整列表视图
3. WHEN 用户在资料管理面板中搜索 THEN THE InkFlow System SHALL 支持按名称和内容搜索资料
4. WHEN 用户在资料管理面板中新建条目 THEN THE InkFlow System SHALL 提供对应类型的创建表单
5. WHEN 用户在资料管理面板中编辑条目 THEN THE InkFlow System SHALL 支持富文本编辑和关联设置
