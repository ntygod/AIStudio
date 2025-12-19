/**
 * 技能选择器组件
 * 显示和切换 AI 助手的技能
 * 集成 ChatStore 进行状态管理
 */

import { useCallback, useMemo } from 'react';
import { Badge } from '../ui/badge';
import { ScrollArea } from '../ui/scroll-area';
import { motion } from 'motion/react';
import { useChatStore } from '@/stores/chat-store';

export interface Skill {
  id: string;
  name: string;
  emoji: string;
  description?: string;
  status: 'inactive' | 'manual' | 'auto';
}

interface SkillSelectorProps {
  /** 外部传入的技能列表（可选，用于非集成模式） */
  skills?: Skill[];
  /** 技能切换回调（可选，用于非集成模式） */
  onToggle?: (skillId: string) => void;
  /** 是否使用集成模式（连接 store） */
  integrated?: boolean;
}

// 默认技能配置
const defaultSkills: Omit<Skill, 'status'>[] = [
  { id: 'psychology', name: '心理', emoji: '🧠', description: '角色心理分析' },
  { id: 'action', name: '动作', emoji: '⚔️', description: '动作场景描写' },
  { id: 'description', name: '描写', emoji: '🎨', description: '环境细节描写' },
  { id: 'dialogue', name: '对话', emoji: '💬', description: '对话风格优化' },
  { id: 'consistency', name: '一致性', emoji: '🔗', description: '设定一致性检查' },
];

export function SkillSelector({ 
  skills: externalSkills, 
  onToggle: externalOnToggle,
  integrated = false 
}: SkillSelectorProps) {
  // Store hooks (only used in integrated mode)
  const { activeSkills, toggleSkill } = useChatStore();

  // 构建技能列表（集成模式下使用默认技能 + 激活状态）
  const skills = useMemo(() => {
    if (!integrated) {
      return externalSkills || [];
    }

    return defaultSkills.map(skill => ({
      ...skill,
      status: activeSkills.includes(skill.id) ? 'manual' as const : 'inactive' as const,
    }));
  }, [integrated, externalSkills, activeSkills]);

  // 处理技能切换
  const handleToggle = useCallback((skillId: string) => {
    if (integrated) {
      toggleSkill(skillId);
    } else if (externalOnToggle) {
      externalOnToggle(skillId);
    }
  }, [integrated, toggleSkill, externalOnToggle]);

  // 如果没有技能，不渲染
  if (skills.length === 0) return null;

  return (
    <div className="px-5 py-4 border-b border-border bg-card">
      <div className="flex items-center justify-between mb-3">
        <div className="text-xs text-muted-foreground font-medium">技能槽</div>
        {integrated && activeSkills.length > 0 && (
          <div className="text-xs text-muted-foreground">
            已激活 {activeSkills.length} 个
          </div>
        )}
      </div>
      <ScrollArea className="w-full">
        <div className="flex gap-2 pb-2">
          {skills.map((skill) => (
            <motion.button
              key={skill.id}
              onClick={() => handleToggle(skill.id)}
              whileTap={{ scale: 0.95 }}
              className="shrink-0"
              title={skill.description}
            >
              <Badge
                variant={skill.status === 'inactive' ? 'outline' : 'default'}
                className={`
                  cursor-pointer transition-all rounded-full px-4 py-1.5 
                  ${skill.status === 'inactive' ? 'bg-transparent text-muted-foreground border-border/50 hover:bg-accent hover:text-accent-foreground hover:border-border' : ''}
                  ${skill.status === 'manual' ? 'bg-primary text-primary-foreground shadow-[0_0_15px_rgba(124,58,237,0.3)] ring-1 ring-primary/50 border-primary' : ''}
                  ${skill.status === 'auto' ? 'bg-primary text-primary-foreground animate-pulse shadow-md' : ''}
                `}
              >
                <span className="mr-1.5">{skill.emoji}</span>
                {skill.name}
              </Badge>
            </motion.button>
          ))}
        </div>
      </ScrollArea>
    </div>
  );
}