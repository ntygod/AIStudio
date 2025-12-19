/**
 * 项目相关 hooks
 */

import { useEffect } from 'react';
import { useProjectStore } from '@/stores';

/**
 * 使用项目列表
 */
export function useProjects() {
  const projects = useProjectStore((state) => state.projects);
  const isLoading = useProjectStore((state) => state.isLoading);
  const error = useProjectStore((state) => state.error);
  const pagination = useProjectStore((state) => state.pagination);
  
  const fetchProjects = useProjectStore((state) => state.fetchProjects);
  const createProject = useProjectStore((state) => state.createProject);
  const deleteProject = useProjectStore((state) => state.deleteProject);
  const clearError = useProjectStore((state) => state.clearError);

  return {
    projects,
    isLoading,
    error,
    pagination,
    fetchProjects,
    createProject,
    deleteProject,
    clearError,
  };
}

/**
 * 使用当前项目
 */
export function useCurrentProject(projectId?: string) {
  const currentProject = useProjectStore((state) => state.currentProject);
  const isLoading = useProjectStore((state) => state.isLoading);
  const error = useProjectStore((state) => state.error);
  
  const fetchProject = useProjectStore((state) => state.fetchProject);
  const updateProject = useProjectStore((state) => state.updateProject);
  const updatePhase = useProjectStore((state) => state.updatePhase);
  const setCurrentProject = useProjectStore((state) => state.setCurrentProject);
  const exportProject = useProjectStore((state) => state.exportProject);

  // 自动加载项目
  useEffect(() => {
    if (projectId && (!currentProject || currentProject.id !== projectId)) {
      fetchProject(projectId);
    }
  }, [projectId, currentProject, fetchProject]);

  return {
    project: currentProject,
    isLoading,
    error,
    updateProject: (data: Parameters<typeof updateProject>[1]) => 
      currentProject ? updateProject(currentProject.id, data) : Promise.resolve(),
    updatePhase: (phase: Parameters<typeof updatePhase>[1]) =>
      currentProject ? updatePhase(currentProject.id, phase) : Promise.resolve(),
    setCurrentProject,
    exportProject: () => currentProject ? exportProject(currentProject.id) : Promise.resolve(),
  };
}
