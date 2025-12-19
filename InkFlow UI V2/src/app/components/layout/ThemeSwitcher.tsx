import { Palette } from 'lucide-react';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../ui/select';

export type ThemeMode = 'dark' | 'light' | 'parchment' | 'forest';

interface ThemeSwitcherProps {
  currentTheme: ThemeMode;
  onThemeChange: (theme: ThemeMode) => void;
}

const themes = [
  { value: 'dark' as const, label: '🌙 深空蓝', description: '深色护眼模式' },
  { value: 'light' as const, label: '☀️ 纸张白', description: '明亮清爽' },
  { value: 'parchment' as const, label: '📜 羊皮纸', description: '复古温暖' },
  { value: 'forest' as const, label: '🌲 森林绿', description: '自然护眼' },
];

export function ThemeSwitcher({ currentTheme, onThemeChange }: ThemeSwitcherProps) {
  return (
    <div className="flex items-center gap-2">
      <Palette className="h-4 w-4 text-muted-foreground" />
      <Select value={currentTheme} onValueChange={onThemeChange}>
        <SelectTrigger className="w-[160px] h-9 rounded-full">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {themes.map((theme) => (
            <SelectItem key={theme.value} value={theme.value}>
              <div>
                <div>{theme.label}</div>
                <div className="text-xs text-muted-foreground">{theme.description}</div>
              </div>
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}
