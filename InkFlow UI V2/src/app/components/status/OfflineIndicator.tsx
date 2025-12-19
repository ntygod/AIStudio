/**
 * Offline Indicator Component
 * Displays network status and pending sync operations
 */

import { useEffect } from 'react';
import { WifiOff, Wifi, RefreshCw, CloudOff, Cloud } from 'lucide-react';
import { useOffline, initializeOfflineDetection } from '@/lib/offline-manager';
import { cn } from '../ui/utils';
import { Button } from '../ui/button';
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '../ui/tooltip';

interface OfflineIndicatorProps {
  className?: string;
  showPendingCount?: boolean;
  compact?: boolean;
}

/**
 * Offline indicator that shows network status
 */
export function OfflineIndicator({
  className,
  showPendingCount = true,
  compact = false,
}: OfflineIndicatorProps) {
  const { isOnline, isOffline, pendingCount, isReplaying } = useOffline();

  // Initialize offline detection on mount
  useEffect(() => {
    const cleanup = initializeOfflineDetection();
    return cleanup;
  }, []);

  // Don't show anything if online and no pending operations
  if (isOnline && pendingCount === 0) {
    return null;
  }

  if (compact) {
    return (
      <TooltipProvider>
        <Tooltip>
          <TooltipTrigger asChild>
            <div
              className={cn(
                'flex items-center gap-1 px-2 py-1 rounded-full text-xs',
                isOffline
                  ? 'bg-destructive/10 text-destructive'
                  : 'bg-warning/10 text-warning',
                className
              )}
            >
              {isOffline ? (
                <WifiOff className="h-3 w-3" />
              ) : isReplaying ? (
                <RefreshCw className="h-3 w-3 animate-spin" />
              ) : (
                <CloudOff className="h-3 w-3" />
              )}
              {showPendingCount && pendingCount > 0 && (
                <span>{pendingCount}</span>
              )}
            </div>
          </TooltipTrigger>
          <TooltipContent>
            {isOffline
              ? '网络已断开'
              : isReplaying
                ? '正在同步...'
                : `${pendingCount} 个操作待同步`}
          </TooltipContent>
        </Tooltip>
      </TooltipProvider>
    );
  }

  return (
    <div
      className={cn(
        'flex items-center gap-2 px-3 py-2 rounded-lg border',
        isOffline
          ? 'bg-destructive/10 border-destructive/20 text-destructive'
          : 'bg-warning/10 border-warning/20 text-warning-foreground',
        className
      )}
    >
      {isOffline ? (
        <>
          <WifiOff className="h-4 w-4" />
          <span className="text-sm font-medium">离线模式</span>
        </>
      ) : isReplaying ? (
        <>
          <RefreshCw className="h-4 w-4 animate-spin" />
          <span className="text-sm">正在同步...</span>
        </>
      ) : (
        <>
          <Cloud className="h-4 w-4" />
          <span className="text-sm">{pendingCount} 个操作待同步</span>
        </>
      )}
    </div>
  );
}

/**
 * Offline banner that appears at the top of the screen
 */
export function OfflineBanner({ className }: { className?: string }) {
  const { isOffline, pendingCount, isReplaying } = useOffline();

  // Initialize offline detection on mount
  useEffect(() => {
    const cleanup = initializeOfflineDetection();
    return cleanup;
  }, []);

  if (!isOffline && pendingCount === 0) {
    return null;
  }

  return (
    <div
      className={cn(
        'w-full px-4 py-2 flex items-center justify-center gap-2 text-sm',
        isOffline
          ? 'bg-destructive text-destructive-foreground'
          : 'bg-warning text-warning-foreground',
        className
      )}
    >
      {isOffline ? (
        <>
          <WifiOff className="h-4 w-4" />
          <span>您当前处于离线状态，部分功能可能不可用</span>
        </>
      ) : isReplaying ? (
        <>
          <RefreshCw className="h-4 w-4 animate-spin" />
          <span>正在同步离线操作...</span>
        </>
      ) : (
        <>
          <CloudOff className="h-4 w-4" />
          <span>有 {pendingCount} 个操作等待同步</span>
        </>
      )}
    </div>
  );
}

/**
 * Network status icon for toolbar/header
 */
export function NetworkStatusIcon({ className }: { className?: string }) {
  const { isOnline, pendingCount } = useOffline();

  // Initialize offline detection on mount
  useEffect(() => {
    const cleanup = initializeOfflineDetection();
    return cleanup;
  }, []);

  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <Button
            variant="ghost"
            size="icon"
            className={cn(
              'relative',
              !isOnline && 'text-destructive',
              className
            )}
          >
            {isOnline ? (
              <Wifi className="h-4 w-4" />
            ) : (
              <WifiOff className="h-4 w-4" />
            )}
            {pendingCount > 0 && (
              <span className="absolute -top-1 -right-1 h-4 w-4 rounded-full bg-warning text-warning-foreground text-xs flex items-center justify-center">
                {pendingCount > 9 ? '9+' : pendingCount}
              </span>
            )}
          </Button>
        </TooltipTrigger>
        <TooltipContent>
          {isOnline
            ? pendingCount > 0
              ? `在线 - ${pendingCount} 个操作待同步`
              : '在线'
            : '离线'}
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}
