/**
 * Conflict Resolution Dialog
 * Displays content conflicts and allows users to resolve them
 * Requirements: 15.4
 */

import { useState, useMemo } from 'react';
import { AlertTriangle, FileText, X, GitMerge } from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '../ui/dialog';
import { Button } from '../ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '../ui/tabs';
import { ScrollArea } from '../ui/scroll-area';
import { Textarea } from '../ui/textarea';
import { Badge } from '../ui/badge';
import { cn } from '../ui/utils';
import { useOffline, type ConflictInfo, type ConflictResolution } from '@/lib/offline-manager';
import { resolveContentConflict } from '@/lib/sync-service';
import { useProjectStore } from '@/stores/project-store';

interface ConflictResolutionDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  conflict: ConflictInfo | null;
  onResolved?: () => void;
}

/**
 * Simple diff view component
 */
function DiffView({ 
  localContent, 
  serverContent 
}: { 
  localContent: string; 
  serverContent: string;
}) {
  const localLines = localContent.split('\n');
  const serverLines = serverContent.split('\n');
  
  // Simple line-by-line comparison (maxLines used for future expansion)
  void Math.max(localLines.length, serverLines.length);
  
  return (
    <div className="grid grid-cols-2 gap-2 text-sm font-mono">
      <div className="space-y-1">
        <div className="text-xs font-semibold text-muted-foreground mb-2 flex items-center gap-1">
          <FileText className="h-3 w-3" />
          本地版本
        </div>
        <ScrollArea className="h-[300px] border rounded-md p-2 bg-muted/30">
          {localLines.map((line, i) => {
            const isDifferent = serverLines[i] !== line;
            return (
              <div
                key={i}
                className={cn(
                  'px-1 py-0.5 rounded',
                  isDifferent && 'bg-green-500/20 text-green-700 dark:text-green-300'
                )}
              >
                {line || '\u00A0'}
              </div>
            );
          })}
        </ScrollArea>
      </div>
      <div className="space-y-1">
        <div className="text-xs font-semibold text-muted-foreground mb-2 flex items-center gap-1">
          <FileText className="h-3 w-3" />
          服务器版本
        </div>
        <ScrollArea className="h-[300px] border rounded-md p-2 bg-muted/30">
          {serverLines.map((line, i) => {
            const isDifferent = localLines[i] !== line;
            return (
              <div
                key={i}
                className={cn(
                  'px-1 py-0.5 rounded',
                  isDifferent && 'bg-blue-500/20 text-blue-700 dark:text-blue-300'
                )}
              >
                {line || '\u00A0'}
              </div>
            );
          })}
        </ScrollArea>
      </div>
    </div>
  );
}

/**
 * Conflict Resolution Dialog Component
 */
export function ConflictResolutionDialog({
  open,
  onOpenChange,
  conflict,
  onResolved,
}: ConflictResolutionDialogProps) {
  const [activeTab, setActiveTab] = useState<'compare' | 'merge'>('compare');
  const [mergedContent, setMergedContent] = useState('');
  const [isResolving, setIsResolving] = useState(false);
  const { dismissConflict } = useOffline();
  const currentProject = useProjectStore((state) => state.currentProject);

  // Initialize merged content when conflict changes
  useMemo(() => {
    if (conflict) {
      setMergedContent(conflict.localContent);
    }
  }, [conflict]);

  if (!conflict) return null;

  const handleResolve = async (resolution: ConflictResolution) => {
    if (!currentProject) return;
    
    setIsResolving(true);
    try {
      const content = resolution === 'merge' ? mergedContent : undefined;
      const success = await resolveContentConflict(
        conflict.resourceId,
        currentProject.id,
        resolution,
        content
      );
      
      if (success) {
        onOpenChange(false);
        onResolved?.();
      }
    } finally {
      setIsResolving(false);
    }
  };

  const handleDismiss = () => {
    dismissConflict(conflict.resourceId);
    onOpenChange(false);
  };

  const formatTimestamp = (timestamp: number) => {
    return new Date(timestamp).toLocaleString('zh-CN', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-4xl max-h-[90vh] overflow-hidden flex flex-col">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <AlertTriangle className="h-5 w-5 text-warning" />
            内容冲突
          </DialogTitle>
          <DialogDescription>
            检测到本地更改与服务器版本存在冲突。请选择如何解决此冲突。
          </DialogDescription>
        </DialogHeader>

        <div className="flex items-center gap-4 text-sm text-muted-foreground">
          <div className="flex items-center gap-1">
            <Badge variant="outline" className="bg-green-500/10">
              本地 v{conflict.localVersion}
            </Badge>
          </div>
          <div className="flex items-center gap-1">
            <Badge variant="outline" className="bg-blue-500/10">
              服务器 v{conflict.serverVersion}
            </Badge>
          </div>
          <div className="text-xs">
            冲突时间: {formatTimestamp(conflict.timestamp)}
          </div>
        </div>

        <Tabs value={activeTab} onValueChange={(v) => setActiveTab(v as 'compare' | 'merge')} className="flex-1 overflow-hidden">
          <TabsList className="grid w-full grid-cols-2">
            <TabsTrigger value="compare">对比查看</TabsTrigger>
            <TabsTrigger value="merge">手动合并</TabsTrigger>
          </TabsList>
          
          <TabsContent value="compare" className="flex-1 overflow-hidden mt-4">
            <DiffView 
              localContent={conflict.localContent} 
              serverContent={conflict.serverContent} 
            />
          </TabsContent>
          
          <TabsContent value="merge" className="flex-1 overflow-hidden mt-4">
            <div className="space-y-2">
              <div className="text-sm text-muted-foreground">
                编辑下方内容以手动合并两个版本：
              </div>
              <Textarea
                value={mergedContent}
                onChange={(e) => setMergedContent(e.target.value)}
                className="h-[300px] font-mono text-sm"
                placeholder="在此编辑合并后的内容..."
              />
            </div>
          </TabsContent>
        </Tabs>

        <DialogFooter className="flex-shrink-0 gap-2 sm:gap-2">
          <Button
            variant="outline"
            onClick={handleDismiss}
            disabled={isResolving}
          >
            <X className="h-4 w-4 mr-1" />
            稍后处理
          </Button>
          
          <Button
            variant="outline"
            onClick={() => handleResolve('keep-server')}
            disabled={isResolving}
            className="text-blue-600 hover:text-blue-700"
          >
            <FileText className="h-4 w-4 mr-1" />
            使用服务器版本
          </Button>
          
          <Button
            variant="outline"
            onClick={() => handleResolve('keep-local')}
            disabled={isResolving}
            className="text-green-600 hover:text-green-700"
          >
            <FileText className="h-4 w-4 mr-1" />
            使用本地版本
          </Button>
          
          {activeTab === 'merge' && (
            <Button
              onClick={() => handleResolve('merge')}
              disabled={isResolving || !mergedContent.trim()}
            >
              <GitMerge className="h-4 w-4 mr-1" />
              {isResolving ? '保存中...' : '保存合并结果'}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/**
 * Conflict List Dialog - shows all pending conflicts
 */
export function ConflictListDialog({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { conflicts } = useOffline();
  const [selectedConflict, setSelectedConflict] = useState<ConflictInfo | null>(null);
  const [showResolutionDialog, setShowResolutionDialog] = useState(false);

  const handleSelectConflict = (conflict: ConflictInfo) => {
    setSelectedConflict(conflict);
    setShowResolutionDialog(true);
  };

  const handleResolved = () => {
    setSelectedConflict(null);
    if (conflicts.length <= 1) {
      onOpenChange(false);
    }
  };

  if (conflicts.length === 0) {
    return null;
  }

  return (
    <>
      <Dialog open={open && !showResolutionDialog} onOpenChange={onOpenChange}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <AlertTriangle className="h-5 w-5 text-warning" />
              待解决的冲突 ({conflicts.length})
            </DialogTitle>
            <DialogDescription>
              以下内容存在版本冲突，请逐一解决。
            </DialogDescription>
          </DialogHeader>

          <ScrollArea className="max-h-[400px]">
            <div className="space-y-2">
              {conflicts.map((conflict) => (
                <div
                  key={conflict.resourceId}
                  className="flex items-center justify-between p-3 border rounded-lg hover:bg-muted/50 cursor-pointer"
                  onClick={() => handleSelectConflict(conflict)}
                >
                  <div className="flex items-center gap-3">
                    <AlertTriangle className="h-4 w-4 text-warning" />
                    <div>
                      <div className="font-medium text-sm">
                        {conflict.resourceType === 'chapter' ? '章节' : '内容'} 冲突
                      </div>
                      <div className="text-xs text-muted-foreground">
                        本地 v{conflict.localVersion} vs 服务器 v{conflict.serverVersion}
                      </div>
                    </div>
                  </div>
                  <Button variant="ghost" size="sm">
                    解决
                  </Button>
                </div>
              ))}
            </div>
          </ScrollArea>

          <DialogFooter>
            <Button variant="outline" onClick={() => onOpenChange(false)}>
              稍后处理
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConflictResolutionDialog
        open={showResolutionDialog}
        onOpenChange={setShowResolutionDialog}
        conflict={selectedConflict}
        onResolved={handleResolved}
      />
    </>
  );
}

export default ConflictResolutionDialog;
