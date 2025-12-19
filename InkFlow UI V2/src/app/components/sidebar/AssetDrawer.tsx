/**
 * 资产抽屉组件
 * 显示角色、Wiki、伏笔列表
 * 支持集成模式连接 AssetStore
 * 
 * Requirements: 2.6, 2.7, 6.1, 13.1
 */

import { useEffect, useState, useCallback } from 'react';
import {
  Users,
  BookOpen,
  Zap,
  Loader2,
  CheckCircle,
  XCircle,
  Network,
  GitBranch,
} from 'lucide-react';
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from '../ui/accordion';
import { ScrollArea } from '../ui/scroll-area';
import { Button } from '../ui/button';
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '../ui/tooltip';
import { useAssetStore } from '@/stores/asset-store';
import { useProjectStore } from '@/stores/project-store';
import { useEvolutionStore } from '@/stores/evolution-store';
import { AssetDetailDialog, type AssetType } from './AssetDetailDialog';
import type { Character, WikiEntry, PlotLoop } from '@/types';
import type { EvolutionEntityType } from '@/services/evolution-service';

// 简化的内部类型（用于非集成模式）
interface SimpleCharacter {
  id: string;
  name: string;
  description: string;
  traits?: string[];
}

interface SimpleWikiEntry {
  id: string;
  title: string;
  content: string;
}

interface SimplePlotLoop {
  id: string;
  description: string;
  status: 'open' | 'resolved' | 'OPEN' | 'RESOLVED' | 'ABANDONED';
}

interface AssetDrawerProps {
  /** 外部传入的角色数据（可选，用于非集成模式） */
  characters?: SimpleCharacter[];
  /** 外部传入的Wiki数据（可选，用于非集成模式） */
  wiki?: SimpleWikiEntry[];
  /** 外部传入的伏笔数据（可选，用于非集成模式） */
  plotLoops?: SimplePlotLoop[];
  /** 资产点击回调 */
  onAssetClick: (type: 'character' | 'wiki' | 'plot', id: string) => void;
  /** 是否使用集成模式（连接 store） */
  integrated?: boolean;
  /** 查看关系图谱回调 */
  onViewRelationGraph?: () => void;
}

// 判断伏笔是否为开放状态
const isPlotOpen = (status: string): boolean => {
  return status === 'open' || status === 'OPEN';
};

// 判断伏笔是否已解决
const isPlotResolved = (status: string): boolean => {
  return status === 'resolved' || status === 'RESOLVED';
};

// 判断伏笔是否已放弃
const isPlotAbandoned = (status: string): boolean => {
  return status === 'ABANDONED';
};

export function AssetDrawer({ 
  characters: externalCharacters, 
  wiki: externalWiki, 
  plotLoops: externalPlotLoops, 
  onAssetClick,
  integrated = false,
  onViewRelationGraph,
}: AssetDrawerProps) {
  // Store hooks (only used in integrated mode)
  const currentProject = useProjectStore(state => state.currentProject);
  const {
    characters: storeCharacters,
    wikiEntries: storeWikiEntries,
    plotLoops: storePlotLoops,
    isLoading,
    fetchCharacters,
    fetchWikiEntries,
    fetchPlotLoops
  } = useAssetStore();

  // Evolution store for timeline
  const { selectEntity, openTimeline } = useEvolutionStore();

  // 详情对话框状态
  const [detailDialogOpen, setDetailDialogOpen] = useState(false);
  const [selectedAssetType, setSelectedAssetType] = useState<AssetType | null>(null);
  const [selectedCharacter, setSelectedCharacter] = useState<Character | null>(null);
  const [selectedWikiEntry, setSelectedWikiEntry] = useState<WikiEntry | null>(null);
  const [selectedPlotLoop, setSelectedPlotLoop] = useState<PlotLoop | null>(null);

  // 决定使用哪个数据源
  const characters = integrated ? storeCharacters : (externalCharacters || []);
  const wiki = integrated ? storeWikiEntries : (externalWiki || []);
  const plotLoops = integrated ? storePlotLoops : (externalPlotLoops || []);

  // 集成模式下加载数据
  useEffect(() => {
    if (integrated && currentProject) {
      fetchCharacters(currentProject.id);
      fetchWikiEntries(currentProject.id);
      fetchPlotLoops(currentProject.id);
    }
  }, [integrated, currentProject, fetchCharacters, fetchWikiEntries, fetchPlotLoops]);

  // 计算开放伏笔数量
  const openPlotCount = plotLoops.filter(p => isPlotOpen(p.status)).length;

  // 处理资产点击 - 显示详情对话框
  const handleAssetClick = useCallback((type: AssetType, id: string) => {
    setSelectedAssetType(type);
    
    if (type === 'character') {
      const char = (integrated ? storeCharacters : externalCharacters)?.find(c => c.id === id);
      setSelectedCharacter(char as Character || null);
      setSelectedWikiEntry(null);
      setSelectedPlotLoop(null);
    } else if (type === 'wiki') {
      const entry = (integrated ? storeWikiEntries : externalWiki)?.find(e => e.id === id);
      setSelectedWikiEntry(entry as WikiEntry || null);
      setSelectedCharacter(null);
      setSelectedPlotLoop(null);
    } else if (type === 'plot') {
      const loop = (integrated ? storePlotLoops : externalPlotLoops)?.find(p => p.id === id);
      setSelectedPlotLoop(loop as PlotLoop || null);
      setSelectedCharacter(null);
      setSelectedWikiEntry(null);
    }
    
    setDetailDialogOpen(true);
    
    // 同时调用外部回调
    onAssetClick(type, id);
  }, [integrated, storeCharacters, storeWikiEntries, storePlotLoops, externalCharacters, externalWiki, externalPlotLoops, onAssetClick]);

  // 处理查看演进时间线
  const handleViewEvolution = useCallback((entityId: string, entityType: 'character' | 'wiki', entityName: string) => {
    // Convert to EvolutionEntityType format
    const evolutionEntityType: EvolutionEntityType = entityType === 'character' ? 'CHARACTER' : 'WIKI_ENTRY';
    selectEntity(entityId, evolutionEntityType, entityName);
    openTimeline();
    setDetailDialogOpen(false);
  }, [selectEntity, openTimeline]);

  // 处理查看关系图谱
  const handleViewRelationGraph = useCallback(() => {
    setDetailDialogOpen(false);
    onViewRelationGraph?.();
  }, [onViewRelationGraph]);

  return (
    <div className="border-t border-border">
      {/* 快捷操作按钮 */}
      {integrated && (
        <div className="px-4 py-3 flex items-center gap-2">
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant="outline"
                  size="sm"
                  className="flex-1"
                  onClick={onViewRelationGraph}
                >
                  <Network className="h-4 w-4 mr-2" />
                  关系图谱
                </Button>
              </TooltipTrigger>
              <TooltipContent>
                <p>查看角色关系图谱</p>
              </TooltipContent>
            </Tooltip>
          </TooltipProvider>
        </div>
      )}

      <Accordion type="multiple" defaultValue={['characters', 'wiki', 'plots']} className="w-full">
        {/* 角色列表 */}
        <AccordionItem value="characters">
          <AccordionTrigger className="px-5 py-4 hover:no-underline">
            <div className="flex items-center gap-2">
              <Users className="h-4 w-4" />
              <span>人物</span>
              <span className="text-xs text-muted-foreground">({characters.length})</span>
              {integrated && isLoading && characters.length === 0 && (
                <Loader2 className="h-3 w-3 animate-spin ml-1" />
              )}
            </div>
          </AccordionTrigger>
          <AccordionContent>
            <ScrollArea className="max-h-48">
              <div className="px-4 pb-3 space-y-2">
                {characters.length === 0 ? (
                  <p className="text-xs text-muted-foreground/70 py-2 text-center">
                    暂无角色
                  </p>
                ) : (
                  characters.map((char) => (
                    <button
                      key={char.id}
                      onClick={() => handleAssetClick('character', char.id)}
                      className="w-full text-left px-4 py-3 rounded-xl hover:bg-accent transition-all group shadow-sm hover:shadow-md"
                    >
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-full bg-primary/10 flex items-center justify-center text-primary shrink-0">
                          {char.name[0]}
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="truncate font-medium">{char.name}</div>
                          <div className="text-xs text-muted-foreground truncate">
                            {char.description || ('role' in char ? (char as Character).role : '')}
                          </div>
                        </div>
                        {/* 演进按钮 */}
                        {integrated && (
                          <TooltipProvider>
                            <Tooltip>
                              <TooltipTrigger asChild>
                                <Button
                                  variant="ghost"
                                  size="icon"
                                  className="h-8 w-8 opacity-0 group-hover:opacity-100 transition-opacity"
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    handleViewEvolution(char.id, 'character', char.name);
                                  }}
                                >
                                  <GitBranch className="h-4 w-4" />
                                </Button>
                              </TooltipTrigger>
                              <TooltipContent>
                                <p>查看演进时间线</p>
                              </TooltipContent>
                            </Tooltip>
                          </TooltipProvider>
                        )}
                      </div>
                    </button>
                  ))
                )}
              </div>
            </ScrollArea>
          </AccordionContent>
        </AccordionItem>

        {/* Wiki 列表 */}
        <AccordionItem value="wiki">
          <AccordionTrigger className="px-5 py-4 hover:no-underline">
            <div className="flex items-center gap-2">
              <BookOpen className="h-4 w-4" />
              <span>世界观</span>
              <span className="text-xs text-muted-foreground">({wiki.length})</span>
              {integrated && isLoading && wiki.length === 0 && (
                <Loader2 className="h-3 w-3 animate-spin ml-1" />
              )}
            </div>
          </AccordionTrigger>
          <AccordionContent>
            <ScrollArea className="max-h-48">
              <div className="px-4 pb-3 space-y-2">
                {wiki.length === 0 ? (
                  <p className="text-xs text-muted-foreground/70 py-2 text-center">
                    暂无设定
                  </p>
                ) : (
                  wiki.map((entry) => (
                    <button
                      key={entry.id}
                      onClick={() => handleAssetClick('wiki', entry.id)}
                      className="w-full text-left px-4 py-2.5 rounded-xl hover:bg-accent transition-all text-sm shadow-sm hover:shadow-md group"
                    >
                      <div className="flex items-center justify-between">
                        <div className="flex-1 min-w-0">
                          <div className="truncate">{entry.title}</div>
                          {'type' in entry && (
                            <div className="text-xs text-muted-foreground mt-0.5">
                              {(entry as WikiEntry).type}
                            </div>
                          )}
                        </div>
                        {/* 演进按钮 */}
                        {integrated && (
                          <TooltipProvider>
                            <Tooltip>
                              <TooltipTrigger asChild>
                                <Button
                                  variant="ghost"
                                  size="icon"
                                  className="h-7 w-7 opacity-0 group-hover:opacity-100 transition-opacity"
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    handleViewEvolution(entry.id, 'wiki', entry.title);
                                  }}
                                >
                                  <GitBranch className="h-3.5 w-3.5" />
                                </Button>
                              </TooltipTrigger>
                              <TooltipContent>
                                <p>查看演进时间线</p>
                              </TooltipContent>
                            </Tooltip>
                          </TooltipProvider>
                        )}
                      </div>
                    </button>
                  ))
                )}
              </div>
            </ScrollArea>
          </AccordionContent>
        </AccordionItem>

        {/* 伏笔列表 */}
        <AccordionItem value="plots">
          <AccordionTrigger className="px-5 py-4 hover:no-underline">
            <div className="flex items-center gap-2">
              <Zap className="h-4 w-4" />
              <span>伏笔</span>
              <span className="text-xs text-muted-foreground">({openPlotCount})</span>
              {integrated && isLoading && plotLoops.length === 0 && (
                <Loader2 className="h-3 w-3 animate-spin ml-1" />
              )}
            </div>
          </AccordionTrigger>
          <AccordionContent>
            <ScrollArea className="max-h-48">
              <div className="px-4 pb-3 space-y-2">
                {plotLoops.length === 0 ? (
                  <p className="text-xs text-muted-foreground/70 py-2 text-center">
                    暂无伏笔
                  </p>
                ) : (
                  plotLoops.map((plot) => {
                    const resolved = isPlotResolved(plot.status);
                    const abandoned = isPlotAbandoned(plot.status);
                    
                    return (
                      <button
                        key={plot.id}
                        onClick={() => handleAssetClick('plot', plot.id)}
                        className={`w-full text-left px-4 py-2.5 rounded-xl hover:bg-accent transition-all text-sm shadow-sm hover:shadow-md ${
                          resolved || abandoned ? 'opacity-50' : ''
                        }`}
                      >
                        <div className="flex items-center gap-2">
                          {resolved && (
                            <CheckCircle className="h-3.5 w-3.5 text-green-500 flex-shrink-0" />
                          )}
                          {abandoned && (
                            <XCircle className="h-3.5 w-3.5 text-red-500 flex-shrink-0" />
                          )}
                          <span className={resolved ? 'line-through' : ''}>
                            {'title' in plot ? (plot as PlotLoop).title : plot.description}
                          </span>
                        </div>
                      </button>
                    );
                  })
                )}
              </div>
            </ScrollArea>
          </AccordionContent>
        </AccordionItem>
      </Accordion>

      {/* 资产详情对话框 */}
      <AssetDetailDialog
        open={detailDialogOpen}
        onOpenChange={setDetailDialogOpen}
        assetType={selectedAssetType}
        character={selectedCharacter}
        wikiEntry={selectedWikiEntry}
        plotLoop={selectedPlotLoop}
        onViewEvolution={integrated ? handleViewEvolution : undefined}
        onViewRelationGraph={integrated ? handleViewRelationGraph : undefined}
      />
    </div>
  );
}
