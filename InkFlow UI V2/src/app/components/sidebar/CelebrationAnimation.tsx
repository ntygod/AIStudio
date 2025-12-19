/**
 * 庆祝动画组件
 * 当用户完成每日目标时显示庆祝动画
 * 
 * Requirements: 7.4
 */

import { useEffect, useState, useCallback } from 'react';
import { Sparkles, Trophy, Star, PartyPopper } from 'lucide-react';
import { Button } from '../ui/button';

interface CelebrationAnimationProps {
  /** 动画完成后的回调 */
  onComplete: () => void;
  /** 自动关闭延迟（毫秒），默认 5000ms */
  autoCloseDelay?: number;
}

interface Particle {
  id: number;
  x: number;
  y: number;
  size: number;
  color: string;
  delay: number;
  duration: number;
}

// 粒子颜色
const PARTICLE_COLORS = [
  'bg-yellow-400',
  'bg-orange-400',
  'bg-pink-400',
  'bg-purple-400',
  'bg-blue-400',
  'bg-green-400',
];

// 生成随机粒子
function generateParticles(count: number): Particle[] {
  return Array.from({ length: count }, (_, i) => ({
    id: i,
    x: Math.random() * 100,
    y: Math.random() * 100,
    size: Math.random() * 8 + 4,
    color: PARTICLE_COLORS[Math.floor(Math.random() * PARTICLE_COLORS.length)],
    delay: Math.random() * 0.5,
    duration: Math.random() * 1 + 1,
  }));
}

export function CelebrationAnimation({ 
  onComplete, 
  autoCloseDelay = 5000 
}: CelebrationAnimationProps) {
  const [isVisible, setIsVisible] = useState(true);
  const [particles] = useState(() => generateParticles(20));

  // 自动关闭
  useEffect(() => {
    const timer = setTimeout(() => {
      setIsVisible(false);
      setTimeout(onComplete, 300); // 等待淡出动画完成
    }, autoCloseDelay);

    return () => clearTimeout(timer);
  }, [autoCloseDelay, onComplete]);

  // 手动关闭
  const handleClose = useCallback(() => {
    setIsVisible(false);
    setTimeout(onComplete, 300);
  }, [onComplete]);

  return (
    <div 
      className={`fixed inset-0 z-50 flex items-center justify-center transition-opacity duration-300 ${
        isVisible ? 'opacity-100' : 'opacity-0 pointer-events-none'
      }`}
    >
      {/* 背景遮罩 */}
      <div 
        className="absolute inset-0 bg-black/50 backdrop-blur-sm"
        onClick={handleClose}
      />

      {/* 粒子效果 */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        {particles.map((particle) => (
          <div
            key={particle.id}
            className={`absolute rounded-full ${particle.color} animate-celebration-particle`}
            style={{
              left: `${particle.x}%`,
              top: `${particle.y}%`,
              width: `${particle.size}px`,
              height: `${particle.size}px`,
              animationDelay: `${particle.delay}s`,
              animationDuration: `${particle.duration}s`,
            }}
          />
        ))}
      </div>

      {/* 主内容 */}
      <div 
        className={`relative bg-card rounded-2xl p-8 shadow-2xl max-w-sm mx-4 text-center transform transition-all duration-500 ${
          isVisible ? 'scale-100 translate-y-0' : 'scale-95 translate-y-4'
        }`}
      >
        {/* 图标 */}
        <div className="relative mb-4">
          <div className="w-20 h-20 mx-auto bg-gradient-to-br from-yellow-400 to-orange-500 rounded-full flex items-center justify-center animate-bounce-slow">
            <Trophy className="h-10 w-10 text-white" />
          </div>
          
          {/* 装饰星星 */}
          <Star className="absolute top-0 left-1/4 h-5 w-5 text-yellow-400 animate-twinkle" />
          <Star className="absolute top-2 right-1/4 h-4 w-4 text-yellow-400 animate-twinkle" style={{ animationDelay: '0.3s' }} />
          <Sparkles className="absolute bottom-0 left-1/3 h-5 w-5 text-orange-400 animate-twinkle" style={{ animationDelay: '0.6s' }} />
        </div>

        {/* 标题 */}
        <h2 className="text-2xl font-bold mb-2 bg-gradient-to-r from-yellow-500 to-orange-500 bg-clip-text text-transparent">
          恭喜完成目标！
        </h2>

        {/* 描述 */}
        <p className="text-muted-foreground mb-6">
          你已经完成了今日的写作目标，继续保持！
        </p>

        {/* 装饰图标 */}
        <div className="flex justify-center gap-4 mb-6">
          <PartyPopper className="h-6 w-6 text-pink-500 animate-wiggle" />
          <Sparkles className="h-6 w-6 text-yellow-500 animate-wiggle" style={{ animationDelay: '0.2s' }} />
          <PartyPopper className="h-6 w-6 text-purple-500 animate-wiggle" style={{ animationDelay: '0.4s' }} />
        </div>

        {/* 关闭按钮 */}
        <Button onClick={handleClose} className="w-full">
          继续创作
        </Button>
      </div>

      {/* 自定义动画样式 */}
      <style>{`
        @keyframes celebration-particle {
          0% {
            transform: translateY(0) scale(1);
            opacity: 1;
          }
          100% {
            transform: translateY(-100vh) scale(0);
            opacity: 0;
          }
        }
        
        @keyframes bounce-slow {
          0%, 100% {
            transform: translateY(0);
          }
          50% {
            transform: translateY(-10px);
          }
        }
        
        @keyframes twinkle {
          0%, 100% {
            opacity: 1;
            transform: scale(1);
          }
          50% {
            opacity: 0.5;
            transform: scale(0.8);
          }
        }
        
        @keyframes wiggle {
          0%, 100% {
            transform: rotate(-5deg);
          }
          50% {
            transform: rotate(5deg);
          }
        }
        
        .animate-celebration-particle {
          animation: celebration-particle linear forwards;
        }
        
        .animate-bounce-slow {
          animation: bounce-slow 2s ease-in-out infinite;
        }
        
        .animate-twinkle {
          animation: twinkle 1.5s ease-in-out infinite;
        }
        
        .animate-wiggle {
          animation: wiggle 0.5s ease-in-out infinite;
        }
      `}</style>
    </div>
  );
}
