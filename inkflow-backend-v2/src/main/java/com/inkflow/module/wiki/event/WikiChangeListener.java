package com.inkflow.module.wiki.event;

import com.inkflow.module.consistency.service.ProactiveConsistencyService;
import com.inkflow.module.evolution.entity.EntityType;
import com.inkflow.module.rag.entity.KnowledgeChunk;
import com.inkflow.module.rag.repository.KnowledgeChunkRepository;
import com.inkflow.module.rag.service.EmbeddingService;
import com.inkflow.module.rag.service.ParentChildSearchService;
import com.inkflow.module.wiki.entity.WikiEntry;
import com.inkflow.module.wiki.repository.WikiEntryRepository;
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
 * Wiki变更事件监听器
 * 
 * 监听WikiEntryChangedEvent事件，触发：
 * 1. 异步embedding生成
 * 2. 主动式一致性检查（带防抖和限流）
 * 
 * Requirements: 9.1-9.9
 */
@Component
@RequiredArgsConstructor
public class WikiChangeListener {

    private static final Logger log = LoggerFactory.getLogger(WikiChangeListener.class);

    private final ProactiveConsistencyService consistencyService;
    private final EmbeddingService embeddingService;
    @Nullable
    private final ParentChildSearchService parentChildSearchService;
    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final WikiEntryRepository wikiEntryRepository;

    /**
     * 处理Wiki条目变更事件
     * 
     * 在事务提交后异步执行，避免阻塞主流程
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleWikiEntryChanged(WikiEntryChangedEvent event) {
        log.info("收到Wiki变更事件: entryId={}, projectId={}, changeType={}",
            event.getEntryId(), event.getProjectId(), event.getChangeType());

        switch (event.getChangeType()) {
            case "CREATED", "UPDATED" -> {
                // 触发embedding生成
                triggerEmbeddingGeneration(event);
                // 触发主动式一致性检查（带防抖和限流）
                triggerConsistencyCheck(event);
            }
            case "DELETED" -> {
                // 清理相关embedding
                cleanupEmbeddings(event);
            }
        }
    }

    /**
     * 触发embedding生成和父子块索引创建
     * 使用父子块策略为WikiEntry内容创建语义分块索引
     */
    private void triggerEmbeddingGeneration(WikiEntryChangedEvent event) {
        log.debug("触发embedding生成: entryId={}", event.getEntryId());
        
        // 使用父子块索引策略
        if (parentChildSearchService != null) {
            try {
                WikiEntry entry = wikiEntryRepository.findById(event.getEntryId()).orElse(null);
                if (entry == null || entry.getContent() == null || entry.getContent().isBlank()) {
                    log.debug("WikiEntry not found or content is empty, skipping indexing: {}", event.getEntryId());
                    return;
                }
                
                // 构建元数据
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("title", entry.getTitle());
                metadata.put("type", entry.getType());
                if (entry.getAliases() != null && entry.getAliases().length > 0) {
                    metadata.put("aliases", entry.getAliases());
                }
                if (entry.getTags() != null && entry.getTags().length > 0) {
                    metadata.put("tags", entry.getTags());
                }
                if (entry.getTimeVersion() != null) {
                    metadata.put("timeVersion", entry.getTimeVersion());
                }
                
                // 创建父子块索引
                parentChildSearchService.createParentChildIndex(
                        event.getProjectId(),
                        KnowledgeChunk.SOURCE_TYPE_WIKI_ENTRY,
                        event.getEntryId(),
                        entry.getContent(),
                        metadata
                ).subscribe(
                        parent -> log.debug("Created parent-child index for WikiEntry: {}, parentId: {}", 
                                event.getEntryId(), parent.getId()),
                        error -> log.error("Failed to create parent-child index for WikiEntry: {}", 
                                event.getEntryId(), error)
                );
            } catch (Exception e) {
                log.error("Error triggering parent-child indexing for WikiEntry: {}", event.getEntryId(), e);
            }
        }
    }

    /**
     * 触发主动式一致性检查
     * 使用 ProactiveConsistencyService 实现防抖和限流
     * Requirements: 9.1, 9.7
     */
    private void triggerConsistencyCheck(WikiEntryChangedEvent event) {
        log.debug("触发一致性检查: projectId={}, entryId={}", 
            event.getProjectId(), event.getEntryId());
        try {
            consistencyService.triggerCheck(
                event.getProjectId(),
                event.getEntryId(),
                EntityType.WIKI_ENTRY,
                "WikiEntry"
            );
        } catch (Exception e) {
            log.error("一致性检查触发失败: entryId={}", event.getEntryId(), e);
        }
    }

    /**
     * 清理相关embedding和父子块索引
     * 删除WikiEntry时清理所有相关的知识块（包括父块和子块）
     */
    private void cleanupEmbeddings(WikiEntryChangedEvent event) {
        log.debug("清理embedding和父子块索引: entryId={}", event.getEntryId());
        try {
            // 使用 knowledgeChunkRepository.deleteBySourceId 清理所有相关块（父块和子块）
            int deletedCount = knowledgeChunkRepository.deleteBySourceId(event.getEntryId());
            log.debug("Deleted {} knowledge chunks for WikiEntry: {}", deletedCount, event.getEntryId());
        } catch (Exception e) {
            log.error("知识块清理失败: entryId={}", event.getEntryId(), e);
        }
    }
}
