/**
 * 一致性警告指示器组件
 * 在侧边栏显示警告数量徽章，点击打开警告面板
 * 
 * Requirements: 7.2, 7.3
 */

import { useEffect } from 'react';
import { AlertTriangle, AlertCircle, Info } from 'lucide-react';
import { Badge } from '../ui/badge';
import { Button } from '../ui/button';
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '../ui/tooltip';
import { useConsistencyStore, selectWarningCount } from '@/stores/consistency-store';
import { useProjectStore } from '@/stores/project-store';

interface ConsistencyWarningIndicatorProps {
  /** 是否显示详细信息 */
  showDetails?: boolean;
  /** 自定义类名 */
  className?: string;
}

export function ConsistencyWarningIndicator({
  showDetails = false,
  className = '',
}: ConsistencyWarningIndicatorProps) {
  const currentProject = useProjectStore(state => state.currentProject);
  const warningCount = useConsistencyStore(selectWarningCount);
  const { fetchWarningCount, togglePanel } = useConsistencyStore();

  // 加载警告数量
  useEffect(() => {
    if (currentProject) {
      fetchWarningCount(currentProject.id);
      
      // 定期刷新（每30秒）
      const interval = setInterval(() => {
        fetchWarningCount(currentProject.id);
      }, 30000);
      
      return () => clearInterval(interval);
    }
  }, [currentProject, fetchWarningCount]);

  // 没有警告时不显示
  if (!warningCount || warningCount.total === 0) {
    return null;
  }

  // 确定显示的图标和颜色
  const getIndicatorStyle = () => {
    if (warningCount.error > 0) {
      return {
        icon: AlertCircle,
        variant: 'destructive' as const,
        color: 'text-destructive',
        bgColor: 'bg-destructive/10',
      };
    }
    if (warningCount.warning > 0) {
      return {
        icon: AlertTriangle,
        variant: 'default' as const,
        color: 'text-amber-500',
        bgColor: 'bg-amber-500/10',
      };
    }
    return {
      icon: Info,
      variant: 'secondary' as const,
      color: 'text-blue-500',
      bgColor: 'bg-blue-500/10',
    };
  };

  const style = getIndicatorStyle();
  const Icon = style.icon;

  // 简洁模式：只显示图标和数量
  if (!showDetails) {
    return (
      <TooltipProvider>
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant="ghost"
              size="sm"
              onClick={togglePanel}
              className={`relative p-2 ${className}`}
            >
              <Icon className={`h-5 w-5 ${style.color}`} />
              <Badge
                variant={style.variant}
                className="absolute -top-1 -right-1 h-5 min-w-5 px-1 text-[10px]"
              >
                {warningCount.total > 99 ? '99+' : warningCount.total}
              </Badge>
            </Button>
          </TooltipTrigger>
          <TooltipContent side="right">
            <div className="text-sm">
              <p className="font-medium">一致性警告</p>
              <div className="mt-1 space-y-0.5 text-xs text-muted-foreground">
                {warningCount.error > 0 && (
                  <p className="text-destructive">{warningCount.error} 个错误</p>
                )}
                {warningCount.warning > 0 && (
                  <p className="text-amber-500">{warningCount.warning} 个警告</p>
                )}
                {warningCount.info > 0 && (
                  <p className="text-blue-500">{warningCount.info} 个提示</p>
                )}
              </div>
            </div>
          </TooltipContent>
        </Tooltip>
      </TooltipProvider>
    );
  }

  // 详细模式：显示完整信息
  return (
    <Button
      variant="ghost"
      onClick={togglePanel}
      className={`w-full justify-start gap-3 px-3 py-2 h-auto ${style.bgColor} hover:${style.bgColor} ${className}`}
    >
      <Icon className={`h-5 w-5 ${style.color} flex-shrink-0`} />
      <div className="flex-1 text-left">
        <p className="text-sm font-medium">一致性检查</p>
        <p className="text-xs text-muted-foreground">
          {warningCount.total} 个问题待处理
        </p>
      </div>
      <div className="flex gap-1">
        {warningCount.error > 0 && (
          <Badge variant="destructive" className="text-[10px] px-1.5">
            {warningCount.error}
          </Badge>
        )}
        {warningCount.warning > 0 && (
          <Badge className="bg-amber-500 text-white text-[10px] px-1.5">
            {warningCount.warning}
          </Badge>
        )}
        {warningCount.info > 0 && (
          <Badge variant="secondary" className="text-[10px] px-1.5">
            {warningCount.info}
          </Badge>
        )}
      </div>
    </Button>
  );
}

export default ConsistencyWarningIndicator;
