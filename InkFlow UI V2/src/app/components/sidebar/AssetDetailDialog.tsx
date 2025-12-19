/**
 * 资产详情对话框组件
 * 显示角色、Wiki、伏笔的详细信息
 * 
 * Requirements: 2.6, 2.7
 */

import {
  Users,
  BookOpen,
  Zap,
  Clock,
  GitBranch,
  Network,
  CheckCircle,
  XCircle,
  AlertCircle,
  Tag,
  Edit,
} from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '../ui/dialog';
import { Button } from '../ui/button';
import { Badge } from '../ui/badge';
import { ScrollArea } from '../ui/scroll-area';
import { Separator } from '../ui/separator';
import type { Character, WikiEntry, PlotLoop } from '@/types';

export type AssetType = 'character' | 'wiki' | 'plot';

interface AssetDetailDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  assetType: AssetType | null;
  character?: Character | null;
  wikiEntry?: WikiEntry | null;
  plotLoop?: PlotLoop | null;
  onViewEvolution?: (entityId: string, entityType: 'character' | 'wiki', entityName: string) => void;
  onViewRelationGraph?: () => void;
  onEdit?: (type: AssetType, id: string) => void;
}

// 角色详情组件
function CharacterDetail({
  character,
  onViewEvolution,
  onViewRelationGraph,
}: {
  character: Character;
  onViewEvolution?: (entityId: string, entityType: 'character' | 'wiki', entityName: string) => void;
  onViewRelationGraph?: () => void;
}) {
  return (
    <div className="space-y-4">
      {/* 头部信息 */}
      <div className="flex items-start gap-4">
        <div className="w-16 h-16 rounded-full bg-primary/10 flex items-center justify-center text-primary text-2xl font-medium shrink-0">
          {character.name[0]}
        </div>
        <div className="flex-1 min-w-0">
          <h3 className="text-lg font-semibold">{character.name}</h3>
          <p className="text-sm text-muted-foreground">{character.role}</p>
          <div className="flex items-center gap-2 mt-2">
            <Badge variant={character.isActive ? 'default' : 'secondary'}>
              {character.isActive ? '活跃' : '非活跃'}
            </Badge>
            {character.archetype && (
              <Badge variant="outline">{character.archetype}</Badge>
            )}
          </div>
        </div>
      </div>

      <Separator />

      {/* 描述 */}
      {character.description && (
        <div>
          <h4 className="text-sm font-medium mb-2">描述</h4>
          <p className="text-sm text-muted-foreground whitespace-pre-wrap">
            {character.description}
          </p>
        </div>
      )}

      {/* 性格特征 */}
      {character.personality && Object.keys(character.personality).length > 0 && (
        <div>
          <h4 className="text-sm font-medium mb-2">性格特征</h4>
          <div className="flex flex-wrap gap-2">
            {Object.entries(character.personality).map(([key, value]) => (
              <Badge key={key} variant="outline" className="text-xs">
                {key}: {String(value)}
              </Badge>
            ))}
          </div>
        </div>
      )}

      {/* 关系 */}
      {character.relationships && character.relationships.length > 0 && (
        <div>
          <h4 className="text-sm font-medium mb-2">人物关系</h4>
          <div className="space-y-2">
            {character.relationships.map((rel, index) => (
              <div
                key={index}
                className="flex items-center gap-2 text-sm p-2 rounded-lg bg-muted/50"
              >
                <span className="font-medium">{rel.type}</span>
                {rel.description && (
                  <span className="text-muted-foreground">- {rel.description}</span>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* 操作按钮 */}
      <div className="flex items-center gap-2 pt-2">
        {onViewEvolution && (
          <Button
            variant="outline"
            size="sm"
            onClick={() => onViewEvolution(character.id, 'character', character.name)}
          >
            <GitBranch className="h-4 w-4 mr-2" />
            查看演进
          </Button>
        )}
        {onViewRelationGraph && (
          <Button variant="outline" size="sm" onClick={onViewRelationGraph}>
            <Network className="h-4 w-4 mr-2" />
            关系图谱
          </Button>
        )}
      </div>

      {/* 元信息 */}
      <div className="flex items-center gap-4 text-xs text-muted-foreground pt-2">
        <span className="flex items-center gap-1">
          <Clock className="h-3 w-3" />
          创建于 {new Date(character.createdAt).toLocaleDateString('zh-CN')}
        </span>
        <span className="flex items-center gap-1">
          <Clock className="h-3 w-3" />
          更新于 {new Date(character.updatedAt).toLocaleDateString('zh-CN')}
        </span>
      </div>
    </div>
  );
}


// Wiki 详情组件
function WikiDetail({
  wikiEntry,
  onViewEvolution,
}: {
  wikiEntry: WikiEntry;
  onViewEvolution?: (entityId: string, entityType: 'character' | 'wiki', entityName: string) => void;
}) {
  return (
    <div className="space-y-4">
      {/* 头部信息 */}
      <div className="flex items-start gap-4">
        <div className="w-12 h-12 rounded-lg bg-blue-500/10 flex items-center justify-center text-blue-500 shrink-0">
          <BookOpen className="h-6 w-6" />
        </div>
        <div className="flex-1 min-w-0">
          <h3 className="text-lg font-semibold">{wikiEntry.title}</h3>
          <Badge variant="outline" className="mt-1">{wikiEntry.type}</Badge>
        </div>
      </div>

      <Separator />

      {/* 内容 */}
      <div>
        <h4 className="text-sm font-medium mb-2">内容</h4>
        <ScrollArea className="max-h-[200px]">
          <p className="text-sm text-muted-foreground whitespace-pre-wrap">
            {wikiEntry.content}
          </p>
        </ScrollArea>
      </div>

      {/* 别名 */}
      {wikiEntry.aliases && wikiEntry.aliases.length > 0 && (
        <div>
          <h4 className="text-sm font-medium mb-2">别名</h4>
          <div className="flex flex-wrap gap-2">
            {wikiEntry.aliases.map((alias, index) => (
              <Badge key={index} variant="secondary" className="text-xs">
                {alias}
              </Badge>
            ))}
          </div>
        </div>
      )}

      {/* 标签 */}
      {wikiEntry.tags && wikiEntry.tags.length > 0 && (
        <div>
          <h4 className="text-sm font-medium mb-2">标签</h4>
          <div className="flex flex-wrap gap-2">
            {wikiEntry.tags.map((tag, index) => (
              <Badge key={index} variant="outline" className="text-xs">
                <Tag className="h-3 w-3 mr-1" />
                {tag}
              </Badge>
            ))}
          </div>
        </div>
      )}

      {/* 时间版本 */}
      {wikiEntry.timeVersion && (
        <div>
          <h4 className="text-sm font-medium mb-2">时间版本</h4>
          <p className="text-sm text-muted-foreground">{wikiEntry.timeVersion}</p>
        </div>
      )}

      {/* 操作按钮 */}
      <div className="flex items-center gap-2 pt-2">
        {onViewEvolution && (
          <Button
            variant="outline"
            size="sm"
            onClick={() => onViewEvolution(wikiEntry.id, 'wiki', wikiEntry.title)}
          >
            <GitBranch className="h-4 w-4 mr-2" />
            查看演进
          </Button>
        )}
      </div>

      {/* 元信息 */}
      <div className="flex items-center gap-4 text-xs text-muted-foreground pt-2">
        <span className="flex items-center gap-1">
          <Clock className="h-3 w-3" />
          创建于 {new Date(wikiEntry.createdAt).toLocaleDateString('zh-CN')}
        </span>
        <span className="flex items-center gap-1">
          <Clock className="h-3 w-3" />
          更新于 {new Date(wikiEntry.updatedAt).toLocaleDateString('zh-CN')}
        </span>
      </div>
    </div>
  );
}

// 伏笔详情组件
function PlotLoopDetail({ plotLoop }: { plotLoop: PlotLoop }) {
  const getStatusIcon = () => {
    switch (plotLoop.status) {
      case 'RESOLVED':
        return <CheckCircle className="h-5 w-5 text-green-500" />;
      case 'ABANDONED':
        return <XCircle className="h-5 w-5 text-red-500" />;
      default:
        return <AlertCircle className="h-5 w-5 text-yellow-500" />;
    }
  };

  const getStatusText = () => {
    switch (plotLoop.status) {
      case 'RESOLVED':
        return '已解决';
      case 'ABANDONED':
        return '已放弃';
      default:
        return '进行中';
    }
  };

  const getStatusColor = () => {
    switch (plotLoop.status) {
      case 'RESOLVED':
        return 'bg-green-100 text-green-700';
      case 'ABANDONED':
        return 'bg-red-100 text-red-700';
      default:
        return 'bg-yellow-100 text-yellow-700';
    }
  };

  return (
    <div className="space-y-4">
      {/* 头部信息 */}
      <div className="flex items-start gap-4">
        <div className="w-12 h-12 rounded-lg bg-amber-500/10 flex items-center justify-center text-amber-500 shrink-0">
          <Zap className="h-6 w-6" />
        </div>
        <div className="flex-1 min-w-0">
          <h3 className="text-lg font-semibold">{plotLoop.title}</h3>
          <div className="flex items-center gap-2 mt-1">
            {getStatusIcon()}
            <Badge className={getStatusColor()}>{getStatusText()}</Badge>
          </div>
        </div>
      </div>

      <Separator />

      {/* 描述 */}
      <div>
        <h4 className="text-sm font-medium mb-2">描述</h4>
        <p className="text-sm text-muted-foreground whitespace-pre-wrap">
          {plotLoop.description}
        </p>
      </div>

      {/* 章节信息 */}
      <div className="grid grid-cols-2 gap-4">
        {plotLoop.introChapterOrder !== undefined && (
          <div className="p-3 rounded-lg bg-muted/50">
            <h4 className="text-xs font-medium text-muted-foreground mb-1">引入章节</h4>
            <p className="text-sm font-medium">第 {plotLoop.introChapterOrder} 章</p>
          </div>
        )}
        {plotLoop.resolutionChapterOrder !== undefined && (
          <div className="p-3 rounded-lg bg-muted/50">
            <h4 className="text-xs font-medium text-muted-foreground mb-1">解决章节</h4>
            <p className="text-sm font-medium">第 {plotLoop.resolutionChapterOrder} 章</p>
          </div>
        )}
      </div>

      {/* 放弃原因 */}
      {plotLoop.status === 'ABANDONED' && plotLoop.abandonReason && (
        <div className="p-3 rounded-lg bg-red-50 border border-red-200">
          <h4 className="text-xs font-medium text-red-700 mb-1">放弃原因</h4>
          <p className="text-sm text-red-600">{plotLoop.abandonReason}</p>
        </div>
      )}

      {/* 元信息 */}
      <div className="flex items-center gap-4 text-xs text-muted-foreground pt-2">
        <span className="flex items-center gap-1">
          <Clock className="h-3 w-3" />
          创建于 {new Date(plotLoop.createdAt).toLocaleDateString('zh-CN')}
        </span>
        <span className="flex items-center gap-1">
          <Clock className="h-3 w-3" />
          更新于 {new Date(plotLoop.updatedAt).toLocaleDateString('zh-CN')}
        </span>
      </div>
    </div>
  );
}

// 主对话框组件
export function AssetDetailDialog({
  open,
  onOpenChange,
  assetType,
  character,
  wikiEntry,
  plotLoop,
  onViewEvolution,
  onViewRelationGraph,
  onEdit,
}: AssetDetailDialogProps) {
  const getIcon = () => {
    switch (assetType) {
      case 'character':
        return <Users className="h-5 w-5" />;
      case 'wiki':
        return <BookOpen className="h-5 w-5" />;
      case 'plot':
        return <Zap className="h-5 w-5" />;
      default:
        return null;
    }
  };

  const getTitle = () => {
    switch (assetType) {
      case 'character':
        return '角色详情';
      case 'wiki':
        return '设定详情';
      case 'plot':
        return '伏笔详情';
      default:
        return '详情';
    }
  };

  const getCurrentId = () => {
    if (assetType === 'character' && character) return character.id;
    if (assetType === 'wiki' && wikiEntry) return wikiEntry.id;
    if (assetType === 'plot' && plotLoop) return plotLoop.id;
    return '';
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[500px] max-h-[80vh] overflow-hidden flex flex-col">
        <DialogHeader className="flex-shrink-0">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              {getIcon()}
              <DialogTitle>{getTitle()}</DialogTitle>
            </div>
            {onEdit && assetType && (
              <Button
                variant="ghost"
                size="sm"
                onClick={() => onEdit(assetType, getCurrentId())}
              >
                <Edit className="h-4 w-4 mr-1" />
                编辑
              </Button>
            )}
          </div>
        </DialogHeader>

        <ScrollArea className="flex-1 pr-4">
          {assetType === 'character' && character && (
            <CharacterDetail
              character={character}
              onViewEvolution={onViewEvolution}
              onViewRelationGraph={onViewRelationGraph}
            />
          )}
          {assetType === 'wiki' && wikiEntry && (
            <WikiDetail
              wikiEntry={wikiEntry}
              onViewEvolution={onViewEvolution}
            />
          )}
          {assetType === 'plot' && plotLoop && (
            <PlotLoopDetail plotLoop={plotLoop} />
          )}
        </ScrollArea>
      </DialogContent>
    </Dialog>
  );
}

export default AssetDetailDialog;
