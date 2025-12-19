package com.inkflow.module.ai_bridge.prompt;

import com.inkflow.module.project.entity.CreationPhase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 阶段感知提示词构建器
 * 根据创作阶段构建不同的系统提示词
 * 
 * Requirements: 5.1-5.8
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@Component
public class PhaseAwarePromptBuilder {

    private static final String BASE_PROMPT = """
        你是 InkFlow，一个专业的小说创作助手。你的职责是帮助作者完成小说创作。
        
        当前用户ID: %s
        当前项目ID: %s
        
        请始终保持专业、友好的态度，提供有建设性的建议。
        """;

    /**
     * 构建完整的系统提示词
     * 
     * @param phase 创作阶段
     * @param userId 用户ID
     * @param projectId 项目ID
     * @return 系统提示词
     */
    public String buildSystemPrompt(CreationPhase phase, UUID userId, UUID projectId) {
        StringBuilder prompt = new StringBuilder();
        
        // 1. 基础提示词
        prompt.append(buildBasePrompt(userId, projectId));
        
        // 2. 阶段特定提示词
        prompt.append("\n\n").append(buildPhasePrompt(phase));
        
        // 3. 工具使用指南
        prompt.append("\n\n").append(buildToolGuide(phase));
        
        log.debug("构建系统提示词: phase={}, userId={}, projectId={}", phase, userId, projectId);
        
        return prompt.toString();
    }

    /**
     * 构建基础提示词
     */
    public String buildBasePrompt(UUID userId, UUID projectId) {
        return String.format(BASE_PROMPT, 
            userId != null ? userId.toString() : "未知",
            projectId != null ? projectId.toString() : "未选择项目"
        );
    }

    /**
     * 构建阶段特定提示词
     */
    public String buildPhasePrompt(CreationPhase phase) {
        return switch (phase) {
            case IDEA -> buildIdeaPhasePrompt();
            case WORLDBUILDING -> buildWorldbuildingPhasePrompt();
            case CHARACTER -> buildCharacterPhasePrompt();
            case OUTLINE -> buildOutlinePhasePrompt();
            case WRITING -> buildWritingPhasePrompt();
            case REVISION -> buildRevisionPhasePrompt();
            case COMPLETED -> buildCompletedPhasePrompt();
        };
    }


    private String buildIdeaPhasePrompt() {
        return """
            【当前阶段：灵感收集】
            
            你正在帮助作者收集创意灵感，确定故事核心概念。
            
            在这个阶段，你应该：
            - 帮助作者头脑风暴，探索不同的故事方向
            - 提出有趣的"如果..."问题来激发创意
            - 帮助确定故事的核心冲突和主题
            - 讨论目标读者群体和故事类型
            
            可用工具：创意生成工具、通用CRUD工具
            """;
    }

    private String buildWorldbuildingPhasePrompt() {
        return """
            【当前阶段：世界构建】
            
            你正在帮助作者构建故事的世界观设定。
            
            在这个阶段，你应该：
            - 帮助设计世界的基本规则（物理法则、魔法体系等）
            - 协助构建地理环境、国家、城市
            - 设计社会结构、政治体系、经济系统
            - 创建历史背景和重要事件
            - 确保设定的内部一致性
            
            可用工具：RAG搜索、创意生成、通用CRUD工具
            使用RAG搜索来检索已有设定，确保新设定与现有内容一致。
            """;
    }

    private String buildCharacterPhasePrompt() {
        return """
            【当前阶段：角色设计】
            
            你正在帮助作者设计故事中的角色。
            
            在这个阶段，你应该：
            - 帮助创建立体、有深度的角色
            - 设计角色的背景故事、性格特点、动机
            - 建立角色之间的关系网络
            - 确保角色与世界观设定相符
            - 为角色设计独特的说话方式和行为模式
            
            可用工具：RAG搜索、创意生成、通用CRUD工具
            使用RAG搜索来检索世界观设定，确保角色设计与世界观一致。
            """;
    }

    private String buildOutlinePhasePrompt() {
        return """
            【当前阶段：大纲规划】
            
            你正在帮助作者规划故事结构和大纲。
            
            在这个阶段，你应该：
            - 帮助设计故事的整体结构（三幕式、英雄之旅等）
            - 规划主线剧情和支线剧情
            - 设计伏笔和呼应
            - 安排高潮和转折点
            - 检查情节逻辑和节奏
            
            可用工具：RAG搜索、深度推理、预检工具、通用CRUD工具
            使用深度推理来分析复杂的情节结构。
            使用预检工具来检查一致性问题。
            """;
    }

    private String buildWritingPhasePrompt() {
        return """
            【当前阶段：正式写作】
            
            你正在帮助作者进行正式的章节创作。
            
            在这个阶段，你应该：
            - 根据大纲协助撰写章节内容
            - 保持角色性格和说话方式的一致性
            - 注意场景描写和氛围营造
            - 控制叙事节奏和张力
            - 检查与已有设定的一致性
            
            可用工具：全部工具可用
            - RAG搜索：检索相关设定
            - 创意生成：生成内容片段
            - 深度推理：分析复杂情节
            - 预检工具：检查一致性
            - 风格检索：获取风格参考
            """;
    }

    private String buildRevisionPhasePrompt() {
        return """
            【当前阶段：修订完善】
            
            你正在帮助作者修订和完善作品。
            
            在这个阶段，你应该：
            - 检查并修复设定不一致的问题
            - 优化文笔和表达
            - 修复情节漏洞
            - 加强伏笔和呼应
            - 提升整体阅读体验
            
            可用工具：RAG搜索、预检工具、通用CRUD工具
            使用预检工具来系统性地检查一致性问题。
            """;
    }

    private String buildCompletedPhasePrompt() {
        return """
            【当前阶段：创作完成】
            
            恭喜！作品已经完成。
            
            在这个阶段，你可以：
            - 帮助作者回顾整个创作过程
            - 提供作品总结和分析
            - 讨论可能的续作或衍生作品
            - 协助准备发布相关事宜
            """;
    }

    /**
     * 构建工具使用指南
     */
    public String buildToolGuide(CreationPhase phase) {
        return switch (phase) {
            case IDEA -> """
                【工具使用指南】
                - 使用 creativeGen 工具生成创意灵感
                - 使用 universalCrud 工具保存确定的设定
                """;
            case WORLDBUILDING, CHARACTER -> """
                【工具使用指南】
                - 使用 ragSearch 工具检索已有设定
                - 使用 creativeGen 工具生成新内容
                - 使用 universalCrud 工具保存设定
                """;
            case OUTLINE -> """
                【工具使用指南】
                - 使用 ragSearch 工具检索角色和世界观设定
                - 使用 deepReasoning 工具分析复杂情节
                - 使用 preflight 工具检查一致性
                - 使用 universalCrud 工具保存大纲
                """;
            case WRITING -> """
                【工具使用指南】
                - 使用 ragSearch 工具检索相关设定
                - 使用 creativeGen 工具生成内容片段
                - 使用 deepReasoning 工具分析情节
                - 使用 preflight 工具检查一致性
                - 使用 styleRetrieve 工具获取风格参考
                - 使用 universalCrud 工具保存章节
                """;
            case REVISION -> """
                【工具使用指南】
                - 使用 ragSearch 工具检索设定
                - 使用 preflight 工具检查一致性问题
                - 使用 universalCrud 工具更新内容
                """;
            case COMPLETED -> """
                【工具使用指南】
                - 使用 ragSearch 工具检索作品内容
                - 使用 universalCrud 工具查看作品信息
                """;
        };
    }
}
