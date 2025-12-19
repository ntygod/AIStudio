/**
 * MainLayout 组件
 * 三栏响应式布局，支持移动端面板切换
 * Requirements: 1.2, 1.3, 1.5, 14.1, 14.2
 */

import { ReactNode, useEffect, useCallback } from 'react';
import { ChevronLeft, ChevronRight, PanelLeft, Edit3, MessageSquare } from 'lucide-react';
import { Button } from '../ui/button';
import { 
  useUIStore, 
  selectLeftSidebarCollapsed,
  selectRightPanelCollapsed,
  selectZenMode,
  selectBreakpoint,
  selectMobileActivePanel,
} from '@/stores';

interface MainLayoutProps {
  leftSidebar: ReactNode;
  editor: ReactNode;
  rightSidebar: ReactNode;
  zenMode?: boolean;
  mobileView?: 'left' | 'editor' | 'right';
}

// 布局尺寸常量
const LAYOUT_SIZES = {
  leftSidebar: {
    default: 280,
    collapsed: 0,
  },
  rightPanel: {
    default: 400,
    collapsed: 0,
  },
} as const;

export function MainLayout({ 
  leftSidebar, 
  editor, 
  rightSidebar, 
  zenMode: zenModeProp,
  mobileView: mobileViewProp,
}: MainLayoutProps) {
  // 从 UIStore 获取状态
  const leftCollapsed = useUIStore(selectLeftSidebarCollapsed);
  const rightCollapsed = useUIStore(selectRightPanelCollapsed);
  const zenModeStore = useUIStore(selectZenMode);
  const breakpoint = useUIStore(selectBreakpoint);
  const mobileActivePanel = useUIStore(selectMobileActivePanel);
  
  // Actions
  const toggleLeftSidebar = useUIStore((state) => state.toggleLeftSidebar);
  const toggleRightPanel = useUIStore((state) => state.toggleRightPanel);
  const updateViewportWidth = useUIStore((state) => state.updateViewportWidth);
  const setMobileActivePanel = useUIStore((state) => state.setMobileActivePanel);

  // 使用 prop 或 store 的值
  const zenMode = zenModeProp ?? zenModeStore;
  const currentMobilePanel = mobileViewProp ?? mobileActivePanel;

  // 响应式断点检测
  const handleResize = useCallback(() => {
    updateViewportWidth(window.innerWidth);
  }, [updateViewportWidth]);

  useEffect(() => {
    // 初始化视口宽度
    updateViewportWidth(window.innerWidth);
    
    // 监听窗口大小变化
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, [handleResize, updateViewportWidth]);

  // Zen 模式渲染
  if (zenMode) {
    return (
      <div className="h-screen w-screen bg-background flex items-center justify-center">
        <div className="max-w-4xl w-full px-8">
          {editor}
        </div>
      </div>
    );
  }

  // 移动端布局 (< 768px)
  if (breakpoint === 'mobile') {
    return (
      <div className="h-screen w-screen bg-background flex flex-col overflow-hidden">
        {/* 移动端内容区域 */}
        <div className="flex-1 overflow-hidden">
          {currentMobilePanel === 'left' && (
            <div className="h-full overflow-auto">
              {leftSidebar}
            </div>
          )}
          {currentMobilePanel === 'editor' && (
            <div className="h-full overflow-hidden">
              {editor}
            </div>
          )}
          {currentMobilePanel === 'right' && (
            <div className="h-full overflow-auto">
              {rightSidebar}
            </div>
          )}
        </div>

        {/* 移动端底部导航 */}
        <MobileNavBar 
          activePanel={currentMobilePanel} 
          onPanelChange={setMobileActivePanel} 
        />
      </div>
    );
  }

  // 平板布局 (768px - 1024px): 双栏
  if (breakpoint === 'tablet') {
    return (
      <div className="h-screen w-screen bg-background flex overflow-hidden">
        {/* 左侧边栏 */}
        <div 
          className="relative border-r border-border bg-background transition-all duration-300"
          style={{ width: leftCollapsed ? LAYOUT_SIZES.leftSidebar.collapsed : LAYOUT_SIZES.leftSidebar.default }}
        >
          <div className={`h-full overflow-hidden ${leftCollapsed ? 'invisible' : 'visible'}`}>
            {leftSidebar}
          </div>
          <CollapseButton 
            direction="left" 
            collapsed={leftCollapsed} 
            onClick={toggleLeftSidebar} 
          />
        </div>

        {/* 主编辑器 */}
        <div className="flex-1 min-w-0 overflow-hidden">
          {editor}
        </div>

        {/* 平板端右侧面板切换按钮 */}
        <Button
          variant="ghost"
          size="icon"
          className="fixed right-4 bottom-4 h-12 w-12 rounded-full border border-border bg-card shadow-lg hover:shadow-xl transition-all z-50"
          onClick={toggleRightPanel}
          title="Toggle AI Panel"
        >
          <MessageSquare className="h-5 w-5" />
        </Button>

        {/* 右侧面板 (平板端作为抽屉) */}
        {!rightCollapsed && (
          <div 
            className="fixed inset-y-0 right-0 w-[400px] border-l border-border bg-background shadow-xl z-40 animate-in slide-in-from-right duration-300"
          >
            <div className="h-full overflow-hidden">
              {rightSidebar}
            </div>
            <Button
              variant="ghost"
              size="icon"
              className="absolute left-2 top-2 h-8 w-8 rounded-full"
              onClick={toggleRightPanel}
            >
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        )}
      </div>
    );
  }

  // 桌面端布局 (> 1024px): 三栏
  return (
    <div className="h-screen w-screen bg-background flex overflow-hidden">
      {/* 左侧边栏 */}
      <div 
        className="relative border-r border-border bg-background transition-all duration-300"
        style={{ width: leftCollapsed ? LAYOUT_SIZES.leftSidebar.collapsed : LAYOUT_SIZES.leftSidebar.default }}
      >
        <div className={`h-full overflow-hidden ${leftCollapsed ? 'invisible' : 'visible'}`}>
          {leftSidebar}
        </div>
        <CollapseButton 
          direction="left" 
          collapsed={leftCollapsed} 
          onClick={toggleLeftSidebar} 
        />
      </div>

      {/* 主编辑器 */}
      <div className="flex-1 min-w-0 overflow-hidden">
        {editor}
      </div>

      {/* 右侧面板 */}
      <div 
        className="relative border-l border-border bg-background transition-all duration-300"
        style={{ width: rightCollapsed ? LAYOUT_SIZES.rightPanel.collapsed : LAYOUT_SIZES.rightPanel.default }}
      >
        <div className={`h-full overflow-hidden ${rightCollapsed ? 'invisible' : 'visible'}`}>
          {rightSidebar}
        </div>
        <CollapseButton 
          direction="right" 
          collapsed={rightCollapsed} 
          onClick={toggleRightPanel} 
        />
      </div>
    </div>
  );
}

// ============ 子组件 ============

interface CollapseButtonProps {
  direction: 'left' | 'right';
  collapsed: boolean;
  onClick: () => void;
}

function CollapseButton({ direction, collapsed, onClick }: CollapseButtonProps) {
  const isLeft = direction === 'left';
  const positionClass = isLeft 
    ? '-right-4' 
    : '-left-4';
  
  const Icon = isLeft
    ? (collapsed ? ChevronRight : ChevronLeft)
    : (collapsed ? ChevronLeft : ChevronRight);

  return (
    <Button
      variant="ghost"
      size="icon"
      className={`absolute ${positionClass} top-1/2 -translate-y-1/2 h-8 w-8 rounded-full border border-border bg-card shadow-lg hover:shadow-xl transition-all z-10`}
      onClick={onClick}
    >
      <Icon className="h-4 w-4" />
    </Button>
  );
}

interface MobileNavBarProps {
  activePanel: 'left' | 'editor' | 'right';
  onPanelChange: (panel: 'left' | 'editor' | 'right') => void;
}

function MobileNavBar({ activePanel, onPanelChange }: MobileNavBarProps) {
  const navItems = [
    { id: 'left' as const, icon: PanelLeft, label: '项目' },
    { id: 'editor' as const, icon: Edit3, label: '编辑' },
    { id: 'right' as const, icon: MessageSquare, label: 'AI' },
  ];

  return (
    <nav className="flex items-center justify-around border-t border-border bg-card px-2 py-2 shrink-0">
      {navItems.map((item) => {
        const Icon = item.icon;
        const isActive = activePanel === item.id;
        
        return (
          <Button
            key={item.id}
            variant={isActive ? 'default' : 'ghost'}
            size="sm"
            className={`flex-1 mx-1 flex flex-col items-center gap-1 h-auto py-2 ${
              isActive ? 'bg-primary text-primary-foreground' : ''
            }`}
            onClick={() => onPanelChange(item.id)}
          >
            <Icon className="h-5 w-5" />
            <span className="text-xs">{item.label}</span>
          </Button>
        );
      })}
    </nav>
  );
}

// 导出布局尺寸常量供其他组件使用
export { LAYOUT_SIZES };
