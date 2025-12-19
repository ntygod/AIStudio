/**
 * 项目列表页面
 * 显示用户的所有项目，支持创建、搜索、分页
 */

import { useEffect, useState, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { 
  Plus, 
  Search, 
  FolderOpen, 
  Clock, 
  FileText, 
  MoreVertical,
  Trash2,
  Download,
  Upload,
  ChevronLeft,
  ChevronRight,
  Loader2,
  AlertCircle,
  BookOpen
} from 'lucide-react';
import { Button } from '../components/ui/button';
import { useProjectStore } from '@/stores/project-store';
import type { Project, CreationPhase } from '@/types';

interface ProjectListPageProps {
  onSelectProject: (project: Project) => void;
  onCreateProject: () => void;
}

// Phase display configuration - 与后端 CreationPhase 枚举保持一致
const phaseConfig: Record<CreationPhase, { label: string; color: string; labelEn: string }> = {
  IDEA: { label: '灵感', labelEn: 'Idea', color: 'bg-yellow-500/20 text-yellow-400' },
  WORLDBUILDING: { label: '世界观', labelEn: 'Worldbuilding', color: 'bg-blue-500/20 text-blue-400' },
  CHARACTER: { label: '角色', labelEn: 'Character', color: 'bg-purple-500/20 text-purple-400' },
  OUTLINE: { label: '大纲', labelEn: 'Outline', color: 'bg-green-500/20 text-green-400' },
  WRITING: { label: '写作', labelEn: 'Writing', color: 'bg-orange-500/20 text-orange-400' },
  REVISION: { label: '修订', labelEn: 'Revision', color: 'bg-red-500/20 text-red-400' },
  COMPLETED: { label: '完成', labelEn: 'Completed', color: 'bg-emerald-500/20 text-emerald-400' },
};

export function ProjectListPage({ onSelectProject, onCreateProject }: ProjectListPageProps) {
  const [searchQuery, setSearchQuery] = useState('');
  const [showMenu, setShowMenu] = useState<string | null>(null);
  
  const { 
    projects, 
    isLoading, 
    error, 
    pagination,
    fetchProjects,
    deleteProject,
    exportProject,
    importProject,
    clearError
  } = useProjectStore();

  // Load projects on mount
  useEffect(() => {
    fetchProjects(0);
  }, [fetchProjects]);

  // Filter projects by search query
  const filteredProjects = projects.filter(project => 
    project.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
    project.description?.toLowerCase().includes(searchQuery.toLowerCase())
  );

  // Handle page change
  const handlePageChange = useCallback((page: number) => {
    fetchProjects(page);
  }, [fetchProjects]);

  // Handle delete project
  const handleDelete = useCallback(async (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (window.confirm('确定要删除这个项目吗？此操作不可撤销。')) {
      try {
        await deleteProject(id);
      } catch {
        // Error handled by store
      }
    }
    setShowMenu(null);
  }, [deleteProject]);

  // Handle export project
  const handleExport = useCallback(async (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      await exportProject(id);
    } catch {
      // Error handled by store
    }
    setShowMenu(null);
  }, [exportProject]);

  // Handle import project
  const handleImport = useCallback(async () => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.json';
    input.onchange = async (e) => {
      const file = (e.target as HTMLInputElement).files?.[0];
      if (file) {
        try {
          await importProject(file);
        } catch {
          // Error handled by store
        }
      }
    };
    input.click();
  }, [importProject]);

  // Format date
  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleDateString('zh-CN', { 
      year: 'numeric', 
      month: 'short', 
      day: 'numeric' 
    });
  };

  // Format word count
  const formatWordCount = (count?: number) => {
    if (!count) return '0';
    if (count >= 10000) return `${(count / 10000).toFixed(1)}万`;
    return count.toLocaleString();
  };

  return (
    <div className="min-h-screen bg-[#09090b] text-white">
      {/* Header */}
      <header className="border-b border-zinc-800 bg-zinc-950/50 backdrop-blur-sm sticky top-0 z-10">
        <div className="max-w-7xl mx-auto px-6 py-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <BookOpen className="h-8 w-8 text-violet-500" />
              <h1 className="text-2xl font-serif font-medium">我的作品</h1>
            </div>
            
            <div className="flex items-center gap-4">
              {/* Search */}
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-zinc-500" />
                <input
                  type="text"
                  placeholder="搜索项目..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="w-64 bg-zinc-900 border border-zinc-800 rounded-lg pl-10 pr-4 py-2 text-sm text-zinc-200 placeholder:text-zinc-600 focus:outline-none focus:border-violet-500 transition-colors"
                />
              </div>
              
              {/* Import Button */}
              <Button
                variant="outline"
                size="sm"
                onClick={handleImport}
                className="border-zinc-700 text-zinc-300 hover:bg-zinc-800"
              >
                <Upload className="h-4 w-4 mr-2" />
                导入
              </Button>
              
              {/* Create Button */}
              <Button
                onClick={onCreateProject}
                className="bg-violet-600 hover:bg-violet-700"
              >
                <Plus className="h-4 w-4 mr-2" />
                新建项目
              </Button>
            </div>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-6 py-8">
        {/* Error Alert */}
        {error && (
          <motion.div
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            className="mb-6 p-4 bg-red-500/10 border border-red-500/20 rounded-xl flex items-center justify-between"
          >
            <div className="flex items-center gap-3">
              <AlertCircle className="h-5 w-5 text-red-400" />
              <p className="text-red-400">{error}</p>
            </div>
            <button onClick={clearError} className="text-red-400 hover:text-red-300">
              ✕
            </button>
          </motion.div>
        )}

        {/* Loading State */}
        {isLoading && projects.length === 0 && (
          <div className="flex flex-col items-center justify-center py-20">
            <Loader2 className="h-8 w-8 text-violet-500 animate-spin mb-4" />
            <p className="text-zinc-500">加载中...</p>
          </div>
        )}

        {/* Empty State */}
        {!isLoading && filteredProjects.length === 0 && (
          <div className="flex flex-col items-center justify-center py-20">
            <FolderOpen className="h-16 w-16 text-zinc-700 mb-4" />
            <h2 className="text-xl font-medium text-zinc-400 mb-2">
              {searchQuery ? '没有找到匹配的项目' : '还没有项目'}
            </h2>
            <p className="text-zinc-600 mb-6">
              {searchQuery ? '尝试其他搜索词' : '创建你的第一个小说项目开始创作'}
            </p>
            {!searchQuery && (
              <Button onClick={onCreateProject} className="bg-violet-600 hover:bg-violet-700">
                <Plus className="h-4 w-4 mr-2" />
                创建项目
              </Button>
            )}
          </div>
        )}

        {/* Project Grid */}
        {filteredProjects.length > 0 && (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            <AnimatePresence>
              {filteredProjects.map((project, index) => (
                <motion.div
                  key={project.id}
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, scale: 0.95 }}
                  transition={{ delay: index * 0.05 }}
                  onClick={() => onSelectProject(project)}
                  className="group relative bg-zinc-900/50 border border-zinc-800 rounded-xl overflow-hidden cursor-pointer hover:border-violet-500/50 hover:bg-zinc-900 transition-all"
                >
                  {/* Cover Image */}
                  <div className="aspect-[16/9] bg-gradient-to-br from-violet-900/30 to-indigo-900/30 relative overflow-hidden">
                    {project.coverUrl ? (
                      <img 
                        src={project.coverUrl} 
                        alt={project.title}
                        className="w-full h-full object-cover"
                      />
                    ) : (
                      <div className="absolute inset-0 flex items-center justify-center">
                        <FileText className="h-12 w-12 text-zinc-700" />
                      </div>
                    )}
                    
                    {/* Phase Badge */}
                    <div className={`absolute top-3 left-3 px-2 py-1 rounded-md text-xs font-medium ${phaseConfig[project.creationPhase].color}`}>
                      {phaseConfig[project.creationPhase].label}
                    </div>
                    
                    {/* Menu Button */}
                    <div className="absolute top-3 right-3">
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          setShowMenu(showMenu === project.id ? null : project.id);
                        }}
                        className="p-1.5 rounded-lg bg-black/50 text-zinc-400 hover:text-white opacity-0 group-hover:opacity-100 transition-opacity"
                      >
                        <MoreVertical className="h-4 w-4" />
                      </button>
                      
                      {/* Dropdown Menu */}
                      {showMenu === project.id && (
                        <div className="absolute right-0 top-full mt-1 w-36 bg-zinc-900 border border-zinc-700 rounded-lg shadow-xl overflow-hidden z-10">
                          <button
                            onClick={(e) => handleExport(project.id, e)}
                            className="w-full px-3 py-2 text-left text-sm text-zinc-300 hover:bg-zinc-800 flex items-center gap-2"
                          >
                            <Download className="h-4 w-4" />
                            导出
                          </button>
                          <button
                            onClick={(e) => handleDelete(project.id, e)}
                            className="w-full px-3 py-2 text-left text-sm text-red-400 hover:bg-zinc-800 flex items-center gap-2"
                          >
                            <Trash2 className="h-4 w-4" />
                            删除
                          </button>
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Content */}
                  <div className="p-4">
                    <h3 className="font-medium text-lg text-zinc-100 mb-1 truncate">
                      {project.title}
                    </h3>
                    {project.description && (
                      <p className="text-sm text-zinc-500 line-clamp-2 mb-3">
                        {project.description}
                      </p>
                    )}
                    
                    {/* Stats */}
                    <div className="flex items-center gap-4 text-xs text-zinc-500">
                      <div className="flex items-center gap-1">
                        <FileText className="h-3.5 w-3.5" />
                        <span>{formatWordCount(project.wordCount)} 字</span>
                      </div>
                      <div className="flex items-center gap-1">
                        <Clock className="h-3.5 w-3.5" />
                        <span>{formatDate(project.updatedAt)}</span>
                      </div>
                    </div>
                  </div>
                </motion.div>
              ))}
            </AnimatePresence>
          </div>
        )}

        {/* Pagination */}
        {pagination.totalPages > 1 && (
          <div className="flex items-center justify-center gap-2 mt-8">
            <Button
              variant="outline"
              size="sm"
              onClick={() => handlePageChange(pagination.page - 1)}
              disabled={pagination.page === 0}
              className="border-zinc-700"
            >
              <ChevronLeft className="h-4 w-4" />
            </Button>
            
            <span className="text-sm text-zinc-500 px-4">
              第 {pagination.page + 1} / {pagination.totalPages} 页
            </span>
            
            <Button
              variant="outline"
              size="sm"
              onClick={() => handlePageChange(pagination.page + 1)}
              disabled={pagination.page >= pagination.totalPages - 1}
              className="border-zinc-700"
            >
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        )}
      </main>
      
      {/* Click outside to close menu */}
      {showMenu && (
        <div 
          className="fixed inset-0 z-0" 
          onClick={() => setShowMenu(null)}
        />
      )}
    </div>
  );
}
