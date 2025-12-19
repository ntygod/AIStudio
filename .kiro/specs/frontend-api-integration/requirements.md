# Requirements Document

## Introduction

本文档定义了 InkFlow UI V2 前端与 inkflow-backend-v2 后端 API 集成的需求。当前前端使用 Mock 数据，需要接入真实的后端 API 以实现完整的功能。前端基于 React + TypeScript + Vite 构建，后端提供 RESTful API 和 SSE 流式接口。

## Glossary

- **InkFlow_Frontend**: InkFlow UI V2 React 前端应用
- **InkFlow_Backend**: inkflow-backend-v2 Spring Boot 后端服务
- **JWT**: JSON Web Token，用于用户认证
- **SSE**: Server-Sent Events，用于流式响应
- **API_Client**: 封装 HTTP 请求的客户端模块
- **Auth_Store**: 管理认证状态的全局存储
- **Project_Store**: 管理项目数据的全局存储
- **Chat_Service**: 处理 AI 聊天交互的服务模块

## Requirements

### Requirement 1: API 客户端基础设施

**User Story:** As a developer, I want a centralized API client infrastructure, so that all API calls are consistent and maintainable.

#### Acceptance Criteria

1. WHEN the InkFlow_Frontend initializes THEN the API_Client SHALL configure base URL and default headers for all HTTP requests
2. WHEN an API request is made THEN the API_Client SHALL automatically attach JWT token from Auth_Store to Authorization header
3. WHEN an API response returns 401 status THEN the API_Client SHALL attempt token refresh using refresh token
4. IF token refresh fails THEN the API_Client SHALL redirect user to login page and clear Auth_Store
5. WHEN an API response returns error status THEN the API_Client SHALL transform error into standardized error format with message and code

### Requirement 2: 用户认证集成

**User Story:** As a user, I want to register, login, and manage my session, so that I can securely access my projects.

#### Acceptance Criteria

1. WHEN a user submits registration form with valid data THEN the InkFlow_Frontend SHALL call POST /api/auth/register and store returned tokens in Auth_Store
2. WHEN a user submits login form with valid credentials THEN the InkFlow_Frontend SHALL call POST /api/auth/login and store returned tokens in Auth_Store
3. WHEN a user clicks logout THEN the InkFlow_Frontend SHALL call POST /api/auth/logout and clear Auth_Store
4. WHEN the InkFlow_Frontend loads with stored tokens THEN the InkFlow_Frontend SHALL call GET /api/auth/me to validate session
5. WHEN JWT token expires within 5 minutes THEN the InkFlow_Frontend SHALL proactively refresh token using POST /api/auth/refresh

### Requirement 3: 项目管理集成

**User Story:** As a writer, I want to create, view, and manage my novel projects, so that I can organize my creative work.

#### Acceptance Criteria

1. WHEN a user views project list THEN the InkFlow_Frontend SHALL call GET /api/projects and display paginated results
2. WHEN a user creates a new project THEN the InkFlow_Frontend SHALL call POST /api/projects with project details
3. WHEN a user selects a project THEN the InkFlow_Frontend SHALL call GET /api/projects/{id} to load project details
4. WHEN a user updates project settings THEN the InkFlow_Frontend SHALL call PUT /api/projects/{id} with updated data
5. WHEN a user changes creation phase THEN the InkFlow_Frontend SHALL call PATCH /api/projects/{id}/phase with target phase

### Requirement 4: 内容结构集成（卷/章节）

**User Story:** As a writer, I want to manage volumes and chapters, so that I can structure my novel content.

#### Acceptance Criteria

1. WHEN a user views project structure THEN the InkFlow_Frontend SHALL call GET /api/projects/{projectId}/volumes to load volume list
2. WHEN a user creates a volume THEN the InkFlow_Frontend SHALL call POST /api/projects/{projectId}/volumes with volume details
3. WHEN a user views chapter list THEN the InkFlow_Frontend SHALL call GET /api/projects/{projectId}/chapters/volume/{volumeId}
4. WHEN a user creates a chapter THEN the InkFlow_Frontend SHALL call POST /api/projects/{projectId}/chapters with chapter details
5. WHEN a user reorders chapters THEN the InkFlow_Frontend SHALL call PUT /api/projects/{projectId}/chapters/reorder with new order

### Requirement 5: 角色管理集成

**User Story:** As a writer, I want to manage characters and their relationships, so that I can maintain consistent character development.

#### Acceptance Criteria

1. WHEN a user views character list THEN the InkFlow_Frontend SHALL call GET /characters/project/{projectId} to load characters
2. WHEN a user creates a character THEN the InkFlow_Frontend SHALL call POST /characters with character details
3. WHEN a user views character relationships THEN the InkFlow_Frontend SHALL call GET /characters/project/{projectId}/graph
4. WHEN a user adds character relationship THEN the InkFlow_Frontend SHALL call POST /characters/{id}/relationships
5. WHEN a user updates character THEN the InkFlow_Frontend SHALL call PUT /characters/{id} with updated data

### Requirement 6: 知识库（Wiki）集成

**User Story:** As a writer, I want to manage world-building entries, so that I can maintain consistent story settings.

#### Acceptance Criteria

1. WHEN a user views wiki entries THEN the InkFlow_Frontend SHALL call GET /wiki/project/{projectId} to load entries
2. WHEN a user creates wiki entry THEN the InkFlow_Frontend SHALL call POST /wiki with entry details
3. WHEN a user searches wiki THEN the InkFlow_Frontend SHALL call GET /wiki/project/{projectId}/search with keyword
4. WHEN a user filters by type THEN the InkFlow_Frontend SHALL call GET /wiki/project/{projectId}/type/{type}
5. WHEN a user updates wiki entry THEN the InkFlow_Frontend SHALL call PUT /wiki/{id} with updated data

### Requirement 7: AI 聊天集成（SSE 流式）

**User Story:** As a writer, I want to interact with AI assistant in real-time, so that I can get immediate creative assistance.

#### Acceptance Criteria

1. WHEN a user sends chat message THEN the Chat_Service SHALL establish SSE connection to POST /api/v2/agent/chat
2. WHILE SSE connection receives content events THEN the InkFlow_Frontend SHALL append content to chat display incrementally
3. WHEN SSE connection receives done event THEN the Chat_Service SHALL close connection and finalize message
4. IF SSE connection receives error event THEN the Chat_Service SHALL display error message and allow retry
5. WHEN a user requests scene creation THEN the Chat_Service SHALL include sceneType and characterIds in request body

### Requirement 8: 伏笔（PlotLoop）集成

**User Story:** As a writer, I want to track plot threads and foreshadowing, so that I can maintain narrative consistency.

#### Acceptance Criteria

1. WHEN a user views plot loops THEN the InkFlow_Frontend SHALL call GET /plotloops/project/{projectId}
2. WHEN a user creates plot loop THEN the InkFlow_Frontend SHALL call POST /plotloops with loop details
3. WHEN a user resolves plot loop THEN the InkFlow_Frontend SHALL call PATCH /plotloops/{id}/status with resolved status
4. WHEN a user links plot loop to chapter THEN the InkFlow_Frontend SHALL call POST /plotloops/{id}/chapters/{chapterId}

### Requirement 9: 状态管理架构

**User Story:** As a developer, I want organized state management, so that data flows predictably through the application.

#### Acceptance Criteria

1. WHEN the InkFlow_Frontend initializes THEN the state management SHALL create isolated stores for auth, project, content, and chat
2. WHEN API data is fetched THEN the corresponding store SHALL cache data with timestamp for staleness checking
3. WHEN user navigates between views THEN the stores SHALL preserve loaded data to avoid redundant API calls
4. WHEN data mutation succeeds THEN the affected store SHALL optimistically update UI before server confirmation
5. IF server rejects mutation THEN the store SHALL rollback optimistic update and display error

### Requirement 10: 项目导入导出

**User Story:** As a writer, I want to export and import my projects, so that I can backup my work and transfer between devices.

#### Acceptance Criteria

1. WHEN a user clicks export button THEN the InkFlow_Frontend SHALL call GET /api/projects/{id}/export and download JSON file
2. WHEN a user clicks import button THEN the InkFlow_Frontend SHALL display file upload dialog for JSON file
3. WHEN a user uploads valid JSON file THEN the InkFlow_Frontend SHALL call POST /api/projects/import and create new project
4. IF import file format is invalid THEN the InkFlow_Frontend SHALL display validation error message
5. WHEN export or import completes THEN the InkFlow_Frontend SHALL display success notification and refresh project list

### Requirement 11: 错误处理与用户反馈

**User Story:** As a user, I want clear feedback on operations, so that I understand what is happening in the application.

#### Acceptance Criteria

1. WHEN an API call is in progress THEN the InkFlow_Frontend SHALL display loading indicator in relevant UI area
2. WHEN an API call succeeds with mutation THEN the InkFlow_Frontend SHALL display success toast notification
3. WHEN an API call fails THEN the InkFlow_Frontend SHALL display error message with retry option when applicable
4. WHEN network connection is lost THEN the InkFlow_Frontend SHALL display offline indicator and queue mutations
5. WHEN network connection is restored THEN the InkFlow_Frontend SHALL replay queued mutations in order
