/**
 * 编辑器组件
 * 支持章节内容编辑、自动保存、字数统计、浮动工具栏
 * 集成 ContentStore 进行数据管理
 * 
 * Requirements: 3.1, 3.3, 3.4, 3.5, 3.6, 3.7
 */

import { useState, useEffect, useCallback, useRef } from 'react';
import { Maximize2, Minimize2, BarChart3, Save, Loader2, Check, AlertCircle } from 'lucide-react';
import { Button } from '../ui/button';
import { Textarea } from '../ui/textarea';
import { useContentStore } from '@/stores/content-store';
import { useProjectStore } from '@/stores/project-store';
import { FloatingToolbar, type EnhanceType } from './FloatingToolbar';

// 选中文本的位置信息
interface SelectionPosition {
  x: number;
  y: number;
}

// 选中状态
interface SelectionState {
  text: string;
  position: SelectionPosition;
  start: number;
  end: number;
}

interface TipTapEditorProps {
  /** 外部传入的内容（可选，用于非集成模式） */
  content?: string;
  /** 内容变更回调（可选，用于非集成模式） */
  onChange?: (content: string) => void;
  /** 禅模式切换回调 */
  onZenToggle: () => void;
  /** 是否处于禅模式 */
  zenMode: boolean;
  /** 面包屑导航文本 */
  breadcrumb?: string;
  /** 是否使用集成模式（连接 store） */
  integrated?: boolean;
  /** 自动保存延迟（毫秒） */
  autoSaveDelay?: number;
  /** AI 增强回调 */
  onAIEnhance?: (type: EnhanceType, text: string) => void;
}

// 保存状态类型
type SaveStatus = 'idle' | 'saving' | 'saved' | 'error';

// 计算中文字数
const countChineseWords = (text: string): number => {
  // 移除空白字符后计算字符数（中文按字符计算）
  const chineseChars = text.replace(/\s/g, '').length;
  return chineseChars;
};

export function TipTapEditor({ 
  content: externalContent, 
  onChange: externalOnChange, 
  onZenToggle, 
  zenMode, 
  breadcrumb,
  integrated = false,
  autoSaveDelay = 2000,
  onAIEnhance
}: TipTapEditorProps) {
  const [showStats, setShowStats] = useState(false);
  const [localContent, setLocalContent] = useState('');
  const [saveStatus, setSaveStatus] = useState<SaveStatus>('idle');
  const [selection, setSelection] = useState<SelectionState | null>(null);
  const [isAIProcessing, setIsAIProcessing] = useState(false);
  const saveTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const lastSavedContentRef = useRef<string>('');
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // Store hooks (only used in integrated mode)
  const currentProject = useProjectStore(state => state.currentProject);
  const { 
    currentChapter,
    currentContent,
    isLoading,
    isSaving,
    error,
    fetchChapterContent,
    saveChapterContent,
    getCurrentBreadcrumb,
    setCursorPosition
  } = useContentStore();

  // 决定使用哪个内容
  const content = integrated 
    ? (localContent || currentContent?.content || '')
    : (externalContent || '');

  // 集成模式下加载章节内容
  useEffect(() => {
    if (integrated && currentProject && currentChapter) {
      fetchChapterContent(currentProject.id, currentChapter.id);
    }
  }, [integrated, currentProject, currentChapter, fetchChapterContent]);

  // 同步 store 内容到本地状态
  useEffect(() => {
    if (integrated && currentContent) {
      setLocalContent(currentContent.content);
      lastSavedContentRef.current = currentContent.content;
    }
  }, [integrated, currentContent]);

  // 更新保存状态
  useEffect(() => {
    if (isSaving) {
      setSaveStatus('saving');
    } else if (error) {
      setSaveStatus('error');
    }
  }, [isSaving, error]);

  // 自动保存逻辑
  const scheduleAutoSave = useCallback(() => {
    if (!integrated || !currentProject || !currentChapter) return;

    // 清除之前的定时器
    if (saveTimeoutRef.current) {
      clearTimeout(saveTimeoutRef.current);
    }

    // 设置新的定时器
    saveTimeoutRef.current = setTimeout(async () => {
      // 只有内容有变化时才保存
      if (localContent !== lastSavedContentRef.current) {
        try {
          await saveChapterContent(currentProject.id, currentChapter.id, localContent);
          lastSavedContentRef.current = localContent;
          setSaveStatus('saved');
          // 3秒后重置状态
          setTimeout(() => setSaveStatus('idle'), 3000);
        } catch {
          setSaveStatus('error');
        }
      }
    }, autoSaveDelay);
  }, [integrated, currentProject, currentChapter, localContent, autoSaveDelay, saveChapterContent]);

  // 处理内容变更
  const handleContentChange = useCallback((newContent: string) => {
    if (integrated) {
      setLocalContent(newContent);
      scheduleAutoSave();
    } else if (externalOnChange) {
      externalOnChange(newContent);
    }
  }, [integrated, externalOnChange, scheduleAutoSave]);

  // 手动保存
  const handleManualSave = useCallback(async () => {
    if (!integrated || !currentProject || !currentChapter) return;

    // 清除自动保存定时器
    if (saveTimeoutRef.current) {
      clearTimeout(saveTimeoutRef.current);
    }

    try {
      await saveChapterContent(currentProject.id, currentChapter.id, localContent);
      lastSavedContentRef.current = localContent;
      setSaveStatus('saved');
      setTimeout(() => setSaveStatus('idle'), 3000);
    } catch {
      setSaveStatus('error');
    }
  }, [integrated, currentProject, currentChapter, localContent, saveChapterContent]);

  // 清理定时器
  useEffect(() => {
    return () => {
      if (saveTimeoutRef.current) {
        clearTimeout(saveTimeoutRef.current);
      }
    };
  }, []);

  // 处理文本选择和光标位置更新 (Requirements: 4.8)
  const handleTextSelect = useCallback(() => {
    const textarea = textareaRef.current;
    if (!textarea) return;

    const selectedText = textarea.value.substring(
      textarea.selectionStart,
      textarea.selectionEnd
    );

    // 更新光标位置到 store（用于 ArtifactCard 插入）
    if (integrated) {
      setCursorPosition(textarea.selectionStart);
    }

    // 只有选中了文本才显示工具栏
    if (selectedText.trim().length > 0) {
      // 获取选中文本的位置
      const rect = textarea.getBoundingClientRect();
      
      // 计算选中文本的大致位置（基于字符位置估算）
      const textBeforeSelection = textarea.value.substring(0, textarea.selectionStart);
      const lines = textBeforeSelection.split('\n');
      const lineHeight = 36; // 大约的行高
      const charWidth = 18; // 大约的字符宽度
      
      const currentLine = lines.length - 1;
      const currentCol = lines[lines.length - 1].length;
      
      // 计算位置（相对于视口）
      const x = rect.left + Math.min(currentCol * charWidth, rect.width / 2) + 100;
      const y = rect.top + currentLine * lineHeight + 80 - textarea.scrollTop;

      setSelection({
        text: selectedText,
        position: { x, y },
        start: textarea.selectionStart,
        end: textarea.selectionEnd
      });
    } else {
      setSelection(null);
    }
  }, [integrated, setCursorPosition]);

  // 关闭浮动工具栏
  const handleCloseToolbar = useCallback(() => {
    setSelection(null);
  }, []);

  // 处理 AI 增强
  const handleAIEnhance = useCallback(async (type: EnhanceType, text: string) => {
    if (onAIEnhance) {
      setIsAIProcessing(true);
      try {
        await onAIEnhance(type, text);
      } finally {
        setIsAIProcessing(false);
        setSelection(null);
      }
    } else {
      // 默认行为：关闭工具栏并在控制台输出
      console.log('AI Enhance:', type, text);
      setSelection(null);
    }
  }, [onAIEnhance]);

  // 键盘快捷键：Ctrl+S 保存
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 's') {
        e.preventDefault();
        handleManualSave();
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [handleManualSave]);

  // 计算统计数据
  const wordCount = countChineseWords(content);
  const charCount = content.length;

  // 渲染保存状态指示器
  const renderSaveStatus = () => {
    if (!integrated) return null;

    switch (saveStatus) {
      case 'saving':
        return (
          <div className="flex items-center gap-1.5 text-muted-foreground">
            <Loader2 className="h-3.5 w-3.5 animate-spin" />
            <span className="text-xs">保存中...</span>
          </div>
        );
      case 'saved':
        return (
          <div className="flex items-center gap-1.5 text-green-500">
            <Check className="h-3.5 w-3.5" />
            <span className="text-xs">已保存</span>
          </div>
        );
      case 'error':
        return (
          <div className="flex items-center gap-1.5 text-red-500">
            <AlertCircle className="h-3.5 w-3.5" />
            <span className="text-xs">保存失败</span>
          </div>
        );
      default:
        return null;
    }
  };

  // 加载状态
  if (integrated && isLoading && !currentContent) {
    return (
      <div className="h-full flex items-center justify-center">
        <div className="flex flex-col items-center gap-3">
          <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
          <p className="text-sm text-muted-foreground">加载章节内容...</p>
        </div>
      </div>
    );
  }

  // 无章节选中状态
  if (integrated && !currentChapter) {
    return (
      <div className="h-full flex items-center justify-center">
        <div className="flex flex-col items-center gap-3 text-center px-4">
          <p className="text-muted-foreground">选择一个章节开始编辑</p>
          <p className="text-xs text-muted-foreground/70">从左侧目录选择章节</p>
        </div>
      </div>
    );
  }

  return (
    <div className="h-full flex flex-col">
      {/* Meta Bar */}
      <div className="border-b border-border bg-card px-6 py-4 flex items-center justify-between shrink-0 shadow-sm">
        <div className="flex items-center gap-4">
          {/* 面包屑导航 - 集成模式使用 store 的面包屑，否则使用 prop */}
          {integrated ? (
            <div className="text-sm text-muted-foreground font-medium">
              {getCurrentBreadcrumb() || '选择章节'}
            </div>
          ) : (
            breadcrumb && (
              <div className="text-sm text-muted-foreground font-medium">{breadcrumb}</div>
            )
          )}
        </div>

        <div className="flex items-center gap-3">
          {/* 保存状态 */}
          {renderSaveStatus()}

          {/* 手动保存按钮 */}
          {integrated && (
            <Button
              variant="ghost"
              size="sm"
              onClick={handleManualSave}
              disabled={isSaving}
              className="gap-2 rounded-full"
            >
              <Save className="h-4 w-4" />
              <span className="text-sm hidden sm:inline">保存</span>
            </Button>
          )}

          {/* 字数统计 */}
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setShowStats(!showStats)}
            className="gap-2 rounded-full"
          >
            <BarChart3 className="h-4 w-4" />
            <span className="text-sm">{wordCount} 字</span>
          </Button>

          {/* 禅模式切换 */}
          <Button
            variant="ghost"
            size="icon"
            onClick={onZenToggle}
            className="rounded-full"
          >
            {zenMode ? <Minimize2 className="h-4 w-4" /> : <Maximize2 className="h-4 w-4" />}
          </Button>
        </div>
      </div>

      {/* Editor Area */}
      <div className="flex-1 overflow-auto relative">
        <div className="w-full h-full flex justify-center">
          <Textarea
            ref={textareaRef}
            value={content}
            onChange={(e) => handleContentChange(e.target.value)}
            onSelect={handleTextSelect}
            onMouseUp={handleTextSelect}
            placeholder="开始书写你的故事..."
            className="w-full max-w-[800px] min-h-[calc(100vh-200px)] px-6 py-16 border-none resize-none focus-visible:ring-0 bg-transparent text-lg leading-loose font-serif text-[#E4E4E7]/90 dark:text-[#E4E4E7]/90 text-zinc-800 placeholder:text-muted-foreground/30"
            style={{
              fontFamily: '"Noto Serif SC", serif',
              lineHeight: '2.0',
              fontSize: '18px'
            }}
          />
        </div>

        {/* 浮动工具栏 */}
        {selection && (
          <FloatingToolbar
            selectedText={selection.text}
            position={selection.position}
            onEnhance={handleAIEnhance}
            onClose={handleCloseToolbar}
            isProcessing={isAIProcessing}
          />
        )}
      </div>

      {/* Stats Panel */}
      {showStats && (
        <div className="border-t border-border bg-card px-6 py-4 flex gap-8 text-sm text-muted-foreground shrink-0 shadow-sm">
          <div className="flex items-center gap-2">
            <span className="font-medium">字数:</span>
            <span>{wordCount.toLocaleString()}</span>
          </div>
          <div className="flex items-center gap-2">
            <span className="font-medium">字符:</span>
            <span>{charCount.toLocaleString()}</span>
          </div>
          <div className="flex items-center gap-2">
            <span className="font-medium">预估阅读:</span>
            <span>{Math.ceil(wordCount / 500)} 分钟</span>
          </div>
          {integrated && currentContent && (
            <div className="flex items-center gap-2">
              <span className="font-medium">上次保存:</span>
              <span>{new Date(currentContent.updatedAt).toLocaleTimeString()}</span>
            </div>
          )}
        </div>
      )}
    </div>
  );
}