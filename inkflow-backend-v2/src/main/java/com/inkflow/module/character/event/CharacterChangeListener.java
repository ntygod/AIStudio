package com.inkflow.module.character.event;

import com.inkflow.module.consistency.service.ConsistencyWarningService;
import com.inkflow.module.consistency.service.ProactiveConsistencyService;
import com.inkflow.module.evolution.entity.EntityType;
import com.inkflow.module.evolution.service.EvolutionAnalysisService;
import com.inkflow.module.rag.entity.KnowledgeChunk;
import com.inkflow.module.rag.repository.KnowledgeChunkRepository;
import com.inkflow.module.rag.service.ParentChildSearchService;
import com.inkflow.module.character.entity.Character;
import com.inkflow.module.character.repository.CharacterRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * 角色变更事件监听器
 * 
 * 监听 CharacterChangedEvent 事件，触发：
 * 1. 主动式一致性检查（带防抖和限流）
 * 2. 演进快照创建
 * 3. RAG 索引更新
 * 
 * Requirements: 2.1, 2.2, 2.3, 2.4
 */
@Component
@RequiredArgsConstructor
public class CharacterChangeListener {

    private static final Logger log = LoggerFactory.getLogger(CharacterChangeListener.class);

    private final ProactiveConsistencyService consistencyService;
    private final EvolutionAnalysisService evolutionService;
    private final ConsistencyWarningService warningService;
    @Nullable
    private final ParentChildSearchService parentChildSearchService;
    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final CharacterRepository characterRepository;

    /**
     * 处理角色变更事件
     * 
     * 在事务提交后异步执行，避免阻塞主流程
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCharacterChanged(CharacterChangedEvent event) {
        log.info("收到角色变更事件: characterId={}, projectId={}, operation={}",
                event.getCharacterId(), event.getProjectId(), event.getOperation());

        switch (event.getOperation()) {
            case CREATE -> {
                // 创建演进快照
                createEvolutionSnapshot(event);
                // 触发 RAG 索引创建
                triggerRagIndexing(event);
            }
            case UPDATE -> {
                // 触发一致性检查
                triggerConsistencyCheck(event);
                // 创建演进快照
                createEvolutionSnapshot(event);
                // 更新 RAG 索引
                triggerRagIndexing(event);
            }
            case DELETE -> {
                // 清理关联数据
                cleanupAssociatedData(event);
            }
        }
    }

    /**
     * 触发一致性检查
     * Requirements: 2.2
     */
    private void triggerConsistencyCheck(CharacterChangedEvent event) {
        log.debug("触发角色一致性检查: projectId={}, characterId={}",
                event.getProjectId(), event.getCharacterId());
        try {
            consistencyService.triggerCheck(
                    event.getProjectId(),
                    event.getCharacterId(),
                    EntityType.CHARACTER,
                    event.getCharacterName()
            );
        } catch (Exception e) {
            log.error("角色一致性检查触发失败: characterId={}", event.getCharacterId(), e);
        }
    }

    /**
     * 创建演进快照
     * Requirements: 2.1, 2.3
     */
    private void createEvolutionSnapshot(CharacterChangedEvent event) {
        log.debug("创建角色演进快照: projectId={}, characterId={}",
                event.getProjectId(), event.getCharacterId());
        try {
            if (event.getCurrentState() != null) {
                evolutionService.createSnapshotForEntity(
                        event.getProjectId(),
                        event.getCharacterId(),
                        EntityType.CHARACTER,
                        event.getCurrentState()
                );
            }
        } catch (Exception e) {
            log.error("角色演进快照创建失败: characterId={}", event.getCharacterId(), e);
        }
    }

    /**
     * 触发 RAG 索引创建/更新
     */
    private void triggerRagIndexing(CharacterChangedEvent event) {
        if (parentChildSearchService == null) {
            return;
        }

        log.debug("触发角色 RAG 索引: characterId={}", event.getCharacterId());
        try {
            Character character = characterRepository.findById(event.getCharacterId()).orElse(null);
            if (character == null) {
                log.debug("Character not found, skipping RAG indexing: {}", event.getCharacterId());
                return;
            }

            // 构建索引内容
            String content = buildCharacterContent(character);
            if (content.isBlank()) {
                return;
            }

            // 构建元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("name", character.getName());
            metadata.put("role", character.getRole());
            if (character.getArchetype() != null) {
                metadata.put("archetype", character.getArchetype());
            }

            // 创建父子块索引
            parentChildSearchService.createParentChildIndex(
                    event.getProjectId(),
                    KnowledgeChunk.SOURCE_TYPE_CHARACTER,
                    event.getCharacterId(),
                    content,
                    metadata
            ).subscribe(
                    parent -> log.debug("Created parent-child index for Character: {}, parentId: {}",
                            event.getCharacterId(), parent.getId()),
                    error -> log.error("Failed to create parent-child index for Character: {}",
                            event.getCharacterId(), error)
            );
        } catch (Exception e) {
            log.error("Error triggering RAG indexing for Character: {}", event.getCharacterId(), e);
        }
    }

    /**
     * 构建角色内容用于 RAG 索引
     */
    private String buildCharacterContent(Character character) {
        StringBuilder sb = new StringBuilder();
        sb.append("角色名称: ").append(character.getName()).append("\n");
        
        if (character.getRole() != null) {
            sb.append("角色类型: ").append(character.getRole()).append("\n");
        }
        if (character.getDescription() != null && !character.getDescription().isBlank()) {
            sb.append("角色描述: ").append(character.getDescription()).append("\n");
        }
        if (character.getPersonality() != null && !character.getPersonality().isEmpty()) {
            sb.append("性格特点: ").append(character.getPersonality().toString()).append("\n");
        }
        if (character.getArchetype() != null) {
            sb.append("原型: ").append(character.getArchetype()).append("\n");
        }
        
        return sb.toString();
    }

    /**
     * 清理关联数据
     * Requirements: 2.4
     */
    private void cleanupAssociatedData(CharacterChangedEvent event) {
        log.debug("清理角色关联数据: characterId={}", event.getCharacterId());
        
        try {
            // 删除关联的一致性警告
            warningService.deleteWarningsByEntity(event.getCharacterId());
            log.debug("Deleted consistency warnings for Character: {}", event.getCharacterId());
        } catch (Exception e) {
            log.error("Failed to delete consistency warnings for Character: {}", event.getCharacterId(), e);
        }

        try {
            // 删除关联的 RAG 知识块
            int deletedCount = knowledgeChunkRepository.deleteBySourceId(event.getCharacterId());
            log.debug("Deleted {} knowledge chunks for Character: {}", deletedCount, event.getCharacterId());
        } catch (Exception e) {
            log.error("Failed to delete knowledge chunks for Character: {}", event.getCharacterId(), e);
        }

        // 注意: 演进快照的清理可以选择保留（用于历史追溯）或删除
        // 这里我们选择保留，因为删除的角色可能在历史章节中仍有意义
    }
}
