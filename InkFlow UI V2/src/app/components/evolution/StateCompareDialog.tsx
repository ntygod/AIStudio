/**
 * 状态对比对话框组件
 * 对比两个快照的差异，高亮显示变化字段
 * 
 * Requirements: 8.4
 */

import { useEffect, useMemo } from 'react';
import {
  GitCompare,
  ArrowRight,
  Plus,
  Minus,
  Edit,
  Loader2,
  AlertCircle,
} from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '../ui/dialog';
import { Badge } from '../ui/badge';
import { ScrollArea } from '../ui/scroll-area';
import { Separator } from '../ui/separator';
import {
  useEvolutionStore,
  selectCompareState,
} from '@/stores/evolution-store';
import type { StateChange } from '@/services/evolution-service';

// 变更类型
type DiffType = 'added' | 'removed' | 'modified';

interface DiffItemProps {
  fieldPath: string;
  change: StateChange;
}

function getDiffType(change: StateChange): DiffType {
  if (change.oldValue === null || change.oldValue === undefined) {
    return 'added';
  }
  if (change.newValue === null || change.newValue === undefined) {
    return 'removed';
  }
  return 'modified';
}

const DIFF_CONFIG: Record<DiffType, {
  label: string;
  icon: typeof Plus;
  color: string;
  bgColor: string;
  borderColor: string;
}> = {
  added: {
    label: '新增',
    icon: Plus,
    color: 'text-green-600',
    bgColor: 'bg-green-50',
    borderColor: 'border-green-200',
  },
  removed: {
    label: '删除',
    icon: Minus,
    color: 'text-red-600',
    bgColor: 'bg-red-50',
    borderColor: 'border-red-200',
  },
  modified: {
    label: '修改',
    icon: Edit,
    color: 'text-blue-600',
    bgColor: 'bg-blue-50',
    borderColor: 'border-blue-200',
  },
};

function formatValue(value: unknown): string {
  if (value === null || value === undefined) {
    return '(空)';
  }
  if (typeof value === 'object') {
    return JSON.stringify(value, null, 2);
  }
  return String(value);
}

function DiffItem({ fieldPath, change }: DiffItemProps) {
  const diffType = getDiffType(change);
  const config = DIFF_CONFIG[diffType];
  const Icon = config.icon;

  return (
    <div className={`p-3 rounded-lg border ${config.borderColor} ${config.bgColor}`}>
      {/* 字段路径和类型 */}
      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-2">
          <Icon className={`h-4 w-4 ${config.color}`} />
          <code className="text-sm font-mono bg-background/50 px-1.5 py-0.5 rounded">
            {fieldPath}
          </code>
        </div>
        <Badge variant="outline" className={`text-xs ${config.color}`}>
          {config.label}
        </Badge>
      </div>

      {/* 值对比 */}
      <div className="space-y-2">
        {diffType !== 'added' && (
          <div className="flex items-start gap-2">
            <span className="text-xs text-muted-foreground w-12 flex-shrink-0 pt-0.5">
              旧值:
            </span>
            <pre className="text-xs bg-red-100/50 text-red-800 px-2 py-1 rounded flex-1 overflow-x-auto whitespace-pre-wrap break-all">
              {formatValue(change.oldValue)}
            </pre>
          </div>
        )}
        {diffType !== 'removed' && (
          <div className="flex items-start gap-2">
            <span className="text-xs text-muted-foreground w-12 flex-shrink-0 pt-0.5">
              新值:
            </span>
            <pre className="text-xs bg-green-100/50 text-green-800 px-2 py-1 rounded flex-1 overflow-x-auto whitespace-pre-wrap break-all">
              {formatValue(change.newValue)}
            </pre>
          </div>
        )}
      </div>

      {/* 变更原因 */}
      {change.changeReason && (
        <div className="mt-2 pt-2 border-t border-dashed">
          <p className="text-xs text-muted-foreground">
            <span className="font-medium">原因:</span> {change.changeReason}
          </p>
        </div>
      )}

      {/* 来源文本 */}
      {change.sourceText && (
        <div className="mt-2">
          <p className="text-xs text-muted-foreground italic">
            "{change.sourceText}"
          </p>
        </div>
      )}
    </div>
  );
}

export function StateCompareDialog() {
  const { from, to, result, isComparing } = useEvolutionStore(selectCompareState);
  const { isCompareDialogOpen, closeCompareDialog, compareSnapshots } = useEvolutionStore();

  // 触发比较
  useEffect(() => {
    if (isCompareDialogOpen && from && to && !result && !isComparing) {
      compareSnapshots();
    }
  }, [isCompareDialogOpen, from, to, result, isComparing, compareSnapshots]);

  // 按类型分组变更
  const groupedChanges = useMemo(() => {
    if (!result) return { added: [], removed: [], modified: [] };

    const groups: Record<DiffType, Array<{ fieldPath: string; change: StateChange }>> = {
      added: [],
      removed: [],
      modified: [],
    };

    Object.entries(result).forEach(([fieldPath, change]) => {
      const diffType = getDiffType(change);
      groups[diffType].push({ fieldPath, change });
    });

    return groups;
  }, [result]);

  const totalChanges = Object.keys(result || {}).length;

  return (
    <Dialog open={isCompareDialogOpen} onOpenChange={(open) => !open && closeCompareDialog()}>
      <DialogContent className="max-w-2xl max-h-[80vh] flex flex-col">
        <DialogHeader>
          <div className="flex items-center gap-2">
            <GitCompare className="h-5 w-5 text-primary" />
            <DialogTitle>状态对比</DialogTitle>
          </div>
          <DialogDescription>
            比较两个快照之间的状态差异
          </DialogDescription>
        </DialogHeader>

        {/* 快照信息 */}
        {from && to && (
          <div className="flex items-center justify-center gap-4 py-3 bg-muted/30 rounded-lg">
            <div className="text-center">
              <Badge variant="outline" className="mb-1">
                {from.chapterOrder !== undefined ? `第 ${from.chapterOrder} 章` : '起始'}
              </Badge>
              <p className="text-xs text-muted-foreground">
                {new Date(from.createdAt).toLocaleDateString('zh-CN')}
              </p>
            </div>
            <ArrowRight className="h-5 w-5 text-muted-foreground" />
            <div className="text-center">
              <Badge variant="outline" className="mb-1">
                {to.chapterOrder !== undefined ? `第 ${to.chapterOrder} 章` : '结束'}
              </Badge>
              <p className="text-xs text-muted-foreground">
                {new Date(to.createdAt).toLocaleDateString('zh-CN')}
              </p>
            </div>
          </div>
        )}

        <Separator />

        {/* 变更内容 */}
        <ScrollArea className="flex-1 min-h-0">
          {isComparing ? (
            <div className="flex items-center justify-center py-12">
              <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
              <span className="ml-2 text-sm text-muted-foreground">正在比较...</span>
            </div>
          ) : totalChanges === 0 ? (
            <div className="flex flex-col items-center justify-center py-12 text-center">
              <AlertCircle className="h-12 w-12 text-muted-foreground/50 mb-3" />
              <p className="text-sm font-medium">没有检测到变化</p>
              <p className="text-xs text-muted-foreground mt-1">
                两个快照之间的状态完全相同
              </p>
            </div>
          ) : (
            <div className="space-y-4 p-1">
              {/* 统计摘要 */}
              <div className="flex items-center gap-4 text-sm">
                <span className="text-muted-foreground">共 {totalChanges} 处变化:</span>
                {groupedChanges.added.length > 0 && (
                  <span className="text-green-600">
                    +{groupedChanges.added.length} 新增
                  </span>
                )}
                {groupedChanges.modified.length > 0 && (
                  <span className="text-blue-600">
                    ~{groupedChanges.modified.length} 修改
                  </span>
                )}
                {groupedChanges.removed.length > 0 && (
                  <span className="text-red-600">
                    -{groupedChanges.removed.length} 删除
                  </span>
                )}
              </div>

              {/* 新增的字段 */}
              {groupedChanges.added.length > 0 && (
                <div className="space-y-2">
                  <h4 className="text-sm font-medium flex items-center gap-2">
                    <Plus className="h-4 w-4 text-green-600" />
                    新增字段
                  </h4>
                  {groupedChanges.added.map(({ fieldPath, change }) => (
                    <DiffItem key={fieldPath} fieldPath={fieldPath} change={change} />
                  ))}
                </div>
              )}

              {/* 修改的字段 */}
              {groupedChanges.modified.length > 0 && (
                <div className="space-y-2">
                  <h4 className="text-sm font-medium flex items-center gap-2">
                    <Edit className="h-4 w-4 text-blue-600" />
                    修改字段
                  </h4>
                  {groupedChanges.modified.map(({ fieldPath, change }) => (
                    <DiffItem key={fieldPath} fieldPath={fieldPath} change={change} />
                  ))}
                </div>
              )}

              {/* 删除的字段 */}
              {groupedChanges.removed.length > 0 && (
                <div className="space-y-2">
                  <h4 className="text-sm font-medium flex items-center gap-2">
                    <Minus className="h-4 w-4 text-red-600" />
                    删除字段
                  </h4>
                  {groupedChanges.removed.map(({ fieldPath, change }) => (
                    <DiffItem key={fieldPath} fieldPath={fieldPath} change={change} />
                  ))}
                </div>
              )}
            </div>
          )}
        </ScrollArea>
      </DialogContent>
    </Dialog>
  );
}

export default StateCompareDialog;
