/**
 * 设置页面
 * Requirements: 9.1-9.5, 11.1-11.5, 12.1-12.5
 */

import { useState, useRef, useEffect } from 'react';
import { motion } from 'motion/react';
import { Button } from '../components/ui/button';
import { ThemeSwitcher, ThemeMode } from '../components/layout/ThemeSwitcher';
import { 
  WritingStylePanel, 
  AIProviderPanel, 
  ImportExportPanel 
} from '../components/settings';
import { 
  User, 
  Bell, 
  Shield, 
  Palette, 
  ArrowLeft, 
  Save,
  Pen,
  Bot,
  FolderSync,
  Loader2,
  Upload,
} from 'lucide-react';
import { ScrollArea } from '../components/ui/scroll-area';
import { authService, UpdateProfileRequest } from '@/services/auth-service';
import { useAuthStore } from '@/stores/auth-store';

interface SettingsPageProps {
  onBack: () => void;
  currentTheme: ThemeMode;
  onThemeChange: (theme: ThemeMode) => void;
}

export function SettingsPage({ onBack, currentTheme, onThemeChange }: SettingsPageProps) {
  const [activeTab, setActiveTab] = useState('profile');
  const { user, setUser } = useAuthStore();
  
  // 个人资料表单状态
  const [displayName, setDisplayName] = useState(user?.displayName || '');
  const [bio, setBio] = useState(user?.bio || '');
  const [avatarUrl, setAvatarUrl] = useState(user?.avatarUrl || '');
  const [isSaving, setIsSaving] = useState(false);
  const [saveMessage, setSaveMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  
  // 当用户数据变化时更新表单
  useEffect(() => {
    if (user) {
      setDisplayName(user.displayName || '');
      setBio(user.bio || '');
      setAvatarUrl(user.avatarUrl || '');
    }
  }, [user]);
  
  // 保存个人资料
  const handleSaveProfile = async () => {
    setIsSaving(true);
    setSaveMessage(null);
    
    try {
      const request: UpdateProfileRequest = {
        displayName: displayName || undefined,
        bio: bio || undefined,
        avatarUrl: avatarUrl || undefined,
      };
      
      const updatedUser = await authService.updateProfile(request);
      setUser(updatedUser);
      setSaveMessage({ type: 'success', text: '保存成功！' });
      
      // 3秒后清除消息
      setTimeout(() => setSaveMessage(null), 3000);
    } catch (error) {
      console.error('保存失败:', error);
      setSaveMessage({ type: 'error', text: '保存失败，请重试' });
    } finally {
      setIsSaving(false);
    }
  };
  
  // 处理头像上传
  const handleAvatarClick = () => {
    fileInputRef.current?.click();
  };
  
  // 处理文件选择
  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;
    
    // 验证文件类型
    if (!file.type.startsWith('image/')) {
      setSaveMessage({ type: 'error', text: '请选择图片文件' });
      return;
    }
    
    // 验证文件大小 (最大 2MB)
    if (file.size > 2 * 1024 * 1024) {
      setSaveMessage({ type: 'error', text: '图片大小不能超过 2MB' });
      return;
    }
    
    // 转换为 Base64 Data URL (简单方案，生产环境应上传到 OSS)
    const reader = new FileReader();
    reader.onload = (e) => {
      const dataUrl = e.target?.result as string;
      setAvatarUrl(dataUrl);
      setSaveMessage({ type: 'success', text: '头像已更新，请点击保存' });
    };
    reader.readAsDataURL(file);
  };

  const tabs = [
    { id: 'profile', label: '个人资料', icon: User },
    { id: 'appearance', label: '外观设置', icon: Palette },
    { id: 'style', label: '写作风格', icon: Pen },
    { id: 'providers', label: 'AI 服务商', icon: Bot },
    { id: 'import-export', label: '导入导出', icon: FolderSync },
    { id: 'account', label: '账户安全', icon: Shield },
    { id: 'notifications', label: '通知偏好', icon: Bell },
  ];

  return (
    <div className="h-screen w-screen bg-background flex flex-col overflow-hidden">
      {/* Header */}
      <div className="shrink-0 h-16 border-b border-border bg-card/50 backdrop-blur-md flex items-center px-6 justify-between">
        <div className="flex items-center gap-4">
          <Button variant="ghost" size="icon" onClick={onBack} className="rounded-full hover:bg-accent">
            <ArrowLeft className="h-5 w-5" />
          </Button>
          <h1 className="text-xl font-serif font-bold">设置</h1>
        </div>
        <div className="flex items-center gap-3">
          {saveMessage && (
            <span className={`text-sm ${saveMessage.type === 'success' ? 'text-green-600' : 'text-red-600'}`}>
              {saveMessage.text}
            </span>
          )}
          <Button 
            size="sm" 
            className="rounded-xl shadow-sm"
            onClick={handleSaveProfile}
            disabled={isSaving}
          >
            {isSaving ? (
              <Loader2 className="h-4 w-4 mr-2 animate-spin" />
            ) : (
              <Save className="h-4 w-4 mr-2" />
            )}
            {isSaving ? '保存中...' : '保存更改'}
          </Button>
        </div>
      </div>

      <div className="flex-1 flex overflow-hidden">
        {/* Sidebar */}
        <div className="w-64 border-r border-border bg-card/30 flex flex-col p-4 gap-2 shrink-0">
          {tabs.map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium transition-all ${
                activeTab === tab.id
                  ? 'bg-primary/10 text-primary'
                  : 'text-muted-foreground hover:bg-accent/50 hover:text-foreground'
              }`}
            >
              <tab.icon className="h-4 w-4" />
              {tab.label}
            </button>
          ))}
        </div>

        {/* Content */}
        <div className="flex-1 bg-background/50 relative">
          <ScrollArea className="h-full">
            <div className="max-w-3xl mx-auto p-12 space-y-8">
              {/* Profile Tab */}
              {activeTab === 'profile' && (
                <motion.div
                  initial={{ opacity: 0, x: 20 }}
                  animate={{ opacity: 1, x: 0 }}
                  className="space-y-8"
                >
                  <div>
                    <h2 className="text-lg font-medium mb-1">公开信息</h2>
                    <p className="text-sm text-muted-foreground">这些信息将显示在你的公开资料页上</p>
                  </div>

                  <div className="flex items-center gap-6">
                    <div 
                      className="h-24 w-24 rounded-full bg-accent flex items-center justify-center text-4xl shadow-inner border border-border overflow-hidden cursor-pointer hover:opacity-80 transition-opacity relative group"
                      onClick={handleAvatarClick}
                    >
                      {avatarUrl ? (
                        <img src={avatarUrl} alt="头像" className="w-full h-full object-cover" />
                      ) : (
                        '👾'
                      )}
                      <div className="absolute inset-0 bg-black/50 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                        <Upload className="h-6 w-6 text-white" />
                      </div>
                    </div>
                    <div className="space-y-2">
                      <Button variant="outline" className="rounded-xl" onClick={handleAvatarClick}>
                        更换头像
                      </Button>
                      <p className="text-xs text-muted-foreground">支持 JPG、PNG 格式，最大 2MB</p>
                    </div>
                    <input
                      ref={fileInputRef}
                      type="file"
                      accept="image/*"
                      className="hidden"
                      onChange={handleFileChange}
                    />
                  </div>

                  <div className="space-y-4">
                    <div className="grid gap-2">
                      <label className="text-sm font-medium">昵称</label>
                      <input
                        type="text"
                        value={displayName}
                        onChange={(e) => setDisplayName(e.target.value)}
                        placeholder="请输入昵称"
                        className="flex h-10 w-full rounded-xl border border-input bg-transparent px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                      />
                    </div>
                    <div className="grid gap-2">
                      <label className="text-sm font-medium">简介</label>
                      <textarea
                        value={bio}
                        onChange={(e) => setBio(e.target.value)}
                        className="flex min-h-[100px] w-full rounded-xl border border-input bg-transparent px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 resize-none"
                        placeholder="写一句话介绍你自己..."
                        maxLength={500}
                      />
                      <p className="text-xs text-muted-foreground text-right">{bio.length}/500</p>
                    </div>
                  </div>
                  
                  <div className="p-4 rounded-xl bg-accent/30 border border-border">
                    <h3 className="text-sm font-medium mb-2">账户信息</h3>
                    <div className="space-y-1 text-sm text-muted-foreground">
                      <p>用户名: {user?.username || '-'}</p>
                      <p>邮箱: {user?.email || '-'}</p>
                      <p>注册时间: {user?.createdAt ? new Date(user.createdAt).toLocaleDateString('zh-CN') : '-'}</p>
                    </div>
                  </div>
                </motion.div>
              )}

              {/* Appearance Tab */}
              {activeTab === 'appearance' && (
                <motion.div
                  initial={{ opacity: 0, x: 20 }}
                  animate={{ opacity: 1, x: 0 }}
                  className="space-y-8"
                >
                  <div>
                    <h2 className="text-lg font-medium mb-1">界面主题</h2>
                    <p className="text-sm text-muted-foreground">自定义你的创作环境外观</p>
                  </div>

                  <div className="p-6 rounded-2xl border border-border bg-card">
                    <div className="mb-4">
                      <label className="text-sm font-medium block mb-2">选择主题模式</label>
                      <ThemeSwitcher currentTheme={currentTheme} onThemeChange={onThemeChange} />
                    </div>
                    <div className="p-4 rounded-xl bg-accent/50 text-sm text-muted-foreground">
                      <p>当前主题: {currentTheme}</p>
                      <p className="mt-1">InkFlow V2 支持深色、浅色以及特色的羊皮纸和森林模式。</p>
                    </div>
                  </div>
                </motion.div>
              )}

              {/* Writing Style Tab - Requirements: 9.1-9.5 */}
              {activeTab === 'style' && <WritingStylePanel />}

              {/* AI Providers Tab - Requirements: 11.1-11.5 */}
              {activeTab === 'providers' && <AIProviderPanel />}

              {/* Import/Export Tab - Requirements: 12.1-12.5 */}
              {activeTab === 'import-export' && <ImportExportPanel />}

              {/* Account Tab */}
              {activeTab === 'account' && (
                <motion.div
                  initial={{ opacity: 0, x: 20 }}
                  animate={{ opacity: 1, x: 0 }}
                  className="flex flex-col items-center justify-center p-12 text-muted-foreground"
                >
                  <Shield className="h-12 w-12 mb-4 opacity-50" />
                  <p>账户安全设置开发中...</p>
                </motion.div>
              )}

              {/* Notifications Tab */}
              {activeTab === 'notifications' && (
                <motion.div
                  initial={{ opacity: 0, x: 20 }}
                  animate={{ opacity: 1, x: 0 }}
                  className="flex flex-col items-center justify-center p-12 text-muted-foreground"
                >
                  <Bell className="h-12 w-12 mb-4 opacity-50" />
                  <p>通知设置开发中...</p>
                </motion.div>
              )}
            </div>
          </ScrollArea>
        </div>
      </div>
    </div>
  );
}
