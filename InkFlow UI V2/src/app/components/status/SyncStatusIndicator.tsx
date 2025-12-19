/**
 * Sync Status Indicator Component
 * Displays synchronization status with visual feedback
 * Requirements: 15.5
 */

import { useState, useEffect } from 'react';
import { 
  Cloud, 
  RefreshCw, 
  AlertTriangle, 
  Check,
  WifiOff,
  Upload
} from 'lucide-react';
import { useSyncStatus, initializeOfflineDetection } from '@/lib/offline-manager';
import { syncAllPendingChanges } from '@/lib/sync-service';
import { ConflictListDialog } from './ConflictResolutionDialog';
import { cn } from '../ui/utils';
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
import { Badge } from '../ui/badge';

interface SyncStatusIndicatorProps {
  className?: string;
  showLabel?: boolean;
  compact?: boolean;
}

/**
 * Main Sync Status Indicator
 * Requirements: 15.5
 */
export function SyncStatusIndicator({
  className,
  showLabel = false,
  compact = false,
}: SyncStatusIndicatorProps) {
  const {
    syncStatus,
    statusText,
    statusColor,
    pendingChangesCount,
    isSyncing,
    lastSyncAt,
    hasConflicts,
    conflictCount,
    isOnline,
  } = useSyncStatus();
  
  const [showConflictDialog, setShowConflictDialog] = useState(false);
  const [isManualSyncing, setIsManualSyncing] = useState(false);

  // Initialize offline detection
  useEffect(() => {
    const cleanup = initializeOfflineDetection();
    return cleanup;
  }, []);

  const handleManualSync = async () => {
    if (!isOnline || isSyncing || isManualSyncing) return;
    
    setIsManualSyncing(true);
    try {
      await syncAllPendingChanges();
    } finally {
      setIsManualSyncing(false);
    }
  };

  const getIcon = () => {
    if (!isOnline) return <WifiOff className="h-4 w-4" />;
    if (isSyncing || isManualSyncing) return <RefreshCw className="h-4 w-4 animate-spin" />;
    if (hasConflicts) return <AlertTriangle className="h-4 w-4" />;
    if (pendingChangesCount > 0) return <Upload className="h-4 w-4" />;
    return <Check className="h-4 w-4" />;
  };

  const getColorClass = () => {
    switch (statusColor) {
      case 'destructive':
        return 'text-destructive';
      case 'warning':
        return 'text-warning';
      case 'info':
        return 'text-blue-500';
      case 'success':
        return 'text-green-500';
      default:
        return 'text-muted-foreground';
    }
  };

  const formatLastSync = () => {
    if (!lastSyncAt) return '从未同步';
    
    const diff = Date.now() - lastSyncAt;
    if (diff < 60000) return '刚刚';
    if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`;
    if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`;
    return new Date(lastSyncAt).toLocaleDateString('zh-CN');
  };

  if (compact) {
    return (
      <TooltipProvider>
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              className={cn('relative', getColorClass(), className)}
              onClick={hasConflicts ? () => setShowConflictDialog(true) : handleManualSync}
              disabled={!isOnline && !hasConflicts}
            >
              {getIcon()}
              {(pendingChangesCount > 0 || hasConflicts) && (
                <span className={cn(
                  'absolute -top-1 -right-1 h-4 w-4 rounded-full text-xs flex items-center justify-center',
                  hasConflicts ? 'bg-warning text-warning-foreground' : 'bg-blue-500 text-white'
                )}>
                  {hasConflicts ? conflictCount : (pendingChangesCount > 9 ? '9+' : pendingChangesCount)}
                </span>
              )}
            </Button>
          </TooltipTrigger>
          <TooltipContent>
            <p>{statusText}</p>
            <p className="text-xs text-muted-foreground">上次同步: {formatLastSync()}</p>
          </TooltipContent>
        </Tooltip>
        
        <ConflictListDialog 
          open={showConflictDialog} 
          onOpenChange={setShowConflictDialog} 
        />
      </TooltipProvider>
    );
  }

  return (
    <>
      <Popover>
        <PopoverTrigger asChild>
          <Button
            variant="ghost"
            size="sm"
            className={cn(
              'flex items-center gap-2',
              getColorClass(),
              className
            )}
          >
            {getIcon()}
            {showLabel && <span className="text-sm">{statusText}</span>}
            {(pendingChangesCount > 0 || hasConflicts) && !showLabel && (
              <Badge variant="secondary" className="h-5 px-1.5 text-xs">
                {hasConflicts ? `${conflictCount} 冲突` : pendingChangesCount}
              </Badge>
            )}
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-72" align="end">
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <span className="font-medium">同步状态</span>
              <Badge 
                variant={syncStatus === 'synced' ? 'default' : 'secondary'}
                className={cn(
                  syncStatus === 'conflict' && 'bg-warning text-warning-foreground',
                  syncStatus === 'synced' && 'bg-green-500'
                )}
              >
                {statusText}
              </Badge>
            </div>
            
            <div className="text-sm text-muted-foreground space-y-1">
              <div className="flex justify-between">
                <span>网络状态</span>
                <span className={isOnline ? 'text-green-500' : 'text-destructive'}>
                  {isOnline ? '在线' : '离线'}
                </span>
              </div>
              <div className="flex justify-between">
                <span>待同步</span>
                <span>{pendingChangesCount} 项</span>
              </div>
              {hasConflicts && (
                <div className="flex justify-between text-warning">
                  <span>冲突</span>
                  <span>{conflictCount} 项</span>
                </div>
              )}
              <div className="flex justify-between">
                <span>上次同步</span>
                <span>{formatLastSync()}</span>
              </div>
            </div>

            <div className="flex gap-2 pt-2 border-t">
              {hasConflicts && (
                <Button
                  variant="outline"
                  size="sm"
                  className="flex-1 text-warning"
                  onClick={() => setShowConflictDialog(true)}
                >
                  <AlertTriangle className="h-4 w-4 mr-1" />
                  解决冲突
                </Button>
              )}
              <Button
                variant="outline"
                size="sm"
                className="flex-1"
                onClick={handleManualSync}
                disabled={!isOnline || isSyncing || isManualSyncing || pendingChangesCount === 0}
              >
                <RefreshCw className={cn(
                  'h-4 w-4 mr-1',
                  (isSyncing || isManualSyncing) && 'animate-spin'
                )} />
                {isSyncing || isManualSyncing ? '同步中...' : '立即同步'}
              </Button>
            </div>
          </div>
        </PopoverContent>
      </Popover>
      
      <ConflictListDialog 
        open={showConflictDialog} 
        onOpenChange={setShowConflictDialog} 
      />
    </>
  );
}

/**
 * Sync Status Bar - for displaying at the bottom of the screen
 */
export function SyncStatusBar({ className }: { className?: string }) {
  const {
    pendingChangesCount,
    isSyncing,
    hasConflicts,
    isOnline,
  } = useSyncStatus();

  // Initialize offline detection
  useEffect(() => {
    const cleanup = initializeOfflineDetection();
    return cleanup;
  }, []);

  // Don't show if everything is synced and online
  if (isOnline && pendingChangesCount === 0 && !hasConflicts && !isSyncing) {
    return null;
  }

  return (
    <div
      className={cn(
        'flex items-center justify-center gap-2 px-4 py-1.5 text-sm',
        !isOnline && 'bg-destructive/10 text-destructive',
        hasConflicts && 'bg-warning/10 text-warning',
        isSyncing && 'bg-blue-500/10 text-blue-600',
        pendingChangesCount > 0 && !hasConflicts && !isSyncing && 'bg-muted text-muted-foreground',
        className
      )}
    >
      {!isOnline ? (
        <>
          <WifiOff className="h-4 w-4" />
          <span>离线模式 - 更改将在恢复连接后同步</span>
        </>
      ) : isSyncing ? (
        <>
          <RefreshCw className="h-4 w-4 animate-spin" />
          <span>正在同步...</span>
        </>
      ) : hasConflicts ? (
        <>
          <AlertTriangle className="h-4 w-4" />
          <span>检测到内容冲突，请点击解决</span>
        </>
      ) : (
        <>
          <Cloud className="h-4 w-4" />
          <span>{pendingChangesCount} 项更改待同步</span>
        </>
      )}
    </div>
  );
}

export default SyncStatusIndicator;
