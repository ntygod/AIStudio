# InkFlow UI V2

InkFlow 2.0 前端应用 - AI 辅助小说创作平台的用户界面。

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| React | 18.3.x | UI 框架 |
| TypeScript | 5.5.x | 类型安全 |
| Vite | 6.3.x | 构建工具 |
| Tailwind CSS | 4.1.x | 样式框架 |
| Zustand | 4.5.x | 状态管理 |
| TanStack Query | 5.x | 服务端状态管理 |
| shadcn/ui | - | UI 组件库 |
| Vitest | 1.6.x | 测试框架 |
| fast-check | 3.19.x | 属性测试 |

## 快速开始

### 环境要求

- Node.js >= 18.0.0
- npm >= 9.0.0 或 pnpm >= 8.0.0

### 安装依赖

```bash
npm install
# 或
pnpm install
```

### 环境配置

复制环境变量模板并配置：

```bash
cp .env.example .env.development
```

主要环境变量：

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `VITE_API_BASE_URL` | 后端 API 地址 | `http://localhost:8080` |
| `VITE_WS_URL` | WebSocket 地址 | `ws://localhost:8080` |

### 启动开发服务器

```bash
npm run dev
```

访问 http://localhost:5173

### 构建生产版本

```bash
npm run build
```

构建产物位于 `dist/` 目录。

### 运行测试

```bash
# 运行所有测试
npm run test

# 单次运行（CI 模式）
npm run test:run

# 代码检查
npm run lint
```

## 项目结构

```
src/
├── api/                    # API 客户端层
│   ├── client.ts          # HTTP 客户端封装
│   ├── sse-client.ts      # SSE 流式客户端
│   ├── token-manager.ts   # Token 管理
│   └── __tests__/         # API 层测试
├── app/                    # 应用层
│   ├── App.tsx            # 根组件
│   ├── components/        # UI 组件
│   │   ├── consistency/   # 一致性警告组件
│   │   ├── copilot/       # AI Copilot 组件
│   │   ├── editor/        # 编辑器组件
│   │   ├── evolution/     # 演进时间线组件
│   │   ├── layout/        # 布局组件
│   │   ├── loading/       # 加载状态组件
│   │   ├── project/       # 项目管理组件
│   │   ├── relationship/  # 关系图谱组件
│   │   ├── settings/      # 设置页面组件
│   │   ├── sidebar/       # 侧边栏组件
│   │   ├── status/        # 状态指示器组件
│   │   └── ui/            # 基础 UI 组件 (shadcn)
│   └── pages/             # 页面组件
├── hooks/                  # 自定义 Hooks
├── lib/                    # 工具库
│   ├── indexed-db.ts      # IndexedDB 封装
│   ├── offline-manager.ts # 离线管理
│   ├── sync-service.ts    # 同步服务
│   └── toast.ts           # Toast 通知
├── services/              # 业务服务层
│   ├── asset-service.ts   # 资产服务
│   ├── auth-service.ts    # 认证服务
│   ├── chat-service.ts    # 聊天服务
│   ├── content-service.ts # 内容服务
│   └── ...
├── stores/                # Zustand 状态管理
│   ├── auth-store.ts      # 认证状态
│   ├── chat-store.ts      # 聊天状态
│   ├── content-store.ts   # 内容状态
│   ├── project-store.ts   # 项目状态
│   ├── ui-store.ts        # UI 状态
│   └── __tests__/         # Store 测试
├── styles/                # 全局样式
├── types/                 # TypeScript 类型定义
└── main.tsx               # 应用入口
```

## 核心功能

### 三栏布局

- 左侧边栏：项目导航、资产管理、创作进度
- 中央编辑器：TipTap 富文本编辑器
- 右侧面板：AI Copilot、一致性检查、技能选择

### AI Copilot

- 实时流式对话 (SSE)
- 多 Agent 支持
- 技能槽系统
- 产物卡片展示

### 离线支持

- IndexedDB 本地缓存
- 离线编辑队列
- 自动同步
- 冲突解决对话框

### 一致性检查

- 实时警告指示器
- 警告详情面板
- 一键解决/忽略

### 角色演进

- 时间线可视化
- 状态对比
- 手动快照

## 状态管理

使用 Zustand 进行状态管理，主要 Store：

| Store | 说明 |
|-------|------|
| `authStore` | 用户认证状态 |
| `projectStore` | 项目列表和当前项目 |
| `contentStore` | 卷、章节、内容 |
| `chatStore` | AI 对话消息 |
| `assetStore` | 角色、设定、伏笔 |
| `uiStore` | 布局、主题、响应式 |
| `consistencyStore` | 一致性警告 |
| `evolutionStore` | 演进时间线 |
| `progressStore` | 创作进度统计 |

## API 集成

### HTTP 客户端

```typescript
import { apiClient } from '@/api/client';

// GET 请求
const projects = await apiClient.get<Project[]>('/api/projects');

// POST 请求
const newProject = await apiClient.post<Project>('/api/projects', { title: '新项目' });
```

### SSE 流式请求

```typescript
import { sseClient } from '@/api/sse-client';

sseClient.stream('/api/agent/chat', {
  body: { message: '帮我写一段描写' },
  onMessage: (data) => console.log(data),
  onError: (error) => console.error(error),
  onComplete: () => console.log('完成'),
});
```

### 离线支持

```typescript
import { useOffline, saveContentWithOfflineSupport } from '@/lib';

// 使用离线状态
const { isOnline, syncStatus, pendingChangesCount } = useOffline();

// 带离线支持的保存
await saveContentWithOfflineSupport(
  chapterId,
  projectId,
  content,
  () => contentService.saveChapterContent(projectId, chapterId, { content })
);
```

## 测试

### 单元测试

```typescript
import { describe, it, expect } from 'vitest';

describe('MyComponent', () => {
  it('should render correctly', () => {
    // ...
  });
});
```

### 属性测试

```typescript
import * as fc from 'fast-check';

/**
 * Feature: ui-layout-redesign, Property 2: LocalStorage persistence round-trip
 * Validates: Requirements 1.5
 */
test('localStorage persistence round-trip', () => {
  fc.assert(
    fc.property(fc.record({ theme: fc.constantFrom('light', 'dark') }), (state) => {
      localStorage.setItem('ui-state', JSON.stringify(state));
      const restored = JSON.parse(localStorage.getItem('ui-state')!);
      expect(restored).toEqual(state);
    }),
    { numRuns: 100 }
  );
});
```

## 开发指南

### 添加新组件

1. 在 `src/app/components/` 下创建组件目录
2. 创建组件文件和 `index.ts` 导出
3. 添加必要的类型定义
4. 编写测试

### 添加新 Store

1. 在 `src/stores/` 下创建 store 文件
2. 定义 State 和 Actions 接口
3. 使用 `create` 创建 store
4. 在 `src/stores/index.ts` 中导出

### 添加新服务

1. 在 `src/services/` 下创建服务文件
2. 使用 `apiClient` 进行 API 调用
3. 在 `src/services/index.ts` 中导出

## 设计资源

- Figma 设计稿: https://www.figma.com/design/BoOlUV0npwzGt2eOp66jj9/InkFlow-UI-Design-Specification
- 设计指南: `guidelines/Guidelines.md`

## 相关文档

- [后端 README](../inkflow-backend-v2/README.md)
- [功能概览](../docs/FEATURE_OVERVIEW.md)
- [RAG API 文档](../docs/RAG_API_DOCUMENTATION.md)

## License

Private - All rights reserved.
