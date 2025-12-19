/**
 * Artifact 卡片组件
 * 显示 AI 生成的资产（角色、Wiki、内容等）
 * 支持应用到编辑器
 */

import { useCallback } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '../ui/card';
import { Badge } from '../ui/badge';
import { Button } from '../ui/button';
import { User, BookOpen, FileText, Zap, Copy, Check, ExternalLink } from 'lucide-react';
import { useState } from 'react';
import { useContentStore } from '@/stores/content-store';
import { useProjectStore } from '@/stores/project-store';
import type { Artifact } from '@/types';

export type ArtifactType = 'character' | 'wiki' | 'outline' | 'plotloop' | 'content';

interface ArtifactCardProps {
  /** Artifact 类型 */
  type: ArtifactType;
  /** 标题 */
  title: string;
  /** 描述 */
  description?: string;
  /** 标签 */
  tags?: string[];
  /** 内容预览 */
  content?: string;
  /** 完整内容（用于应用到编辑器） */
  fullContent?: string;
  /** Artifact ID（用于跳转） */
  artifactId?: string;
  /** 应用回调（可选，用于非集成模式） */
  onApply?: () => void;
  /** 查看详情回调 */
  onView?: () => void;
  /** 是否使用集成模式（连接 store） */
  integrated?: boolean;
}

// 类型图标配置
const typeIcons = {
  character: User,
  wiki: BookOpen,
  outline: FileText,
  plotloop: Zap,
  content: FileText,
};

// 类型颜色配置
const typeColors = {
  character: 'text-pink-500',
  wiki: 'text-blue-500',
  outline: 'text-purple-500',
  plotloop: 'text-orange-500',
  content: 'text-green-500',
};

// 类型标签配置
const typeLabels = {
  character: '角色',
  wiki: '设定',
  outline: '大纲',
  plotloop: '伏笔',
  content: '内容',
};

export function ArtifactCard({ 
  type, 
  title, 
  description, 
  tags = [], 
  content, 
  fullContent,
  artifactId: _artifactId,
  onApply: externalOnApply, 
  onView,
  integrated = false 
}: ArtifactCardProps) {
  const [copied, setCopied] = useState(false);
  const [applying, setApplying] = useState(false);

  // Store hooks (only used in integrated mode)
  const currentProject = useProjectStore(state => state.currentProject);
  const { currentChapter, insertAtCursor } = useContentStore();

  const Icon = typeIcons[type] || FileText;
  const iconColor = typeColors[type] || 'text-foreground';
  const typeLabel = typeLabels[type] || type;

  // 复制内容到剪贴板
  const handleCopy = useCallback(async () => {
    const textToCopy = fullContent || content || '';
    if (!textToCopy) return;

    try {
      await navigator.clipboard.writeText(textToCopy);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error('Failed to copy:', err);
    }
  }, [fullContent, content]);

  // 应用到编辑器 (Requirements: 4.8 - 在光标位置插入)
  const handleApply = useCallback(async () => {
    if (externalOnApply) {
      externalOnApply();
      return;
    }

    if (!integrated || !currentProject || !currentChapter) return;

    const textToApply = fullContent || content || '';
    if (!textToApply) return;

    setApplying(true);
    try {
      // 在光标位置插入内容
      await insertAtCursor(currentProject.id, currentChapter.id, textToApply);
    } catch (err) {
      console.error('Failed to apply:', err);
    } finally {
      setApplying(false);
    }
  }, [
    externalOnApply, 
    integrated, 
    currentProject, 
    currentChapter, 
    fullContent, 
    content, 
    insertAtCursor
  ]);

  // 是否可以应用到编辑器
  const canApply = type === 'content' || type === 'outline';
  const hasApplyAction = externalOnApply || (integrated && canApply && currentChapter);

  return (
    <Card className="mb-4 border-border shadow-md rounded-2xl overflow-hidden hover:shadow-lg transition-shadow">
      <CardHeader className="pb-3">
        <div className="flex items-start justify-between">
          <CardTitle className="flex items-center gap-3">
            <div className={`p-2 rounded-xl bg-accent ${iconColor}`}>
              <Icon className="h-5 w-5" />
            </div>
            <div>
              <span className="block">{title}</span>
              <span className="text-xs text-muted-foreground font-normal">
                {typeLabel}
              </span>
            </div>
          </CardTitle>
          
          {/* 操作按钮 */}
          <div className="flex items-center gap-1">
            {content && (
              <Button
                variant="ghost"
                size="icon"
                className="h-8 w-8"
                onClick={handleCopy}
                title="复制内容"
              >
                {copied ? (
                  <Check className="h-4 w-4 text-green-500" />
                ) : (
                  <Copy className="h-4 w-4" />
                )}
              </Button>
            )}
            {onView && (
              <Button
                variant="ghost"
                size="icon"
                className="h-8 w-8"
                onClick={onView}
                title="查看详情"
              >
                <ExternalLink className="h-4 w-4" />
              </Button>
            )}
          </div>
        </div>
        
        {description && (
          <p className="text-sm text-muted-foreground mt-2">{description}</p>
        )}
      </CardHeader>
      
      {(tags.length > 0 || content || hasApplyAction) && (
        <CardContent className="space-y-4">
          {/* 标签 */}
          {tags.length > 0 && (
            <div className="flex flex-wrap gap-2">
              {tags.map((tag) => (
                <Badge key={tag} variant="secondary" className="text-xs rounded-full px-3 py-1">
                  {tag}
                </Badge>
              ))}
            </div>
          )}
          
          {/* 内容预览 */}
          {content && (
            <div className="text-sm text-muted-foreground bg-accent/50 rounded-xl p-4 whitespace-pre-wrap border border-border max-h-48 overflow-y-auto">
              {content}
            </div>
          )}
          
          {/* 应用按钮 */}
          {hasApplyAction && (
            <Button 
              onClick={handleApply}
              disabled={applying}
              className="w-full rounded-xl shadow-sm hover:shadow-md transition-all"
              variant="default"
            >
              {applying ? '应用中...' : '应用到编辑器'}
            </Button>
          )}
        </CardContent>
      )}
    </Card>
  );
}

/**
 * 从 Artifact 类型创建 ArtifactCard props
 */
export function artifactToCardProps(artifact: Artifact): Partial<ArtifactCardProps> {
  return {
    type: artifact.type as ArtifactType,
    title: artifact.title,
    content: artifact.preview,
    artifactId: artifact.id,
  };
}