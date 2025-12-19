package com.inkflow.module.agent.workflow.impl;

import com.inkflow.module.agent.context.ContextBus;
import com.inkflow.module.agent.core.CapableAgent;
import com.inkflow.module.agent.core.Intent;
import com.inkflow.module.agent.dto.ChatRequest;
import com.inkflow.module.agent.impl.ConsistencyAgent;
import com.inkflow.module.agent.impl.WriterAgent;
import com.inkflow.module.agent.orchestration.AgentOrchestrator;
import com.inkflow.module.agent.skill.PromptInjector;
import com.inkflow.module.agent.workflow.*;
import com.inkflow.module.character.entity.Character;
import com.inkflow.module.character.repository.CharacterRepository;
import com.inkflow.module.evolution.dto.InconsistencyReport;
import com.inkflow.module.evolution.entity.EntityType;
import com.inkflow.module.evolution.service.PreflightService;
import com.inkflow.module.evolution.service.PreflightService.PreflightRequest;
import com.inkflow.module.evolution.service.PreflightService.PreflightResult;
import com.inkflow.module.evolution.service.StateRetrievalService;
import com.inkflow.module.rag.dto.SearchResult;
import com.inkflow.module.rag.service.HybridSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;

/**
 * 内容生成工作流
 * 
 * 执行流程：
 * 1. 预检：检查角色状态、场景逻辑、时间线一致性
 * 2. 并行预处理：RAG检索 + 角色状态 + 风格样本
 * 3. Skill 注入：ActionSkill, PsychologySkill, DescriptionSkill
 * 4. WriterAgent 执行
 * 5. 同步一致性检查（在 done 之前）
 */
@Slf4j
@Component
public class ContentGenerationWorkflow extends AbstractWorkflow {
    
    private final WriterAgent writerAgent;
    private final ConsistencyAgent consistencyAgent;
    private final HybridSearchService hybridSearchService;
    private final StateRetrievalService stateRetrievalService;
    private final PreflightService preflightService;
    private final CharacterRepository characterRepository;
    
    public ContentGenerationWorkflow(
            AgentOrchestrator orchestrator,
            PromptInjector promptInjector,
            ContextBus contextBus,
            WriterAgent writerAgent,
            ConsistencyAgent consistencyAgent,
            HybridSearchService hybridSearchService,
            StateRetrievalService stateRetrievalService,
            PreflightService preflightService,
            CharacterRepository characterRepository) {
        super(orchestrator, promptInjector, contextBus);
        this.writerAgent = writerAgent;
        this.consistencyAgent = consistencyAgent;
        this.hybridSearchService = hybridSearchService;
        this.stateRetrievalService = stateRetrievalService;
        this.preflightService = preflightService;
        this.characterRepository = characterRepository;
    }
    
    @Override
    public String getName() {
        return "内容生成";
    }
    
    @Override
    public List<Intent> getSupportedIntents() {
        return List.of(Intent.WRITE_CONTENT);
    }
    
    @Override
    public WorkflowType getType() {
        return WorkflowType.CONTENT_GENERATION;
    }
    
    @Override
    protected CapableAgent<ChatRequest, String> getMainAgent(ChatRequest request) {
        return writerAgent;
    }
    
    @Override
    protected boolean needsSkillInjection() {
        return true;
    }
    
    /**
     * 并行预处理
     * 使用 subscribeOn(Schedulers.boundedElastic()) 避免阻塞 Netty IO 线程
     */
    @Override
    protected Mono<PreprocessingContext> preprocess(ChatRequest request) {
        UUID projectId = request.projectId();
        String query = request.message();
        
        // 检查是否启用一致性检查（默认启用）
        boolean consistencyEnabled = isConsistencyEnabled(request);
        
        // 1. 预检（如果启用）
        Mono<PreflightResult> preflightMono;
        if (consistencyEnabled) {
            publishThought(request.sessionId(), "执行预检: 检查角色状态和场景逻辑...");
            preflightMono = Mono.fromCallable(() -> performPreflight(request))
                    .subscribeOn(Schedulers.boundedElastic())
                    .onErrorResume(e -> {
                        log.warn("[ContentGenerationWorkflow] 预检失败: {}", e.getMessage());
                        return Mono.just(new PreflightResult(true, Collections.emptyList()));
                    });
        } else {
            preflightMono = Mono.just(new PreflightResult(true, Collections.emptyList()));
        }
        
        // 2. 并行预处理
        publishThought(request.sessionId(), "并行预处理: RAG检索 + 角色状态 + 风格样本");
        
        return preflightMono.flatMap(preflightResult -> {
            // 发送预检结果 SSE 事件
            if (consistencyEnabled && !preflightResult.issues().isEmpty()) {
                publishPreflightResult(request.sessionId(), preflightResult);
            }
            
            // 如果预检失败且有严重错误，可以选择中止（这里选择继续但带警告）
            if (!preflightResult.canProceed()) {
                publishThought(request.sessionId(), "⚠️ 预检发现严重问题，建议检查后再继续");
            }
            
            // 并行执行三个预处理任务
            return Mono.zip(
                // Task 1: RAG 检索
                hybridSearchService.search(projectId, query, 5)
                    .onErrorResume(e -> {
                        log.warn("[ContentGenerationWorkflow] RAG检索失败: {}", e.getMessage());
                        return Mono.just(Collections.emptyList());
                    }),
                
                // Task 2: 角色状态获取（包装为 Mono）
                Mono.fromCallable(() -> getCharacterStates(projectId))
                    .subscribeOn(Schedulers.boundedElastic())
                    .onErrorResume(e -> {
                        log.warn("[ContentGenerationWorkflow] 角色状态获取失败: {}", e.getMessage());
                        return Mono.just(Collections.<UUID, CharacterState>emptyMap());
                    }),
                
                // Task 3: 风格样本获取
                hybridSearchService.buildContextForGeneration(projectId, query, 3)
                    .onErrorResume(e -> {
                        log.warn("[ContentGenerationWorkflow] 风格样本获取失败: {}", e.getMessage());
                        return Mono.just("");
                    })
            ).map(tuple -> {
                List<SearchResult> ragResults = tuple.getT1();
                Map<UUID, CharacterState> characterStates = tuple.getT2();
                String styleContext = tuple.getT3();
                
                log.debug("[ContentGenerationWorkflow] 预处理完成: RAG={}, 角色={}, 风格长度={}",
                    ragResults.size(), characterStates.size(), styleContext.length());
                
                // 将预检结果存入额外数据
                Map<String, Object> extraData = new HashMap<>();
                extraData.put("preflightResult", preflightResult);
                extraData.put("consistencyEnabled", consistencyEnabled);
                
                return new PreprocessingContext(
                    ragResults,
                    characterStates,
                    styleContext,
                    extraData
                );
            });
        }).subscribeOn(Schedulers.boundedElastic()); // 确保整个预处理在弹性线程池执行
    }
    
    /**
     * 执行预检
     */
    private PreflightResult performPreflight(ChatRequest request) {
        // 从请求中提取预检所需信息
        List<UUID> characterIds = extractCharacterIds(request);
        Integer chapterOrder = extractChapterOrder(request);
        String sceneDescription = request.message();
        String sceneLocation = extractSceneLocation(request);
        
        PreflightRequest preflightRequest = new PreflightRequest(
                request.projectId(),
                chapterOrder != null ? chapterOrder : 1,
                characterIds,
                sceneDescription,
                sceneLocation,
                null, // sceneTime
                null  // previousSceneTime
        );
        
        return preflightService.preflight(preflightRequest);
    }
    
    /**
     * 检查是否启用一致性检查
     */
    private boolean isConsistencyEnabled(ChatRequest request) {
        if (request.metadata() == null) {
            return true; // 默认启用
        }
        Object disabled = request.metadata().get("consistencyDisabled");
        if (disabled instanceof Boolean) {
            return !(Boolean) disabled;
        }
        return true;
    }
    
    /**
     * 从请求中提取角色ID列表
     */
    @SuppressWarnings("unchecked")
    private List<UUID> extractCharacterIds(ChatRequest request) {
        if (request.metadata() == null) {
            return Collections.emptyList();
        }
        Object characterIds = request.metadata().get("characterIds");
        if (characterIds instanceof List) {
            return ((List<?>) characterIds).stream()
                    .map(id -> {
                        if (id instanceof UUID) return (UUID) id;
                        if (id instanceof String) return UUID.fromString((String) id);
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .toList();
        }
        return Collections.emptyList();
    }
    
    /**
     * 从请求中提取章节顺序
     */
    private Integer extractChapterOrder(ChatRequest request) {
        if (request.metadata() == null) {
            return null;
        }
        Object chapterOrder = request.metadata().get("chapterOrder");
        if (chapterOrder instanceof Integer) {
            return (Integer) chapterOrder;
        }
        if (chapterOrder instanceof Number) {
            return ((Number) chapterOrder).intValue();
        }
        return null;
    }
    
    /**
     * 从请求中提取场景位置
     */
    private String extractSceneLocation(ChatRequest request) {
        if (request.metadata() == null) {
            return null;
        }
        Object location = request.metadata().get("sceneLocation");
        return location instanceof String ? (String) location : null;
    }
    
    /**
     * 发布预检结果 SSE 事件
     */
    private void publishPreflightResult(String sessionId, PreflightResult result) {
        StringBuilder message = new StringBuilder();
        message.append("预检结果: ");
        if (result.canProceed()) {
            message.append("✓ 可以继续");
        } else {
            message.append("⚠️ 发现问题");
        }
        
        if (!result.issues().isEmpty()) {
            message.append("\n发现 ").append(result.issues().size()).append(" 个问题:");
            for (InconsistencyReport issue : result.issues()) {
                message.append("\n- [").append(issue.severity()).append("] ")
                       .append(issue.description());
            }
        }
        
        publishThought(sessionId, message.toString());
    }
    
    /**
     * 同步后处理：一致性检查
     * 在发送 done 之前完成，确保前端能收到检查结果
     *
     */
    @Override
    protected Mono<PostProcessingResult> postprocess(ChatRequest request, String generatedContent) {
        if (generatedContent == null || generatedContent.isBlank()) {
            return Mono.empty();
        }
        
        // 检查是否启用一致性检查
        if (!isConsistencyEnabled(request)) {
            log.debug("[ContentGenerationWorkflow] 一致性检查已禁用，跳过后检");
            return Mono.empty();
        }
        
        publishThought(request.sessionId(), "执行一致性检查...");
        
        return Mono.fromCallable(() -> {
            // 构建一致性检查请求
            ChatRequest checkRequest = new ChatRequest(
                generatedContent,
                request.projectId(),
                request.sessionId(),
                request.currentPhase(),
                Intent.CHECK_CONSISTENCY,
                Map.of("checkType", "post_generation")
            );
            
            // 同步执行一致性检查
            String checkResult = consistencyAgent.execute(checkRequest);
            
            // 解析检查结果，提取警告
            List<PostProcessingResult.ConsistencyWarning> warnings = parseWarnings(checkResult);
            
            log.debug("[ContentGenerationWorkflow] 一致性检查完成，发现 {} 个警告", warnings.size());
            
            // 发送一致性警告 SSE 事件
            if (!warnings.isEmpty()) {
                publishConsistencyWarnings(request.sessionId(), warnings);
            }
            
            return new PostProcessingResult("consistency_check", checkResult, warnings);
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> {
              log.error("[ContentGenerationWorkflow] 一致性检查失败: {}", e.getMessage(), e);
              return Mono.just(new PostProcessingResult(
                  "consistency_check",
                  "一致性检查失败: " + e.getMessage(),
                  List.of(PostProcessingResult.ConsistencyWarning.error("检查过程出错", ""))
              ));
          });
    }
    
    /**
     * 发布一致性警告 SSE 事件
     */
    private void publishConsistencyWarnings(String sessionId, List<PostProcessingResult.ConsistencyWarning> warnings) {
        StringBuilder message = new StringBuilder();
        message.append("⚠️ 一致性检查发现 ").append(warnings.size()).append(" 个问题:");
        for (PostProcessingResult.ConsistencyWarning warning : warnings) {
            message.append("\n- ").append(warning.message());
        }
        publishThought(sessionId, message.toString());
    }
    
    /**
     * 获取角色状态
     * 从 StateRetrievalService 获取项目中所有角色的最新状态
     * 
     * 实现逻辑：
     * 1. 从 CharacterRepository 获取项目的所有活跃角色
     * 2. 对每个角色调用 stateRetrievalService.getLatestState() 获取演进状态
     * 3. 合并角色基础信息和演进状态，转换为 CharacterState
     */
    private Map<UUID, CharacterState> getCharacterStates(UUID projectId) {
        Map<UUID, CharacterState> states = new HashMap<>();
        
        // 1. 获取项目的所有活跃角色
        List<Character> characters = characterRepository.findByProjectIdAndIsActiveTrue(projectId);
        
        if (characters.isEmpty()) {
            log.debug("[ContentGenerationWorkflow] 项目 {} 没有活跃角色", projectId);
            return states;
        }
        
        log.debug("[ContentGenerationWorkflow] 获取 {} 个角色的状态", characters.size());
        
        // 2. 对每个角色获取演进状态并转换为 CharacterState
        for (Character character : characters) {
            try {
                CharacterState characterState = buildCharacterState(character);
                states.put(character.getId(), characterState);
            } catch (Exception e) {
                log.warn("[ContentGenerationWorkflow] 获取角色 {} 状态失败: {}", 
                        character.getName(), e.getMessage());
                // 即使获取演进状态失败，也使用基础信息创建状态
                states.put(character.getId(), buildBasicCharacterState(character));
            }
        }
        
        return states;
    }
    
    /**
     * 构建角色状态（包含演进状态）
     */
    private CharacterState buildCharacterState(Character character) {
        // 尝试获取角色的最新演进状态
        Optional<Map<String, Object>> evolutionState = stateRetrievalService
                .getLatestState(EntityType.CHARACTER, character.getId());
        
        // 合并基础属性和演进状态
        Map<String, Object> attributes = new HashMap<>();
        
        // 添加基础属性
        attributes.put("role", character.getRole());
        attributes.put("archetype", character.getArchetype());
        attributes.put("status", character.getStatus());
        attributes.put("description", character.getDescription());
        
        // 添加性格特征
        if (character.getPersonality() != null && !character.getPersonality().isEmpty()) {
            attributes.put("personality", character.getPersonality());
        }
        
        // 添加关系信息
        if (character.getRelationships() != null && !character.getRelationships().isEmpty()) {
            attributes.put("relationshipCount", character.getRelationships().size());
            attributes.put("relationships", character.getRelationships());
        }
        
        // 构建当前状态描述
        String currentState;
        if (evolutionState.isPresent()) {
            // 使用演进状态中的信息
            Map<String, Object> evolvedData = evolutionState.get();
            attributes.put("evolutionData", evolvedData);
            currentState = buildStateDescription(character, evolvedData);
        } else {
            // 使用基础状态
            currentState = buildBasicStateDescription(character);
        }
        
        return CharacterState.of(
                character.getId(),
                character.getName(),
                currentState,
                attributes
        );
    }
    
    /**
     * 构建基础角色状态（无演进数据时使用）
     */
    private CharacterState buildBasicCharacterState(Character character) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("role", character.getRole());
        attributes.put("archetype", character.getArchetype());
        attributes.put("status", character.getStatus());
        
        return CharacterState.of(
                character.getId(),
                character.getName(),
                buildBasicStateDescription(character),
                attributes
        );
    }
    
    /**
     * 根据演进数据构建状态描述
     */
    private String buildStateDescription(Character character, Map<String, Object> evolvedData) {
        StringBuilder sb = new StringBuilder();
        sb.append(character.getName());
        
        // 添加角色类型
        if (character.getRole() != null) {
            sb.append(" (").append(translateRole(character.getRole())).append(")");
        }
        
        // 添加当前状态
        if (character.getStatus() != null && !"active".equals(character.getStatus())) {
            sb.append(" - ").append(translateStatus(character.getStatus()));
        }
        
        // 从演进数据中提取关键状态变化
        if (evolvedData.containsKey("currentMood")) {
            sb.append(", 当前情绪: ").append(evolvedData.get("currentMood"));
        }
        if (evolvedData.containsKey("currentLocation")) {
            sb.append(", 位置: ").append(evolvedData.get("currentLocation"));
        }
        if (evolvedData.containsKey("recentEvent")) {
            sb.append(", 近期事件: ").append(evolvedData.get("recentEvent"));
        }
        
        return sb.toString();
    }
    
    /**
     * 构建基础状态描述
     */
    private String buildBasicStateDescription(Character character) {
        StringBuilder sb = new StringBuilder();
        sb.append(character.getName());
        
        if (character.getRole() != null) {
            sb.append(" (").append(translateRole(character.getRole())).append(")");
        }
        
        if (character.getStatus() != null && !"active".equals(character.getStatus())) {
            sb.append(" - ").append(translateStatus(character.getStatus()));
        }
        
        return sb.toString();
    }
    
    /**
     * 翻译角色类型
     */
    private String translateRole(String role) {
        return switch (role.toLowerCase()) {
            case "protagonist" -> "主角";
            case "antagonist" -> "反派";
            case "supporting" -> "配角";
            case "minor" -> "龙套";
            default -> role;
        };
    }
    
    /**
     * 翻译角色状态
     */
    private String translateStatus(String status) {
        return switch (status.toLowerCase()) {
            case "active" -> "活跃";
            case "inactive" -> "不活跃";
            case "deceased" -> "已故";
            case "unknown" -> "未知";
            default -> status;
        };
    }
    
    /**
     * 解析一致性检查结果中的警告
     */
    private List<PostProcessingResult.ConsistencyWarning> parseWarnings(String checkResult) {
        List<PostProcessingResult.ConsistencyWarning> warnings = new ArrayList<>();
        
        if (checkResult == null || checkResult.isBlank()) {
            return warnings;
        }
        
        // 简单解析：查找包含 "warning" 或 "问题" 的行
        String[] lines = checkResult.split("\n");
        for (String line : lines) {
            String lowerLine = line.toLowerCase();
            if (lowerLine.contains("warning") || 
                lowerLine.contains("问题") || 
                lowerLine.contains("不一致") ||
                lowerLine.contains("矛盾")) {
                warnings.add(PostProcessingResult.ConsistencyWarning.warning(line.trim(), ""));
            }
        }
        
        return warnings;
    }
}
