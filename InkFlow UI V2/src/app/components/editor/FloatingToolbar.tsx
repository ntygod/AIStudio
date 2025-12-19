/**
 * 浮动工具栏组件
 * 文本选择时显示 AI 增强选项
 * 
 * Requirements: 3.3, 3.4
 * - 文本选择时显示浮动工具栏
 * - 提供 AI 增强选项（润色、扩写、缩写、改写等）
 */

import { useState, useEffect, useRef, useCallback } from 'react';
import { 
  Sparkles, 
  Wand2, 
  Minimize2, 
  Maximize2, 
  RefreshCw,
  MessageSquare,
  X
} from 'lucide-react';
import { Button } from '../ui/button';
import { 
  Tooltip, 
  TooltipContent, 
  TooltipProvider, 
  TooltipTrigger 
} from '../ui/tooltip';

export type EnhanceType = 
  | 'polish'      // 润色
  | 'expand'      // 扩写
  | 'condense'    // 缩写
  | 'rewrite'     // 改写
  | 'continue'    // 续写
  | 'chat';       // 对话讨论

interface FloatingToolbarProps {
  /** 选中的文本 */
  selectedText: string;
  /** 工具栏位置 */
  position: { x: number; y: number };
  /** AI 增强回调 */
  onEnhance: (type: EnhanceType, text: string) => void;
  /** 关闭回调 */
  onClose: () => void;
  /** 是否正在处理 */
  isProcessing?: boolean;
}

interface EnhanceOption {
  type: EnhanceType;
  icon: React.ElementType;
  label: string;
  description: string;
}

const enhanceOptions: EnhanceOption[] = [
  { 
    type: 'polish', 
    icon: Sparkles, 
    label: '润色', 
    description: '优化文字表达，提升文采' 
  },
  { 
    type: 'expand', 
    icon: Maximize2, 
    label: '扩写', 
    description: '丰富细节，扩展内容' 
  },
  { 
    type: 'condense', 
    icon: Minimize2, 
    label: '缩写', 
    description: '精简内容，保留核心' 
  },
  { 
    type: 'rewrite', 
    icon: RefreshCw, 
    label: '改写', 
    description: '换种方式表达相同意思' 
  },
  { 
    type: 'continue', 
    icon: Wand2, 
    label: '续写', 
    description: '基于选中内容继续创作' 
  },
  { 
    type: 'chat', 
    icon: MessageSquare, 
    label: '讨论', 
    description: '与 AI 讨论这段内容' 
  },
];

export function FloatingToolbar({
  selectedText,
  position,
  onEnhance,
  onClose,
  isProcessing = false
}: FloatingToolbarProps) {
  const toolbarRef = useRef<HTMLDivElement>(null);
  const [adjustedPosition, setAdjustedPosition] = useState(position);

  // 调整位置确保工具栏在视口内
  useEffect(() => {
    if (!toolbarRef.current) return;

    const toolbar = toolbarRef.current;
    const rect = toolbar.getBoundingClientRect();
    const viewportWidth = window.innerWidth;

    let newX = position.x;
    let newY = position.y;

    // 水平方向调整
    if (position.x + rect.width / 2 > viewportWidth - 20) {
      newX = viewportWidth - rect.width / 2 - 20;
    } else if (position.x - rect.width / 2 < 20) {
      newX = rect.width / 2 + 20;
    }

    // 垂直方向调整（工具栏显示在选中文本上方）
    if (position.y - rect.height - 10 < 20) {
      // 如果上方空间不足，显示在下方
      newY = position.y + 30;
    } else {
      newY = position.y - rect.height - 10;
    }

    setAdjustedPosition({ x: newX, y: newY });
  }, [position]);

  // 点击外部关闭
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (toolbarRef.current && !toolbarRef.current.contains(e.target as Node)) {
        onClose();
      }
    };

    // 延迟添加事件监听，避免立即触发
    const timer = setTimeout(() => {
      document.addEventListener('mousedown', handleClickOutside);
    }, 100);

    return () => {
      clearTimeout(timer);
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [onClose]);

  // ESC 键关闭
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  const handleEnhance = useCallback((type: EnhanceType) => {
    if (!isProcessing) {
      onEnhance(type, selectedText);
    }
  }, [isProcessing, onEnhance, selectedText]);

  return (
    <TooltipProvider delayDuration={300}>
      <div
        ref={toolbarRef}
        className="fixed z-50 animate-in fade-in-0 zoom-in-95 duration-150"
        style={{
          left: adjustedPosition.x,
          top: adjustedPosition.y,
          transform: 'translateX(-50%)',
        }}
      >
        <div className="flex items-center gap-1 px-2 py-1.5 bg-popover border border-border rounded-lg shadow-lg">
          {enhanceOptions.map((option) => (
            <Tooltip key={option.type}>
              <TooltipTrigger asChild>
                <Button
                  variant="ghost"
                  size="sm"
                  className="h-8 w-8 p-0 hover:bg-accent"
                  onClick={() => handleEnhance(option.type)}
                  disabled={isProcessing}
                >
                  <option.icon className="h-4 w-4" />
                </Button>
              </TooltipTrigger>
              <TooltipContent side="bottom" className="max-w-[200px]">
                <p className="font-medium">{option.label}</p>
                <p className="text-xs text-muted-foreground">{option.description}</p>
              </TooltipContent>
            </Tooltip>
          ))}
          
          {/* 分隔线 */}
          <div className="w-px h-5 bg-border mx-1" />
          
          {/* 关闭按钮 */}
          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                variant="ghost"
                size="sm"
                className="h-8 w-8 p-0 hover:bg-accent"
                onClick={onClose}
              >
                <X className="h-4 w-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent side="bottom">
              <p>关闭 (Esc)</p>
            </TooltipContent>
          </Tooltip>
        </div>

        {/* 选中文本预览（可选，当文本较长时显示） */}
        {selectedText.length > 50 && (
          <div className="mt-2 px-3 py-2 bg-muted/50 rounded-md max-w-[300px]">
            <p className="text-xs text-muted-foreground line-clamp-2">
              {selectedText}
            </p>
          </div>
        )}
      </div>
    </TooltipProvider>
  );
}
