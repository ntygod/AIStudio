/**
 * 项目导入导出面板
 * Requirements: 12.1, 12.2, 12.3, 12.4, 12.5
 */

import { useState, useRef } from 'react';
import { motion } from 'motion/react';
import { 
  Download, 
  Upload, 
  FileJson, 
  AlertCircle,
  Check,
  RefreshCw,
  FolderOpen,
  BookOpen,
  FileText,
} from 'lucide-react';
import { Button } from '../ui/button';
import { 
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '../ui/dialog';
import { 
  importExportService, 
  type ImportPreview 
} from '../../../services';
import { useProjectStore } from '../../../stores';

export function ImportExportPanel() {
  const { currentProject, fetchProjects } = useProjectStore();
  const fileInputRef = useRef<HTMLInputElement>(null);
  
  const [exporting, setExporting] = useState(false);
  const [exportSuccess, setExportSuccess] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  const [importFile, setImportFile] = useState<File | null>(null);
  const [importPreview, setImportPreview] = useState<ImportPreview | null>(null);
  const [importing, setImporting] = useState(false);
  const [importDialogOpen, setImportDialogOpen] = useState(false);
  const [importSuccess, setImportSuccess] = useState(false);

  // 导出项目
  const handleExport = async () => {
    if (!currentProject) return;
    
    setExporting(true);
    setError(null);
    setExportSuccess(false);
    
    try {
      await importExportService.downloadExport(
        currentProject.id,
        `${currentProject.title}-export.json`
      );
      setExportSuccess(true);
      setTimeout(() => setExportSuccess(false), 3000);
    } catch (err) {
      setError(err instanceof Error ? err.message : '导出失败');
    } finally {
      setExporting(false);
    }
  };

  // 选择导入文件
  const handleFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    
    // 验证文件
    const validation = importExportService.validateImportFile(file);
    if (!validation.valid) {
      setError(validation.error || '无效文件');
      return;
    }
    
    setError(null);
    setImportFile(file);
    
    try {
      const preview = await importExportService.parseImportFile(file);
      setImportPreview(preview);
      setImportDialogOpen(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : '解析文件失败');
      setImportFile(null);
    }
    
    // 重置 input
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  // 确认导入
  const handleConfirmImport = async () => {
    if (!importFile) return;
    
    setImporting(true);
    setError(null);
    
    try {
      await importExportService.importProject(importFile);
      setImportSuccess(true);
      setImportDialogOpen(false);
      
      // 刷新项目列表
      await fetchProjects();
      
      setTimeout(() => setImportSuccess(false), 3000);
    } catch (err) {
      setError(err instanceof Error ? err.message : '导入失败');
    } finally {
      setImporting(false);
      setImportFile(null);
      setImportPreview(null);
    }
  };

  // 取消导入
  const handleCancelImport = () => {
    setImportDialogOpen(false);
    setImportFile(null);
    setImportPreview(null);
  };

  return (
    <motion.div
      initial={{ opacity: 0, x: 20 }}
      animate={{ opacity: 1, x: 0 }}
      className="space-y-8"
    >
      {/* 标题 */}
      <div>
        <h2 className="text-lg font-medium mb-1">导入导出</h2>
        <p className="text-sm text-muted-foreground">
          备份你的项目数据或从其他设备导入
        </p>
      </div>

      {/* 错误提示 */}
      {error && (
        <div className="p-4 rounded-xl bg-destructive/10 text-destructive flex items-center gap-2">
          <AlertCircle className="h-4 w-4" />
          <span className="text-sm">{error}</span>
        </div>
      )}

      {/* 成功提示 */}
      {(exportSuccess || importSuccess) && (
        <div className="p-4 rounded-xl bg-green-500/10 text-green-500 flex items-center gap-2">
          <Check className="h-4 w-4" />
          <span className="text-sm">
            {exportSuccess ? '导出成功！' : '导入成功！'}
          </span>
        </div>
      )}

      {/* 导出卡片 */}
      <div className="p-6 rounded-2xl border border-border bg-card">
        <div className="flex items-center gap-3 mb-4">
          <div className="p-2 rounded-xl bg-primary/10">
            <Download className="h-5 w-5 text-primary" />
          </div>
          <div>
            <h3 className="font-medium">导出项目</h3>
            <p className="text-sm text-muted-foreground">
              将当前项目导出为 JSON 文件
            </p>
          </div>
        </div>

        {currentProject ? (
          <div className="space-y-4">
            <div className="p-4 rounded-xl bg-accent/30">
              <div className="flex items-center gap-2 mb-2">
                <BookOpen className="h-4 w-4 text-muted-foreground" />
                <span className="font-medium">{currentProject.title}</span>
              </div>
              <div className="text-sm text-muted-foreground">
                {currentProject.wordCount?.toLocaleString() || 0} 字
              </div>
            </div>
            
            <Button 
              onClick={handleExport} 
              disabled={exporting}
              className="w-full"
            >
              {exporting ? (
                <>
                  <RefreshCw className="h-4 w-4 mr-2 animate-spin" />
                  导出中...
                </>
              ) : (
                <>
                  <FileJson className="h-4 w-4 mr-2" />
                  导出为 JSON
                </>
              )}
            </Button>
          </div>
        ) : (
          <div className="text-center text-muted-foreground py-4">
            <FolderOpen className="h-8 w-8 mx-auto mb-2 opacity-50" />
            <p className="text-sm">请先选择一个项目</p>
          </div>
        )}
      </div>

      {/* 导入卡片 */}
      <div className="p-6 rounded-2xl border border-border bg-card">
        <div className="flex items-center gap-3 mb-4">
          <div className="p-2 rounded-xl bg-primary/10">
            <Upload className="h-5 w-5 text-primary" />
          </div>
          <div>
            <h3 className="font-medium">导入项目</h3>
            <p className="text-sm text-muted-foreground">
              从 JSON 文件导入项目数据
            </p>
          </div>
        </div>

        <input
          ref={fileInputRef}
          type="file"
          accept=".json"
          onChange={handleFileSelect}
          className="hidden"
        />

        <Button 
          variant="outline"
          onClick={() => fileInputRef.current?.click()}
          className="w-full"
        >
          <FolderOpen className="h-4 w-4 mr-2" />
          选择文件
        </Button>

        <p className="text-xs text-muted-foreground mt-3 text-center">
          支持 InkFlow 2.0 导出的 JSON 文件
        </p>
      </div>

      {/* 帮助信息 */}
      <div className="p-4 rounded-xl bg-accent/30 text-sm text-muted-foreground">
        <p className="font-medium mb-2">💡 提示</p>
        <ul className="space-y-1 list-disc list-inside">
          <li>导出文件包含项目的所有内容（分卷、章节、剧情块）</li>
          <li>导入时会创建新项目，不会覆盖现有数据</li>
          <li>建议定期导出备份重要项目</li>
          <li>导出文件最大支持 50MB</li>
        </ul>
      </div>

      {/* 导入预览对话框 */}
      <Dialog open={importDialogOpen} onOpenChange={setImportDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>确认导入</DialogTitle>
            <DialogDescription>
              请确认以下项目信息
            </DialogDescription>
          </DialogHeader>

          {importPreview && (
            <div className="space-y-4 py-4">
              <div className="p-4 rounded-xl bg-accent/30">
                <h4 className="font-medium mb-2">{importPreview.title}</h4>
                {importPreview.description && (
                  <p className="text-sm text-muted-foreground mb-3">
                    {importPreview.description}
                  </p>
                )}
                <div className="grid grid-cols-3 gap-4 text-center">
                  <div>
                    <div className="text-lg font-bold text-primary">
                      {importPreview.volumeCount}
                    </div>
                    <div className="text-xs text-muted-foreground">分卷</div>
                  </div>
                  <div>
                    <div className="text-lg font-bold text-primary">
                      {importPreview.chapterCount}
                    </div>
                    <div className="text-xs text-muted-foreground">章节</div>
                  </div>
                  <div>
                    <div className="text-lg font-bold text-primary">
                      {importPreview.wordCount.toLocaleString()}
                    </div>
                    <div className="text-xs text-muted-foreground">字数</div>
                  </div>
                </div>
              </div>

              <div className="text-sm text-muted-foreground">
                <div className="flex items-center gap-2">
                  <FileText className="h-4 w-4" />
                  <span>导出版本: {importPreview.version}</span>
                </div>
                <div className="flex items-center gap-2 mt-1">
                  <FileText className="h-4 w-4" />
                  <span>
                    导出时间: {new Date(importPreview.exportedAt).toLocaleString()}
                  </span>
                </div>
              </div>
            </div>
          )}

          <DialogFooter>
            <Button 
              variant="ghost" 
              onClick={handleCancelImport}
              disabled={importing}
            >
              取消
            </Button>
            <Button 
              onClick={handleConfirmImport}
              disabled={importing}
            >
              {importing ? (
                <>
                  <RefreshCw className="h-4 w-4 mr-2 animate-spin" />
                  导入中...
                </>
              ) : (
                '确认导入'
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </motion.div>
  );
}
