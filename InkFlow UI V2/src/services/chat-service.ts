/**
 * 聊天服务
 * 封装 AI 聊天相关的 API 调用（SSE 流式）
 * 
 * Requirements: 4.4, 9.1, 9.2, 9.3
 */

import type { ChatRequest, ThoughtEvent, ToolEvent } from '@/types';
import { 
  getSSEClient, 
  type SSEClientOptions,
  type PreflightResultEvent,
  type ConsistencyWarningEvent,
  type ConsistencyCheckCompleteEvent,
  type EvolutionUpdateEvent,
  type SnapshotCreatedEvent,
} from '@/api';

export interface SceneRequest extends ChatRequest {
  sceneType: string;
  chapterId?: string;
  characterIds?: string[];
  consistencyEnabled?: boolean;
}

export interface ChatCallbacks {
  onContent: (content: string) => void;
  onThought?: (thought: ThoughtEvent) => void;
  onTool?: (tool: ToolEvent) => void;
  onDone: () => void;
  onError: (error: Error) => void;
  // 一致性相关回调 (Requirements: 4.4, 9.1, 9.3)
  onPreflightResult?: (result: PreflightResultEvent) => void;
  onConsistencyWarning?: (warning: ConsistencyWarningEvent) => void;
  onConsistencyCheckComplete?: (result: ConsistencyCheckCompleteEvent) => void;
  // 演进相关回调 (Requirements: 9.2)
  onEvolutionUpdate?: (update: EvolutionUpdateEvent) => void;
  onSnapshotCreated?: (snapshot: SnapshotCreatedEvent) => void;
}

export class ChatService {
  /**
   * 流式聊天
   * Requirements: 4.4, 9.1, 9.2, 9.3
   */
  streamChat(request: ChatRequest, callbacks: ChatCallbacks): void {
    const sseClient = getSSEClient();
    
    const options: SSEClientOptions = {
      onContent: callbacks.onContent,
      onThought: callbacks.onThought,
      onTool: callbacks.onTool,
      onDone: callbacks.onDone,
      onError: callbacks.onError,
      // 一致性相关回调 (Requirements: 4.4, 9.1, 9.3)
      onPreflightResult: callbacks.onPreflightResult,
      onConsistencyWarning: callbacks.onConsistencyWarning,
      onConsistencyCheckComplete: callbacks.onConsistencyCheckComplete,
      // 演进相关回调 (Requirements: 9.2)
      onEvolutionUpdate: callbacks.onEvolutionUpdate,
      onSnapshotCreated: callbacks.onSnapshotCreated,
    };

    sseClient.connect('/v2/agent/chat', request, options);
  }

  /**
   * 流式场景创作
   * Requirements: 4.4, 9.1, 9.2, 9.3
   */
  streamSceneCreation(request: SceneRequest, callbacks: ChatCallbacks): void {
    const sseClient = getSSEClient();
    
    const options: SSEClientOptions = {
      onContent: callbacks.onContent,
      onThought: callbacks.onThought,
      onTool: callbacks.onTool,
      onDone: callbacks.onDone,
      onError: callbacks.onError,
      // 一致性相关回调 (Requirements: 4.4, 9.1, 9.3)
      onPreflightResult: callbacks.onPreflightResult,
      onConsistencyWarning: callbacks.onConsistencyWarning,
      onConsistencyCheckComplete: callbacks.onConsistencyCheckComplete,
      // 演进相关回调 (Requirements: 9.2)
      onEvolutionUpdate: callbacks.onEvolutionUpdate,
      onSnapshotCreated: callbacks.onSnapshotCreated,
    };

    sseClient.connect('/v2/agent/chat', request, options);
  }

  /**
   * 中断当前流
   */
  abort(): void {
    const sseClient = getSSEClient();
    sseClient.abort();
  }

  /**
   * 检查是否正在流式传输
   */
  isStreaming(): boolean {
    const sseClient = getSSEClient();
    return sseClient.isConnected();
  }
}

// 单例实例
export const chatService = new ChatService();
