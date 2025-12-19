# Requirements Document

## Introduction

本文档记录 InkFlow V2 前后端 API 对接分析结果，以及需要修复的 API 不匹配问题。经过全面分析，前端与后端的对接完成度约为 95%，仅 PlotLoop 模块存在 2 处 API 不匹配需要修复。

## Glossary

- **PlotLoop**: 伏笔管理系统，用于追踪小说中的伏笔埋设和回收
- **Frontend**: InkFlow UI V2，基于 React + TypeScript + Vite
- **Backend**: inkflow-backend-v2，基于 Spring Boot 3.5.x + Java 22
- **API Base URL**: `http://localhost:8080/api`

## API 对接状态总览

### 已完成对接的模块 (100% 匹配)

| 模块 | 前端服务 | 后端控制器 | 状态 |
|------|----------|------------|------|
| 认证 | auth-service.ts | AuthController | ✅ |
| 项目 | project-service.ts | ProjectController | ✅ |
| AI 聊天 | chat-service.ts + sse-client.ts | AgentController | ✅ |
| 卷管理 | content-service.ts | VolumeController | ✅ |
| 章节管理 | content-service.ts | ChapterController | ✅ |
| 角色管理 | asset-service.ts | CharacterController | ✅ |
| Wiki 管理 | asset-service.ts | WikiEntryController | ✅ |
| 伏笔基础 CRUD | asset-service.ts | PlotLoopController | ✅ |

---

## Requirements

### Requirement 1: PlotLoop 状态更新 API 修复

**User Story:** As a 前端开发者, I want 前端 PlotLoop 状态更新方法与后端 API 匹配, so that 用户可以正确地解决、放弃或重新打开伏笔。

#### Acceptance Criteria

1. WHEN 前端调用 `updatePlotLoopStatus(id, 'RESOLVED', chapterId)` THEN the AssetService SHALL 调用后端 `POST /api/plotloops/{id}/resolve` 端点
2. WHEN 前端调用 `updatePlotLoopStatus(id, 'ABANDONED', null, reason)` THEN the AssetService SHALL 调用后端 `POST /api/plotloops/{id}/abandon` 端点
3. WHEN 前端调用 `updatePlotLoopStatus(id, 'OPEN')` THEN the AssetService SHALL 调用后端 `POST /api/plotloops/{id}/reopen` 端点
4. WHEN 状态更新成功 THEN the AssetService SHALL 返回更新后的 PlotLoop 对象

#### 当前问题

**前端调用** (`asset-service.ts`):
```typescript
async updatePlotLoopStatus(id: string, status: string, resolutionChapterId?: string, abandonReason?: string): Promise<PlotLoop> {
  const response = await client.patch(`/plotloops/${id}/status`, {
    status,
    resolutionChapterId,
    abandonReason,
  });
  return response.data;
}
```

**后端实际端点** (`PlotLoopController.java`):
- `POST /api/plotloops/{id}/resolve` - 解决伏笔 (需要 chapterId, chapterOrder)
- `POST /api/plotloops/{id}/abandon` - 放弃伏笔 (需要 reason)
- `POST /api/plotloops/{id}/reopen` - 重新打开伏笔

#### 修复方案

修改前端 `asset-service.ts` 中的 `updatePlotLoopStatus` 方法，根据 status 参数调用不同的后端端点。

---

### Requirement 2: PlotLoop 关联章节 API 补充

**User Story:** As a 用户, I want 将伏笔关联到特定章节, so that 我可以追踪伏笔在哪些章节中被提及。

#### Acceptance Criteria

1. WHEN 前端调用 `linkPlotLoopToChapter(plotLoopId, chapterId)` THEN the Backend SHALL 将伏笔与章节建立关联
2. IF 后端不支持此功能 THEN the Frontend SHALL 移除此方法或标记为未实现

#### 当前问题

**前端调用** (`asset-service.ts`):
```typescript
async linkPlotLoopToChapter(plotLoopId: string, chapterId: string): Promise<void> {
  const client = getApiClient();
  await client.post(`/plotloops/${plotLoopId}/chapters/${chapterId}`, {});
}
```

**后端**: 没有对应的 `POST /api/plotloops/{plotLoopId}/chapters/{chapterId}` 端点

#### 修复方案

方案 A (推荐): 后端添加 `POST /api/plotloops/{id}/chapters/{chapterId}` 端点
方案 B: 前端移除 `linkPlotLoopToChapter` 方法，或标记为 `@deprecated`

---

## 附录：完整 API 对接清单

### 认证模块 (auth-service.ts ↔ AuthController)

| 前端方法 | 前端路径 | 后端路径 | 状态 |
|----------|----------|----------|------|
| login | POST /auth/login | POST /api/auth/login | ✅ |
| register | POST /auth/register | POST /api/auth/register | ✅ |
| logout | POST /auth/logout | POST /api/auth/logout | ✅ |
| refreshToken | POST /auth/refresh | POST /api/auth/refresh | ✅ |
| getCurrentUser | GET /auth/me | GET /api/auth/me | ✅ |

### 项目模块 (project-service.ts ↔ ProjectController)

| 前端方法 | 前端路径 | 后端路径 | 状态 |
|----------|----------|----------|------|
| getProjects | GET /projects | GET /api/projects | ✅ |
| getProject | GET /projects/{id} | GET /api/projects/{id} | ✅ |
| createProject | POST /projects | POST /api/projects | ✅ |
| updateProject | PUT /projects/{id} | PUT /api/projects/{id} | ✅ |
| deleteProject | DELETE /projects/{id} | DELETE /api/projects/{id} | ✅ |
| updatePhase | PATCH /projects/{id}/phase | PATCH /api/projects/{id}/phase | ✅ |
| exportProject | GET /projects/{id}/export | GET /api/projects/{id}/export | ✅ |
| importProject | POST /projects/import | POST /api/projects/import | ✅ |

### AI 聊天模块 (chat-service.ts ↔ AgentController)

| 前端方法 | 前端路径 | 后端路径 | 状态 |
|----------|----------|----------|------|
| streamChat | POST /v2/agent/chat (SSE) | POST /api/v2/agent/chat | ✅ |

### 内容模块 (content-service.ts ↔ VolumeController + ChapterController)

| 前端方法 | 前端路径 | 后端路径 | 状态 |
|----------|----------|----------|------|
| getVolumes | GET /projects/{id}/volumes | GET /api/projects/{id}/volumes | ✅ |
| createVolume | POST /projects/{id}/volumes | POST /api/projects/{id}/volumes | ✅ |
| updateVolume | PUT /projects/{id}/volumes/{vid} | PUT /api/projects/{id}/volumes/{vid} | ✅ |
| deleteVolume | DELETE /projects/{id}/volumes/{vid} | DELETE /api/projects/{id}/volumes/{vid} | ✅ |
| getChapters | GET /projects/{id}/chapters/volume/{vid} | GET /api/projects/{id}/chapters/volume/{vid} | ✅ |
| getChapter | GET /projects/{id}/chapters/{cid} | GET /api/projects/{id}/chapters/{cid} | ✅ |
| createChapter | POST /projects/{id}/chapters | POST /api/projects/{id}/chapters | ✅ |
| updateChapter | PUT /projects/{id}/chapters/{cid} | PUT /api/projects/{id}/chapters/{cid} | ✅ |
| deleteChapter | DELETE /projects/{id}/chapters/{cid} | DELETE /api/projects/{id}/chapters/{cid} | ✅ |
| getChapterContent | GET /projects/{id}/chapters/{cid}/content | GET /api/projects/{id}/chapters/{cid}/content | ✅ |
| saveChapterContent | PUT /projects/{id}/chapters/{cid}/content | PUT /api/projects/{id}/chapters/{cid}/content | ✅ |

### 角色模块 (asset-service.ts ↔ CharacterController)

| 前端方法 | 前端路径 | 后端路径 | 状态 |
|----------|----------|----------|------|
| getCharacters | GET /characters/project/{pid} | GET /api/characters/project/{pid} | ✅ |
| getCharacter | GET /characters/{id} | GET /api/characters/{id} | ✅ |
| createCharacter | POST /characters | POST /api/characters | ✅ |
| updateCharacter | PUT /characters/{id} | PUT /api/characters/{id} | ✅ |
| deleteCharacter | DELETE /characters/{id} | DELETE /api/characters/{id} | ✅ |
| getRelationshipGraph | GET /characters/project/{pid}/graph | GET /api/characters/project/{pid}/graph | ✅ |
| addRelationship | POST /characters/{id}/relationships | POST /api/characters/{id}/relationships | ✅ |

### Wiki 模块 (asset-service.ts ↔ WikiEntryController)

| 前端方法 | 前端路径 | 后端路径 | 状态 |
|----------|----------|----------|------|
| getWikiEntries | GET /wiki/project/{pid} | GET /api/wiki/project/{pid} | ✅ |
| searchWikiEntries | GET /wiki/project/{pid}/search | GET /api/wiki/project/{pid}/search | ✅ |
| getWikiEntriesByType | GET /wiki/project/{pid}/type/{type} | GET /api/wiki/project/{pid}/type/{type} | ✅ |
| createWikiEntry | POST /wiki | POST /api/wiki | ✅ |
| updateWikiEntry | PUT /wiki/{id} | PUT /api/wiki/{id} | ✅ |
| deleteWikiEntry | DELETE /wiki/{id} | DELETE /api/wiki/{id} | ✅ |

### PlotLoop 模块 (asset-service.ts ↔ PlotLoopController)

| 前端方法 | 前端路径 | 后端路径 | 状态 |
|----------|----------|----------|------|
| getPlotLoops | GET /plotloops/project/{pid} | GET /api/plotloops/project/{pid} | ✅ |
| createPlotLoop | POST /plotloops | POST /api/plotloops | ✅ |
| updatePlotLoop | PUT /plotloops/{id} | PUT /api/plotloops/{id} | ✅ |
| deletePlotLoop | DELETE /plotloops/{id} | DELETE /api/plotloops/{id} | ✅ |
| updatePlotLoopStatus | PATCH /plotloops/{id}/status | N/A | ❌ 需修复 |
| linkPlotLoopToChapter | POST /plotloops/{id}/chapters/{cid} | N/A | ❌ 需修复 |

---

## 总结

| 指标 | 数值 |
|------|------|
| 已对接 API 数量 | 35+ |
| 需修复 API 数量 | 2 |
| 对接完成度 | ~95% |
