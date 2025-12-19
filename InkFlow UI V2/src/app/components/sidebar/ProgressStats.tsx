/**
 * 进度统计组件
 * 显示总字数、今日字数、每日目标和周活动图表
 * 
 * Requirements: 7.1, 7.2, 7.3, 7.4
 */

import { useEffect, useMemo, useCallback, useState } from 'react';
import {
  FileText,
  Target,
  TrendingUp,
  Loader2,
  Settings,
  Sparkles,
} from 'lucide-react';
import { Progress } from '../ui/progress';
import { Button } from '../ui/button';
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '../ui/tooltip';
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '../ui/popover';
import { Input } from '../ui/input';
import { Label } from '../ui/label';
import { useProgressStore, selectTodayWordCount, selectDailyGoalProgress } from '@/stores/progress-store';
import { useProjectStore } from '@/stores/project-store';
import { CelebrationAnimation } from './CelebrationAnimation';

interface ProgressStatsProps {
  /** 是否使用集成模式（连接 store） */
  integrated?: boolean;
  /** 外部传入的总字数（非集成模式） */
  totalWordCount?: number;
  /** 外部传入的今日字数（非集成模式） */
  todayWordCount?: number;
  /** 外部传入的每日目标（非集成模式） */
  dailyGoal?: number;
  /** 外部传入的周活动数据（非集成模式） */
  weeklyActivity?: { date: string; wordCount: number; wordCountChange: number }[];
}

/**
 * 格式化数字（添加千分位）
 */
function formatNumber(num: number): string {
  return num.toLocaleString('zh-CN');
}

/**
 * 周活动图表组件
 */
function WeeklyActivityChart({ 
  data 
}: { 
  data: { date: string; wordCount: number; wordCountChange: number }[] 
}) {
  // 获取最近7天的数据
  const chartData = useMemo(() => {
    const today = new Date();
    const days: { date: string; label: string; value: number }[] = [];
    
    for (let i = 6; i >= 0; i--) {
      const date = new Date(today);
      date.setDate(date.getDate() - i);
      const dateStr = date.toISOString().split('T')[0];
      const dayLabel = ['日', '一', '二', '三', '四', '五', '六'][date.getDay()];
      
      const activity = data.find(d => d.date === dateStr);
      days.push({
        date: dateStr,
        label: dayLabel,
        value: activity?.wordCountChange || 0,
      });
    }
    
    return days;
  }, [data]);

  // 计算最大值用于归一化
  const maxValue = useMemo(() => {
    const max = Math.max(...chartData.map(d => d.value), 1);
    return max;
  }, [chartData]);

  return (
    <div className="flex items-end gap-1 h-12">
      {chartData.map((day, index) => {
        const height = day.value > 0 ? Math.max(4, (day.value / maxValue) * 100) : 4;
        const isToday = index === chartData.length - 1;
        
        return (
          <TooltipProvider key={day.date}>
            <Tooltip>
              <TooltipTrigger asChild>
                <div className="flex flex-col items-center gap-1 flex-1">
                  <div 
                    className={`w-full rounded-sm transition-all ${
                      isToday 
                        ? 'bg-primary' 
                        : day.value > 0 
                          ? 'bg-primary/60' 
                          : 'bg-muted'
                    }`}
                    style={{ height: `${height}%`, minHeight: '4px' }}
                  />
                  <span className={`text-[10px] ${isToday ? 'text-primary font-medium' : 'text-muted-foreground'}`}>
                    {day.label}
                  </span>
                </div>
              </TooltipTrigger>
              <TooltipContent>
                <p>{day.date}</p>
                <p className="font-medium">{formatNumber(day.value)} 字</p>
              </TooltipContent>
            </Tooltip>
          </TooltipProvider>
        );
      })}
    </div>
  );
}

/**
 * 每日目标设置弹窗
 */
function DailyGoalSettings({ 
  currentGoal, 
  onSave 
}: { 
  currentGoal: number; 
  onSave: (goal: number) => void;
}) {
  const [goal, setGoal] = useState(currentGoal);
  const [open, setOpen] = useState(false);

  const handleSave = useCallback(() => {
    onSave(goal);
    setOpen(false);
  }, [goal, onSave]);

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button variant="ghost" size="icon" className="h-6 w-6">
          <Settings className="h-3 w-3" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-64" align="end">
        <div className="space-y-3">
          <div className="space-y-1">
            <Label htmlFor="daily-goal">每日目标（字数）</Label>
            <Input
              id="daily-goal"
              type="number"
              min={100}
              max={50000}
              step={100}
              value={goal}
              onChange={(e) => setGoal(parseInt(e.target.value, 10) || 0)}
            />
          </div>
          <div className="flex gap-2">
            <Button size="sm" variant="outline" className="flex-1" onClick={() => setOpen(false)}>
              取消
            </Button>
            <Button size="sm" className="flex-1" onClick={handleSave}>
              保存
            </Button>
          </div>
        </div>
      </PopoverContent>
    </Popover>
  );
}

export function ProgressStats({
  integrated = false,
  totalWordCount: externalTotalWordCount,
  todayWordCount: externalTodayWordCount,
  dailyGoal: externalDailyGoal,
  weeklyActivity: externalWeeklyActivity,
}: ProgressStatsProps) {
  // Store hooks
  const currentProject = useProjectStore(state => state.currentProject);
  const {
    progress,
    weeklyActivity: storeWeeklyActivity,
    dailyGoal: storeDailyGoal,
    isLoading,
    showCelebration,
    fetchAll,
    setDailyGoal,
    dismissCelebration,
  } = useProgressStore();

  // 计算今日字数和目标进度
  const storeTodayWordCount = useProgressStore(selectTodayWordCount);
  const storeDailyGoalProgress = useProgressStore(selectDailyGoalProgress);

  // 决定使用哪个数据源
  const totalWordCount = integrated ? (progress?.wordCount || 0) : (externalTotalWordCount || 0);
  const todayWordCount = integrated ? storeTodayWordCount : (externalTodayWordCount || 0);
  const dailyGoal = integrated ? storeDailyGoal : (externalDailyGoal || 2000);
  const weeklyActivity = integrated ? storeWeeklyActivity : (externalWeeklyActivity || []);

  // 计算目标进度
  const goalProgress = integrated 
    ? storeDailyGoalProgress 
    : (dailyGoal > 0 ? Math.min(100, Math.round((todayWordCount / dailyGoal) * 100)) : 0);

  // 集成模式下加载数据
  useEffect(() => {
    if (integrated && currentProject) {
      fetchAll(currentProject.id);
    }
  }, [integrated, currentProject, fetchAll]);

  // 处理每日目标保存
  const handleDailyGoalSave = useCallback((goal: number) => {
    if (integrated) {
      setDailyGoal(goal);
    }
  }, [integrated, setDailyGoal]);

  return (
    <div className="border-t border-border px-4 py-3 space-y-3">
      {/* 庆祝动画 */}
      {integrated && showCelebration && (
        <CelebrationAnimation onComplete={dismissCelebration} />
      )}

      {/* 标题栏 */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2 text-sm font-medium">
          <TrendingUp className="h-4 w-4 text-primary" />
          <span>创作进度</span>
          {integrated && isLoading && (
            <Loader2 className="h-3 w-3 animate-spin" />
          )}
        </div>
        {integrated && (
          <DailyGoalSettings currentGoal={dailyGoal} onSave={handleDailyGoalSave} />
        )}
      </div>

      {/* 总字数 */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2 text-muted-foreground">
          <FileText className="h-3.5 w-3.5" />
          <span className="text-xs">总字数</span>
        </div>
        <span className="text-sm font-medium">{formatNumber(totalWordCount)}</span>
      </div>

      {/* 今日字数与目标 */}
      <div className="space-y-1.5">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-muted-foreground">
            <Target className="h-3.5 w-3.5" />
            <span className="text-xs">今日目标</span>
          </div>
          <div className="flex items-center gap-1">
            <span className={`text-sm font-medium ${goalProgress >= 100 ? 'text-green-500' : ''}`}>
              {formatNumber(todayWordCount)}
            </span>
            <span className="text-xs text-muted-foreground">/ {formatNumber(dailyGoal)}</span>
            {goalProgress >= 100 && (
              <Sparkles className="h-3.5 w-3.5 text-yellow-500 ml-1" />
            )}
          </div>
        </div>
        <Progress value={goalProgress} className="h-1.5" />
        <div className="text-right">
          <span className={`text-xs ${goalProgress >= 100 ? 'text-green-500 font-medium' : 'text-muted-foreground'}`}>
            {goalProgress}%
          </span>
        </div>
      </div>

      {/* 周活动图表 */}
      <div className="space-y-2">
        <div className="text-xs text-muted-foreground">本周活动</div>
        <WeeklyActivityChart data={weeklyActivity} />
      </div>
    </div>
  );
}
