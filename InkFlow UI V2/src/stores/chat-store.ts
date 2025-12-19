/**
 * 聊天状态管理
 * 使用 Zustand 管理 AI 聊天状态
 * 
 * Requirements: 4.4, 9.1
 */

import { create } from 'zustand';
import type { Message, ThoughtEvent, Artifact, ChatRequest } from '@/types';
import { chatService, type SceneRequest } from '@/services/chat-service';
import type { 
  PreflightResultEvent, 
  ConsistencyWarningEvent, 
  ConsistencyCheckCompleteEvent,
  EvolutionUpdateEvent,
  SnapshotCreatedEvent,
} from '@/api/sse-client';

type AgentState = 'idle' | 'thinking' | 'searching' | 'generating' | 'preflight' | 'error';

// 聊天流中的一致性警告消息
export interface ConsistencyWarningMessage {
  id: string;
  type: 'consistency_warning';
  entityId: string;
  entityName: string;
  entityType: string;
  severity: 'ERROR' | 'WARNING' | 'INFO';
  description: string;
  suggestion?: string;
  timestamp: Date;
}

// 预检结果消息
export interface PreflightResultMessage {
  id: string;
  type: 'preflight_result';
  passed: boolean;
  warningCount: number;
  warnings: Array<{
    entityName: string;
    severity: string;
    message: string;
  }>;
  timestamp: Date;
}

// 演进更新消息
export interface EvolutionUpdateMessage {
  id: string;
  type: 'evolution_update';
  entityId: string;
  entityName: string;
  entityType: string;
  changeType: string;
  changeSummary?: string;
  timestamp: Date;
}

// 系统消息类型（包含一致性警告、预检结果、演进更新）
export type SystemMessage = ConsistencyWarningMessage | PreflightResultMessage | EvolutionUpdateMessage;

interface ChatState {
  messages: Message[];
  isStreaming: boolean;
  currentThoughts: ThoughtEvent[];
  activeSkills: string[];
  agentState: AgentState;
  sessionId: string | null;
  error: string | null;
  // 当前正在生成的消息内容
  pendingContent: string;
  // 一致性相关状态 (Requirements: 4.4, 9.1)
  systemMessages: SystemMessage[];
  preflightResult: PreflightResultEvent | null;
  consistencyWarnings: ConsistencyWarningEvent[];
}

interface ChatOptions {
  phase?: string;
  chapterId?: string;
  characterIds?: string[];
  ragEnabled?: boolean;
  consistencyEnabled?: boolean;
}

interface ChatActions {
  sendMessage(projectId: string, content: string, options?: ChatOptions): Promise<void>;
  sendSceneRequest(request: SceneRequest): Promise<void>;
  appendContent(content: string): void;
  addThought(thought: ThoughtEvent): void;
  setAgentState(state: AgentState): void;
  toggleSkill(skillId: string): void;
  clearMessages(): void;
  abortStream(): void;
  clearError(): void;
  setSessionId(sessionId: string): void;
  // 一致性相关操作 (Requirements: 4.4, 9.1)
  handlePreflightResult(result: PreflightResultEvent): void;
  handleConsistencyWarning(warning: ConsistencyWarningEvent): void;
  handleConsistencyCheckComplete(result: ConsistencyCheckCompleteEvent): void;
  handleEvolutionUpdate(update: EvolutionUpdateEvent): void;
  handleSnapshotCreated(snapshot: SnapshotCreatedEvent): void;
  clearSystemMessages(): void;
}

type ChatStore = ChatState & ChatActions;

// 生成唯一 ID
const generateId = () => `msg_${Date.now()}_${Math.random().toString(36).substring(2, 11)}`;

export const useChatStore = create<ChatStore>()((set, get) => ({
  // 初始状态
  messages: [],
  isStreaming: false,
  currentThoughts: [],
  activeSkills: [],
  agentState: 'idle',
  sessionId: null,
  error: null,
  pendingContent: '',
  // 一致性相关状态 (Requirements: 4.4, 9.1)
  systemMessages: [],
  preflightResult: null,
  consistencyWarnings: [],

  /**
   * 发送消息
   */
  sendMessage: async (projectId: string, content: string, options?: ChatOptions) => {
    const state = get();

    // 如果正在流式传输，先中断
    if (state.isStreaming) {
      chatService.abort();
    }

    // 添加用户消息
    const userMessage: Message = {
      id: generateId(),
      role: 'user',
      content,
      timestamp: new Date(),
    };

    set((state) => ({
      messages: [...state.messages, userMessage],
      isStreaming: true,
      agentState: 'thinking',
      currentThoughts: [],
      pendingContent: '',
      error: null,
    }));

    // 构建请求 (Requirements: 4.4 - 包含激活的技能)
    const request: ChatRequest = {
      projectId,
      message: content,
      sessionId: state.sessionId || undefined,
      phase: options?.phase,
      chapterId: options?.chapterId,
      characterIds: options?.characterIds,
      ragEnabled: options?.ragEnabled,
      skills: state.activeSkills.length > 0 ? state.activeSkills : undefined,
    };

    // 发起流式请求
    chatService.streamChat(request, {
      onContent: (chunk) => {
        set((state) => ({
          pendingContent: state.pendingContent + chunk,
          agentState: 'generating',
        }));
      },
      onThought: (thought) => {
        set((state) => ({
          currentThoughts: [...state.currentThoughts, thought],
          agentState: thought.type === 'rag' ? 'searching' : 'thinking',
        }));
      },
      onTool: (tool) => {
        // 工具调用可以转换为思考事件显示
        const thought: ThoughtEvent = {
          id: tool.id,
          type: 'tool',
          agent: 'system',
          message: `调用工具: ${tool.name}`,
        };
        set((state) => ({
          currentThoughts: [...state.currentThoughts, thought],
        }));
      },
      onDone: () => {
        const currentState = get();
        
        // 创建助手消息
        const assistantMessage: Message = {
          id: generateId(),
          role: 'assistant',
          content: currentState.pendingContent,
          timestamp: new Date(),
          artifacts: extractArtifacts(currentState.pendingContent),
        };

        set({
          messages: [...currentState.messages, assistantMessage],
          isStreaming: false,
          agentState: 'idle',
          pendingContent: '',
        });
      },
      onError: (error) => {
        set({
          isStreaming: false,
          agentState: 'error',
          error: error.message,
          pendingContent: '',
        });
      },
      // 一致性相关回调 (Requirements: 4.4, 9.1, 9.3)
      onPreflightResult: (result) => {
        get().handlePreflightResult(result);
      },
      onConsistencyWarning: (warning) => {
        get().handleConsistencyWarning(warning);
      },
      onConsistencyCheckComplete: (result) => {
        get().handleConsistencyCheckComplete(result);
      },
      // 演进相关回调 (Requirements: 9.2)
      onEvolutionUpdate: (update) => {
        get().handleEvolutionUpdate(update);
      },
      onSnapshotCreated: (snapshot) => {
        get().handleSnapshotCreated(snapshot);
      },
    });
  },

  /**
   * 发送场景创作请求
   */
  sendSceneRequest: async (request: SceneRequest) => {
    const state = get();

    // 如果正在流式传输，先中断
    if (state.isStreaming) {
      chatService.abort();
    }

    // 添加用户消息
    const userMessage: Message = {
      id: generateId(),
      role: 'user',
      content: `[场景创作] ${request.message}`,
      timestamp: new Date(),
    };

    set((state) => ({
      messages: [...state.messages, userMessage],
      isStreaming: true,
      agentState: 'thinking',
      currentThoughts: [],
      pendingContent: '',
      error: null,
    }));

    // 发起流式请求
    chatService.streamSceneCreation(request, {
      onContent: (chunk) => {
        set((state) => ({
          pendingContent: state.pendingContent + chunk,
          agentState: 'generating',
        }));
      },
      onThought: (thought) => {
        set((state) => ({
          currentThoughts: [...state.currentThoughts, thought],
          agentState: thought.type === 'rag' ? 'searching' : 'thinking',
        }));
      },
      onTool: (tool) => {
        const thought: ThoughtEvent = {
          id: tool.id,
          type: 'tool',
          agent: 'system',
          message: `调用工具: ${tool.name}`,
        };
        set((state) => ({
          currentThoughts: [...state.currentThoughts, thought],
        }));
      },
      onDone: () => {
        const currentState = get();
        
        const assistantMessage: Message = {
          id: generateId(),
          role: 'assistant',
          content: currentState.pendingContent,
          timestamp: new Date(),
          artifacts: extractArtifacts(currentState.pendingContent),
        };

        set({
          messages: [...currentState.messages, assistantMessage],
          isStreaming: false,
          agentState: 'idle',
          pendingContent: '',
        });
      },
      onError: (error) => {
        set({
          isStreaming: false,
          agentState: 'error',
          error: error.message,
          pendingContent: '',
        });
      },
      // 一致性相关回调 (Requirements: 4.4, 9.1, 9.3)
      onPreflightResult: (result) => {
        get().handlePreflightResult(result);
      },
      onConsistencyWarning: (warning) => {
        get().handleConsistencyWarning(warning);
      },
      onConsistencyCheckComplete: (result) => {
        get().handleConsistencyCheckComplete(result);
      },
      // 演进相关回调 (Requirements: 9.2)
      onEvolutionUpdate: (update) => {
        get().handleEvolutionUpdate(update);
      },
      onSnapshotCreated: (snapshot) => {
        get().handleSnapshotCreated(snapshot);
      },
    });
  },

  /**
   * 追加内容（用于外部调用）
   */
  appendContent: (content: string) => {
    set((state) => ({
      pendingContent: state.pendingContent + content,
    }));
  },

  /**
   * 添加思考事件
   */
  addThought: (thought: ThoughtEvent) => {
    set((state) => ({
      currentThoughts: [...state.currentThoughts, thought],
    }));
  },

  /**
   * 设置 Agent 状态
   */
  setAgentState: (agentState: AgentState) => {
    set({ agentState });
  },

  /**
   * 切换技能
   */
  toggleSkill: (skillId: string) => {
    set((state) => {
      const isActive = state.activeSkills.includes(skillId);
      return {
        activeSkills: isActive
          ? state.activeSkills.filter(id => id !== skillId)
          : [...state.activeSkills, skillId],
      };
    });
  },

  /**
   * 清除消息
   */
  clearMessages: () => {
    set({
      messages: [],
      currentThoughts: [],
      pendingContent: '',
      sessionId: null,
    });
  },

  /**
   * 中断流
   */
  abortStream: () => {
    chatService.abort();
    
    const state = get();
    
    // 如果有待处理的内容，保存为消息
    if (state.pendingContent) {
      const assistantMessage: Message = {
        id: generateId(),
        role: 'assistant',
        content: state.pendingContent + '\n\n[已中断]',
        timestamp: new Date(),
      };

      set((state) => ({
        messages: [...state.messages, assistantMessage],
        isStreaming: false,
        agentState: 'idle',
        pendingContent: '',
      }));
    } else {
      set({
        isStreaming: false,
        agentState: 'idle',
      });
    }
  },

  /**
   * 清除错误
   */
  clearError: () => {
    set({ error: null, agentState: 'idle' });
  },

  /**
   * 设置会话 ID
   */
  setSessionId: (sessionId: string) => {
    set({ sessionId });
  },

  // ============ 一致性相关操作 (Requirements: 4.4, 9.1) ============

  /**
   * 处理预检结果
   * Requirements: 9.3
   */
  handlePreflightResult: (result: PreflightResultEvent) => {
    const preflightMessage: PreflightResultMessage = {
      id: generateId(),
      type: 'preflight_result',
      passed: result.passed,
      warningCount: result.warnings.length,
      warnings: result.warnings.map(w => ({
        entityName: w.entityName,
        severity: w.severity,
        message: w.message,
      })),
      timestamp: new Date(),
    };

    set((state) => ({
      preflightResult: result,
      systemMessages: [...state.systemMessages, preflightMessage],
      agentState: result.passed ? state.agentState : 'preflight',
    }));
  },

  /**
   * 处理一致性警告
   * Requirements: 9.1, 4.4
   */
  handleConsistencyWarning: (warning: ConsistencyWarningEvent) => {
    const warningMessage: ConsistencyWarningMessage = {
      id: generateId(),
      type: 'consistency_warning',
      entityId: warning.entityId,
      entityName: warning.entityName,
      entityType: warning.entityType,
      severity: warning.severity,
      description: warning.description,
      suggestion: warning.suggestion,
      timestamp: new Date(),
    };

    set((state) => ({
      consistencyWarnings: [...state.consistencyWarnings, warning],
      systemMessages: [...state.systemMessages, warningMessage],
    }));
  },

  /**
   * 处理一致性检查完成
   */
  handleConsistencyCheckComplete: (_result: ConsistencyCheckCompleteEvent) => {
    // 检查完成后可以清除预检状态
    set((state) => ({
      agentState: state.agentState === 'preflight' ? 'idle' : state.agentState,
    }));
  },

  /**
   * 处理演进更新
   * Requirements: 9.2
   */
  handleEvolutionUpdate: (update: EvolutionUpdateEvent) => {
    const evolutionMessage: EvolutionUpdateMessage = {
      id: generateId(),
      type: 'evolution_update',
      entityId: update.entityId,
      entityName: update.entityName,
      entityType: update.entityType,
      changeType: update.changeType,
      changeSummary: update.changeSummary,
      timestamp: new Date(),
    };

    set((state) => ({
      systemMessages: [...state.systemMessages, evolutionMessage],
    }));
  },

  /**
   * 处理快照创建
   * Requirements: 9.2
   */
  handleSnapshotCreated: (_snapshot: SnapshotCreatedEvent) => {
    // 快照创建通常不需要在聊天中显示
    // 但可以用于更新演进时间线
  },

  /**
   * 清除系统消息
   */
  clearSystemMessages: () => {
    set({
      systemMessages: [],
      preflightResult: null,
      consistencyWarnings: [],
    });
  },
}));

/**
 * 从内容中提取 Artifacts
 */
function extractArtifacts(content: string): Artifact[] {
  const artifacts: Artifact[] = [];
  
  // 简单的 artifact 提取逻辑
  // 实际实现可能需要更复杂的解析
  const characterMatch = content.match(/\[角色:([^\]]+)\]/g);
  if (characterMatch) {
    characterMatch.forEach((match, index) => {
      const name = match.replace(/\[角色:|]/g, '');
      artifacts.push({
        type: 'character',
        id: `char_${index}`,
        title: name,
        preview: `角色: ${name}`,
      });
    });
  }

  const wikiMatch = content.match(/\[设定:([^\]]+)\]/g);
  if (wikiMatch) {
    wikiMatch.forEach((match, index) => {
      const title = match.replace(/\[设定:|]/g, '');
      artifacts.push({
        type: 'wiki',
        id: `wiki_${index}`,
        title,
        preview: `设定: ${title}`,
      });
    });
  }

  return artifacts;
}

// 导出选择器
export const selectMessages = (state: ChatStore) => state.messages;
export const selectIsStreaming = (state: ChatStore) => state.isStreaming;
export const selectCurrentThoughts = (state: ChatStore) => state.currentThoughts;
export const selectActiveSkills = (state: ChatStore) => state.activeSkills;
export const selectAgentState = (state: ChatStore) => state.agentState;
export const selectPendingContent = (state: ChatStore) => state.pendingContent;
export const selectError = (state: ChatStore) => state.error;

// 一致性相关选择器 (Requirements: 4.4, 9.1)
export const selectSystemMessages = (state: ChatStore) => state.systemMessages;
export const selectPreflightResult = (state: ChatStore) => state.preflightResult;
export const selectConsistencyWarnings = (state: ChatStore) => state.consistencyWarnings;

// 获取最近的一致性警告
export const selectRecentWarnings = (state: ChatStore) => 
  state.systemMessages.filter(m => m.type === 'consistency_warning') as ConsistencyWarningMessage[];

// 获取最近的预检结果
export const selectLatestPreflightResult = (state: ChatStore) => {
  const preflightMessages = state.systemMessages.filter(m => m.type === 'preflight_result') as PreflightResultMessage[];
  return preflightMessages.length > 0 ? preflightMessages[preflightMessages.length - 1] : null;
};

// 是否有未处理的警告
export const selectHasActiveWarnings = (state: ChatStore) => 
  state.consistencyWarnings.length > 0;
