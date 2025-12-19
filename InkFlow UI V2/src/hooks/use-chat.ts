/**
 * 聊天相关 hooks
 */

import { useCallback } from 'react';
import { useChatStore } from '@/stores';
import type { SceneRequest } from '@/services';

/**
 * 使用聊天功能
 */
export function useChat(projectId: string) {
  const messages = useChatStore((state) => state.messages);
  const isStreaming = useChatStore((state) => state.isStreaming);
  const currentThoughts = useChatStore((state) => state.currentThoughts);
  const activeSkills = useChatStore((state) => state.activeSkills);
  const agentState = useChatStore((state) => state.agentState);
  const pendingContent = useChatStore((state) => state.pendingContent);
  const error = useChatStore((state) => state.error);
  
  const sendMessageAction = useChatStore((state) => state.sendMessage);
  const sendSceneRequestAction = useChatStore((state) => state.sendSceneRequest);
  const toggleSkill = useChatStore((state) => state.toggleSkill);
  const clearMessages = useChatStore((state) => state.clearMessages);
  const abortStream = useChatStore((state) => state.abortStream);
  const clearError = useChatStore((state) => state.clearError);

  const sendMessage = useCallback(
    (content: string, options?: { phase?: string; chapterId?: string; characterIds?: string[] }) => {
      return sendMessageAction(projectId, content, options);
    },
    [projectId, sendMessageAction]
  );

  const sendSceneRequest = useCallback(
    (request: Omit<SceneRequest, 'projectId'>) => {
      return sendSceneRequestAction({ ...request, projectId });
    },
    [projectId, sendSceneRequestAction]
  );

  return {
    messages,
    isStreaming,
    currentThoughts,
    activeSkills,
    agentState,
    pendingContent,
    error,
    sendMessage,
    sendSceneRequest,
    toggleSkill,
    clearMessages,
    abortStream,
    clearError,
  };
}

/**
 * 获取当前显示的内容（包括正在生成的）
 */
export function useChatContent() {
  const messages = useChatStore((state) => state.messages);
  const pendingContent = useChatStore((state) => state.pendingContent);
  const isStreaming = useChatStore((state) => state.isStreaming);

  return {
    messages,
    pendingContent,
    isStreaming,
    // 完整的消息列表（包括正在生成的）
    allContent: isStreaming && pendingContent
      ? [...messages, { id: 'pending', role: 'assistant' as const, content: pendingContent, timestamp: new Date() }]
      : messages,
  };
}
