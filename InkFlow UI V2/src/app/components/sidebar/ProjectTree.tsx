/**
 * 项目树组件
 * 显示卷/章节的树形结构，支持展开/折叠和选择
 * 集成 ContentStore 进行数据管理
 * 
 * Requirements: 2.3, 2.4, 2.5
 * - 显示所有卷和章节，包含字数统计
 * - 展开卷时获取并显示章节
 * - 选择章节时加载内容到编辑器
 */

import { useState, useEffect, useCallback, useMemo } from 'react';
import { 
  ChevronRight, 
  ChevronDown, 
  Book, 
  FileText, 
  MoreVertical,
  Trash2,
  Loader2
} from 'lucide-react';
import { ScrollArea } from '../ui/scroll-area';
import { useContentStore } from '@/stores/content-store';
import { useProjectStore } from '@/stores/project-store';
import type { Volume, Chapter } from '@/types';
import { Badge } from '../ui/badge';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '../ui/tooltip';

interface ProjectTreeProps {
  /** 外部传入的卷数据（可选，用于非集成模式） */
  volumes?: VolumeWithChapters[];
  /** 章节选择回调 */
  onChapterSelect: (volumeId: string, chapterId: string) => void;
  /** 当前选中的章节ID */
  selectedChapterId?: string;
  /** 是否使用集成模式（连接 store） */
  integrated?: boolean;
}

interface VolumeWithChapters extends Volume {
  chapters: Chapter[];
}

// 格式化字数显示
const formatWordCount = (count: number | undefined): string => {
  if (count === undefined || count === null) return '0';
  if (count >= 10000) return `${(count / 10000).toFixed(1)}万`;
  if (count >= 1000) return `${(count / 1000).toFixed(1)}k`;
  return String(count);
};

// 获取章节状态的显示配置
const getChapterStatusConfig = (status: string) => {
  switch (status) {
    case 'COMPLETE':
      return { label: '完成', className: 'bg-green-500/10 text-green-500 border-green-500/20' };
    case 'REVIEW':
      return { label: '审阅', className: 'bg-yellow-500/10 text-yellow-500 border-yellow-500/20' };
    case 'WRITING':
      return { label: '写作中', className: 'bg-blue-500/10 text-blue-500 border-blue-500/20' };
    case 'DRAFT':
    default:
      return { label: '草稿', className: 'bg-muted text-muted-foreground border-muted' };
  }
};

export function ProjectTree({ 
  volumes: externalVolumes, 
  onChapterSelect, 
  selectedChapterId,
  integrated = false 
}: ProjectTreeProps) {
  const [expandedVolumes, setExpandedVolumes] = useState<Set<string>>(new Set());
  const [showVolumeMenu, setShowVolumeMenu] = useState<string | null>(null);
  const [showChapterMenu, setShowChapterMenu] = useState<string | null>(null);
  const [loadingChapters, setLoadingChapters] = useState<Set<string>>(new Set());

  // Store hooks (only used in integrated mode)
  const currentProject = useProjectStore(state => state.currentProject);
  const { 
    volumes: storeVolumes, 
    chapters: storeChapters,
    isLoading,
    fetchVolumes,
    fetchChapters,
    deleteVolume,
    deleteChapter,
    setCurrentChapter
  } = useContentStore();

  // 决定使用哪个数据源
  const volumes = integrated ? storeVolumes : (externalVolumes || []);

  // 获取卷的章节（按 orderIndex 排序）
  const getVolumeChapters = useCallback((volumeId: string): Chapter[] => {
    if (!integrated) {
      const vol = externalVolumes?.find(v => v.id === volumeId);
      const chapters = vol?.chapters || [];
      // 确保按 orderIndex 排序
      return [...chapters].sort((a, b) => (a.orderIndex ?? 0) - (b.orderIndex ?? 0));
    }
    const chapters = storeChapters.get(volumeId) || [];
    // 确保按 orderIndex 排序
    return [...chapters].sort((a, b) => (a.orderIndex ?? 0) - (b.orderIndex ?? 0));
  }, [integrated, externalVolumes, storeChapters]);

  // 计算卷的总字数（如果卷本身没有 wordCount，则从章节累加）
  const getVolumeWordCount = useCallback((volume: Volume): number => {
    if (volume.wordCount !== undefined && volume.wordCount > 0) {
      return volume.wordCount;
    }
    const chapters = getVolumeChapters(volume.id);
    return chapters.reduce((sum, ch) => sum + (ch.wordCount || 0), 0);
  }, [getVolumeChapters]);

  // 计算项目总字数
  const totalWordCount = useMemo(() => {
    return volumes.reduce((sum, vol) => sum + getVolumeWordCount(vol), 0);
  }, [volumes, getVolumeWordCount]);

  // 集成模式下加载数据
  useEffect(() => {
    if (integrated && currentProject) {
      fetchVolumes(currentProject.id);
    }
  }, [integrated, currentProject, fetchVolumes]);

  // 展开卷时加载章节
  useEffect(() => {
    if (integrated && currentProject) {
      expandedVolumes.forEach(async volumeId => {
        if (!storeChapters.has(volumeId) && !loadingChapters.has(volumeId)) {
          setLoadingChapters(prev => new Set(prev).add(volumeId));
          try {
            await fetchChapters(currentProject.id, volumeId);
          } finally {
            setLoadingChapters(prev => {
              const next = new Set(prev);
              next.delete(volumeId);
              return next;
            });
          }
        }
      });
    }
  }, [integrated, currentProject, expandedVolumes, storeChapters, fetchChapters, loadingChapters]);

  // 初始化展开状态 - 默认展开所有卷
  useEffect(() => {
    if (volumes.length > 0 && expandedVolumes.size === 0) {
      setExpandedVolumes(new Set(volumes.map(v => v.id)));
    }
  }, [volumes, expandedVolumes.size]);

  const toggleVolume = useCallback((volumeId: string) => {
    setExpandedVolumes(prev => {
      const next = new Set(prev);
      if (next.has(volumeId)) {
        next.delete(volumeId);
      } else {
        next.add(volumeId);
      }
      return next;
    });
  }, []);

  // 处理章节选择 - 集成模式下同时更新 store
  const handleChapterSelect = useCallback((volumeId: string, chapterId: string) => {
    if (integrated) {
      // 找到选中的章节和卷并设置到 store
      const chapters = storeChapters.get(volumeId) || [];
      const chapter = chapters.find(c => c.id === chapterId);
      const volume = volumes.find(v => v.id === volumeId);
      if (chapter) {
        setCurrentChapter(chapter, volume);
      }
    }
    // 调用外部回调
    onChapterSelect(volumeId, chapterId);
  }, [integrated, storeChapters, volumes, setCurrentChapter, onChapterSelect]);

  const handleDeleteVolume = useCallback(async (volumeId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!currentProject) return;
    
    if (window.confirm('确定要删除这个卷吗？所有章节也会被删除。')) {
      try {
        await deleteVolume(currentProject.id, volumeId);
      } catch {
        // Error handled by store
      }
    }
    setShowVolumeMenu(null);
  }, [currentProject, deleteVolume]);

  const handleDeleteChapter = useCallback(async (chapterId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!currentProject) return;
    
    if (window.confirm('确定要删除这个章节吗？')) {
      try {
        await deleteChapter(currentProject.id, chapterId);
      } catch {
        // Error handled by store
      }
    }
    setShowChapterMenu(null);
  }, [currentProject, deleteChapter]);

  // 加载状态
  if (integrated && isLoading && volumes.length === 0) {
    return (
      <div className="flex items-center justify-center py-8">
        <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
      </div>
    );
  }

  // 空状态
  if (volumes.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-8 px-4 text-center">
        <Book className="h-10 w-10 text-muted-foreground/50 mb-3" />
        <p className="text-sm text-muted-foreground">暂无卷</p>
        <p className="text-xs text-muted-foreground/70 mt-1">创建第一个卷开始写作</p>
      </div>
    );
  }

  // 按 orderIndex 排序卷
  const sortedVolumes = useMemo(() => {
    return [...volumes].sort((a, b) => (a.orderIndex ?? 0) - (b.orderIndex ?? 0));
  }, [volumes]);

  return (
    <ScrollArea className="flex-1">
      <div className="p-3">
        {/* 项目总字数统计 */}
        {integrated && volumes.length > 0 && (
          <div className="mb-3 px-3 py-2 bg-muted/50 rounded-lg">
            <div className="flex items-center justify-between text-sm">
              <span className="text-muted-foreground">总字数</span>
              <span className="font-medium">{formatWordCount(totalWordCount)}字</span>
            </div>
            <div className="flex items-center justify-between text-xs text-muted-foreground mt-1">
              <span>{volumes.length} 卷</span>
              <span>
                {volumes.reduce((sum, vol) => sum + (vol.chapterCount || getVolumeChapters(vol.id).length), 0)} 章
              </span>
            </div>
          </div>
        )}

        {sortedVolumes.map((volume) => {
          const chapters = getVolumeChapters(volume.id);
          const isExpanded = expandedVolumes.has(volume.id);
          const volumeWordCount = getVolumeWordCount(volume);
          const chapterCount = volume.chapterCount || chapters.length;
          
          return (
            <div key={volume.id} className="mb-2">
              {/* 卷标题 */}
              <div className="group relative">
                <button
                  onClick={() => toggleVolume(volume.id)}
                  className="w-full flex items-center gap-2 px-3 py-2.5 rounded-xl hover:bg-accent transition-colors"
                >
                  {isExpanded ? (
                    <ChevronDown className="h-4 w-4 text-muted-foreground transition-transform" />
                  ) : (
                    <ChevronRight className="h-4 w-4 text-muted-foreground transition-transform" />
                  )}
                  <Book className="h-4 w-4 text-muted-foreground" />
                  <span className="flex-1 text-left truncate font-medium">{volume.title}</span>
                  <div className="flex items-center gap-2 text-xs text-muted-foreground">
                    <span>{chapterCount}章</span>
                    <span className="text-muted-foreground/50">|</span>
                    <span>{formatWordCount(volumeWordCount)}字</span>
                  </div>
                </button>
                
                {/* 卷操作菜单 */}
                {integrated && (
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      setShowVolumeMenu(showVolumeMenu === volume.id ? null : volume.id);
                    }}
                    className="absolute right-2 top-1/2 -translate-y-1/2 p-1 rounded opacity-0 group-hover:opacity-100 hover:bg-accent transition-opacity"
                  >
                    <MoreVertical className="h-4 w-4 text-muted-foreground" />
                  </button>
                )}
                
                {showVolumeMenu === volume.id && (
                  <div className="absolute right-0 top-full mt-1 w-32 bg-popover border rounded-lg shadow-lg z-10">
                    <button
                      onClick={(e) => handleDeleteVolume(volume.id, e)}
                      className="w-full px-3 py-2 text-left text-sm text-destructive hover:bg-accent flex items-center gap-2"
                    >
                      <Trash2 className="h-4 w-4" />
                      删除
                    </button>
                  </div>
                )}
              </div>
              
              {/* 章节列表 */}
              {isExpanded && (
                <div className="ml-6 mt-1.5 space-y-1">
                  {/* 加载中状态 */}
                  {loadingChapters.has(volume.id) && chapters.length === 0 && (
                    <div className="flex items-center gap-2 px-3 py-2 text-muted-foreground">
                      <Loader2 className="h-3.5 w-3.5 animate-spin" />
                      <span className="text-xs">加载章节...</span>
                    </div>
                  )}
                  
                  {/* 空状态 */}
                  {!loadingChapters.has(volume.id) && chapters.length === 0 && (
                    <p className="text-xs text-muted-foreground/70 px-3 py-2">
                      暂无章节
                    </p>
                  )}
                  
                  {/* 章节列表 */}
                  {chapters.map((chapter) => {
                    const statusConfig = getChapterStatusConfig(chapter.status);
                    const isSelected = selectedChapterId === chapter.id;
                    
                    return (
                      <div key={chapter.id} className="group relative">
                        <TooltipProvider>
                          <Tooltip>
                            <TooltipTrigger asChild>
                              <button
                                onClick={() => handleChapterSelect(volume.id, chapter.id)}
                                className={`w-full flex items-center gap-2 px-3 py-2 rounded-lg transition-all ${
                                  isSelected
                                    ? 'bg-primary/10 text-primary shadow-sm ring-1 ring-primary/20'
                                    : 'hover:bg-accent'
                                }`}
                              >
                                <FileText className={`h-3.5 w-3.5 flex-shrink-0 ${isSelected ? 'text-primary' : ''}`} />
                                <span className="flex-1 text-left text-sm truncate">{chapter.title}</span>
                                <span className={`text-xs ${isSelected ? 'text-primary/70' : 'text-muted-foreground'}`}>
                                  {formatWordCount(chapter.wordCount)}字
                                </span>
                              </button>
                            </TooltipTrigger>
                            <TooltipContent side="right" className="max-w-xs">
                              <div className="space-y-1">
                                <p className="font-medium">{chapter.title}</p>
                                {chapter.summary && (
                                  <p className="text-xs text-muted-foreground line-clamp-2">{chapter.summary}</p>
                                )}
                                <div className="flex items-center gap-2 text-xs">
                                  <Badge variant="outline" className={statusConfig.className}>
                                    {statusConfig.label}
                                  </Badge>
                                  <span className="text-muted-foreground">{formatWordCount(chapter.wordCount)}字</span>
                                </div>
                              </div>
                            </TooltipContent>
                          </Tooltip>
                        </TooltipProvider>
                        
                        {/* 章节操作菜单 */}
                        {integrated && (
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              setShowChapterMenu(showChapterMenu === chapter.id ? null : chapter.id);
                            }}
                            className="absolute right-1 top-1/2 -translate-y-1/2 p-1 rounded opacity-0 group-hover:opacity-100 hover:bg-accent transition-opacity"
                          >
                            <MoreVertical className="h-3.5 w-3.5 text-muted-foreground" />
                          </button>
                        )}
                        
                        {showChapterMenu === chapter.id && (
                          <div className="absolute right-0 top-full mt-1 w-32 bg-popover border rounded-lg shadow-lg z-10">
                            <button
                              onClick={(e) => handleDeleteChapter(chapter.id, e)}
                              className="w-full px-3 py-2 text-left text-sm text-destructive hover:bg-accent flex items-center gap-2"
                            >
                              <Trash2 className="h-4 w-4" />
                              删除
                            </button>
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          );
        })}
      </div>
      
      {/* 点击外部关闭菜单 */}
      {(showVolumeMenu || showChapterMenu) && (
        <div 
          className="fixed inset-0 z-0" 
          onClick={() => {
            setShowVolumeMenu(null);
            setShowChapterMenu(null);
          }}
        />
      )}
    </ScrollArea>
  );
}

// 导出用于测试的辅助函数
export { formatWordCount, getChapterStatusConfig };