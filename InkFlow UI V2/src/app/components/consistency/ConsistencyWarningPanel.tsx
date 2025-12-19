/**
 * 一致性警告面板组件
 * 显示警告列表，按严重程度分组，支持解决和忽略操作
 * 
 * Requirements: 7.3, 7.4, 7.5
 */

import { useEffect, useState, useCallback } from 'react';
import {
  AlertCircle,
  AlertTriangle,
  Info,
  Check,
  X,
  ChevronDown,
  ChevronRight,
  Loader2,
  RefreshCw,
  Filter,
} from 'lucide-react';
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
} from '../ui/sheet';
import { Button } from '../ui/button';
import { Badge } from '../ui/badge';
import { ScrollArea } from '../ui/scroll-area';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '../ui/select';
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '../ui/collapsible';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '../ui/dialog';
import { Input } from '../ui/input';
import { Label } from '../ui/label';
import {
  useConsistencyStore,
  selectWarnings,
  selectWarningsBySeverity,
  selectIsLoading,
  selectIsPanelOpen,
} from '@/stores/consistency-store';
import { useProjectStore } from '@/stores/project-store';
import type { ConsistencyWarning, Severity } from '@/services/consistency-service';

// 严重程度配置
const SEVERITY_CONFIG: Record<Severity, {
  label: string;
  icon: typeof AlertCircle;
  color: string;
  bgColor: string;
  borderColor: string;
}> = {
  ERROR: {
    label: '错误',
    icon: AlertCircle,
    color: 'text-destructive',
    bgColor: 'bg-destructive/10',
    borderColor: 'border-destructive/30',
  },
  WARNING: {
    label: '警告',
    icon: AlertTriangle,
    color: 'text-amber-500',
    bgColor: 'bg-amber-500/10',
    borderColor: 'border-amber-500/30',
  },
  INFO: {
    label: '提示',
    icon: Info,
    color: 'text-blue-500',
    bgColor: 'bg-blue-500/10',
    borderColor: 'border-blue-500/30',
  },
};

// 警告类型标签
const WARNING_TYPE_LABELS: Record<string, string> = {
  NAME_CONFLICT: '名称冲突',
  MISSING_FIELD: '缺失字段',
  RELATIONSHIP_INCONSISTENCY: '关系不一致',
  TIMELINE_CONFLICT: '时间线冲突',
  PLOT_HOLE: '情节漏洞',
  CHARACTER_INCONSISTENCY: '角色不一致',
};

// 实体类型标签
const ENTITY_TYPE_LABELS: Record<string, string> = {
  CHARACTER: '角色',
  WIKI_ENTRY: '设定',
  PLOT_LOOP: '伏笔',
  CHAPTER: '章节',
};

interface WarningItemProps {
  warning: ConsistencyWarning;
  onResolve: (id: string) => void;
  onDismiss: (id: string) => void;
}

function WarningItem({ warning, onResolve, onDismiss }: WarningItemProps) {
  const config = SEVERITY_CONFIG[warning.severity];
  const Icon = config.icon;

  return (
    <div
      className={`p-3 rounded-lg border ${config.borderColor} ${config.bgColor} transition-all hover:shadow-sm`}
    >
      <div className="flex items-start gap-3">
        <Icon className={`h-5 w-5 ${config.color} flex-shrink-0 mt-0.5`} />
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <span className="font-medium text-sm truncate">
              {warning.entityName}
            </span>
            <Badge variant="outline" className="text-[10px] px-1.5 py-0">
              {ENTITY_TYPE_LABELS[warning.entityType] || warning.entityType}
            </Badge>
            <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
              {WARNING_TYPE_LABELS[warning.warningType] || warning.warningType}
            </Badge>
          </div>
          <p className="text-sm text-muted-foreground mb-2">
            {warning.description}
          </p>
          {warning.suggestion && (
            <p className="text-xs text-muted-foreground/80 italic mb-2">
              💡 {warning.suggestion}
            </p>
          )}
          {(warning.expectedValue || warning.actualValue) && (
            <div className="text-xs space-y-0.5 mb-2">
              {warning.expectedValue && (
                <p>
                  <span className="text-muted-foreground">期望: </span>
                  <span className="text-green-600">{warning.expectedValue}</span>
                </p>
              )}
              {warning.actualValue && (
                <p>
                  <span className="text-muted-foreground">实际: </span>
                  <span className="text-red-600">{warning.actualValue}</span>
                </p>
              )}
            </div>
          )}
          <div className="flex items-center gap-2 mt-2">
            <Button
              size="sm"
              variant="outline"
              className="h-7 text-xs"
              onClick={() => onResolve(warning.id)}
            >
              <Check className="h-3 w-3 mr-1" />
              解决
            </Button>
            <Button
              size="sm"
              variant="ghost"
              className="h-7 text-xs text-muted-foreground"
              onClick={() => onDismiss(warning.id)}
            >
              <X className="h-3 w-3 mr-1" />
              忽略
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}

interface WarningSectionProps {
  severity: Severity;
  warnings: ConsistencyWarning[];
  onResolve: (id: string) => void;
  onDismiss: (id: string) => void;
  defaultOpen?: boolean;
}

function WarningSection({
  severity,
  warnings,
  onResolve,
  onDismiss,
  defaultOpen = true,
}: WarningSectionProps) {
  const [isOpen, setIsOpen] = useState(defaultOpen);
  const config = SEVERITY_CONFIG[severity];
  const Icon = config.icon;

  if (warnings.length === 0) return null;

  return (
    <Collapsible open={isOpen} onOpenChange={setIsOpen}>
      <CollapsibleTrigger asChild>
        <Button
          variant="ghost"
          className="w-full justify-between px-3 py-2 h-auto"
        >
          <div className="flex items-center gap-2">
            <Icon className={`h-4 w-4 ${config.color}`} />
            <span className="font-medium">{config.label}</span>
            <Badge variant="secondary" className="text-xs">
              {warnings.length}
            </Badge>
          </div>
          {isOpen ? (
            <ChevronDown className="h-4 w-4" />
          ) : (
            <ChevronRight className="h-4 w-4" />
          )}
        </Button>
      </CollapsibleTrigger>
      <CollapsibleContent className="space-y-2 px-1 pb-3">
        {warnings.map((warning) => (
          <WarningItem
            key={warning.id}
            warning={warning}
            onResolve={onResolve}
            onDismiss={onDismiss}
          />
        ))}
      </CollapsibleContent>
    </Collapsible>
  );
}

export function ConsistencyWarningPanel() {
  const currentProject = useProjectStore((state) => state.currentProject);
  const isPanelOpen = useConsistencyStore(selectIsPanelOpen);
  const warnings = useConsistencyStore(selectWarnings);
  const warningsBySeverity = useConsistencyStore(selectWarningsBySeverity);
  const isLoading = useConsistencyStore(selectIsLoading);
  const {
    closePanel,
    fetchWarnings,
    resolveWarning,
    dismissWarning,
    filterSeverity,
    setFilterSeverity,
  } = useConsistencyStore();

  // 解决对话框状态
  const [resolveDialogOpen, setResolveDialogOpen] = useState(false);
  const [selectedWarningId, setSelectedWarningId] = useState<string | null>(null);
  const [resolutionMethod, setResolutionMethod] = useState('');

  // 加载警告
  useEffect(() => {
    if (isPanelOpen && currentProject) {
      fetchWarnings(currentProject.id, true);
    }
  }, [isPanelOpen, currentProject, fetchWarnings]);

  // 处理解决
  const handleResolve = useCallback((warningId: string) => {
    setSelectedWarningId(warningId);
    setResolutionMethod('');
    setResolveDialogOpen(true);
  }, []);

  // 确认解决
  const confirmResolve = useCallback(async () => {
    if (!selectedWarningId) return;
    
    try {
      await resolveWarning(selectedWarningId, resolutionMethod || '手动解决');
      setResolveDialogOpen(false);
      setSelectedWarningId(null);
    } catch {
      // Error handled by store
    }
  }, [selectedWarningId, resolutionMethod, resolveWarning]);

  // 处理忽略
  const handleDismiss = useCallback(async (warningId: string) => {
    try {
      await dismissWarning(warningId);
    } catch {
      // Error handled by store
    }
  }, [dismissWarning]);

  // 刷新
  const handleRefresh = useCallback(() => {
    if (currentProject) {
      fetchWarnings(currentProject.id, true);
    }
  }, [currentProject, fetchWarnings]);

  return (
    <>
      <Sheet open={isPanelOpen} onOpenChange={(open) => !open && closePanel()}>
        <SheetContent side="right" className="w-[400px] sm:w-[450px] p-0">
          <SheetHeader className="px-4 pt-4 pb-2 border-b">
            <div className="flex items-center justify-between">
              <SheetTitle>一致性检查</SheetTitle>
              <Button
                variant="ghost"
                size="icon"
                className="h-8 w-8"
                onClick={handleRefresh}
                disabled={isLoading}
              >
                <RefreshCw className={`h-4 w-4 ${isLoading ? 'animate-spin' : ''}`} />
              </Button>
            </div>
            <SheetDescription>
              检测到 {warnings.length} 个问题需要处理
            </SheetDescription>
          </SheetHeader>

          {/* 过滤器 */}
          <div className="px-4 py-2 border-b flex items-center gap-2">
            <Filter className="h-4 w-4 text-muted-foreground" />
            <Select
              value={filterSeverity ?? 'all'}
              onValueChange={(value) =>
                setFilterSeverity(value === 'all' ? null : (value as Severity))
              }
            >
              <SelectTrigger className="h-8 w-[120px]">
                <SelectValue placeholder="全部" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">全部</SelectItem>
                <SelectItem value="ERROR">错误</SelectItem>
                <SelectItem value="WARNING">警告</SelectItem>
                <SelectItem value="INFO">提示</SelectItem>
              </SelectContent>
            </Select>
          </div>

          {/* 警告列表 */}
          <ScrollArea className="flex-1 h-[calc(100vh-180px)]">
            {isLoading && warnings.length === 0 ? (
              <div className="flex items-center justify-center py-12">
                <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
              </div>
            ) : warnings.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-12 px-4 text-center">
                <Check className="h-12 w-12 text-green-500 mb-3" />
                <p className="text-sm font-medium">没有发现问题</p>
                <p className="text-xs text-muted-foreground mt-1">
                  您的项目设定保持一致
                </p>
              </div>
            ) : (
              <div className="p-2 space-y-1">
                <WarningSection
                  severity="ERROR"
                  warnings={warningsBySeverity.error}
                  onResolve={handleResolve}
                  onDismiss={handleDismiss}
                  defaultOpen={true}
                />
                <WarningSection
                  severity="WARNING"
                  warnings={warningsBySeverity.warning}
                  onResolve={handleResolve}
                  onDismiss={handleDismiss}
                  defaultOpen={warningsBySeverity.error.length === 0}
                />
                <WarningSection
                  severity="INFO"
                  warnings={warningsBySeverity.info}
                  onResolve={handleResolve}
                  onDismiss={handleDismiss}
                  defaultOpen={
                    warningsBySeverity.error.length === 0 &&
                    warningsBySeverity.warning.length === 0
                  }
                />
              </div>
            )}
          </ScrollArea>
        </SheetContent>
      </Sheet>

      {/* 解决对话框 */}
      <Dialog open={resolveDialogOpen} onOpenChange={setResolveDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>解决警告</DialogTitle>
            <DialogDescription>
              请描述您是如何解决这个问题的（可选）
            </DialogDescription>
          </DialogHeader>
          <div className="py-4">
            <Label htmlFor="resolution">解决方法</Label>
            <Input
              id="resolution"
              value={resolutionMethod}
              onChange={(e) => setResolutionMethod(e.target.value)}
              placeholder="例如：已修正角色名称"
              className="mt-2"
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setResolveDialogOpen(false)}>
              取消
            </Button>
            <Button onClick={confirmResolve}>确认解决</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}

export default ConsistencyWarningPanel;
