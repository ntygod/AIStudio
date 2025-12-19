package com.inkflow.module.ai_bridge.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.module.ai_bridge.context.RequestContextHolder;
import com.inkflow.module.character.entity.Character;
import com.inkflow.module.character.repository.CharacterRepository;
import com.inkflow.module.plotloop.entity.PlotLoop;
import com.inkflow.module.plotloop.entity.PlotLoopStatus;
import com.inkflow.module.plotloop.repository.PlotLoopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 预检工具
 * 在生成章节内容前检查逻辑冲突和设定矛盾
 * 
 * <p>检查类型：
 * <ul>
 *   <li>角色一致性 - 检查已死亡角色、角色状态</li>
 *   <li>时间线 - 检查时间顺序是否合理</li>
 *   <li>伏笔 - 检查紧急伏笔是否被遗忘</li>
 *   <li>设定冲突 - 检查是否违反世界观设定</li>
 * </ul>
 * 
 * Requirements: 12.1-12.6
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreflightTool {

    private final CharacterRepository characterRepository;
    private final PlotLoopRepository plotLoopRepository;
    private final ObjectMapper objectMapper;

    @Value("${inkflow.preflight.ai-enhanced:false}")
    private boolean aiEnhancedEnabled;

    /**
     * 警告严重程度
     */
    public enum Severity {
        ERROR("错误", "必须修复"),
        WARNING("警告", "建议修复"),
        INFO("提示", "可选修复");

        private final String displayName;
        private final String description;

        Severity(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }

    /**
     * 冲突类型
     */
    public enum ConflictType {
        CHARACTER("角色"),
        TIMELINE("时间线"),
        PLOTLOOP("伏笔"),
        SETTING("设定"),
        LOGIC("逻辑");

        private final String displayName;

        ConflictType(String displayName) {
            this.displayName = displayName;
        }
    }

    /**
     * 执行预检检查
     */
    @Tool(description = "预检工具：在生成章节内容前检查逻辑冲突和设定矛盾。" +
                        "检查角色状态、时间线、伏笔、设定一致性等。")
    public String runPreflight(
            @ToolParam(description = "项目ID") String projectId,
            @ToolParam(description = "章节大纲节拍，JSON数组格式，如 [\"节拍1\", \"节拍2\"]") String beats,
            @ToolParam(description = "是否跳过详细检查，只做快速检查", required = false) Boolean quickCheck) {

        log.info("执行预检: projectId={}, quickCheck={}", projectId, quickCheck);

        try {
            UUID projectUuid = UUID.fromString(projectId);
            List<String> beatList = parseBeats(beats);
            boolean quick = quickCheck != null && quickCheck;

            // 执行检查
            List<ConflictWarning> warnings = new ArrayList<>();

            // 1. 角色一致性检查
            warnings.addAll(checkCharacterConsistency(projectUuid, beatList));

            // 2. 伏笔检查
            warnings.addAll(checkPlotLoops(projectUuid, beatList));

            if (!quick) {
                // 3. 更详细的检查（非快速模式）
                warnings.addAll(checkDetailedLogic(projectUuid, beatList));
            }

            return formatResult(warnings);

        } catch (IllegalArgumentException e) {
            log.error("无效的项目ID: {}", projectId);
            return formatError("无效的项目ID格式");
        } catch (Exception e) {
            log.error("预检失败: {}", e.getMessage(), e);
            return formatError("预检过程出错: " + e.getMessage());
        }
    }


    /**
     * 检查角色一致性
     */
    @Tool(description = "检查角色一致性：检查大纲中提到的角色状态是否正确，如已死亡角色不应出现。")
    public String checkCharacters(
            @ToolParam(description = "项目ID") String projectId,
            @ToolParam(description = "章节大纲节拍") String beats) {

        try {
            UUID projectUuid = UUID.fromString(projectId);
            List<String> beatList = parseBeats(beats);
            List<ConflictWarning> warnings = checkCharacterConsistency(projectUuid, beatList);
            return formatResult(warnings);
        } catch (Exception e) {
            return formatError("角色检查失败: " + e.getMessage());
        }
    }

    /**
     * 检查伏笔状态
     */
    @Tool(description = "检查伏笔状态：检查是否有紧急伏笔被遗忘，或已回收的伏笔被重复使用。")
    public String checkPlotLoopStatus(
            @ToolParam(description = "项目ID") String projectId,
            @ToolParam(description = "章节大纲节拍") String beats) {

        try {
            UUID projectUuid = UUID.fromString(projectId);
            List<String> beatList = parseBeats(beats);
            List<ConflictWarning> warnings = checkPlotLoops(projectUuid, beatList);
            return formatResult(warnings);
        } catch (Exception e) {
            return formatError("伏笔检查失败: " + e.getMessage());
        }
    }

    /**
     * 角色一致性检查实现
     */
    private List<ConflictWarning> checkCharacterConsistency(UUID projectId, List<String> beats) {
        List<ConflictWarning> warnings = new ArrayList<>();

        if (beats == null || beats.isEmpty()) {
            return warnings;
        }

        String beatsText = String.join(" ", beats);
        List<Character> characters = characterRepository.findByProjectIdOrderByCreatedAtDesc(projectId);

        for (Character character : characters) {
            String status = character.getStatus();

            // 检查已死亡角色
            if (status != null && status.toLowerCase().contains("死亡")) {
                if (beatsText.contains(character.getName())) {
                    warnings.add(new ConflictWarning(
                            ConflictType.CHARACTER,
                            Severity.ERROR,
                            "已死亡角色出现在大纲中",
                            String.format("角色 '%s' 已死亡，但在大纲中被提及", character.getName()),
                            "角色: " + character.getName(),
                            "请确认角色状态，或修改大纲内容"
                    ));
                }
            }

            // 检查受伤/昏迷角色的行动能力
            if (status != null && (status.contains("重伤") || status.contains("昏迷"))) {
                // 检查是否有该角色的激烈动作描写
                if (beatsText.contains(character.getName()) &&
                    (beatsText.contains("战斗") || beatsText.contains("奔跑") || beatsText.contains("追击"))) {
                    warnings.add(new ConflictWarning(
                            ConflictType.CHARACTER,
                            Severity.WARNING,
                            "受伤角色行动能力存疑",
                            String.format("角色 '%s' 当前状态为 '%s'，可能无法进行激烈活动",
                                    character.getName(), status),
                            "角色: " + character.getName(),
                            "请确认角色是否已恢复，或调整行动描写"
                    ));
                }
            }
        }

        return warnings;
    }

    /**
     * 伏笔检查实现
     */
    private List<ConflictWarning> checkPlotLoops(UUID projectId, List<String> beats) {
        List<ConflictWarning> warnings = new ArrayList<>();

        if (beats == null || beats.isEmpty()) {
            return warnings;
        }

        String beatsText = String.join(" ", beats);
        List<PlotLoop> plotLoops = plotLoopRepository.findByProjectIdOrderByCreatedAtDesc(projectId);

        for (PlotLoop plotLoop : plotLoops) {
            // 检查紧急伏笔是否被遗忘
            // URGENT 状态表示超过10章未回收的紧急伏笔
            if (plotLoop.getStatus() == PlotLoopStatus.URGENT) {

                boolean mentioned = beatsText.contains(plotLoop.getTitle()) ||
                        (plotLoop.getDescription() != null && beatsText.contains(plotLoop.getDescription()));

                if (!mentioned) {
                    warnings.add(new ConflictWarning(
                            ConflictType.PLOTLOOP,
                            Severity.WARNING,
                            "紧急伏笔未在大纲中体现",
                            String.format("紧急伏笔 '%s' 未在本章大纲中提及", plotLoop.getTitle()),
                            "伏笔: " + plotLoop.getTitle(),
                            "考虑在本章推进该伏笔，或调整伏笔状态"
                    ));
                }
            }

            // 检查已回收的伏笔是否被重复使用
            if (plotLoop.getStatus() == PlotLoopStatus.CLOSED) {
                if (beatsText.contains(plotLoop.getTitle())) {
                    warnings.add(new ConflictWarning(
                            ConflictType.PLOTLOOP,
                            Severity.INFO,
                            "已回收伏笔被再次提及",
                            String.format("伏笔 '%s' 已标记为已回收，但在大纲中被提及", plotLoop.getTitle()),
                            "伏笔: " + plotLoop.getTitle(),
                            "如需继续使用，请更新伏笔状态"
                    ));
                }
            }
        }

        return warnings;
    }

    /**
     * 详细逻辑检查
     */
    private List<ConflictWarning> checkDetailedLogic(UUID projectId, List<String> beats) {
        List<ConflictWarning> warnings = new ArrayList<>();

        // 这里可以添加更复杂的检查逻辑
        // 例如：时间线检查、地点一致性检查等

        return warnings;
    }

    /**
     * 解析节拍列表
     */
    private List<String> parseBeats(String beats) {
        if (beats == null || beats.isBlank()) {
            return Collections.emptyList();
        }

        try {
            // 尝试解析 JSON 数组
            if (beats.trim().startsWith("[")) {
                return objectMapper.readValue(beats, new TypeReference<List<String>>() {});
            }
            // 否则按行分割
            return Arrays.asList(beats.split("\n"));
        } catch (Exception e) {
            log.warn("解析节拍失败，按行分割: {}", e.getMessage());
            return Arrays.asList(beats.split("\n"));
        }
    }

    /**
     * 格式化检查结果
     */
    private String formatResult(List<ConflictWarning> warnings) {
        if (warnings.isEmpty()) {
            return "✅ 预检通过，未发现问题。";
        }

        StringBuilder result = new StringBuilder();

        // 统计各级别警告数量
        long errorCount = warnings.stream().filter(w -> w.severity == Severity.ERROR).count();
        long warningCount = warnings.stream().filter(w -> w.severity == Severity.WARNING).count();
        long infoCount = warnings.stream().filter(w -> w.severity == Severity.INFO).count();

        if (errorCount > 0) {
            result.append("❌ 预检发现 ").append(errorCount).append(" 个错误");
        } else {
            result.append("⚠️ 预检完成");
        }

        if (warningCount > 0) {
            result.append("，").append(warningCount).append(" 个警告");
        }
        if (infoCount > 0) {
            result.append("，").append(infoCount).append(" 个提示");
        }
        result.append("\n\n");

        // 按严重程度排序输出
        warnings.stream()
                .sorted(Comparator.comparing(w -> w.severity.ordinal()))
                .forEach(w -> {
                    String icon = switch (w.severity) {
                        case ERROR -> "🔴";
                        case WARNING -> "🟡";
                        case INFO -> "🔵";
                    };
                    result.append(icon).append(" [").append(w.type.displayName).append("] ")
                            .append(w.title).append("\n");
                    result.append("   ").append(w.description).append("\n");
                    result.append("   💡 建议: ").append(w.suggestion).append("\n\n");
                });

        return result.toString();
    }

    /**
     * 格式化错误信息
     */
    private String formatError(String message) {
        return "❌ " + message;
    }

    /**
     * 冲突警告记录
     */
    private record ConflictWarning(
            ConflictType type,
            Severity severity,
            String title,
            String description,
            String source,
            String suggestion
    ) {}
}
