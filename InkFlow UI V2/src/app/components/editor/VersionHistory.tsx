/**
 * 版本历史组件
 * 显示章节快照列表，支持差异对比和恢复
 * 
 * Requirements: 8.1, 8.2, 8.3, 8.4
 * - 显示快照列表
 * - 选择快照显示与当前内容的差异
 * - 恢复到指定快照
 */

import { useState, useEffect, useCallback } from 'react';
import { 
  History, 
  Clock, 
  RotateCcw, 
  Plus,
  Loader2,
  ChevronRight,
  FileText,
  Wand2,
  Save,
  AlertCircle
} from 'lucide-react';
import { Button } from '../ui/button';
import { ScrollArea } from '../ui/scroll-area';
import { Badge } from '../ui/badge';
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '../ui/sheet';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '../ui/alert-dialog';
import { snapshotService } from '@/services/snapshot-service';
import type { ChapterSnapshot, SnapshotDiff, SnapshotTrigger } from '@/types';

interface VersionHistoryProps {
  /** 项目 ID */
  projectId: string;
  /** 章节 ID */
  chapterId: string;
  /** 当前内容 */
  currentContent: string;
  /** 恢复回调 */
  onRestore: (content: string) => void;
  /** 是否打开 */
  open?: boolean;
  /** 打开状态变更回调 */
  onOpenChange?: (open: boolean) => void;
}

// 触发类型标签
const triggerLabels: Record<SnapshotTrigger, { label: string; icon: React.ElementType; color: string }> = {
  MANUAL: { label: '手动', icon: Save, color: 'bg-blue-500/10 text-blue-500' },
  AUTO_SAVE: { label: '自动', icon: Clock, color: 'bg-gray-500/10 text-gray-500' },
  AI_EDIT: { label: 'AI编辑', icon: Wand2, color: 'bg-purple-500/10 text-purple-500' },
  RESTORE: { label: '恢复', icon: RotateCcw, color: 'bg-orange-500/10 text-orange-500' },
};

// 格式化时间
const formatTime = (dateStr: string): string => {
  const date = new Date(dateStr);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  
  // 小于1分钟
  if (diff < 60 * 1000) {
    return '刚刚';
  }
  // 小于1小时
  if (diff < 60 * 60 * 1000) {
    return `${Math.floor(diff / (60 * 1000))} 分钟前`;
  }
  // 小于24小时
  if (diff < 24 * 60 * 60 * 1000) {
    return `${Math.floor(diff / (60 * 60 * 1000))} 小时前`;
  }
  // 小于7天
  if (diff < 7 * 24 * 60 * 60 * 1000) {
    return `${Math.floor(diff / (24 * 60 * 60 * 1000))} 天前`;
  }
  // 其他
  return date.toLocaleDateString('zh-CN', { 
    month: 'short', 
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  });
};

export function VersionHistory({
  projectId,
  chapterId,
  currentContent: _currentContent,
  onRestore,
  open,
  onOpenChange
}: VersionHistoryProps) {
  const [snapshots, setSnapshots] = useState<ChapterSnapshot[]>([]);
  const [selectedSnapshot, setSelectedSnapshot] = useState<ChapterSnapshot | null>(null);
  const [diff, setDiff] = useState<SnapshotDiff | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isDiffLoading, setIsDiffLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [restoreDialogOpen, setRestoreDialogOpen] = useState(false);
  const [snapshotToRestore, setSnapshotToRestore] = useState<ChapterSnapshot | null>(null);

  // 加载快照列表
  const loadSnapshots = useCallback(async () => {
    if (!projectId || !chapterId) return;
    
    setIsLoading(true);
    setError(null);
    
    try {
      const data = await snapshotService.getSnapshots(projectId, chapterId);
      setSnapshots(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载快照失败');
    } finally {
      setIsLoading(false);
    }
  }, [projectId, chapterId]);

  // 打开时加载数据
  useEffect(() => {
    if (open) {
      loadSnapshots();
    }
  }, [open, loadSnapshots]);

  // 选择快照并加载差异
  const handleSelectSnapshot = useCallback(async (snapshot: ChapterSnapshot) => {
    setSelectedSnapshot(snapshot);
    setIsDiffLoading(true);
    
    try {
      const diffData = await snapshotService.getDiff(projectId, chapterId, snapshot.id);
      setDiff(diffData);
    } catch (err) {
      console.error('加载差异失败:', err);
      setDiff(null);
    } finally {
      setIsDiffLoading(false);
    }
  }, [projectId, chapterId]);

  // 确认恢复
  const handleConfirmRestore = useCallback(async () => {
    if (!snapshotToRestore) return;
    
    try {
      // 先创建当前内容的快照
      await snapshotService.createSnapshot(projectId, chapterId, {
        description: '恢复前自动保存',
        trigger: 'RESTORE'
      });
      
      // 恢复内容
      onRestore(snapshotToRestore.content);
      
      // 刷新列表
      await loadSnapshots();
      
      setRestoreDialogOpen(false);
      setSnapshotToRestore(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : '恢复失败');
    }
  }, [snapshotToRestore, projectId, chapterId, onRestore, loadSnapshots]);

  // 创建手动快照
  const handleCreateSnapshot = useCallback(async () => {
    try {
      await snapshotService.createSnapshot(projectId, chapterId, {
        description: '手动保存',
        trigger: 'MANUAL'
      });
      await loadSnapshots();
    } catch (err) {
      setError(err instanceof Error ? err.message : '创建快照失败');
    }
  }, [projectId, chapterId, loadSnapshots]);

  // 渲染差异视图
  const renderDiffView = () => {
    if (!selectedSnapshot) {
      return (
        <div className="flex-1 flex items-center justify-center text-muted-foreground">
          <p>选择一个版本查看差异</p>
        </div>
      );
    }

    if (isDiffLoading) {
      return (
        <div className="flex-1 flex items-center justify-center">
          <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
        </div>
      );
    }

    return (
      <div className="flex-1 flex flex-col">
        <div className="px-4 py-3 border-b border-border bg-muted/30">
          <div className="flex items-center justify-between">
            <div>
              <p className="font-medium text-sm">
                {selectedSnapshot.description || formatTime(selectedSnapshot.createdAt)}
              </p>
              <p className="text-xs text-muted-foreground mt-0.5">
                {selectedSnapshot.wordCount} 字
              </p>
            </div>
            {diff && (
              <div className="flex items-center gap-3 text-xs">
                <span className="text-green-500">+{diff.additions}</span>
                <span className="text-red-500">-{diff.deletions}</span>
              </div>
            )}
          </div>
        </div>
        
        <ScrollArea className="flex-1">
          <div className="p-4">
            {diff?.changes ? (
              <div className="font-mono text-sm leading-relaxed whitespace-pre-wrap">
                {diff.changes.map((change, index) => (
                  <span
                    key={index}
                    className={
                      change.type === 'add' 
                        ? 'bg-green-500/20 text-green-700 dark:text-green-300' 
                        : change.type === 'remove'
                        ? 'bg-red-500/20 text-red-700 dark:text-red-300 line-through'
                        : ''
                    }
                  >
                    {change.value}
                  </span>
                ))}
              </div>
            ) : (
              <p className="text-sm text-muted-foreground whitespace-pre-wrap">
                {selectedSnapshot.content}
              </p>
            )}
          </div>
        </ScrollArea>

        <div className="px-4 py-3 border-t border-border">
          <Button
            className="w-full"
            onClick={() => {
              setSnapshotToRestore(selectedSnapshot);
              setRestoreDialogOpen(true);
            }}
          >
            <RotateCcw className="h-4 w-4 mr-2" />
            恢复到此版本
          </Button>
        </div>
      </div>
    );
  };

  return (
    <>
      <Sheet open={open} onOpenChange={onOpenChange}>
        <SheetTrigger asChild>
          <Button variant="ghost" size="sm" className="gap-2">
            <History className="h-4 w-4" />
            <span className="hidden sm:inline">版本历史</span>
          </Button>
        </SheetTrigger>
        <SheetContent side="right" className="w-[600px] sm:max-w-[600px] p-0">
          <SheetHeader className="px-6 py-4 border-b border-border">
            <SheetTitle className="flex items-center gap-2">
              <History className="h-5 w-5" />
              版本历史
            </SheetTitle>
            <SheetDescription>
              查看和恢复章节的历史版本
            </SheetDescription>
          </SheetHeader>

          <div className="flex h-[calc(100vh-120px)]">
            {/* 快照列表 */}
            <div className="w-[200px] border-r border-border flex flex-col">
              <div className="px-3 py-2 border-b border-border">
                <Button
                  variant="outline"
                  size="sm"
                  className="w-full gap-2"
                  onClick={handleCreateSnapshot}
                >
                  <Plus className="h-4 w-4" />
                  创建快照
                </Button>
              </div>

              {isLoading ? (
                <div className="flex-1 flex items-center justify-center">
                  <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
                </div>
              ) : error ? (
                <div className="flex-1 flex flex-col items-center justify-center px-4 text-center">
                  <AlertCircle className="h-8 w-8 text-red-500 mb-2" />
                  <p className="text-sm text-muted-foreground">{error}</p>
                  <Button variant="link" size="sm" onClick={loadSnapshots}>
                    重试
                  </Button>
                </div>
              ) : snapshots.length === 0 ? (
                <div className="flex-1 flex flex-col items-center justify-center px-4 text-center">
                  <FileText className="h-8 w-8 text-muted-foreground mb-2" />
                  <p className="text-sm text-muted-foreground">暂无历史版本</p>
                </div>
              ) : (
                <ScrollArea className="flex-1">
                  <div className="py-2">
                    {snapshots.map((snapshot) => {
                      const trigger = triggerLabels[snapshot.trigger];
                      const isSelected = selectedSnapshot?.id === snapshot.id;
                      
                      return (
                        <button
                          key={snapshot.id}
                          className={`w-full px-3 py-2 text-left hover:bg-accent transition-colors ${
                            isSelected ? 'bg-accent' : ''
                          }`}
                          onClick={() => handleSelectSnapshot(snapshot)}
                        >
                          <div className="flex items-center gap-2">
                            <trigger.icon className="h-3.5 w-3.5 text-muted-foreground" />
                            <span className="text-xs font-medium truncate flex-1">
                              {snapshot.description || formatTime(snapshot.createdAt)}
                            </span>
                            {isSelected && (
                              <ChevronRight className="h-3.5 w-3.5 text-muted-foreground" />
                            )}
                          </div>
                          <div className="flex items-center gap-2 mt-1">
                            <Badge variant="secondary" className={`text-[10px] px-1.5 py-0 ${trigger.color}`}>
                              {trigger.label}
                            </Badge>
                            <span className="text-[10px] text-muted-foreground">
                              {snapshot.wordCount} 字
                            </span>
                          </div>
                        </button>
                      );
                    })}
                  </div>
                </ScrollArea>
              )}
            </div>

            {/* 差异视图 */}
            {renderDiffView()}
          </div>
        </SheetContent>
      </Sheet>

      {/* 恢复确认对话框 */}
      <AlertDialog open={restoreDialogOpen} onOpenChange={setRestoreDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认恢复</AlertDialogTitle>
            <AlertDialogDescription>
              恢复到此版本将覆盖当前内容。系统会自动保存当前内容作为新版本。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction onClick={handleConfirmRestore}>
              确认恢复
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
