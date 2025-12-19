/**
 * 聊天界面组件
 * 显示消息列表和输入框，支持流式响应
 * 集成 ChatStore 进行状态管理
 * 
 * Requirements: 4.4, 9.1
 */

import { useState, useRef, useEffect, useCallback } from 'react';
import { Send, Bot, User, StopCircle, AlertCircle, RefreshCw, AlertTriangle, Info, CheckCircle, History } from 'lucide-react';
import { ScrollArea } from '../ui/scroll-area';
import { Button } from '../ui/button';
import { Textarea } from '../ui/textarea';
import { motion } from 'motion/react';
import { useChatStore, type SystemMessage, type ConsistencyWarningMessage, type PreflightResultMessage, type EvolutionUpdateMessage } from '@/stores/chat-store';
import { useProjectStore } from '@/stores/project-store';
import type { Message } from '@/types';

// 简化的消息类型（用于非集成模式）
export interface SimpleMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
}

interface ChatInterfaceProps {
  /** 外部传入的消息列表（可选，用于非集成模式） */
  messages?: SimpleMessage[];
  /** 发送消息回调（可选，用于非集成模式） */
  onSend?: (message: string) => void;
  /** 是否加载中（可选，用于非集成模式） */
  isLoading?: boolean;
  /** 是否使用集成模式（连接 store） */
  integrated?: boolean;
}

// Agent 状态显示配置
const agentStateConfig = {
  idle: { text: '', icon: null },
  thinking: { text: '思考中...', icon: '🤔' },
  searching: { text: '搜索设定...', icon: '🔍' },
  generating: { text: '生成中...', icon: '✍️' },
  preflight: { text: '一致性预检中...', icon: '🔍' },
  error: { text: '出错了', icon: '❌' },
};

// 严重程度图标和颜色配置
const severityConfig = {
  ERROR: { icon: AlertCircle, color: 'text-destructive', bgColor: 'bg-destructive/10', borderColor: 'border-destructive/20' },
  WARNING: { icon: AlertTriangle, color: 'text-amber-500', bgColor: 'bg-amber-500/10', borderColor: 'border-amber-500/20' },
  INFO: { icon: Info, color: 'text-blue-500', bgColor: 'bg-blue-500/10', borderColor: 'border-blue-500/20' },
};

export function ChatInterface({ 
  messages: externalMessages, 
  onSend: externalOnSend, 
  isLoading: externalIsLoading = false,
  integrated = false 
}: ChatInterfaceProps) {
  const [input, setInput] = useState('');
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // Store hooks (only used in integrated mode)
  const currentProject = useProjectStore(state => state.currentProject);
  const {
    messages: storeMessages,
    isStreaming,
    pendingContent,
    agentState,
    error,
    sendMessage,
    abortStream,
    clearError,
    // 一致性相关状态 (Requirements: 4.4, 9.1)
    systemMessages,
  } = useChatStore();

  // 决定使用哪个数据源
  const messages = integrated ? storeMessages : (externalMessages || []);
  const isLoading = integrated ? isStreaming : externalIsLoading;

  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, []);

  useEffect(() => {
    scrollToBottom();
  }, [messages, isLoading, pendingContent, scrollToBottom]);

  const handleSend = useCallback(() => {
    if (!input.trim() || isLoading) return;

    if (integrated && currentProject) {
      sendMessage(currentProject.id, input.trim());
    } else if (externalOnSend) {
      externalOnSend(input.trim());
    }
    
    setInput('');
  }, [input, isLoading, integrated, currentProject, sendMessage, externalOnSend]);

  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  }, [handleSend]);

  const handleAbort = useCallback(() => {
    if (integrated) {
      abortStream();
    }
  }, [integrated, abortStream]);

  // 渲染消息内容
  const renderMessageContent = (message: Message | SimpleMessage) => {
    return (
      <div className="text-sm whitespace-pre-wrap leading-relaxed">
        {message.content}
      </div>
    );
  };

  /**
   * 渲染系统消息（一致性警告、预检结果、演进更新）
   * Requirements: 4.4, 9.1
   */
  const renderSystemMessage = (message: SystemMessage) => {
    switch (message.type) {
      case 'consistency_warning':
        return renderConsistencyWarning(message as ConsistencyWarningMessage);
      case 'preflight_result':
        return renderPreflightResult(message as PreflightResultMessage);
      case 'evolution_update':
        return renderEvolutionUpdate(message as EvolutionUpdateMessage);
      default:
        return null;
    }
  };

  /**
   * 渲染一致性警告
   * Requirements: 4.4, 9.1
   */
  const renderConsistencyWarning = (warning: ConsistencyWarningMessage) => {
    const config = severityConfig[warning.severity];
    const IconComponent = config.icon;

    return (
      <motion.div
        key={warning.id}
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        className="flex gap-3"
      >
        <div className={`shrink-0 w-9 h-9 rounded-full ${config.bgColor} flex items-center justify-center shadow-sm`}>
          <IconComponent className={`h-5 w-5 ${config.color}`} />
        </div>
        <div className={`max-w-[85%] ${config.bgColor} border ${config.borderColor} rounded-2xl px-4 py-3 shadow-sm`}>
          <div className="flex items-center gap-2 mb-1">
            <span className={`text-xs font-medium ${config.color}`}>
              {warning.severity === 'ERROR' ? '错误' : warning.severity === 'WARNING' ? '警告' : '提示'}
            </span>
            <span className="text-xs text-muted-foreground">
              {warning.entityName} ({warning.entityType})
            </span>
          </div>
          <div className="text-sm">{warning.description}</div>
          {warning.suggestion && (
            <div className="text-xs text-muted-foreground mt-2">
              💡 建议: {warning.suggestion}
            </div>
          )}
        </div>
      </motion.div>
    );
  };

  /**
   * 渲染预检结果
   * Requirements: 9.3
   */
  const renderPreflightResult = (result: PreflightResultMessage) => {
    const passed = result.passed;
    const IconComponent = passed ? CheckCircle : AlertTriangle;
    const bgColor = passed ? 'bg-green-500/10' : 'bg-amber-500/10';
    const borderColor = passed ? 'border-green-500/20' : 'border-amber-500/20';
    const iconColor = passed ? 'text-green-500' : 'text-amber-500';

    return (
      <motion.div
        key={result.id}
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        className="flex gap-3"
      >
        <div className={`shrink-0 w-9 h-9 rounded-full ${bgColor} flex items-center justify-center shadow-sm`}>
          <IconComponent className={`h-5 w-5 ${iconColor}`} />
        </div>
        <div className={`max-w-[85%] ${bgColor} border ${borderColor} rounded-2xl px-4 py-3 shadow-sm`}>
          <div className="flex items-center gap-2 mb-1">
            <span className={`text-xs font-medium ${iconColor}`}>
              一致性预检
            </span>
            <span className={`text-xs ${passed ? 'text-green-600' : 'text-amber-600'}`}>
              {passed ? '通过' : `发现 ${result.warningCount} 个问题`}
            </span>
          </div>
          {!passed && result.warnings.length > 0 && (
            <div className="space-y-1 mt-2">
              {result.warnings.slice(0, 3).map((w, idx) => (
                <div key={idx} className="text-xs text-muted-foreground">
                  • {w.entityName}: {w.message}
                </div>
              ))}
              {result.warnings.length > 3 && (
                <div className="text-xs text-muted-foreground">
                  还有 {result.warnings.length - 3} 个问题...
                </div>
              )}
            </div>
          )}
        </div>
      </motion.div>
    );
  };

  /**
   * 渲染演进更新
   * Requirements: 9.2
   */
  const renderEvolutionUpdate = (update: EvolutionUpdateMessage) => {
    return (
      <motion.div
        key={update.id}
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        className="flex gap-3"
      >
        <div className="shrink-0 w-9 h-9 rounded-full bg-purple-500/10 flex items-center justify-center shadow-sm">
          <History className="h-5 w-5 text-purple-500" />
        </div>
        <div className="max-w-[85%] bg-purple-500/10 border border-purple-500/20 rounded-2xl px-4 py-3 shadow-sm">
          <div className="flex items-center gap-2 mb-1">
            <span className="text-xs font-medium text-purple-500">
              状态演进
            </span>
            <span className="text-xs text-muted-foreground">
              {update.entityName}
            </span>
          </div>
          {update.changeSummary && (
            <div className="text-sm">{update.changeSummary}</div>
          )}
          <div className="text-xs text-muted-foreground mt-1">
            变更类型: {update.changeType === 'INITIAL' ? '初始状态' : update.changeType === 'UPDATE' ? '更新' : '重大变更'}
          </div>
        </div>
      </motion.div>
    );
  };

  // 渲染 Agent 状态
  const renderAgentStatus = () => {
    if (!integrated || agentState === 'idle') return null;

    const config = agentStateConfig[agentState];
    return (
      <div className="flex items-center gap-2 text-xs text-muted-foreground px-4 py-1">
        {config.icon && <span>{config.icon}</span>}
        <span>{config.text}</span>
      </div>
    );
  };

  return (
    <div className="flex flex-col h-full overflow-hidden relative">
      {/* Error Banner */}
      {integrated && error && (
        <motion.div
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
          className="px-4 py-2 bg-destructive/10 border-b border-destructive/20 flex items-center justify-between"
        >
          <div className="flex items-center gap-2 text-sm text-destructive">
            <AlertCircle className="h-4 w-4" />
            <span>{error}</span>
          </div>
          <Button variant="ghost" size="sm" onClick={clearError}>
            <RefreshCw className="h-4 w-4" />
          </Button>
        </motion.div>
      )}

      {/* Messages Wrapper with min-h-0 constraint */}
      <div className="flex-1 min-h-0 relative">
        <ScrollArea className="h-full px-5 py-5 custom-scroll">
          <div className="space-y-4">
            {/* 空状态 */}
            {messages.length === 0 && !isLoading && (
              <div className="flex flex-col items-center justify-center py-12 text-center">
                <Bot className="h-12 w-12 text-muted-foreground/30 mb-4" />
                <p className="text-muted-foreground">开始与 AI 助手对话</p>
                <p className="text-xs text-muted-foreground/70 mt-1">
                  我可以帮你创作角色、构建世界观、撰写场景
                </p>
              </div>
            )}

            {/* 消息列表（包含系统消息和用户/助手消息） */}
            {(() => {
              // 合并消息和系统消息，按时间排序
              const allMessages: Array<{ type: 'message' | 'system'; data: Message | SimpleMessage | SystemMessage; timestamp: Date }> = [
                ...messages.map(m => ({ type: 'message' as const, data: m, timestamp: m.timestamp })),
                ...(integrated ? systemMessages.map(m => ({ type: 'system' as const, data: m, timestamp: m.timestamp })) : []),
              ].sort((a, b) => a.timestamp.getTime() - b.timestamp.getTime());

              return allMessages.map((item) => {
                if (item.type === 'system') {
                  return renderSystemMessage(item.data as SystemMessage);
                }

                const message = item.data as Message | SimpleMessage;
                return (
                  <motion.div
                    key={message.id}
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    className={`flex gap-3 ${message.role === 'user' ? 'justify-end' : 'justify-start'}`}
                  >
                    {message.role === 'assistant' && (
                      <div className="shrink-0 w-9 h-9 rounded-full bg-primary/10 flex items-center justify-center shadow-sm">
                        <Bot className="h-5 w-5 text-primary" />
                      </div>
                    )}

                    <div
                      className={`max-w-[85%] rounded-2xl px-4 py-3 shadow-sm ${message.role === 'user'
                        ? 'bg-primary text-primary-foreground'
                        : 'bg-card border border-border'
                        }`}
                    >
                      {renderMessageContent(message)}
                    </div>

                    {message.role === 'user' && (
                      <div className="shrink-0 w-9 h-9 rounded-full bg-accent flex items-center justify-center shadow-sm">
                        <User className="h-5 w-5" />
                      </div>
                    )}
                  </motion.div>
                );
              });
            })()}

            {/* 流式响应中的内容 */}
            {integrated && isStreaming && pendingContent && (
              <motion.div
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                className="flex gap-3"
              >
                <div className="shrink-0 w-9 h-9 rounded-full bg-primary/10 flex items-center justify-center shadow-sm">
                  <Bot className="h-5 w-5 text-primary" />
                </div>
                <div className="max-w-[85%] bg-card border border-border rounded-2xl px-4 py-3 shadow-sm">
                  <div className="text-sm whitespace-pre-wrap leading-relaxed">
                    {pendingContent}
                    <span className="inline-block w-2 h-4 bg-primary/50 animate-pulse ml-0.5" />
                  </div>
                </div>
              </motion.div>
            )}

            {/* 加载动画 */}
            {isLoading && !pendingContent && (
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                className="flex gap-3"
              >
                <div className="shrink-0 w-9 h-9 rounded-full bg-primary/10 flex items-center justify-center shadow-sm">
                  <Bot className="h-5 w-5 text-primary" />
                </div>
                <div className="bg-card border border-border rounded-2xl px-4 py-3 shadow-sm">
                  <div className="flex gap-1.5">
                    {[0, 1, 2].map((i) => (
                      <motion.div
                        key={i}
                        className="w-2 h-2 rounded-full bg-muted-foreground"
                        animate={{ scale: [1, 1.2, 1] }}
                        transition={{ duration: 1, repeat: Infinity, delay: i * 0.2 }}
                      />
                    ))}
                  </div>
                </div>
              </motion.div>
            )}

            {/* Spacer for bottom scrolling */}
            <div ref={messagesEndRef} className="h-4" />
          </div>
        </ScrollArea>
      </div>

      {/* Agent Status */}
      {renderAgentStatus()}

      {/* Floating Input Area */}
      <div className="p-4 shrink-0 bg-transparent z-10 w-full">
        <div className="bg-card/95 backdrop-blur-sm border border-border/50 rounded-2xl shadow-lg p-3 flex gap-3 ring-1 ring-black/5 dark:ring-white/5 transition-all focus-within:ring-primary/20 focus-within:border-primary/50 focus-within:shadow-xl">
          <Textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={integrated && !currentProject ? "请先选择项目..." : "与 AI 对话..."}
            className="resize-none min-h-[50px] max-h-[120px] rounded-xl border-none shadow-none focus-visible:ring-0 bg-transparent py-3"
            disabled={isLoading || (integrated && !currentProject)}
          />
          
          {/* 发送/停止按钮 */}
          {isLoading && integrated ? (
            <Button
              onClick={handleAbort}
              size="icon"
              variant="destructive"
              className="shrink-0 h-[50px] w-12 rounded-xl shadow-sm hover:shadow-md transition-all self-end mb-1"
            >
              <StopCircle className="h-5 w-5" />
            </Button>
          ) : (
            <Button
              onClick={handleSend}
              disabled={!input.trim() || isLoading || (integrated && !currentProject)}
              size="icon"
              className="shrink-0 h-[50px] w-12 rounded-xl shadow-sm hover:shadow-md transition-all self-end mb-1"
            >
              <Send className="h-5 w-5" />
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}