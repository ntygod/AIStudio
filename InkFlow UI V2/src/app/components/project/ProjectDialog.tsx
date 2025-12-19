/**
 * 项目创建/编辑对话框
 * 支持创建新项目和编辑现有项目
 */

import { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Upload, Image, Loader2 } from 'lucide-react';
import { Button } from '../ui/button';
import { useProjectStore } from '@/stores/project-store';
import type { Project, CreateProjectRequest, UpdateProjectRequest } from '@/types';

interface ProjectDialogProps {
  isOpen: boolean;
  onClose: () => void;
  project?: Project | null; // If provided, edit mode; otherwise create mode
  onSuccess?: (project: Project) => void;
}

interface FormData {
  title: string;
  description: string;
  coverUrl: string;
}

interface FormErrors {
  title?: string;
  description?: string;
  coverUrl?: string;
}

export function ProjectDialog({ isOpen, onClose, project, onSuccess }: ProjectDialogProps) {
  const isEditMode = !!project;
  
  const [formData, setFormData] = useState<FormData>({
    title: '',
    description: '',
    coverUrl: '',
  });
  const [errors, setErrors] = useState<FormErrors>({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  
  const { createProject, updateProject, error: storeError, clearError } = useProjectStore();

  // Initialize form data when project changes
  useEffect(() => {
    if (project) {
      setFormData({
        title: project.title,
        description: project.description || '',
        coverUrl: project.coverUrl || '',
      });
    } else {
      setFormData({
        title: '',
        description: '',
        coverUrl: '',
      });
    }
    setErrors({});
    clearError();
  }, [project, isOpen, clearError]);

  // Update form field
  const updateField = useCallback((field: keyof FormData, value: string) => {
    setFormData(prev => ({ ...prev, [field]: value }));
    if (errors[field]) {
      setErrors(prev => ({ ...prev, [field]: undefined }));
    }
  }, [errors]);

  // Validate form
  const validateForm = useCallback((): boolean => {
    const newErrors: FormErrors = {};

    if (!formData.title.trim()) {
      newErrors.title = '请输入项目标题';
    } else if (formData.title.length > 100) {
      newErrors.title = '标题不能超过100个字符';
    }

    if (formData.description.length > 500) {
      newErrors.description = '描述不能超过500个字符';
    }

    if (formData.coverUrl && !isValidUrl(formData.coverUrl)) {
      newErrors.coverUrl = '请输入有效的图片URL';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  }, [formData]);

  // Check if URL is valid
  const isValidUrl = (url: string): boolean => {
    try {
      new URL(url);
      return true;
    } catch {
      return false;
    }
  };

  // Handle form submission
  const handleSubmit = useCallback(async () => {
    if (!validateForm()) return;

    setIsSubmitting(true);

    try {
      if (isEditMode && project) {
        const updateData: UpdateProjectRequest = {
          title: formData.title,
          description: formData.description || undefined,
          coverUrl: formData.coverUrl || undefined,
        };
        await updateProject(project.id, updateData);
        onSuccess?.({ ...project, ...updateData });
      } else {
        const createData: CreateProjectRequest = {
          title: formData.title,
          description: formData.description || undefined,
          coverUrl: formData.coverUrl || undefined,
        };
        const newProject = await createProject(createData);
        onSuccess?.(newProject);
      }
      onClose();
    } catch {
      // Error handled by store
    } finally {
      setIsSubmitting(false);
    }
  }, [validateForm, isEditMode, project, formData, updateProject, createProject, onSuccess, onClose]);

  // Handle keyboard events
  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === 'Escape') {
      onClose();
    } else if (e.key === 'Enter' && e.ctrlKey) {
      handleSubmit();
    }
  }, [onClose, handleSubmit]);

  if (!isOpen) return null;

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex items-center justify-center">
        {/* Backdrop */}
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={onClose}
          className="absolute inset-0 bg-black/60 backdrop-blur-sm"
        />

        {/* Dialog */}
        <motion.div
          initial={{ opacity: 0, scale: 0.95, y: 20 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.95, y: 20 }}
          onKeyDown={handleKeyDown}
          className="relative w-full max-w-lg bg-zinc-900 border border-zinc-800 rounded-2xl shadow-2xl overflow-hidden"
        >
          {/* Header */}
          <div className="flex items-center justify-between px-6 py-4 border-b border-zinc-800">
            <h2 className="text-xl font-medium text-white">
              {isEditMode ? '编辑项目' : '创建新项目'}
            </h2>
            <button
              onClick={onClose}
              className="p-2 rounded-lg text-zinc-500 hover:text-white hover:bg-zinc-800 transition-colors"
            >
              <X className="h-5 w-5" />
            </button>
          </div>

          {/* Content */}
          <div className="px-6 py-6 space-y-6">
            {/* Store Error */}
            {storeError && (
              <div className="p-3 bg-red-500/10 border border-red-500/20 rounded-lg text-red-400 text-sm">
                {storeError}
              </div>
            )}

            {/* Title Input */}
            <div className="space-y-2">
              <label className="text-sm font-medium text-zinc-400">
                项目标题 <span className="text-red-400">*</span>
              </label>
              <input
                type="text"
                value={formData.title}
                onChange={(e) => updateField('title', e.target.value)}
                placeholder="输入你的小说标题"
                className={`w-full bg-zinc-800 border rounded-lg px-4 py-3 text-white placeholder:text-zinc-600 focus:outline-none transition-colors ${
                  errors.title ? 'border-red-500' : 'border-zinc-700 focus:border-violet-500'
                }`}
                autoFocus
              />
              {errors.title && (
                <p className="text-red-400 text-xs">{errors.title}</p>
              )}
            </div>

            {/* Description Input */}
            <div className="space-y-2">
              <label className="text-sm font-medium text-zinc-400">
                项目描述
              </label>
              <textarea
                value={formData.description}
                onChange={(e) => updateField('description', e.target.value)}
                placeholder="简要描述你的小说内容..."
                rows={3}
                className={`w-full bg-zinc-800 border rounded-lg px-4 py-3 text-white placeholder:text-zinc-600 focus:outline-none resize-none transition-colors ${
                  errors.description ? 'border-red-500' : 'border-zinc-700 focus:border-violet-500'
                }`}
              />
              <div className="flex justify-between text-xs">
                {errors.description ? (
                  <p className="text-red-400">{errors.description}</p>
                ) : (
                  <span />
                )}
                <span className={`${formData.description.length > 500 ? 'text-red-400' : 'text-zinc-600'}`}>
                  {formData.description.length}/500
                </span>
              </div>
            </div>

            {/* Cover URL Input */}
            <div className="space-y-2">
              <label className="text-sm font-medium text-zinc-400">
                封面图片
              </label>
              <div className="flex gap-3">
                <div className="flex-1">
                  <input
                    type="text"
                    value={formData.coverUrl}
                    onChange={(e) => updateField('coverUrl', e.target.value)}
                    placeholder="输入图片URL或上传图片"
                    className={`w-full bg-zinc-800 border rounded-lg px-4 py-3 text-white placeholder:text-zinc-600 focus:outline-none transition-colors ${
                      errors.coverUrl ? 'border-red-500' : 'border-zinc-700 focus:border-violet-500'
                    }`}
                  />
                </div>
                <Button
                  type="button"
                  variant="outline"
                  className="border-zinc-700 text-zinc-400 hover:text-white"
                  disabled
                  title="图片上传功能即将推出"
                >
                  <Upload className="h-4 w-4" />
                </Button>
              </div>
              {errors.coverUrl && (
                <p className="text-red-400 text-xs">{errors.coverUrl}</p>
              )}
              
              {/* Cover Preview */}
              {formData.coverUrl && isValidUrl(formData.coverUrl) && (
                <div className="mt-3 relative aspect-video bg-zinc-800 rounded-lg overflow-hidden">
                  <img
                    src={formData.coverUrl}
                    alt="封面预览"
                    className="w-full h-full object-cover"
                    onError={(e) => {
                      (e.target as HTMLImageElement).style.display = 'none';
                    }}
                  />
                  <div className="absolute inset-0 flex items-center justify-center bg-zinc-800">
                    <Image className="h-8 w-8 text-zinc-600" />
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* Footer */}
          <div className="flex items-center justify-end gap-3 px-6 py-4 border-t border-zinc-800 bg-zinc-900/50">
            <Button
              variant="outline"
              onClick={onClose}
              disabled={isSubmitting}
              className="border-zinc-700 text-zinc-400 hover:text-white"
            >
              取消
            </Button>
            <Button
              onClick={handleSubmit}
              disabled={isSubmitting}
              className="bg-violet-600 hover:bg-violet-700"
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                  {isEditMode ? '保存中...' : '创建中...'}
                </>
              ) : (
                isEditMode ? '保存更改' : '创建项目'
              )}
            </Button>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
}
