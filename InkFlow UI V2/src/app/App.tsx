import { useState, useEffect } from 'react';
import { MainLayout } from './components/layout/MainLayout';
import { ThemeSwitcher } from './components/layout/ThemeSwitcher';
import { PhaseSwitcher } from './components/sidebar/PhaseSwitcher';
import type { CreationPhase } from '@/types';
import { ProjectTree } from './components/sidebar/ProjectTree';
import { AssetDrawer } from './components/sidebar/AssetDrawer';
import { ProgressStats } from './components/sidebar/ProgressStats';
import { TipTapEditor } from './components/editor/TipTapEditor';
import { AgentStatus, AgentType, AgentState } from './components/copilot/AgentStatus';
import { ThoughtChain } from './components/copilot/ThoughtChain';
import { SkillSelector, Skill } from './components/copilot/SkillSelector';
import { ChatInterface } from './components/copilot/ChatInterface';
import { TokenUsageIndicator } from './components/copilot/TokenUsageIndicator';
import type { Message } from '@/types';
import { ArtifactCard } from './components/copilot/ArtifactCard';
import { ScrollArea } from './components/ui/scroll-area';
import { ConsistencyWarningIndicator, ConsistencyWarningPanel } from './components/consistency';
import { LoginPage } from './pages/LoginPage';
import { SettingsPage } from './pages/SettingsPage';
import { Button } from './components/ui/button';
import { Toaster } from './components/ui/sonner';
import { ErrorBoundary } from './components/error/ErrorBoundary';
import { useGlobalErrorHandler } from '@/hooks/useGlobalErrorHandler';
import { useUIStore, selectTheme, selectZenMode } from '@/stores';
import { Settings, LogOut } from 'lucide-react';
import { initApiClient } from '@/api/client';

// Initialize API Client
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';
initApiClient({
  baseUrl: API_BASE_URL,
  timeout: 30000,
  onUnauthorized: () => {
    // Will be handled by the app
    console.log('Unauthorized - redirecting to login');
  },
});

// Mock Data
const mockVolumes = [
  {
    id: 'v1',
    projectId: 'p1',
    title: '第一卷：觉醒',
    orderIndex: 0,
    chapterCount: 3,
    wordCount: 11100,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    chapters: [
      { id: 'c1', projectId: 'p1', volumeId: 'v1', title: '第一章：序幕', orderIndex: 0, status: 'COMPLETE' as const, wordCount: 3200, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
      { id: 'c2', projectId: 'p1', volumeId: 'v1', title: '第二章：初遇', orderIndex: 1, status: 'COMPLETE' as const, wordCount: 4100, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
      { id: 'c3', projectId: 'p1', volumeId: 'v1', title: '第三章：真相', orderIndex: 2, status: 'WRITING' as const, wordCount: 3800, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
    ],
  },
  {
    id: 'v2',
    projectId: 'p1',
    title: '第二卷：征途',
    orderIndex: 1,
    chapterCount: 2,
    wordCount: 2900,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    chapters: [
      { id: 'c4', projectId: 'p1', volumeId: 'v2', title: '第四章：启程', orderIndex: 0, status: 'WRITING' as const, wordCount: 2900, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
      { id: 'c5', projectId: 'p1', volumeId: 'v2', title: '第五章：试炼', orderIndex: 1, status: 'DRAFT' as const, wordCount: 0, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
    ],
  },
];

const mockCharacters = [
  {
    id: 'char1',
    name: '李明',
    description: '主角，勇敢的探险家',
    traits: ['勇敢', '孤儿', '善良'],
  },
  {
    id: 'char2',
    name: '苏婉',
    description: '神秘的引路人',
    traits: ['智慧', '神秘', '冷静'],
  },
];

const mockWiki = [
  { id: 'wiki1', title: '黑曜石之门', content: '连接两个世界的神秘传送门' },
  { id: 'wiki2', title: '旧钥匙', content: '李明从祖父那里继承的古老钥匙' },
];

const mockPlotLoops = [
  { id: 'plot1', description: '李明的钥匙的真正用途', status: 'open' as const },
  { id: 'plot2', description: '苏婉的真实身份', status: 'open' as const },
  { id: 'plot3', description: '村庄的诅咒', status: 'resolved' as const },
];

const initialSkills: Skill[] = [
  { id: 'skill1', name: '动作加强', emoji: '🔥', status: 'inactive' },
  { id: 'skill2', name: '心理侧写', emoji: '💭', status: 'inactive' },
  { id: 'skill3', name: '环境渲染', emoji: '🌧️', status: 'inactive' },
  { id: 'skill4', name: '对话润色', emoji: '🗣️', status: 'inactive' },
  { id: 'skill5', name: 'Show不Tell', emoji: '✨', status: 'inactive' },
];

type AppView = 'login' | 'editor' | 'settings';

export default function App() {
  // Initialize global error handler
  useGlobalErrorHandler({
    onAuthError: () => setView('login'),
  });

  // UI Store state
  const theme = useUIStore(selectTheme);
  const zenMode = useUIStore(selectZenMode);
  const setTheme = useUIStore((state) => state.setTheme);
  const toggleZenMode = useUIStore((state) => state.toggleZenMode);

  const [view, setView] = useState<AppView>('login');
  const [phase, setPhase] = useState<CreationPhase>('WRITING');
  const [selectedChapter, setSelectedChapter] = useState('c1');
  const [editorContent, setEditorContent] = useState(
    '李明站在古老的村落入口，手中紧握着那把祖父留下的钥匙。夕阳的余晖洒在石板路上，远处传来隐约的钟声。\n\n他知道，一旦踏入这个村庄，一切都将改变。'
  );
  const [messages, setMessages] = useState<Message[]>([
    {
      id: 'm1',
      role: 'assistant',
      content: '你好！我是 InkFlow AI 写作助手。我会在你创作的过程中提供帮助，但不会打扰你的思路。有什么我可以帮助的吗？',
      timestamp: new Date(),
    },
  ]);
  const [isAiThinking, setIsAiThinking] = useState(false);
  const [skills, setSkills] = useState<Skill[]>(initialSkills);
  const [agentState, setAgentState] = useState<AgentState>('online');
  const [currentAgent] = useState<AgentType>('WriterAgent');
  const [thoughtEvents, setThoughtEvents] = useState<any[]>([]);
  const [showArtifact, setShowArtifact] = useState(false);

  // Theme is now managed by UIStore with automatic DOM sync

  const handleChapterSelect = (_volumeId: string, chapterId: string) => {
    setSelectedChapter(chapterId);
  };

  const handleAssetClick = (_type: string, _id: string) => {
    // Simulate showing artifact
    setShowArtifact(true);
    setTimeout(() => setShowArtifact(false), 5000);
  };

  const handleSendMessage = (message: string) => {
    const userMessage: Message = {
      id: `m${Date.now()}`,
      role: 'user',
      content: message,
      timestamp: new Date(),
    };
    setMessages([...messages, userMessage]);
    setIsAiThinking(true);
    setAgentState('thinking');

    // Simulate AI thinking with thought chain
    const mockThoughts = [
      {
        id: 't1',
        type: 'thinking' as const,
        agent: 'ThinkingAgent',
        message: '意图识别: 内容生成',
        confidence: 0.95,
      },
      {
        id: 't2',
        type: 'rag' as const,
        agent: 'ThinkingAgent',
        message: '检索知识库: "李明", "旧钥匙"',
      },
      {
        id: 't3',
        type: 'skill' as const,
        agent: 'WriterAgent',
        message: '激活技能: [环境描写], [心理描写]',
      },
    ];
    setThoughtEvents(mockThoughts);

    // Simulate AI response
    setTimeout(() => {
      const aiMessage: Message = {
        id: `m${Date.now()}`,
        role: 'assistant',
        content: '我注意到你在描写李明的心理状态。要不要试试用更具体的细节来展现他的紧张？比如通过身体动作或环境感知来体现内心的不安。',
        timestamp: new Date(),
      };
      setMessages((prev) => [...prev, aiMessage]);
      setIsAiThinking(false);
      setAgentState('online');

      // Auto-activate a skill
      setSkills((prev) =>
        prev.map((s) =>
          s.id === 'skill5' ? { ...s, status: 'auto' as const } : s
        )
      );
      setTimeout(() => {
        setSkills((prev) =>
          prev.map((s) =>
            s.id === 'skill5' ? { ...s, status: 'inactive' as const } : s
          )
        );
      }, 3000);
    }, 2000);
  };

  const handleToggleSkill = (skillId: string) => {
    setSkills((prev) =>
      prev.map((skill) =>
        skill.id === skillId
          ? {
            ...skill,
            status:
              skill.status === 'inactive'
                ? ('manual' as const)
                : ('inactive' as const),
          }
          : skill
      )
    );
  };

  const handleApplyArtifact = () => {
    const newContent = `${editorContent}\n\n李明的手指轻轻摩挲着钥匙冰冷的金属表面，那上面镌刻的古老纹路在指尖传来细微的触感。他能感受到自己的心跳，一下，又一下，在寂静的黄昏里格外清晰。`;
    setEditorContent(newContent);
  };

  // Mock weekly activity data for ProgressStats
  const mockWeeklyActivity = [
    { date: new Date(Date.now() - 6 * 24 * 60 * 60 * 1000).toISOString().split('T')[0], wordCount: 12000, wordCountChange: 1500 },
    { date: new Date(Date.now() - 5 * 24 * 60 * 60 * 1000).toISOString().split('T')[0], wordCount: 13800, wordCountChange: 1800 },
    { date: new Date(Date.now() - 4 * 24 * 60 * 60 * 1000).toISOString().split('T')[0], wordCount: 14500, wordCountChange: 700 },
    { date: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000).toISOString().split('T')[0], wordCount: 16500, wordCountChange: 2000 },
    { date: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString().split('T')[0], wordCount: 17200, wordCountChange: 700 },
    { date: new Date(Date.now() - 1 * 24 * 60 * 60 * 1000).toISOString().split('T')[0], wordCount: 19500, wordCountChange: 2300 },
    { date: new Date().toISOString().split('T')[0], wordCount: 21000, wordCountChange: 1500 },
  ];

  // Left Sidebar
  const leftSidebar = (
    <div className="h-full flex flex-col">
      <PhaseSwitcher currentPhase={phase} onPhaseChange={setPhase} />
      <ProjectTree
        volumes={mockVolumes}
        onChapterSelect={handleChapterSelect}
        selectedChapterId={selectedChapter}
      />
      <AssetDrawer
        characters={mockCharacters}
        wiki={mockWiki}
        plotLoops={mockPlotLoops}
        onAssetClick={handleAssetClick}
      />
      {/* Progress Stats - Requirements: 7.1, 7.2, 7.3 */}
      <div className="mt-auto">
        <ProgressStats
          totalWordCount={21000}
          todayWordCount={1500}
          dailyGoal={2000}
          weeklyActivity={mockWeeklyActivity}
        />
      </div>
    </div>
  );

  // Editor
  const editor = (
    <div className="h-full flex flex-col">
      {/* Theme Switcher Bar */}
      <div className="border-b border-border bg-card px-6 py-3 flex items-center justify-end shrink-0 gap-2">
        <Button
          variant="ghost"
          size="icon"
          onClick={() => setView('settings')}
          className="rounded-full text-muted-foreground hover:text-foreground"
          title="Settings"
        >
          <Settings className="h-4 w-4" />
        </Button>
        <div className="w-px h-6 bg-border mx-2" />
        <ThemeSwitcher currentTheme={theme} onThemeChange={setTheme} />
        <Button
          variant="ghost"
          size="icon"
          onClick={() => setView('login')}
          className="rounded-full text-muted-foreground hover:text-destructive ml-2"
          title="Log Out"
        >
          <LogOut className="h-4 w-4" />
        </Button>
      </div>

      <div className="flex-1 overflow-hidden">
        <TipTapEditor
          content={editorContent}
          onChange={setEditorContent}
          onZenToggle={toggleZenMode}
          zenMode={zenMode}
          breadcrumb="第一卷 > 第一章：序幕"
        />
      </div>
    </div>
  );

  // Right Sidebar (Copilot)
  const rightSidebar = (
    <div className="h-full flex flex-col">
      <AgentStatus agent={currentAgent} state={agentState} />
      {/* Consistency Warning Indicator - Requirements: 5.1, 5.2 */}
      <div className="px-4 py-2 border-b border-border">
        <ConsistencyWarningIndicator showDetails={true} />
      </div>
      {/* Token Usage Indicator - Requirements: 10.1, 10.2, 10.3, 10.4, 10.5 */}
      <TokenUsageIndicator integrated={false} todayUsage={45000} dailyQuota={100000} />

      <ScrollArea className="flex-1">
        <div className="p-4 space-y-4">
          <ThoughtChain events={thoughtEvents} isThinking={isAiThinking} />

          {showArtifact && (
            <ArtifactCard
              type="character"
              title="李明"
              description="主角人物卡片"
              tags={['勇敢', '孤儿', '善良']}
              content="年龄: 23岁
背景: 在祖父身边长大的孤儿
性格: 外表坚强，内心敏感
目标: 揭开家族的秘密"
              onApply={handleApplyArtifact}
            />
          )}
        </div>
      </ScrollArea>

      <SkillSelector skills={skills} onToggle={handleToggleSkill} />

      <div className="flex-1 min-h-0">
        <ChatInterface
          messages={messages}
          onSend={handleSendMessage}
          isLoading={isAiThinking}
        />
      </div>
    </div>
  );

  const renderContent = () => {
    if (view === 'login') {
      return <LoginPage onLogin={() => setView('editor')} />;
    }

    if (view === 'settings') {
      return (
        <SettingsPage
          onBack={() => setView('editor')}
          currentTheme={theme}
          onThemeChange={setTheme}
        />
      );
    }

    return (
      <MainLayout
        leftSidebar={leftSidebar}
        editor={editor}
        rightSidebar={rightSidebar}
        zenMode={zenMode}
      />
    );
  };

  return (
    <ErrorBoundary>
      {renderContent()}
      {/* Consistency Warning Panel - Requirements: 5.3, 5.4, 5.5, 5.6 */}
      <ConsistencyWarningPanel />
      <Toaster position="top-right" richColors closeButton />
    </ErrorBoundary>
  );
}