/**
 * AI Provider 配置面板
 * Requirements: 11.1, 11.2, 11.3, 11.4, 11.5
 */

import { useState, useEffect } from 'react';
import { motion } from 'motion/react';
import { 
  Check, 
  X, 
  Eye, 
  EyeOff, 
  Star,
  Trash2,
  RefreshCw,
  AlertCircle,
  ExternalLink,
} from 'lucide-react';
import { Button } from '../ui/button';
import { Input } from '../ui/input';
import { 
  providerService, 
  PROVIDER_INFO,
  type ProviderConfig, 
  type ProviderType 
} from '../../../services';

export function AIProviderPanel() {
  const [configs, setConfigs] = useState<ProviderConfig[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [editingProvider, setEditingProvider] = useState<ProviderType | null>(null);
  const [apiKeyInput, setApiKeyInput] = useState('');
  const [baseUrlInput, setBaseUrlInput] = useState('');
  const [showApiKey, setShowApiKey] = useState(false);
  const [saving, setSaving] = useState(false);

  // 加载配置
  const loadConfigs = async () => {
    setLoading(true);
    setError(null);
    
    try {
      const data = await providerService.getAllConfigs();
      setConfigs(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadConfigs();
  }, []);

  // 开始编辑
  const handleStartEdit = (providerType: ProviderType) => {
    const config = configs.find(c => c.providerType === providerType);
    setEditingProvider(providerType);
    setApiKeyInput('');
    setBaseUrlInput(config?.baseUrl || PROVIDER_INFO[providerType].defaultBaseUrl);
    setShowApiKey(false);
  };

  // 取消编辑
  const handleCancelEdit = () => {
    setEditingProvider(null);
    setApiKeyInput('');
    setBaseUrlInput('');
    setShowApiKey(false);
  };

  // 保存配置
  const handleSaveConfig = async () => {
    if (!editingProvider) return;
    
    setSaving(true);
    setError(null);
    
    try {
      await providerService.saveConfig({
        providerType: editingProvider,
        apiKey: apiKeyInput || undefined,
        baseUrl: baseUrlInput || undefined,
      });
      
      await loadConfigs();
      handleCancelEdit();
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存失败');
    } finally {
      setSaving(false);
    }
  };

  // 设为默认
  const handleSetDefault = async (providerType: ProviderType) => {
    try {
      await providerService.saveConfig({
        providerType,
        isDefault: true,
      });
      await loadConfigs();
    } catch (err) {
      setError(err instanceof Error ? err.message : '设置失败');
    }
  };

  // 删除配置
  const handleDeleteConfig = async (providerType: ProviderType) => {
    try {
      await providerService.deleteConfig(providerType);
      await loadConfigs();
    } catch (err) {
      setError(err instanceof Error ? err.message : '删除失败');
    }
  };

  return (
    <motion.div
      initial={{ opacity: 0, x: 20 }}
      animate={{ opacity: 1, x: 0 }}
      className="space-y-8"
    >
      {/* 标题 */}
      <div>
        <h2 className="text-lg font-medium mb-1">AI 服务商配置</h2>
        <p className="text-sm text-muted-foreground">
          配置你的 AI 服务商 API Key，支持多个服务商切换使用
        </p>
      </div>

      {/* 错误提示 */}
      {error && (
        <div className="p-4 rounded-xl bg-destructive/10 text-destructive flex items-center gap-2">
          <AlertCircle className="h-4 w-4" />
          <span className="text-sm">{error}</span>
          <Button 
            variant="ghost" 
            size="sm" 
            className="ml-auto"
            onClick={() => setError(null)}
          >
            <X className="h-4 w-4" />
          </Button>
        </div>
      )}

      {/* 服务商列表 */}
      <div className="space-y-4">
        {loading ? (
          <div className="space-y-4">
            {[1, 2, 3].map(i => (
              <div key={i} className="p-6 rounded-2xl border border-border bg-card animate-pulse">
                <div className="h-6 bg-accent/50 rounded w-1/3 mb-2" />
                <div className="h-4 bg-accent/50 rounded w-2/3" />
              </div>
            ))}
          </div>
        ) : (
          configs.map((config) => {
            const info = PROVIDER_INFO[config.providerType];
            const isEditing = editingProvider === config.providerType;
            
            return (
              <div
                key={config.providerType}
                className={`p-6 rounded-2xl border bg-card transition-all ${
                  config.isDefault 
                    ? 'border-primary/50 bg-primary/5' 
                    : 'border-border'
                }`}
              >
                {/* 头部 */}
                <div className="flex items-start justify-between mb-4">
                  <div className="flex items-center gap-3">
                    <span className="text-2xl">{info.icon}</span>
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-medium">{info.name}</span>
                        {config.isDefault && (
                          <span className="text-xs px-2 py-0.5 rounded-full bg-primary/10 text-primary flex items-center gap-1">
                            <Star className="h-3 w-3" />
                            默认
                          </span>
                        )}
                        {config.isConfigured && (
                          <span className="text-xs px-2 py-0.5 rounded-full bg-green-500/10 text-green-500 flex items-center gap-1">
                            <Check className="h-3 w-3" />
                            已配置
                          </span>
                        )}
                      </div>
                      <p className="text-sm text-muted-foreground">
                        {info.description}
                      </p>
                    </div>
                  </div>
                  
                  {!isEditing && (
                    <div className="flex items-center gap-2">
                      {config.isConfigured && !config.isDefault && (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleSetDefault(config.providerType)}
                        >
                          设为默认
                        </Button>
                      )}
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handleStartEdit(config.providerType)}
                      >
                        {config.isConfigured ? '修改' : '配置'}
                      </Button>
                      {config.isConfigured && (
                        <Button
                          variant="ghost"
                          size="icon"
                          className="h-8 w-8 text-destructive"
                          onClick={() => handleDeleteConfig(config.providerType)}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      )}
                    </div>
                  )}
                </div>

                {/* 编辑表单 */}
                {isEditing && (
                  <motion.div
                    initial={{ opacity: 0, height: 0 }}
                    animate={{ opacity: 1, height: 'auto' }}
                    className="space-y-4 pt-4 border-t border-border"
                  >
                    {/* API Key */}
                    <div className="space-y-2">
                      <label className="text-sm font-medium">API Key</label>
                      <div className="relative">
                        <Input
                          type={showApiKey ? 'text' : 'password'}
                          value={apiKeyInput}
                          onChange={(e) => setApiKeyInput(e.target.value)}
                          placeholder={config.keyHint ? `当前: ****${config.keyHint}` : '输入 API Key'}
                          className="pr-10"
                        />
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon"
                          className="absolute right-1 top-1/2 -translate-y-1/2 h-7 w-7"
                          onClick={() => setShowApiKey(!showApiKey)}
                        >
                          {showApiKey ? (
                            <EyeOff className="h-4 w-4" />
                          ) : (
                            <Eye className="h-4 w-4" />
                          )}
                        </Button>
                      </div>
                      {config.keyHint && (
                        <p className="text-xs text-muted-foreground">
                          留空则保持当前 Key 不变
                        </p>
                      )}
                    </div>

                    {/* Base URL */}
                    <div className="space-y-2">
                      <label className="text-sm font-medium">Base URL (可选)</label>
                      <Input
                        type="url"
                        value={baseUrlInput}
                        onChange={(e) => setBaseUrlInput(e.target.value)}
                        placeholder={info.defaultBaseUrl}
                      />
                      <p className="text-xs text-muted-foreground">
                        默认: {info.defaultBaseUrl}
                      </p>
                    </div>

                    {/* 操作按钮 */}
                    <div className="flex justify-end gap-2 pt-2">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={handleCancelEdit}
                        disabled={saving}
                      >
                        取消
                      </Button>
                      <Button
                        size="sm"
                        onClick={handleSaveConfig}
                        disabled={saving || (!apiKeyInput && !config.isConfigured)}
                      >
                        {saving ? (
                          <>
                            <RefreshCw className="h-4 w-4 mr-2 animate-spin" />
                            保存中...
                          </>
                        ) : (
                          '保存配置'
                        )}
                      </Button>
                    </div>
                  </motion.div>
                )}

                {/* 已配置信息 */}
                {!isEditing && config.isConfigured && (
                  <div className="text-sm text-muted-foreground">
                    {config.baseUrl && (
                      <div className="flex items-center gap-1">
                        <span>Base URL:</span>
                        <code className="text-xs bg-accent/50 px-1 rounded">
                          {config.baseUrl}
                        </code>
                      </div>
                    )}
                    {config.defaultModel && (
                      <div className="flex items-center gap-1 mt-1">
                        <span>默认模型:</span>
                        <code className="text-xs bg-accent/50 px-1 rounded">
                          {config.defaultModel}
                        </code>
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })
        )}
      </div>

      {/* 帮助信息 */}
      <div className="p-4 rounded-xl bg-accent/30 text-sm text-muted-foreground">
        <p className="font-medium mb-2">💡 提示</p>
        <ul className="space-y-1 list-disc list-inside">
          <li>API Key 会加密存储，安全可靠</li>
          <li>可以配置多个服务商，随时切换</li>
          <li>设置默认服务商后，AI 功能将优先使用该服务商</li>
          <li>
            <a 
              href="https://platform.openai.com/api-keys" 
              target="_blank" 
              rel="noopener noreferrer"
              className="text-primary hover:underline inline-flex items-center gap-1"
            >
              获取 OpenAI API Key <ExternalLink className="h-3 w-3" />
            </a>
          </li>
        </ul>
      </div>
    </motion.div>
  );
}
