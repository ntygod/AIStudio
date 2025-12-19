/**
 * 演进时间线组件
 * 显示角色状态变化时间线，支持点击查看详情
 * 
 * Requirements: 6.2, 6.3, 6.5, 6.6
 */

import { useEffect, useCallback, useState } from 'react';
import {
  Clock,
  GitBranch,
  Loader2,
  RefreshCw,
  Eye,
  GitCompare,
  Star,
  Circle,
  Plus,
  Camera,
} from 'lucide-react';
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
} from '../ui/sheet';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '../ui/dialog';
import { Button } from '../ui/button';
import { Badge } from '../ui/badge';
import { ScrollArea } from '../ui/scroll-area';
import { Label } from '../ui/label';
import { Textarea } from '../ui/textarea';
import { Checkbox } from '../ui/checkbox';
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '../ui/tooltip';
import {
  useEvolutionStore,
  selectSnapshots,
  selectSelectedEntity,
  selectIsLoading,
  selectIsTimelineOpen,
  selectCreateSnapshotState,
} from '@/stores/evolution-store';
import type { StateSnapshot, ChangeType } from '@/services/evolution-service';

// 变更类型配置
const CHANGE_TYPE_CONFIG: Record<ChangeType, {
  label: string;
  color: string;
  bgColor: string;
}> = {
  INITIAL: {
    label: '初始',
    color: 'text-green-600',
    bgColor: 'bg-green-100',
  },
  UPDATE: {
    label: '更新',
    color: 'text-blue-600',
    bgColor: 'bg-blue-100',
  },
  MAJOR_CHANGE: {
    label: '重大变更',
    color: 'text-orange-600',
    bgColor: 'bg-orange-100',
  },
};

interface TimelineNodeProps {
  snapshot: StateSnapshot;
  isFirst: boolean;
  isLast: boolean;
  onViewDetails: (snapshot: StateSnapshot) => void;
  onSelectForCompare: (snapshot: StateSnapshot) => void;
  isSelectedForCompare: boolean;
}

function TimelineNode({
  snapshot,
  isFirst,
  isLast,
  onViewDetails,
  onSelectForCompare,
  isSelectedForCompare,
}: TimelineNodeProps) {
  const config = CHANGE_TYPE_CONFIG[snapshot.changeType] || CHANGE_TYPE_CONFIG.UPDATE;
  const formattedDate = new Date(snapshot.createdAt).toLocaleDateString('zh-CN', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });

  return (
    <div className="relative flex gap-4">
      {/* 时间线连接线 */}
      <div className="flex flex-col items-center">
        {/* 上方连接线 */}
        {!isFirst && (
          <div className="w-0.5 h-4 bg-border" />
        )}
        {/* 节点 */}
        <div
          className={`relative z-10 flex items-center justify-center w-8 h-8 rounded-full border-2 ${
            snapshot.isKeyframe
              ? 'border-primary bg-primary/10'
              : 'border-muted-foreground/30 bg-background'
          } ${isSelectedForCompare ? 'ring-2 ring-primary ring-offset-2' : ''}`}
        >
          {snapshot.isKeyframe ? (
            <Star className="h-4 w-4 text-primary" />
          ) : (
            <Circle className="h-3 w-3 text-muted-foreground" />
          )}
        </div>
        {/* 下方连接线 */}
        {!isLast && (
          <div className="w-0.5 flex-1 min-h-[40px] bg-border" />
        )}
      </div>

      {/* 内容卡片 */}
      <div className="flex-1 pb-6">
        <div
          className={`p-3 rounded-lg border transition-all hover:shadow-sm ${
            isSelectedForCompare ? 'border-primary bg-primary/5' : 'border-border bg-card'
          }`}
        >
          {/* 头部 */}
          <div className="flex items-center justify-between mb-2">
            <div className="flex items-center gap-2">
              {snapshot.chapterOrder !== undefined && (
                <Badge variant="outline" className="text-xs">
                  第 {snapshot.chapterOrder} 章
                </Badge>
              )}
              <Badge className={`text-xs ${config.bgColor} ${config.color} border-0`}>
                {config.label}
              </Badge>
              {snapshot.isKeyframe && (
                <TooltipProvider>
                  <Tooltip>
                    <TooltipTrigger>
                      <Badge variant="secondary" className="text-xs">
                        关键帧
                      </Badge>
                    </TooltipTrigger>
                    <TooltipContent>
                      <p>完整状态快照，用于状态重建</p>
                    </TooltipContent>
                  </Tooltip>
                </TooltipProvider>
              )}
            </div>
            <span className="text-xs text-muted-foreground">{formattedDate}</span>
          </div>

          {/* 变更摘要 */}
          {snapshot.changeSummary && (
            <p className="text-sm text-muted-foreground mb-2 line-clamp-2">
              {snapshot.changeSummary}
            </p>
          )}

          {/* AI 置信度 */}
          {snapshot.aiConfidence !== undefined && snapshot.aiConfidence !== null && (
            <div className="flex items-center gap-1 mb-2">
              <span className="text-xs text-muted-foreground">AI 置信度:</span>
              <div className="flex-1 h-1.5 bg-muted rounded-full max-w-[100px]">
                <div
                  className="h-full bg-primary rounded-full"
                  style={{ width: `${snapshot.aiConfidence * 100}%` }}
                />
              </div>
              <span className="text-xs text-muted-foreground">
                {Math.round(snapshot.aiConfidence * 100)}%
              </span>
            </div>
          )}

          {/* 操作按钮 */}
          <div className="flex items-center gap-2 mt-2">
            <Button
              size="sm"
              variant="outline"
              className="h-7 text-xs"
              onClick={() => onViewDetails(snapshot)}
            >
              <Eye className="h-3 w-3 mr-1" />
              查看详情
            </Button>
            <Button
              size="sm"
              variant={isSelectedForCompare ? 'default' : 'ghost'}
              className="h-7 text-xs"
              onClick={() => onSelectForCompare(snapshot)}
            >
              <GitCompare className="h-3 w-3 mr-1" />
              {isSelectedForCompare ? '已选中' : '选择对比'}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}

// 创建快照对话框组件
function CreateSnapshotDialog() {
  const { isOpen, isCreating } = useEvolutionStore(selectCreateSnapshotState);
  const selectedEntity = useEvolutionStore(selectSelectedEntity);
  const { closeCreateSnapshotDialog, createSnapshot } = useEvolutionStore();
  
  const [description, setDescription] = useState('');
  const [isKeyframe, setIsKeyframe] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 重置表单
  useEffect(() => {
    if (isOpen) {
      setDescription('');
      setIsKeyframe(false);
      setError(null);
    }
  }, [isOpen]);

  const handleSubmit = async () => {
    if (!description.trim()) {
      setError('请输入快照描述');
      return;
    }

    try {
      await createSnapshot(description.trim(), undefined, isKeyframe);
    } catch (err) {
      setError(err instanceof Error ? err.message : '创建快照失败');
    }
  };

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && closeCreateSnapshotDialog()}>
      <DialogContent className="sm:max-w-[425px]">
        <DialogHeader>
          <div className="flex items-center gap-2">
            <Camera className="h-5 w-5 text-primary" />
            <DialogTitle>创建演进快照</DialogTitle>
          </div>
          <DialogDescription>
            为 <span className="font-medium">{selectedEntity.name}</span> 创建一个状态快照，记录当前状态
          </DialogDescription>
        </DialogHeader>

        <div className="grid gap-4 py-4">
          <div className="grid gap-2">
            <Label htmlFor="description">快照描述 *</Label>
            <Textarea
              id="description"
              placeholder="描述这个快照记录的状态变化..."
              value={description}
              onChange={(e) => {
                setDescription(e.target.value);
                setError(null);
              }}
              className="min-h-[80px]"
            />
            {error && (
              <p className="text-sm text-destructive">{error}</p>
            )}
          </div>

          <div className="flex items-center space-x-2">
            <Checkbox
              id="keyframe"
              checked={isKeyframe}
              onCheckedChange={(checked) => setIsKeyframe(checked === true)}
            />
            <div className="grid gap-1.5 leading-none">
              <Label
                htmlFor="keyframe"
                className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70"
              >
                标记为关键帧
              </Label>
              <p className="text-xs text-muted-foreground">
                关键帧包含完整状态数据，用于状态重建
              </p>
            </div>
          </div>
        </div>

        <DialogFooter>
          <Button
            variant="outline"
            onClick={closeCreateSnapshotDialog}
            disabled={isCreating}
          >
            取消
          </Button>
          <Button onClick={handleSubmit} disabled={isCreating}>
            {isCreating ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                创建中...
              </>
            ) : (
              <>
                <Camera className="mr-2 h-4 w-4" />
                创建快照
              </>
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

interface EvolutionTimelineProps {
  onViewSnapshotDetails?: (snapshot: StateSnapshot) => void;
  onCompare?: (from: StateSnapshot, to: StateSnapshot) => void;
}

export function EvolutionTimeline({
  onViewSnapshotDetails,
  onCompare,
}: EvolutionTimelineProps) {
  const isTimelineOpen = useEvolutionStore(selectIsTimelineOpen);
  const snapshots = useEvolutionStore(selectSnapshots);
  const selectedEntity = useEvolutionStore(selectSelectedEntity);
  const isLoading = useEvolutionStore(selectIsLoading);
  const {
    closeTimeline,
    fetchSnapshots,
    selectSnapshot,
    setCompareFrom,
    setCompareTo,
    compareFromSnapshot,
    compareToSnapshot,
    openCompareDialog,
    openCreateSnapshotDialog,
  } = useEvolutionStore();

  // 加载快照
  useEffect(() => {
    if (isTimelineOpen && selectedEntity.id) {
      fetchSnapshots();
    }
  }, [isTimelineOpen, selectedEntity.id, fetchSnapshots]);

  // 查看详情
  const handleViewDetails = useCallback(
    (snapshot: StateSnapshot) => {
      selectSnapshot(snapshot);
      if (onViewSnapshotDetails) {
        onViewSnapshotDetails(snapshot);
      }
    },
    [selectSnapshot, onViewSnapshotDetails]
  );

  // 选择对比
  const handleSelectForCompare = useCallback(
    (snapshot: StateSnapshot) => {
      const { compareFromSnapshot, compareToSnapshot } = useEvolutionStore.getState();
      
      if (!compareFromSnapshot) {
        setCompareFrom(snapshot);
      } else if (compareFromSnapshot.id === snapshot.id) {
        setCompareFrom(null);
      } else if (!compareToSnapshot) {
        setCompareTo(snapshot);
        // 自动打开对比对话框
        if (onCompare) {
          onCompare(compareFromSnapshot, snapshot);
        } else {
          openCompareDialog();
        }
      } else if (compareToSnapshot.id === snapshot.id) {
        setCompareTo(null);
      } else {
        // 重新开始选择
        setCompareFrom(snapshot);
        setCompareTo(null);
      }
    },
    [setCompareFrom, setCompareTo, openCompareDialog, onCompare]
  );

  // 刷新
  const handleRefresh = useCallback(() => {
    fetchSnapshots(true);
  }, [fetchSnapshots]);

  // 检查是否选中用于对比
  const isSelectedForCompare = (snapshot: StateSnapshot) => {
    return (
      compareFromSnapshot?.id === snapshot.id ||
      compareToSnapshot?.id === snapshot.id
    );
  };

  return (
    <Sheet open={isTimelineOpen} onOpenChange={(open) => !open && closeTimeline()}>
      <SheetContent side="right" className="w-[400px] sm:w-[480px] p-0">
        <SheetHeader className="px-4 pt-4 pb-2 border-b">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <GitBranch className="h-5 w-5 text-primary" />
              <SheetTitle>演进时间线</SheetTitle>
            </div>
            <div className="flex items-center gap-1">
              <TooltipProvider>
                <Tooltip>
                  <TooltipTrigger asChild>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-8 w-8"
                      onClick={openCreateSnapshotDialog}
                      disabled={isLoading || !selectedEntity.id}
                    >
                      <Plus className="h-4 w-4" />
                    </Button>
                  </TooltipTrigger>
                  <TooltipContent>
                    <p>创建快照</p>
                  </TooltipContent>
                </Tooltip>
              </TooltipProvider>
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
          </div>
          <SheetDescription>
            {selectedEntity.name ? (
              <>
                <span className="font-medium">{selectedEntity.name}</span> 的状态变化历史
              </>
            ) : (
              '查看实体的状态变化历史'
            )}
          </SheetDescription>
        </SheetHeader>

        {/* 对比提示 */}
        {(compareFromSnapshot || compareToSnapshot) && (
          <div className="px-4 py-2 bg-primary/5 border-b flex items-center justify-between">
            <div className="flex items-center gap-2 text-sm">
              <GitCompare className="h-4 w-4 text-primary" />
              <span>
                {compareFromSnapshot && !compareToSnapshot
                  ? '请选择第二个快照进行对比'
                  : `已选择 ${compareFromSnapshot ? 1 : 0} + ${compareToSnapshot ? 1 : 0} 个快照`}
              </span>
            </div>
            <Button
              size="sm"
              variant="ghost"
              className="h-7 text-xs"
              onClick={() => {
                setCompareFrom(null);
                setCompareTo(null);
              }}
            >
              清除选择
            </Button>
          </div>
        )}

        {/* 时间线内容 */}
        <ScrollArea className="flex-1 h-[calc(100vh-180px)]">
          {isLoading && snapshots.length === 0 ? (
            <div className="flex items-center justify-center py-12">
              <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            </div>
          ) : snapshots.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-12 px-4 text-center">
              <Clock className="h-12 w-12 text-muted-foreground/50 mb-3" />
              <p className="text-sm font-medium">暂无演进记录</p>
              <p className="text-xs text-muted-foreground mt-1">
                当实体状态发生变化时，将自动记录演进快照
              </p>
            </div>
          ) : (
            <div className="p-4">
              {snapshots.map((snapshot, index) => (
                <TimelineNode
                  key={snapshot.id}
                  snapshot={snapshot}
                  isFirst={index === 0}
                  isLast={index === snapshots.length - 1}
                  onViewDetails={handleViewDetails}
                  onSelectForCompare={handleSelectForCompare}
                  isSelectedForCompare={isSelectedForCompare(snapshot)}
                />
              ))}
            </div>
          )}
        </ScrollArea>

        {/* 底部统计 */}
        {snapshots.length > 0 && (
          <div className="px-4 py-3 border-t bg-muted/30">
            <div className="flex items-center justify-between text-xs text-muted-foreground">
              <span>共 {snapshots.length} 个快照</span>
              <span>
                {snapshots.filter((s) => s.isKeyframe).length} 个关键帧
              </span>
            </div>
          </div>
        )}
      </SheetContent>

      {/* 创建快照对话框 */}
      <CreateSnapshotDialog />
    </Sheet>
  );
}

export default EvolutionTimeline;
