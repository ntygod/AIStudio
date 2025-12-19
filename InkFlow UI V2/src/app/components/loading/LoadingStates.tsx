/**
 * Loading State Components
 * Provides skeleton loaders and loading indicators for various UI elements
 */

import { Skeleton } from '../ui/skeleton';
import { Loader2 } from 'lucide-react';
import { cn } from '../ui/utils';

/**
 * Project list skeleton loader
 */
export function ProjectListSkeleton({ count = 6 }: { count?: number }) {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="border rounded-lg p-4 space-y-3">
          <Skeleton className="h-32 w-full rounded-md" />
          <Skeleton className="h-5 w-3/4" />
          <Skeleton className="h-4 w-1/2" />
          <div className="flex gap-2">
            <Skeleton className="h-6 w-16 rounded-full" />
            <Skeleton className="h-6 w-16 rounded-full" />
          </div>
        </div>
      ))}
    </div>
  );
}

/**
 * Project tree skeleton loader
 */
export function ProjectTreeSkeleton() {
  return (
    <div className="space-y-2 p-2">
      {Array.from({ length: 3 }).map((_, i) => (
        <div key={i} className="space-y-1">
          <div className="flex items-center gap-2">
            <Skeleton className="h-4 w-4" />
            <Skeleton className="h-4 w-32" />
          </div>
          <div className="ml-6 space-y-1">
            {Array.from({ length: 2 }).map((_, j) => (
              <div key={j} className="flex items-center gap-2">
                <Skeleton className="h-3 w-3" />
                <Skeleton className="h-3 w-24" />
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}

/**
 * Asset drawer skeleton loader
 */
export function AssetDrawerSkeleton() {
  return (
    <div className="space-y-4 p-2">
      {/* Characters section */}
      <div className="space-y-2">
        <Skeleton className="h-4 w-16" />
        <div className="space-y-1">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="flex items-center gap-2">
              <Skeleton className="h-8 w-8 rounded-full" />
              <div className="flex-1 space-y-1">
                <Skeleton className="h-3 w-20" />
                <Skeleton className="h-2 w-32" />
              </div>
            </div>
          ))}
        </div>
      </div>
      {/* Wiki section */}
      <div className="space-y-2">
        <Skeleton className="h-4 w-16" />
        <div className="space-y-1">
          {Array.from({ length: 2 }).map((_, i) => (
            <Skeleton key={i} className="h-8 w-full" />
          ))}
        </div>
      </div>
    </div>
  );
}

/**
 * Editor skeleton loader
 */
export function EditorSkeleton() {
  return (
    <div className="p-8 space-y-4">
      <Skeleton className="h-8 w-48" />
      <div className="space-y-2">
        {Array.from({ length: 8 }).map((_, i) => (
          <Skeleton
            key={i}
            className="h-4"
            style={{ width: `${Math.random() * 40 + 60}%` }}
          />
        ))}
      </div>
      <div className="space-y-2 mt-6">
        {Array.from({ length: 6 }).map((_, i) => (
          <Skeleton
            key={i}
            className="h-4"
            style={{ width: `${Math.random() * 40 + 60}%` }}
          />
        ))}
      </div>
    </div>
  );
}

/**
 * Chat message skeleton loader
 */
export function ChatMessageSkeleton({ count = 3 }: { count?: number }) {
  return (
    <div className="space-y-4 p-4">
      {Array.from({ length: count }).map((_, i) => (
        <div
          key={i}
          className={cn(
            'flex gap-3',
            i % 2 === 0 ? 'justify-start' : 'justify-end'
          )}
        >
          {i % 2 === 0 && <Skeleton className="h-8 w-8 rounded-full shrink-0" />}
          <div className="space-y-2 max-w-[70%]">
            <Skeleton className="h-4 w-48" />
            <Skeleton className="h-4 w-32" />
          </div>
          {i % 2 !== 0 && <Skeleton className="h-8 w-8 rounded-full shrink-0" />}
        </div>
      ))}
    </div>
  );
}

/**
 * Thought chain skeleton loader
 */
export function ThoughtChainSkeleton() {
  return (
    <div className="space-y-2 p-2">
      {Array.from({ length: 3 }).map((_, i) => (
        <div key={i} className="flex items-start gap-2">
          <Skeleton className="h-5 w-5 rounded-full shrink-0" />
          <div className="flex-1 space-y-1">
            <Skeleton className="h-3 w-16" />
            <Skeleton className="h-4 w-full" />
          </div>
        </div>
      ))}
    </div>
  );
}

/**
 * Generic card skeleton
 */
export function CardSkeleton() {
  return (
    <div className="border rounded-lg p-4 space-y-3">
      <Skeleton className="h-5 w-3/4" />
      <Skeleton className="h-4 w-full" />
      <Skeleton className="h-4 w-2/3" />
    </div>
  );
}

/**
 * Inline loading spinner
 */
export function LoadingSpinner({
  size = 'default',
  className,
}: {
  size?: 'sm' | 'default' | 'lg';
  className?: string;
}) {
  const sizeClasses = {
    sm: 'h-4 w-4',
    default: 'h-6 w-6',
    lg: 'h-8 w-8',
  };

  return (
    <Loader2
      className={cn('animate-spin text-muted-foreground', sizeClasses[size], className)}
    />
  );
}

/**
 * Full page loading overlay
 */
export function LoadingOverlay({ message = '加载中...' }: { message?: string }) {
  return (
    <div className="fixed inset-0 bg-background/80 backdrop-blur-sm z-50 flex items-center justify-center">
      <div className="flex flex-col items-center gap-4">
        <LoadingSpinner size="lg" />
        <p className="text-sm text-muted-foreground">{message}</p>
      </div>
    </div>
  );
}

/**
 * Section loading state
 */
export function SectionLoading({
  message = '加载中...',
  className,
}: {
  message?: string;
  className?: string;
}) {
  return (
    <div className={cn('flex flex-col items-center justify-center py-8 gap-3', className)}>
      <LoadingSpinner />
      <p className="text-sm text-muted-foreground">{message}</p>
    </div>
  );
}

/**
 * Empty state component
 */
export function EmptyState({
  icon,
  title,
  description,
  action,
}: {
  icon?: React.ReactNode;
  title: string;
  description?: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="flex flex-col items-center justify-center py-12 px-4 text-center">
      {icon && (
        <div className="mb-4 text-muted-foreground">{icon}</div>
      )}
      <h3 className="text-lg font-medium">{title}</h3>
      {description && (
        <p className="mt-1 text-sm text-muted-foreground max-w-sm">{description}</p>
      )}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}
