import { Brain, Activity, AlertCircle, Loader2, CheckCircle2 } from 'lucide-react';
import { motion } from 'motion/react';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/app/components/ui/tooltip';

export type AgentType = 'PlannerAgent' | 'WriterAgent' | 'ThinkingAgent' | 'ConsistencyAgent';
export type AgentState = 'idle' | 'thinking' | 'online' | 'error';

interface AgentStatusProps {
  agent: AgentType;
  state: AgentState;
  errorMessage?: string;
}

const agentNames: Record<AgentType, string> = {
  PlannerAgent: '规划助手',
  WriterAgent: '写作助手',
  ThinkingAgent: '思考助手',
  ConsistencyAgent: '一致性检查',
};

const agentDescriptions: Record<AgentType, string> = {
  PlannerAgent: '帮助规划故事大纲和情节结构',
  WriterAgent: '协助撰写和润色小说内容',
  ThinkingAgent: '深度分析和推理复杂问题',
  ConsistencyAgent: '检查设定一致性和逻辑矛盾',
};

const agentColors: Record<AgentType, string> = {
  PlannerAgent: 'text-[var(--agent-logic)]',
  WriterAgent: 'text-[var(--agent-writer)]',
  ThinkingAgent: 'text-[var(--brand-violet)]',
  ConsistencyAgent: 'text-[var(--agent-error)]',
};

const stateConfig: Record<AgentState, { color: string; bgColor: string; label: string; description: string }> = {
  idle: {
    color: 'text-muted-foreground',
    bgColor: 'bg-muted-foreground',
    label: '待命',
    description: '助手已就绪，等待您的指令',
  },
  thinking: {
    color: 'text-yellow-500',
    bgColor: 'bg-yellow-500',
    label: '思考中',
    description: '正在处理您的请求...',
  },
  online: {
    color: 'text-green-500',
    bgColor: 'bg-green-500',
    label: '在线',
    description: '助手已连接，可以开始对话',
  },
  error: {
    color: 'text-destructive',
    bgColor: 'bg-destructive',
    label: '错误',
    description: '连接出现问题，请稍后重试',
  },
};

export function AgentStatus({ agent, state, errorMessage }: AgentStatusProps) {
  const agentName = agentNames[agent];
  const agentDescription = agentDescriptions[agent];
  const agentColor = agentColors[agent];
  const { color: stateColor, bgColor, label: stateLabel, description: stateDescription } = stateConfig[state];

  // Render state icon based on current state
  const renderStateIcon = () => {
    switch (state) {
      case 'thinking':
        return (
          <motion.div
            animate={{ rotate: 360 }}
            transition={{ duration: 1, repeat: Infinity, ease: 'linear' }}
          >
            <Loader2 className={`h-4 w-4 ${stateColor}`} />
          </motion.div>
        );
      case 'online':
        return <CheckCircle2 className={`h-4 w-4 ${stateColor}`} />;
      case 'error':
        return <AlertCircle className={`h-4 w-4 ${stateColor}`} />;
      default:
        return <Activity className={`h-4 w-4 ${stateColor}`} />;
    }
  };

  return (
    <TooltipProvider>
      <div className="px-5 py-4 border-b border-border bg-card shadow-sm">
        <div className="flex items-center justify-between">
          {/* Agent Info */}
          <div className="flex items-center gap-3">
            <Tooltip>
              <TooltipTrigger asChild>
                <div className={`p-2 rounded-xl bg-accent ${agentColor} cursor-help`}>
                  <Brain className="h-4 w-4" />
                </div>
              </TooltipTrigger>
              <TooltipContent side="bottom" className="max-w-[200px]">
                <p className="font-medium">{agentName}</p>
                <p className="text-xs text-muted-foreground">{agentDescription}</p>
              </TooltipContent>
            </Tooltip>
            <div>
              <div className="text-sm font-medium">{agentName}</div>
              <div className={`text-xs ${state === 'error' ? 'text-destructive' : 'text-muted-foreground'}`}>
                {state === 'error' && errorMessage ? errorMessage : stateDescription}
              </div>
            </div>
          </div>

          {/* State Indicator */}
          <Tooltip>
            <TooltipTrigger asChild>
              <div className="flex items-center gap-2 cursor-help">
                <motion.div
                  className={`w-2.5 h-2.5 rounded-full ${bgColor} shadow-sm`}
                  animate={state === 'thinking' ? { scale: [1, 1.2, 1], opacity: [1, 0.7, 1] } : {}}
                  transition={{ duration: 1, repeat: Infinity }}
                />
                {renderStateIcon()}
              </div>
            </TooltipTrigger>
            <TooltipContent side="left">
              <p className="font-medium">{stateLabel}</p>
              <p className="text-xs text-muted-foreground">{stateDescription}</p>
            </TooltipContent>
          </Tooltip>
        </div>

        {/* Error Banner */}
        {state === 'error' && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            className="mt-3 p-2 rounded-lg bg-destructive/10 border border-destructive/20"
          >
            <div className="flex items-center gap-2 text-xs text-destructive">
              <AlertCircle className="h-3 w-3 flex-shrink-0" />
              <span>{errorMessage || '连接出现问题，请检查网络或稍后重试'}</span>
            </div>
          </motion.div>
        )}
      </div>
    </TooltipProvider>
  );
}