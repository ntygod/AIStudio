/**
 * UI 状态管理
 * 管理布局状态、主题、响应式断点等
 * Requirements: 1.2, 1.3, 1.5, 14.1, 14.2
 */

import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';

// ============ 类型定义 ============

export type ThemeMode = 'light' | 'dark' | 'parchment' | 'forest';
export type MobileActivePanel = 'left' | 'editor' | 'right';
export type BreakpointType = 'mobile' | 'tablet' | 'desktop';

export interface UIState {
  // 侧边栏状态
  leftSidebarCollapsed: boolean;
  rightPanelCollapsed: boolean;
  
  // Zen 模式
  zenMode: boolean;
  
  // 移动端活动面板
  mobileActivePanel: MobileActivePanel;
  
  // 主题
  theme: ThemeMode;
  
  // 响应式断点
  breakpoint: BreakpointType;
  viewportWidth: number;
}

export interface UIActions {
  // 侧边栏操作
  toggleLeftSidebar: () => void;
  toggleRightPanel: () => void;
  setLeftSidebarCollapsed: (collapsed: boolean) => void;
  setRightPanelCollapsed: (collapsed: boolean) => void;
  
  // Zen 模式
  toggleZenMode: () => void;
  setZenMode: (enabled: boolean) => void;
  
  // 移动端面板切换
  setMobileActivePanel: (panel: MobileActivePanel) => void;
  
  // 主题
  setTheme: (theme: ThemeMode) => void;
  
  // 响应式断点
  updateViewportWidth: (width: number) => void;
  
  // 重置
  resetLayout: () => void;
}

type UIStore = UIState & UIActions;

// ============ 断点常量 ============

export const BREAKPOINTS = {
  mobile: 768,   // < 768px
  tablet: 1024,  // 768px - 1024px
  desktop: 1024, // > 1024px
} as const;

// ============ 辅助函数 ============

/**
 * 根据视口宽度计算断点类型
 */
export function calculateBreakpoint(width: number): BreakpointType {
  if (width < BREAKPOINTS.mobile) {
    return 'mobile';
  }
  if (width < BREAKPOINTS.desktop) {
    return 'tablet';
  }
  return 'desktop';
}

// ============ 初始状态 ============

const initialState: UIState = {
  leftSidebarCollapsed: false,
  rightPanelCollapsed: false,
  zenMode: false,
  mobileActivePanel: 'editor',
  theme: 'dark',
  breakpoint: 'desktop',
  viewportWidth: typeof window !== 'undefined' ? window.innerWidth : 1280,
};

// ============ Store 创建 ============

export const useUIStore = create<UIStore>()(
  persist(
    (set, get) => ({
      // 初始状态
      ...initialState,

      // ============ 侧边栏操作 ============
      
      toggleLeftSidebar: () => {
        set((state) => ({
          leftSidebarCollapsed: !state.leftSidebarCollapsed,
        }));
      },

      toggleRightPanel: () => {
        set((state) => ({
          rightPanelCollapsed: !state.rightPanelCollapsed,
        }));
      },

      setLeftSidebarCollapsed: (collapsed: boolean) => {
        set({ leftSidebarCollapsed: collapsed });
      },

      setRightPanelCollapsed: (collapsed: boolean) => {
        set({ rightPanelCollapsed: collapsed });
      },

      // ============ Zen 模式 ============
      
      toggleZenMode: () => {
        set((state) => ({
          zenMode: !state.zenMode,
        }));
      },

      setZenMode: (enabled: boolean) => {
        set({ zenMode: enabled });
      },

      // ============ 移动端面板切换 ============
      
      setMobileActivePanel: (panel: MobileActivePanel) => {
        set({ mobileActivePanel: panel });
      },

      // ============ 主题 ============
      
      setTheme: (theme: ThemeMode) => {
        set({ theme });
        // 同步到 DOM
        if (typeof document !== 'undefined') {
          const root = document.documentElement;
          root.classList.remove('dark', 'light', 'parchment', 'forest');
          root.classList.add(theme);
        }
      },

      // ============ 响应式断点 ============
      
      updateViewportWidth: (width: number) => {
        const newBreakpoint = calculateBreakpoint(width);
        const currentBreakpoint = get().breakpoint;
        
        set({ 
          viewportWidth: width,
          breakpoint: newBreakpoint,
        });
        
        // 断点变化时自动调整布局
        if (newBreakpoint !== currentBreakpoint) {
          if (newBreakpoint === 'mobile') {
            // 移动端：默认显示编辑器
            set({ mobileActivePanel: 'editor' });
          } else if (newBreakpoint === 'tablet') {
            // 平板：折叠右侧面板
            set({ rightPanelCollapsed: true });
          }
        }
      },

      // ============ 重置 ============
      
      resetLayout: () => {
        set({
          leftSidebarCollapsed: false,
          rightPanelCollapsed: false,
          zenMode: false,
          mobileActivePanel: 'editor',
        });
      },
    }),
    {
      name: 'inkflow-ui-state',
      storage: createJSONStorage(() => localStorage),
      // 只持久化部分状态
      partialize: (state) => ({
        leftSidebarCollapsed: state.leftSidebarCollapsed,
        rightPanelCollapsed: state.rightPanelCollapsed,
        zenMode: state.zenMode,
        theme: state.theme,
        mobileActivePanel: state.mobileActivePanel,
      }),
      // 恢复时同步主题到 DOM
      onRehydrateStorage: () => (state) => {
        if (state && typeof document !== 'undefined') {
          const root = document.documentElement;
          root.classList.remove('dark', 'light', 'parchment', 'forest');
          root.classList.add(state.theme);
        }
      },
    }
  )
);

// ============ 选择器 ============

export const selectLeftSidebarCollapsed = (state: UIStore) => state.leftSidebarCollapsed;
export const selectRightPanelCollapsed = (state: UIStore) => state.rightPanelCollapsed;
export const selectZenMode = (state: UIStore) => state.zenMode;
export const selectMobileActivePanel = (state: UIStore) => state.mobileActivePanel;
export const selectTheme = (state: UIStore) => state.theme;
export const selectBreakpoint = (state: UIStore) => state.breakpoint;
export const selectViewportWidth = (state: UIStore) => state.viewportWidth;

// 派生选择器
export const selectIsMobile = (state: UIStore) => state.breakpoint === 'mobile';
export const selectIsTablet = (state: UIStore) => state.breakpoint === 'tablet';
export const selectIsDesktop = (state: UIStore) => state.breakpoint === 'desktop';

// 布局配置选择器
export const selectLayoutConfig = (state: UIStore) => ({
  leftSidebarCollapsed: state.leftSidebarCollapsed,
  rightPanelCollapsed: state.rightPanelCollapsed,
  zenMode: state.zenMode,
  breakpoint: state.breakpoint,
  mobileActivePanel: state.mobileActivePanel,
});
