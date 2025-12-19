package com.inkflow.module.ai_bridge.service;

import com.inkflow.module.project.entity.CreationPhase;
import com.inkflow.module.project.entity.Project;
import com.inkflow.module.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 阶段推断服务
 * 根据项目状态和消息内容推断当前创作阶段
 * 
 * Requirements: 3.1-3.7
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhaseInferenceService {

    private final ProjectRepository projectRepository;

    /**
     * 阶段关键词映射
     */
    private static final Map<CreationPhase, Set<String>> PHASE_KEYWORDS = Map.of(
        CreationPhase.IDEA, Set.of(
            "灵感", "创意", "想法", "点子", "构思", "题材", "类型", "主题"
        ),
        CreationPhase.WORLDBUILDING, Set.of(
            "世界观", "设定", "魔法", "力量体系", "地理", "国家", "城市", "历史", "背景"
        ),
        CreationPhase.CHARACTER, Set.of(
            "角色", "人物", "主角", "配角", "反派", "性格", "背景故事", "关系"
        ),
        CreationPhase.OUTLINE, Set.of(
            "大纲", "结构", "情节", "剧情", "章节", "分卷", "伏笔", "转折"
        ),
        CreationPhase.WRITING, Set.of(
            "写作", "创作", "撰写", "内容", "段落", "场景", "对话", "描写"
        ),
        CreationPhase.REVISION, Set.of(
            "修改", "修订", "完善", "检查", "一致性", "漏洞", "优化", "润色"
        )
    );

    /**
     * 推断创作阶段
     * 
     * @param projectId 项目ID
     * @param message 用户消息
     * @return 推断的创作阶段
     */
    public CreationPhase inferPhase(UUID projectId, String message) {
        // 1. 首先尝试从消息内容推断
        CreationPhase messageInferred = inferFromMessage(message);
        if (messageInferred != null) {
            log.debug("从消息内容推断阶段: {}", messageInferred);
            return messageInferred;
        }

        // 2. 从项目状态推断
        if (projectId != null) {
            CreationPhase projectPhase = inferFromProject(projectId);
            if (projectPhase != null) {
                log.debug("从项目状态推断阶段: {}", projectPhase);
                return projectPhase;
            }
        }

        // 3. 默认返回灵感阶段
        log.debug("使用默认阶段: IDEA");
        return CreationPhase.IDEA;
    }

    /**
     * 从消息内容推断阶段
     */
    private CreationPhase inferFromMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }

        String lowerMessage = message.toLowerCase();
        
        // 计算每个阶段的匹配分数
        CreationPhase bestMatch = null;
        int bestScore = 0;

        for (Map.Entry<CreationPhase, Set<String>> entry : PHASE_KEYWORDS.entrySet()) {
            int score = 0;
            for (String keyword : entry.getValue()) {
                if (lowerMessage.contains(keyword)) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestMatch = entry.getKey();
            }
        }

        // 至少匹配一个关键词才返回
        return bestScore > 0 ? bestMatch : null;
    }

    /**
     * 从项目状态推断阶段
     */
    private CreationPhase inferFromProject(UUID projectId) {
        return projectRepository.findById(projectId)
            .map(Project::getCreationPhase)
            .orElse(null);
    }

    /**
     * 检查消息是否明确指定了阶段
     */
    public boolean hasExplicitPhase(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }

        // 检查是否包含明确的阶段指示
        String[] explicitPatterns = {
            "进入.*阶段", "切换到.*阶段", "开始.*阶段",
            "灵感阶段", "世界观阶段", "角色阶段", "大纲阶段", "写作阶段", "修订阶段"
        };

        for (String pattern : explicitPatterns) {
            if (Pattern.compile(pattern).matcher(message).find()) {
                return true;
            }
        }

        return false;
    }

    /**
     * 获取阶段的显示名称
     */
    public String getPhaseDisplayName(CreationPhase phase) {
        return phase.getDisplayName();
    }

    /**
     * 获取阶段的描述
     */
    public String getPhaseDescription(CreationPhase phase) {
        return phase.getDescription();
    }
}
