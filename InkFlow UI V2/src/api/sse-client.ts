/**
 * SSE 客户端
 * 处理 Server-Sent Events 流式响应
 * 
 * Requirements: 9.1, 9.2, 9.3
 */

import type { ThoughtEvent, ToolEvent } from '@/types';
import { tokenManager } from './token-manager';

// ============ SSE 事件类型常量 ============

export const SSE_EVENT_TYPES = {
  // 内容相关
  CONTENT: 'content',
  THOUGHT: 'thought',
  TOOL: 'tool',
  ERROR: 'error',
  DONE: 'done',
  // 一致性相关 (Requirements: 9.1)
  PREFLIGHT_RESULT: 'preflight_result',
  CONSISTENCY_WARNING: 'consistency_warning',
  CONSISTENCY_CHECK_COMPLETE: 'consistency_check_complete',
  // 演进相关 (Requirements: 9.2)
  EVOLUTION_UPDATE: 'evolution_update',
  SNAPSHOT_CREATED: 'snapshot_created',
} as const;

export type SSEEventType = typeof SSE_EVENT_TYPES[keyof typeof SSE_EVENT_TYPES];

// ============ 一致性相关事件类型 ============

export type Severity = 'ERROR' | 'WARNING' | 'INFO';
export type EntityType = 'CHARACTER' | 'WIKI_ENTRY' | 'PLOT_LOOP' | 'CHAPTER';
export type WarningType = 'NAME_CONFLICT' | 'MISSING_FIELD' | 'RELATIONSHIP_INCONSISTENCY' | 'TIMELINE_CONFLICT' | 'PLOT_HOLE' | 'CHARACTER_INCONSISTENCY';

/**
 * 预检结果事件
 * Requirements: 9.3
 */
export interface PreflightResultEvent {
  type: 'preflight_result';
  passed: boolean;
  warnings: PreflightWarning[];
  characterStates?: Record<string, Record<string, unknown>>;
}

export interface PreflightWarning {
  entityId: string;
  entityName: string;
  entityType: EntityType;
  severity: Severity;
  message: string;
  suggestion?: string;
}

/**
 * 一致性警告事件
 * Requirements: 9.1
 */
export interface ConsistencyWarningEvent {
  type: 'consistency_warning';
  id: string;
  projectId: string;
  entityId: string;
  entityType: EntityType;
  entityName: string;
  warningType: WarningType;
  severity: Severity;
  description: string;
  suggestion?: string;
}

/**
 * 一致性检查完成事件
 */
export interface ConsistencyCheckCompleteEvent {
  type: 'consistency_check_complete';
  totalWarnings: number;
  errors: number;
  warnings: number;
  info: number;
}

// ============ 演进相关事件类型 ============

export type ChangeType = 'INITIAL' | 'UPDATE' | 'MAJOR_CHANGE';
export type EvolutionEntityType = 'CHARACTER' | 'WIKI_ENTRY' | 'RELATIONSHIP';

/**
 * 演进更新事件
 * Requirements: 9.2
 */
export interface EvolutionUpdateEvent {
  type: 'evolution_update';
  entityId: string;
  entityType: EvolutionEntityType;
  entityName: string;
  chapterId?: string;
  chapterOrder?: number;
  changeType: ChangeType;
  changeSummary?: string;
}

/**
 * 快照创建事件
 * Requirements: 9.2
 */
export interface SnapshotCreatedEvent {
  type: 'snapshot_created';
  snapshotId: string;
  timelineId: string;
  entityId: string;
  entityType: EvolutionEntityType;
  chapterId?: string;
  chapterOrder?: number;
  isKeyframe: boolean;
  stateData: Record<string, unknown>;
}

// ============ SSE 客户端选项 ============

export interface SSEClientOptions {
  onContent: (content: string) => void;
  onThought?: (thought: ThoughtEvent) => void;
  onTool?: (tool: ToolEvent) => void;
  onDone: () => void;
  onError: (error: Error) => void;
  // 一致性相关回调 (Requirements: 9.1, 9.3)
  onPreflightResult?: (result: PreflightResultEvent) => void;
  onConsistencyWarning?: (warning: ConsistencyWarningEvent) => void;
  onConsistencyCheckComplete?: (result: ConsistencyCheckCompleteEvent) => void;
  // 演进相关回调 (Requirements: 9.2)
  onEvolutionUpdate?: (update: EvolutionUpdateEvent) => void;
  onSnapshotCreated?: (snapshot: SnapshotCreatedEvent) => void;
}

export interface SSEEvent {
  event: string;
  data: string;
}

export class SSEClient {
  private controller: AbortController | null = null;
  private baseUrl: string;
  private timeout: number;

  constructor(baseUrl: string, timeout: number = 30000) {
    this.baseUrl = baseUrl;
    this.timeout = timeout;
  }

  /**
   * 建立 SSE 连接
   */
  async connect(
    path: string,
    body: unknown,
    options: SSEClientOptions
  ): Promise<void> {
    // 如果有现有连接，先中断
    this.abort();

    // 刷新 token（如果需要）
    await tokenManager.refreshIfNeeded();

    const token = tokenManager.getAccessToken();
    if (!token) {
      options.onError(new Error('未登录'));
      return;
    }

    this.controller = new AbortController();
    const url = `${this.baseUrl}${path}`;

    // 设置超时
    const timeoutId = setTimeout(() => {
      this.abort();
      options.onError(new Error('连接超时'));
    }, this.timeout);

    try {
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`,
          'Accept': 'text/event-stream',
        },
        body: JSON.stringify(body),
        signal: this.controller.signal,
      });

      clearTimeout(timeoutId);

      if (!response.ok) {
        const errorText = await response.text();
        options.onError(new Error(errorText || `HTTP ${response.status}`));
        return;
      }

      if (!response.body) {
        options.onError(new Error('响应体为空'));
        return;
      }

      // 处理 SSE 流
      await this.processStream(response.body, options);
    } catch (error) {
      clearTimeout(timeoutId);

      if (error instanceof DOMException && error.name === 'AbortError') {
        // 用户主动中断，不报错
        return;
      }

      options.onError(
        error instanceof Error ? error : new Error('连接失败')
      );
    }
  }

  /**
   * 处理 SSE 流
   */
  private async processStream(
    body: ReadableStream<Uint8Array>,
    options: SSEClientOptions
  ): Promise<void> {
    const reader = body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    try {
      while (true) {
        const { done, value } = await reader.read();

        if (done) {
          // 处理缓冲区中剩余的数据
          if (buffer.trim()) {
            this.processBuffer(buffer, options);
          }
          options.onDone();
          break;
        }

        buffer += decoder.decode(value, { stream: true });

        // 按行处理
        const lines = buffer.split('\n');
        buffer = lines.pop() || ''; // 保留最后一个不完整的行

        for (const line of lines) {
          this.processLine(line, options);
        }
      }
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        return;
      }
      throw error;
    } finally {
      reader.releaseLock();
    }
  }

  /**
   * 处理缓冲区
   */
  private processBuffer(buffer: string, options: SSEClientOptions): void {
    const lines = buffer.split('\n');
    for (const line of lines) {
      this.processLine(line, options);
    }
  }

  /**
   * 处理单行 SSE 数据
   */
  private processLine(line: string, options: SSEClientOptions): void {
    const trimmedLine = line.trim();
    
    if (!trimmedLine || trimmedLine.startsWith(':')) {
      // 空行或注释，忽略
      return;
    }

    // 解析 SSE 事件
    if (trimmedLine.startsWith('event:')) {
      // 事件类型行，暂存
      return;
    }

    if (trimmedLine.startsWith('data:')) {
      const data = trimmedLine.slice(5).trim();
      this.handleData(data, options);
    }
  }

  /**
   * 处理数据
   * 
   * Requirements: 9.1, 9.2, 9.3
   */
  private handleData(data: string, options: SSEClientOptions): void {
    // 检查是否为完成标记
    if (data === '[DONE]') {
      options.onDone();
      return;
    }

    // 尝试解析 JSON
    try {
      const parsed = JSON.parse(data);

      // 根据数据类型分发
      switch (parsed.type) {
        // 内容相关事件
        case SSE_EVENT_TYPES.CONTENT:
          options.onContent(parsed.content || parsed.data || '');
          break;
          
        case SSE_EVENT_TYPES.THOUGHT:
          if (options.onThought) {
            options.onThought(parsed as ThoughtEvent);
          }
          break;
          
        case SSE_EVENT_TYPES.TOOL:
          if (options.onTool) {
            options.onTool(parsed as ToolEvent);
          }
          break;
          
        case SSE_EVENT_TYPES.ERROR:
          options.onError(new Error(parsed.message || '未知错误'));
          break;
          
        // 一致性相关事件 (Requirements: 9.1, 9.3)
        case SSE_EVENT_TYPES.PREFLIGHT_RESULT:
          if (options.onPreflightResult) {
            options.onPreflightResult(parsed as PreflightResultEvent);
          }
          break;
          
        case SSE_EVENT_TYPES.CONSISTENCY_WARNING:
          if (options.onConsistencyWarning) {
            options.onConsistencyWarning(parsed as ConsistencyWarningEvent);
          }
          break;
          
        case SSE_EVENT_TYPES.CONSISTENCY_CHECK_COMPLETE:
          if (options.onConsistencyCheckComplete) {
            options.onConsistencyCheckComplete(parsed as ConsistencyCheckCompleteEvent);
          }
          break;
          
        // 演进相关事件 (Requirements: 9.2)
        case SSE_EVENT_TYPES.EVOLUTION_UPDATE:
          if (options.onEvolutionUpdate) {
            options.onEvolutionUpdate(parsed as EvolutionUpdateEvent);
          }
          break;
          
        case SSE_EVENT_TYPES.SNAPSHOT_CREATED:
          if (options.onSnapshotCreated) {
            options.onSnapshotCreated(parsed as SnapshotCreatedEvent);
          }
          break;
          
        default:
          // 处理没有 type 字段的情况
          if (parsed.content !== undefined) {
            options.onContent(parsed.content || '');
          } else if (typeof parsed === 'string') {
            // 纯文本内容
            options.onContent(parsed);
          } else if (parsed.data) {
            // 包装的数据
            options.onContent(parsed.data);
          }
          break;
      }
    } catch {
      // 非 JSON 数据，作为纯文本内容处理
      if (data && data !== '[DONE]') {
        options.onContent(data);
      }
    }
  }

  /**
   * 中断连接
   */
  abort(): void {
    if (this.controller) {
      this.controller.abort();
      this.controller = null;
    }
  }

  /**
   * 检查是否正在连接
   */
  isConnected(): boolean {
    return this.controller !== null;
  }
}

// 创建默认实例
let sseClientInstance: SSEClient | null = null;

export function initSSEClient(baseUrl: string, timeout?: number): SSEClient {
  sseClientInstance = new SSEClient(baseUrl, timeout);
  return sseClientInstance;
}

export function getSSEClient(): SSEClient {
  if (!sseClientInstance) {
    throw new Error('SSEClient not initialized. Call initSSEClient first.');
  }
  return sseClientInstance;
}
