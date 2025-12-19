/**
 * 项目状态管理
 * 使用 Zustand 管理项目数据
 */

import { create } from 'zustand';
import type { 
  Project, 
  CreateProjectRequest, 
  UpdateProjectRequest,
  CreationPhase,
  PaginationInfo 
} from '@/types';
import { projectService } from '@/services/project-service';

interface CacheEntry<T> {
  data: T;
  timestamp: number;
}

interface ProjectState {
  projects: Project[];
  currentProject: Project | null;
  isLoading: boolean;
  error: string | null;
  pagination: PaginationInfo;
  // 缓存相关
  projectCache: Map<string, CacheEntry<Project>>;
  listCacheTimestamp: number | null;
}

interface ProjectActions {
  fetchProjects(page?: number): Promise<void>;
  fetchProject(id: string, forceRefresh?: boolean): Promise<void>;
  createProject(data: CreateProjectRequest): Promise<Project>;
  updateProject(id: string, data: UpdateProjectRequest): Promise<void>;
  deleteProject(id: string): Promise<void>;
  setCurrentProject(project: Project | null): void;
  updatePhase(id: string, phase: CreationPhase): Promise<void>;
  exportProject(id: string): Promise<void>;
  importProject(file: File): Promise<Project>;
  clearError(): void;
  invalidateCache(): void;
  isCacheValid(timestamp: number | null): boolean;
}

type ProjectStore = ProjectState & ProjectActions;

// 缓存有效期（5分钟）
const CACHE_TTL = 5 * 60 * 1000;

export const useProjectStore = create<ProjectStore>()((set, get) => ({
  // 初始状态
  projects: [],
  currentProject: null,
  isLoading: false,
  error: null,
  pagination: {
    page: 0,
    size: 20,
    total: 0,
    totalPages: 0,
  },
  projectCache: new Map(),
  listCacheTimestamp: null,

  /**
   * 检查缓存是否有效
   */
  isCacheValid: (timestamp: number | null): boolean => {
    if (!timestamp) return false;
    return Date.now() - timestamp < CACHE_TTL;
  },

  /**
   * 获取项目列表
   */
  fetchProjects: async (page: number = 0) => {
    const state = get();
    
    // 如果是同一页且缓存有效，跳过请求
    if (
      page === state.pagination.page &&
      state.isCacheValid(state.listCacheTimestamp) &&
      state.projects.length > 0
    ) {
      return;
    }

    set({ isLoading: true, error: null });

    try {
      const response = await projectService.getProjects(page, state.pagination.size);

      set({
        projects: response.content,
        pagination: {
          page: response.page,
          size: response.size,
          total: response.totalElements,
          totalPages: response.totalPages,
        },
        isLoading: false,
        listCacheTimestamp: Date.now(),
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : '获取项目列表失败';
      set({ isLoading: false, error: message });
      throw error;
    }
  },

  /**
   * 获取单个项目
   */
  fetchProject: async (id: string, forceRefresh: boolean = false) => {
    const state = get();
    
    // 检查缓存
    if (!forceRefresh) {
      const cached = state.projectCache.get(id);
      if (cached && state.isCacheValid(cached.timestamp)) {
        set({ currentProject: cached.data });
        return;
      }
    }

    set({ isLoading: true, error: null });

    try {
      const project = await projectService.getProject(id);

      // 更新缓存
      const newCache = new Map(state.projectCache);
      newCache.set(id, { data: project, timestamp: Date.now() });

      set({
        currentProject: project,
        projectCache: newCache,
        isLoading: false,
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : '获取项目详情失败';
      set({ isLoading: false, error: message });
      throw error;
    }
  },

  /**
   * 创建项目
   */
  createProject: async (data: CreateProjectRequest) => {
    set({ isLoading: true, error: null });

    try {
      const project = await projectService.createProject(data);

      // 乐观更新：添加到列表开头
      set((state) => ({
        projects: [project, ...state.projects],
        currentProject: project,
        isLoading: false,
        // 使列表缓存失效
        listCacheTimestamp: null,
      }));

      return project;
    } catch (error) {
      const message = error instanceof Error ? error.message : '创建项目失败';
      set({ isLoading: false, error: message });
      throw error;
    }
  },

  /**
   * 更新项目
   */
  updateProject: async (id: string, data: UpdateProjectRequest) => {
    const state = get();
    const originalProject = state.projects.find(p => p.id === id);

    // 乐观更新
    if (originalProject) {
      const optimisticProject = { ...originalProject, ...data, updatedAt: new Date().toISOString() };
      set((state) => ({
        projects: state.projects.map(p => p.id === id ? optimisticProject : p),
        currentProject: state.currentProject?.id === id ? optimisticProject : state.currentProject,
      }));
    }

    try {
      const updatedProject = await projectService.updateProject(id, data);

      // 更新缓存
      const newCache = new Map(state.projectCache);
      newCache.set(id, { data: updatedProject, timestamp: Date.now() });

      set((state) => ({
        projects: state.projects.map(p => p.id === id ? updatedProject : p),
        currentProject: state.currentProject?.id === id ? updatedProject : state.currentProject,
        projectCache: newCache,
      }));
    } catch (error) {
      // 回滚乐观更新
      if (originalProject) {
        set((state) => ({
          projects: state.projects.map(p => p.id === id ? originalProject : p),
          currentProject: state.currentProject?.id === id ? originalProject : state.currentProject,
        }));
      }

      const message = error instanceof Error ? error.message : '更新项目失败';
      set({ error: message });
      throw error;
    }
  },

  /**
   * 删除项目
   */
  deleteProject: async (id: string) => {
    const state = get();
    const originalProjects = [...state.projects];

    // 乐观更新
    set((state) => ({
      projects: state.projects.filter(p => p.id !== id),
      currentProject: state.currentProject?.id === id ? null : state.currentProject,
    }));

    try {
      await projectService.deleteProject(id);

      // 从缓存中移除
      const newCache = new Map(state.projectCache);
      newCache.delete(id);
      set({ projectCache: newCache });
    } catch (error) {
      // 回滚乐观更新
      set({ projects: originalProjects });

      const message = error instanceof Error ? error.message : '删除项目失败';
      set({ error: message });
      throw error;
    }
  },

  /**
   * 设置当前项目
   */
  setCurrentProject: (project: Project | null) => {
    set({ currentProject: project });
  },

  /**
   * 更新项目阶段
   */
  updatePhase: async (id: string, phase: CreationPhase) => {
    const state = get();
    const originalProject = state.projects.find(p => p.id === id);

    // 乐观更新
    if (originalProject) {
      const optimisticProject = { ...originalProject, creationPhase: phase };
      set((state) => ({
        projects: state.projects.map(p => p.id === id ? optimisticProject : p),
        currentProject: state.currentProject?.id === id ? optimisticProject : state.currentProject,
      }));
    }

    try {
      const updatedProject = await projectService.updatePhase(id, phase);

      // 更新缓存
      const newCache = new Map(state.projectCache);
      newCache.set(id, { data: updatedProject, timestamp: Date.now() });

      set((state) => ({
        projects: state.projects.map(p => p.id === id ? updatedProject : p),
        currentProject: state.currentProject?.id === id ? updatedProject : state.currentProject,
        projectCache: newCache,
      }));
    } catch (error) {
      // 回滚乐观更新
      if (originalProject) {
        set((state) => ({
          projects: state.projects.map(p => p.id === id ? originalProject : p),
          currentProject: state.currentProject?.id === id ? originalProject : state.currentProject,
        }));
      }

      const message = error instanceof Error ? error.message : '更新阶段失败';
      set({ error: message });
      throw error;
    }
  },

  /**
   * 导出项目
   */
  exportProject: async (id: string) => {
    set({ isLoading: true, error: null });

    try {
      await projectService.exportProject(id);
      set({ isLoading: false });
    } catch (error) {
      const message = error instanceof Error ? error.message : '导出项目失败';
      set({ isLoading: false, error: message });
      throw error;
    }
  },

  /**
   * 导入项目
   */
  importProject: async (file: File) => {
    set({ isLoading: true, error: null });

    try {
      const project = await projectService.importProject(file);

      // 添加到列表
      set((state) => ({
        projects: [project, ...state.projects],
        currentProject: project,
        isLoading: false,
        listCacheTimestamp: null,
      }));

      return project;
    } catch (error) {
      const message = error instanceof Error ? error.message : '导入项目失败';
      set({ isLoading: false, error: message });
      throw error;
    }
  },

  /**
   * 清除错误
   */
  clearError: () => {
    set({ error: null });
  },

  /**
   * 使缓存失效
   */
  invalidateCache: () => {
    set({
      projectCache: new Map(),
      listCacheTimestamp: null,
    });
  },
}));

// 导出选择器
export const selectProjects = (state: ProjectStore) => state.projects;
export const selectCurrentProject = (state: ProjectStore) => state.currentProject;
export const selectIsLoading = (state: ProjectStore) => state.isLoading;
export const selectError = (state: ProjectStore) => state.error;
export const selectPagination = (state: ProjectStore) => state.pagination;
