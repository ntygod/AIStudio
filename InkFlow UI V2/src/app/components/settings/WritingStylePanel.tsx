/**
 * 写作风格管理面板
 * Requirements: 9.1, 9.2, 9.3, 9.4, 9.5
 */

import { useState, useEffect } from 'react';
import { motion } from 'motion/react';
import { 
  Palette, 
  Upload, 
  Trash2, 
  FileText, 
  BarChart3,
  RefreshCw,
  AlertCircle,
} from 'lucide-react';
import { Button } from '../ui/button';
import { ScrollArea } from '../ui/scroll-area';
import { Progress } from '../ui/progress';
import { 
  styleService, 
  type StyleStats, 
  type StyleSample 
} from '../../../services';
import { useProjectStore } from '../../../stores';

interface WritingStylePanelProps {
  projectId?: string;
}

export function WritingStylePanel({ projectId }: WritingStylePanelProps) {
  const { currentProject } = useProjectStore();
  const activeProjectId = projectId || currentProject?.id;
  
  const [stats, setStats] = useState<StyleStats | null>(null);
  const [samples, setSamples] = useState<StyleSample[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [uploadText, setUploadText] = useState('');
  const [analyzing, setAnalyzing] = useState(false);

  // 加载风格数据
  const loadStyleData = async () => {
    if (!activeProjectId) return;
    
    setLoading(true);
    setError(null);
    
    try {
      const [statsData, samplesData] = await Promise.all([
        styleService.getStyleStats(activeProjectId),
        styleService.getStyleSamples(activeProjectId),
      ]);
      setStats(statsData);
      setSamples(samplesData);
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadStyleData();
  }, [activeProjectId]);

  // 删除样本
  const handleDeleteSample = async (sampleId: string) => {
    if (!activeProjectId) return;
    
    try {
      await styleService.deleteStyleSample(activeProjectId, sampleId);
      setSamples(prev => prev.filter(s => s.id !== sampleId));
      // 重新加载统计
      const newStats = await styleService.getStyleStats(activeProjectId);
      setStats(newStats);
    } catch (err) {
      setError(err instanceof Error ? err.message : '删除失败');
    }
  };

  // 分析样本文本
  const handleAnalyzeSample = async () => {
    if (!activeProjectId || !uploadText.trim()) return;
    
    setAnalyzing(true);
    setError(null);
    
    try {
      // 这里模拟分析过程，实际需要 AI 生成原始文本进行对比
      // 目前只是展示 UI，实际保存需要 originalAI 和 userFinal 对比
      await new Promise(resolve => setTimeout(resolve, 1500));
      setUploadText('');
      await loadStyleData();
    } catch (err) {
      setError(err instanceof Error ? err.message : '分析失败');
    } finally {
      setAnalyzing(false);
    }
  };

  if (!activeProjectId) {
    return (
      <div className="flex flex-col items-center justify-center p-12 text-muted-foreground">
        <Palette className="h-12 w-12 mb-4 opacity-50" />
        <p>请先选择一个项目</p>
      </div>
    );
  }

  return (
    <motion.div
      initial={{ opacity: 0, x: 20 }}
      animate={{ opacity: 1, x: 0 }}
      className="space-y-8"
    >
      {/* 标题 */}
      <div>
        <h2 className="text-lg font-medium mb-1">写作风格管理</h2>
        <p className="text-sm text-muted-foreground">
          系统会学习你对 AI 生成内容的修改，逐渐适应你的写作风格
        </p>
      </div>

      {/* 错误提示 */}
      {error && (
        <div className="p-4 rounded-xl bg-destructive/10 text-destructive flex items-center gap-2">
          <AlertCircle className="h-4 w-4" />
          <span className="text-sm">{error}</span>
        </div>
      )}

      {/* 风格统计卡片 */}
      <div className="p-6 rounded-2xl border border-border bg-card">
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2">
            <BarChart3 className="h-5 w-5 text-primary" />
            <span className="font-medium">风格学习统计</span>
          </div>
          <Button 
            variant="ghost" 
            size="sm" 
            onClick={loadStyleData}
            disabled={loading}
          >
            <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          </Button>
        </div>

        {loading && !stats ? (
          <div className="space-y-3">
            <div className="h-4 bg-accent/50 rounded animate-pulse" />
            <div className="h-4 bg-accent/50 rounded animate-pulse w-2/3" />
          </div>
        ) : stats ? (
          <div className="grid grid-cols-3 gap-4">
            <div className="p-4 rounded-xl bg-accent/30">
              <div className="text-2xl font-bold text-primary">
                {stats.sampleCount}
              </div>
              <div className="text-xs text-muted-foreground">学习样本数</div>
            </div>
            <div className="p-4 rounded-xl bg-accent/30">
              <div className="text-2xl font-bold text-primary">
                {(stats.averageEditRatio * 100).toFixed(1)}%
              </div>
              <div className="text-xs text-muted-foreground">平均编辑比例</div>
            </div>
            <div className="p-4 rounded-xl bg-accent/30">
              <div className="text-2xl font-bold text-primary">
                {stats.totalWordCount.toLocaleString()}
              </div>
              <div className="text-xs text-muted-foreground">总字数</div>
            </div>
          </div>
        ) : (
          <div className="text-center text-muted-foreground py-4">
            暂无统计数据
          </div>
        )}

        {/* 学习进度 */}
        {stats && stats.sampleCount > 0 && (
          <div className="mt-4">
            <div className="flex justify-between text-xs text-muted-foreground mb-1">
              <span>风格学习进度</span>
              <span>{Math.min(stats.sampleCount * 10, 100)}%</span>
            </div>
            <Progress value={Math.min(stats.sampleCount * 10, 100)} className="h-2" />
            <p className="text-xs text-muted-foreground mt-2">
              {stats.sampleCount < 10 
                ? `还需要 ${10 - stats.sampleCount} 个样本以达到最佳效果`
                : '已收集足够样本，AI 将更好地模仿你的风格'}
            </p>
          </div>
        )}
      </div>

      {/* 上传样本文本 */}
      <div className="p-6 rounded-2xl border border-border bg-card">
        <div className="flex items-center gap-2 mb-4">
          <Upload className="h-5 w-5 text-primary" />
          <span className="font-medium">上传样本文本</span>
        </div>
        <p className="text-sm text-muted-foreground mb-4">
          粘贴你的写作样本，系统将分析你的写作风格特征
        </p>
        <textarea
          value={uploadText}
          onChange={(e) => setUploadText(e.target.value)}
          placeholder="粘贴你的写作样本（至少 100 字）..."
          className="flex min-h-[120px] w-full rounded-xl border border-input bg-transparent px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 resize-none"
        />
        <div className="flex justify-between items-center mt-3">
          <span className="text-xs text-muted-foreground">
            {uploadText.length} 字
          </span>
          <Button 
            onClick={handleAnalyzeSample}
            disabled={uploadText.length < 100 || analyzing}
            size="sm"
          >
            {analyzing ? (
              <>
                <RefreshCw className="h-4 w-4 mr-2 animate-spin" />
                分析中...
              </>
            ) : (
              '分析风格'
            )}
          </Button>
        </div>
      </div>

      {/* 风格样本列表 */}
      <div className="p-6 rounded-2xl border border-border bg-card">
        <div className="flex items-center gap-2 mb-4">
          <FileText className="h-5 w-5 text-primary" />
          <span className="font-medium">学习样本</span>
          <span className="text-xs text-muted-foreground">
            ({samples.length})
          </span>
        </div>

        {samples.length === 0 ? (
          <div className="text-center text-muted-foreground py-8">
            <FileText className="h-8 w-8 mx-auto mb-2 opacity-50" />
            <p className="text-sm">暂无学习样本</p>
            <p className="text-xs mt-1">
              当你修改 AI 生成的内容时，系统会自动学习你的风格
            </p>
          </div>
        ) : (
          <ScrollArea className="h-[300px]">
            <div className="space-y-3">
              {samples.map((sample) => (
                <div
                  key={sample.id}
                  className="p-4 rounded-xl bg-accent/30 hover:bg-accent/50 transition-colors"
                >
                  <div className="flex justify-between items-start mb-2">
                    <div className="flex items-center gap-2">
                      <span className="text-xs px-2 py-0.5 rounded-full bg-primary/10 text-primary">
                        编辑比例: {(sample.editRatio * 100).toFixed(1)}%
                      </span>
                      <span className="text-xs text-muted-foreground">
                        {sample.wordCount} 字
                      </span>
                    </div>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-6 w-6"
                      onClick={() => handleDeleteSample(sample.id)}
                    >
                      <Trash2 className="h-3 w-3" />
                    </Button>
                  </div>
                  <p className="text-sm text-muted-foreground line-clamp-2">
                    {sample.userFinal}
                  </p>
                  <div className="text-xs text-muted-foreground mt-2">
                    {new Date(sample.createdAt).toLocaleDateString()}
                  </div>
                </div>
              ))}
            </div>
          </ScrollArea>
        )}
      </div>
    </motion.div>
  );
}
