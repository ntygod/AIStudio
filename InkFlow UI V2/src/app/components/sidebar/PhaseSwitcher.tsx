/**
 * 创作阶段切换器
 * 显示和切换当前项目的创作阶段
 * 支持集成模式连接 ProjectStore
 * 
 * Requirements: 2.1, 2.2
 * - 显示当前创作阶段及视觉指示器
 * - 阶段切换时同步到后端
 */

import { useCallback, useMemo } from 'react';
import { 
  Lightbulb, 
  Globe, 
  Users, 
  Map, 
  FileText, 
  Wrench, 
  CheckCircle2,
  Loader2,
  ChevronRight
} from 'lucide-react';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../ui/select';
import { Badge } from '../ui/badge';
import { cn } from '../ui/utils';
import { useProjectStore } from '@/stores/project-store';
import type { CreationPhase } from '@/types';

// 旧版阶段类型（用于向后兼容）
export type LegacyCreationPhase = 'INSPIRATION' | 'SETTING' | 'PLANNING' | 'OUTLINING' | 'EDITING';

interface PhaseSwitcherProps {
  /** 外部传入的当前阶段（可选，用于非集成模式） */
  currentPhase?: CreationPhase | LegacyCreationPhase;
  /** 阶段变更回调（可选，用于非集成模式） */
  onPhaseChange?: (phase: CreationPhase) => void;
  /** 是否使用集成模式（连接 store） */
  integrated?: boolean;
  /** 是否显示紧凑模式 */
  compact?: boolean;
}

// 阶段配置 - 与后端 CreationPhase 枚举保持一致
interface PhaseConfig {
  value: CreationPhase;
  label: string;
  labelEn: string;
  icon: typeof Lightbulb;
  description: string;
  color: string;
  bgColor: string;
  order: number;
}

const phases: PhaseConfig[] = [
  { 
    value: 'IDEA', 
    label: '💡 灵感', 
    labelEn: 'Idea',
    icon: Lightbulb,
    description: '收集创意灵感，确定故事核心概念',
    color: 'text-yellow-600',
    bgColor: 'bg-yellow-50 dark:bg-yellow-950/30',
    order: 0
  },
  { 
    value: 'WORLDBUILDING', 
    label: '🌍 世界观', 
    labelEn: 'Worldbuilding',
    icon: Globe,
    description: '设计世界观、力量体系、地理环境',
    color: 'text-blue-600',
    bgColor: 'bg-blue-50 dark:bg-blue-950/30',
    order: 1
  },
  { 
    value: 'CHARACTER', 
    label: '👥 角色', 
    labelEn: 'Character',
    icon: Users,
    description: '创建主要角色，设定性格、背景、关系',
    color: 'text-purple-600',
    bgColor: 'bg-purple-50 dark:bg-purple-950/30',
    order: 2
  },
  { 
    value: 'OUTLINE', 
    label: '🗺️ 大纲', 
    labelEn: 'Outline',
    icon: Map,
    description: '设计故事主线、分卷结构、章节大纲',
    color: 'text-green-600',
    bgColor: 'bg-green-50 dark:bg-green-950/30',
    order: 3
  },
  { 
    value: 'WRITING', 
    label: '📝 写作', 
    labelEn: 'Writing',
    icon: FileText,
    description: '按大纲进行章节创作',
    color: 'text-orange-600',
    bgColor: 'bg-orange-50 dark:bg-orange-950/30',
    order: 4
  },
  { 
    value: 'REVISION', 
    label: '🔧 修订', 
    labelEn: 'Revision',
    icon: Wrench,
    description: '检查一致性、优化文笔、修复漏洞',
    color: 'text-red-600',
    bgColor: 'bg-red-50 dark:bg-red-950/30',
    order: 5
  },
  { 
    value: 'COMPLETED', 
    label: '✅ 完成', 
    labelEn: 'Completed',
    icon: CheckCircle2,
    description: '作品已完结',
    color: 'text-emerald-600',
    bgColor: 'bg-emerald-50 dark:bg-emerald-950/30',
    order: 6
  },
];

// 旧版阶段到新版阶段的映射
const legacyPhaseMap: Record<LegacyCreationPhase, CreationPhase> = {
  'INSPIRATION': 'IDEA',
  'SETTING': 'WORLDBUILDING',
  'PLANNING': 'OUTLINE',
  'OUTLINING': 'OUTLINE',
  'EDITING': 'REVISION',
};

// 转换阶段值
const normalizePhase = (phase: CreationPhase | LegacyCreationPhase): CreationPhase => {
  if (phase in legacyPhaseMap) {
    return legacyPhaseMap[phase as LegacyCreationPhase];
  }
  return phase as CreationPhase;
};

// 获取阶段配置
const getPhaseConfig = (phase: CreationPhase): PhaseConfig => {
  return phases.find(p => p.value === phase) || phases[0];
};

export function PhaseSwitcher({ 
  currentPhase: externalPhase, 
  onPhaseChange: externalOnPhaseChange,
  integrated = false,
  compact = false
}: PhaseSwitcherProps) {
  // Store hooks (only used in integrated mode)
  const currentProject = useProjectStore(state => state.currentProject);
  const updatePhase = useProjectStore(state => state.updatePhase);
  const isLoading = useProjectStore(state => state.isLoading);

  // 决定使用哪个数据源
  const currentPhase = useMemo(() => {
    if (integrated) {
      return currentProject?.creationPhase || 'IDEA';
    }
    return normalizePhase(externalPhase || 'IDEA');
  }, [integrated, currentProject?.creationPhase, externalPhase]);

  // 获取当前阶段配置
  const currentPhaseConfig = useMemo(() => getPhaseConfig(currentPhase), [currentPhase]);

  // 处理阶段变更
  const handlePhaseChange = useCallback(async (newPhase: string) => {
    const phase = newPhase as CreationPhase;
    
    if (integrated && currentProject) {
      try {
        await updatePhase(currentProject.id, phase);
      } catch {
        // Error handled by store
      }
    } else if (externalOnPhaseChange) {
      externalOnPhaseChange(phase);
    }
  }, [integrated, currentProject, updatePhase, externalOnPhaseChange]);

  // 计算进度百分比
  const progressPercentage = useMemo(() => {
    return Math.round((currentPhaseConfig.order / (phases.length - 1)) * 100);
  }, [currentPhaseConfig.order]);

  return (
    <div className="p-4 border-b border-border">
      {/* 阶段选择器 */}
      <Select 
        value={currentPhase} 
        onValueChange={handlePhaseChange}
        disabled={integrated && isLoading}
      >
        <SelectTrigger 
          className={cn(
            "w-full rounded-xl shadow-sm transition-colors",
            currentPhaseConfig.bgColor,
            compact ? "h-10" : "h-12"
          )}
        >
          {integrated && isLoading ? (
            <div className="flex items-center gap-2">
              <Loader2 className="h-4 w-4 animate-spin" />
              <span className="text-sm">切换中...</span>
            </div>
          ) : (
            <SelectValue>
              <div className="flex items-center gap-2">
                <currentPhaseConfig.icon className={cn("h-4 w-4", currentPhaseConfig.color)} />
                <span className="font-medium">{currentPhaseConfig.label}</span>
              </div>
            </SelectValue>
          )}
        </SelectTrigger>
        <SelectContent className="max-h-[400px]">
          {phases.map((phase, index) => {
            const isCurrentPhase = phase.value === currentPhase;
            const isPastPhase = phase.order < currentPhaseConfig.order;
            
            return (
              <SelectItem 
                key={phase.value} 
                value={phase.value}
                className={cn(
                  "cursor-pointer transition-colors",
                  isCurrentPhase && phase.bgColor
                )}
              >
                <div className="flex items-center gap-3 py-1">
                  {/* 阶段图标 */}
                  <div className={cn(
                    "flex items-center justify-center w-8 h-8 rounded-full",
                    isCurrentPhase ? phase.bgColor : isPastPhase ? "bg-muted" : "bg-muted/50"
                  )}>
                    <phase.icon className={cn(
                      "h-4 w-4",
                      isCurrentPhase ? phase.color : isPastPhase ? "text-muted-foreground" : "text-muted-foreground/50"
                    )} />
                  </div>
                  
                  {/* 阶段信息 */}
                  <div className="flex flex-col flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className={cn(
                        "font-medium",
                        isCurrentPhase && phase.color
                      )}>
                        {phase.label}
                      </span>
                      {isCurrentPhase && (
                        <Badge variant="secondary" className="text-xs px-1.5 py-0">
                          当前
                        </Badge>
                      )}
                      {isPastPhase && (
                        <CheckCircle2 className="h-3.5 w-3.5 text-green-500" />
                      )}
                    </div>
                    <span className="text-xs text-muted-foreground truncate">
                      {phase.description}
                    </span>
                  </div>
                  
                  {/* 阶段序号 */}
                  <span className="text-xs text-muted-foreground">
                    {index + 1}/{phases.length}
                  </span>
                </div>
              </SelectItem>
            );
          })}
        </SelectContent>
      </Select>

      {/* 进度指示器 */}
      {!compact && (
        <div className="mt-3 space-y-2">
          {/* 进度条 */}
          <div className="flex items-center gap-2">
            <div className="flex-1 h-1.5 bg-muted rounded-full overflow-hidden">
              <div 
                className={cn(
                  "h-full rounded-full transition-all duration-500",
                  currentPhaseConfig.color.replace('text-', 'bg-')
                )}
                style={{ width: `${progressPercentage}%` }}
              />
            </div>
            <span className="text-xs text-muted-foreground font-medium">
              {progressPercentage}%
            </span>
          </div>
          
          {/* 阶段流程指示 */}
          <div className="flex items-center justify-between text-xs text-muted-foreground">
            <span>创作进度</span>
            <div className="flex items-center gap-1">
              <span>{currentPhaseConfig.labelEn}</span>
              {currentPhase !== 'COMPLETED' && (
                <>
                  <ChevronRight className="h-3 w-3" />
                  <span className="text-muted-foreground/60">
                    {phases[currentPhaseConfig.order + 1]?.labelEn || ''}
                  </span>
                </>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// 导出阶段配置供其他组件使用
export { phases, getPhaseConfig, normalizePhase };
export type { PhaseConfig };
