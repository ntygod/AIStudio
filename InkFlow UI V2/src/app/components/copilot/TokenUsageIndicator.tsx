/**
 * Token 使用量指示器组件
 * 显示今日 Token 使用量和配额
 * 
 * Requirements: 10.1, 10.2, 10.3, 10.4, 10.5
 */

import { useState, useEffect, useCallback } from 'react';
import { Coins, TrendingUp, AlertTriangle, ChevronDown, ChevronUp } from 'lucide-react';
import { Button } from '../ui/button';
import { Progress } from '../ui/progress';
import { motion, AnimatePresence } from 'motion/react';
import { usageService, type TokenUsage, type TokenUsageDetail } from '@/services/usage-service';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/app/components/ui/tooltip';

interface TokenUsageIndicatorProps {
  /** 是否使用集成模式（自动获取数据） */
  integrated?: boolean;
  /** 外部传入的使用量数据（非集成模式） */
  todayUsage?: number;
  /** 外部传入的配额数据（非集成模式） */
  dailyQuota?: number;
  /** 点击回调 */
  onClick?: () => void;
}

// 警告阈值（80%）
const WARNING_THRESHOLD = 0.8;

// 格式化数字
const formatNumber = (num: number): string => {
  if (num >= 1000000) {
    return `${(num / 1000000).toFixed(1)}M`;
  }
  if (num >= 1000) {
    return `${(num / 1000).toFixed(1)}K`;
  }
  return num.toString();
};

export function TokenUsageIndicator({
  integrated = false,
  todayUsage: externalUsage,
  dailyQuota: externalQuota,
  onClick
}: TokenUsageIndicatorProps) {
  const [usage, setUsage] = useState<TokenUsage | null>(null);
  const [detail, setDetail] = useState<TokenUsageDetail | null>(null);
  const [isExpanded, setIsExpanded] = useState(false);
  const [_isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 获取使用量数据
  const fetchUsage = useCallback(async () => {
    if (!integrated) return;

    setIsLoading(true);
    setError(null);

    try {
      const data = await usageService.getTodayUsage();
      setUsage(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : '获取使用量失败');
    } finally {
      setIsLoading(false);
    }
  }, [integrated]);

  // 获取详细数据
  const fetchDetail = useCallback(async () => {
    if (!integrated) return;

    try {
      const data = await usageService.getUsageDetail();
      setDetail(data);
    } catch (err) {
      console.error('Failed to fetch usage detail:', err);
    }
  }, [integrated]);

  // 初始加载
  useEffect(() => {
    fetchUsage();
  }, [fetchUsage]);

  // 展开时加载详细数据
  useEffect(() => {
    if (isExpanded && !detail) {
      fetchDetail();
    }
  }, [isExpanded, detail, fetchDetail]);

  // 计算使用量数据
  const todayUsage = integrated ? (usage?.todayUsage ?? 0) : (externalUsage ?? 0);
  const dailyQuota = integrated ? (usage?.dailyQuota ?? 100000) : (externalQuota ?? 100000);
  const usagePercentage = dailyQuota > 0 ? (todayUsage / dailyQuota) * 100 : 0;
  const isWarning = usagePercentage >= WARNING_THRESHOLD * 100;

  // 处理点击
  const handleClick = useCallback(() => {
    if (onClick) {
      onClick();
    } else {
      setIsExpanded(!isExpanded);
    }
  }, [onClick, isExpanded]);

  // 获取进度条颜色
  const getProgressColor = () => {
    if (usagePercentage >= 90) return 'bg-destructive';
    if (usagePercentage >= 80) return 'bg-yellow-500';
    return 'bg-primary';
  };

  return (
    <TooltipProvider>
      <div className="border-b border-border bg-card">
        {/* 主指示器 */}
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant="ghost"
              className="w-full px-5 py-3 h-auto justify-between hover:bg-accent/50"
              onClick={handleClick}
            >
              <div className="flex items-center gap-3">
                <div className={`p-1.5 rounded-lg ${isWarning ? 'bg-yellow-500/10' : 'bg-accent'}`}>
                  {isWarning ? (
                    <AlertTriangle className="h-4 w-4 text-yellow-500" />
                  ) : (
                    <Coins className="h-4 w-4 text-muted-foreground" />
                  )}
                </div>
                <div className="text-left">
                  <div className="text-xs text-muted-foreground">今日 Token</div>
                  <div className={`text-sm font-medium ${isWarning ? 'text-yellow-500' : ''}`}>
                    {formatNumber(todayUsage)} / {formatNumber(dailyQuota)}
                  </div>
                </div>
              </div>

              <div className="flex items-center gap-2">
                <div className="w-16">
                  <Progress 
                    value={Math.min(usagePercentage, 100)} 
                    className="h-1.5"
                  />
                </div>
                <span className={`text-xs ${isWarning ? 'text-yellow-500' : 'text-muted-foreground'}`}>
                  {usagePercentage.toFixed(0)}%
                </span>
                {isExpanded ? (
                  <ChevronUp className="h-4 w-4 text-muted-foreground" />
                ) : (
                  <ChevronDown className="h-4 w-4 text-muted-foreground" />
                )}
              </div>
            </Button>
          </TooltipTrigger>
          <TooltipContent side="left">
            <p>点击查看详细使用情况</p>
          </TooltipContent>
        </Tooltip>

        {/* 展开的详细面板 */}
        <AnimatePresence>
          {isExpanded && (
            <motion.div
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: 'auto', opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              transition={{ duration: 0.2 }}
              className="overflow-hidden"
            >
              <div className="px-5 pb-4 space-y-4">
                {/* 使用量进度条 */}
                <div className="space-y-2">
                  <div className="flex justify-between text-xs text-muted-foreground">
                    <span>已使用</span>
                    <span>剩余 {formatNumber(dailyQuota - todayUsage)}</span>
                  </div>
                  <div className="h-2 bg-accent rounded-full overflow-hidden">
                    <motion.div
                      className={`h-full ${getProgressColor()}`}
                      initial={{ width: 0 }}
                      animate={{ width: `${Math.min(usagePercentage, 100)}%` }}
                      transition={{ duration: 0.5, ease: 'easeOut' }}
                    />
                  </div>
                </div>

                {/* 操作类型分解 */}
                {detail?.breakdown && detail.breakdown.length > 0 && (
                  <div className="space-y-2">
                    <div className="text-xs font-medium text-muted-foreground flex items-center gap-1">
                      <TrendingUp className="h-3 w-3" />
                      按操作类型
                    </div>
                    <div className="space-y-1.5">
                      {detail.breakdown.map((item) => (
                        <div key={item.operation} className="flex items-center justify-between text-xs">
                          <span className="text-muted-foreground">{item.operation}</span>
                          <span>{formatNumber(item.usage)} ({item.percentage.toFixed(0)}%)</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* 周趋势 */}
                {detail?.weeklyTrend && detail.weeklyTrend.length > 0 && (
                  <div className="space-y-2">
                    <div className="text-xs font-medium text-muted-foreground">本周趋势</div>
                    <div className="flex items-end gap-1 h-12">
                      {detail.weeklyTrend.map((day) => {
                        const maxUsage = Math.max(...detail.weeklyTrend.map(d => d.usage));
                        const height = maxUsage > 0 ? (day.usage / maxUsage) * 100 : 0;
                        return (
                          <Tooltip key={day.date}>
                            <TooltipTrigger asChild>
                              <div
                                className="flex-1 bg-primary/20 rounded-t cursor-help hover:bg-primary/30 transition-colors"
                                style={{ height: `${Math.max(height, 4)}%` }}
                              />
                            </TooltipTrigger>
                            <TooltipContent>
                              <p className="text-xs">{day.date}</p>
                              <p className="text-xs font-medium">{formatNumber(day.usage)} tokens</p>
                            </TooltipContent>
                          </Tooltip>
                        );
                      })}
                    </div>
                    <div className="flex justify-between text-[10px] text-muted-foreground">
                      {detail.weeklyTrend.slice(0, 7).map((day) => (
                        <span key={day.date}>{new Date(day.date).toLocaleDateString('zh-CN', { weekday: 'narrow' })}</span>
                      ))}
                    </div>
                  </div>
                )}

                {/* 警告提示 */}
                {isWarning && (
                  <motion.div
                    initial={{ opacity: 0, y: -10 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="p-2 rounded-lg bg-yellow-500/10 border border-yellow-500/20"
                  >
                    <div className="flex items-center gap-2 text-xs text-yellow-600 dark:text-yellow-400">
                      <AlertTriangle className="h-3 w-3 flex-shrink-0" />
                      <span>使用量已超过 80%，请注意控制使用</span>
                    </div>
                  </motion.div>
                )}

                {/* 错误提示 */}
                {error && (
                  <div className="p-2 rounded-lg bg-destructive/10 border border-destructive/20">
                    <div className="text-xs text-destructive">{error}</div>
                  </div>
                )}
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </TooltipProvider>
  );
}
