/**
 * 思维链组件
 * 显示 AI 的思考过程和推理步骤
 * 集成 ChatStore 进行状态管理
 */

import { useState } from 'react';
import { ChevronDown, ChevronRight, Brain, Search, Sparkles, Wrench } from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';
import { useChatStore } from '@/stores/chat-store';

// 简化的思考事件类型（用于非集成模式）
interface SimpleThoughtEvent {
  id: string;
  type: 'thinking' | 'rag' | 'skill' | 'tool';
  agent: string;
  message: string;
  confidence?: number;
  duration?: number;
}

interface ThoughtChainProps {
  /** 外部传入的事件列表（可选，用于非集成模式） */
  events?: SimpleThoughtEvent[];
  /** 是否正在思考（可选，用于非集成模式） */
  isThinking?: boolean;
  /** 是否使用集成模式（连接 store） */
  integrated?: boolean;
}

// Agent 颜色配置
const agentColors: Record<string, string> = {
  ThinkingAgent: 'text-blue-500',
  WriterAgent: 'text-green-500',
  PlannerAgent: 'text-purple-500',
  ConsistencyAgent: 'text-orange-500',
  CharacterAgent: 'text-pink-500',
  WorldBuilderAgent: 'text-cyan-500',
  system: 'text-gray-500',
};

// 事件图标配置
const eventIcons = {
  thinking: Brain,
  rag: Search,
  skill: Sparkles,
  tool: Wrench,
};

// Agent 状态到思考状态的映射
const agentStateToThinking = (state: string): boolean => {
  return state === 'thinking' || state === 'searching';
};

export function ThoughtChain({ 
  events: externalEvents, 
  isThinking: externalIsThinking = false,
  integrated = false 
}: ThoughtChainProps) {
  const [expanded, setExpanded] = useState(false);

  // Store hooks (only used in integrated mode)
  const { currentThoughts, agentState, isStreaming } = useChatStore();

  // 决定使用哪个数据源
  const events = integrated ? currentThoughts : (externalEvents || []);
  const isThinking = integrated 
    ? (isStreaming && agentStateToThinking(agentState))
    : externalIsThinking;

  // 如果没有事件且不在思考中，不渲染
  if (events.length === 0 && !isThinking) return null;

  // 获取事件图标
  const getEventIcon = (type: string) => {
    return eventIcons[type as keyof typeof eventIcons] || Brain;
  };

  // 获取 Agent 颜色
  const getAgentColor = (agent: string) => {
    return agentColors[agent] || 'text-foreground';
  };

  return (
    <div className="mb-4">
      {/* Collapsed State */}
      {!expanded && (
        <motion.button
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
          onClick={() => setExpanded(true)}
          className="w-full flex items-center gap-3 px-4 py-3 rounded-xl border border-border bg-card hover:bg-accent transition-all shadow-sm hover:shadow-md"
        >
          <ChevronRight className="h-4 w-4 text-muted-foreground" />
          <Brain className="h-4 w-4 text-primary" />
          <span className="text-sm">
            {isThinking ? (
              <span className="flex items-center gap-2">
                {integrated && agentState === 'searching' ? '搜索设定...' : '思考中...'}
                <motion.div
                  className="h-2 w-2 rounded-full bg-primary"
                  animate={{ scale: [1, 1.2, 1] }}
                  transition={{ duration: 1, repeat: Infinity }}
                />
              </span>
            ) : (
              `思维链 (${events.length} 步骤)`
            )}
          </span>
          {!isThinking && events.length > 0 && (events[0] as SimpleThoughtEvent).duration && (
            <span className="text-xs text-muted-foreground ml-auto">
              {(events[0] as SimpleThoughtEvent).duration}s
            </span>
          )}
        </motion.button>
      )}

      {/* Expanded State */}
      <AnimatePresence>
        {expanded && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            className="border border-border rounded-xl bg-card overflow-hidden shadow-md"
          >
            <button
              onClick={() => setExpanded(false)}
              className="w-full flex items-center gap-3 px-4 py-3 border-b border-border hover:bg-accent transition-colors"
            >
              <ChevronDown className="h-4 w-4 text-muted-foreground" />
              <Brain className="h-4 w-4 text-primary" />
              <span className="text-sm font-medium">思维链</span>
              <span className="text-xs text-muted-foreground ml-auto">
                {events.length} 步骤
              </span>
            </button>

            <div className="p-4 space-y-3 max-h-64 overflow-y-auto">
              {events.map((event, index) => {
                const Icon = getEventIcon(event.type);
                const agentColor = getAgentColor(event.agent);
                
                return (
                  <motion.div
                    key={event.id}
                    initial={{ opacity: 0, x: -10 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: index * 0.05 }}
                    className="flex gap-3 text-sm bg-accent/50 rounded-lg p-3"
                  >
                    <div className="shrink-0 mt-0.5">
                      <Icon className={`h-4 w-4 ${agentColor}`} />
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className={`font-medium ${agentColor}`}>
                          [{event.agent}]
                        </span>
                        <span className="text-xs text-muted-foreground capitalize">
                          {event.type}
                        </span>
                        {event.confidence && (
                          <span className="text-xs text-muted-foreground">
                            置信度 {Math.round(event.confidence * 100)}%
                          </span>
                        )}
                      </div>
                      <div className="text-muted-foreground mt-1">
                        {event.message}
                      </div>
                    </div>
                  </motion.div>
                );
              })}

              {isThinking && (
                <motion.div
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  className="flex items-center gap-3 text-sm text-muted-foreground bg-accent/50 rounded-lg p-3"
                >
                  <motion.div
                    className="h-2 w-2 rounded-full bg-primary"
                    animate={{ scale: [1, 1.3, 1] }}
                    transition={{ duration: 1, repeat: Infinity }}
                  />
                  <span>
                    {integrated && agentState === 'searching' ? '搜索相关设定...' : '处理中...'}
                  </span>
                </motion.div>
              )}

              {/* 空状态 */}
              {events.length === 0 && !isThinking && (
                <div className="text-center text-sm text-muted-foreground py-4">
                  暂无思考记录
                </div>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}