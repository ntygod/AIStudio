package com.inkflow.module.rag.service;

import com.inkflow.module.content.entity.Chapter;
import com.inkflow.module.content.entity.StoryBlock;
import com.inkflow.module.content.repository.ChapterRepository;
import com.inkflow.module.content.repository.StoryBlockRepository;
import com.inkflow.module.rag.config.RagProperties;
import com.inkflow.module.rag.dto.SearchResult;
import com.inkflow.module.rag.entity.KnowledgeChunk;
import com.inkflow.module.rag.repository.KnowledgeChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 父子块检索服务
 * 实现"小块检索，大块返回"策略
 * 
 * 核心功能：
 * 1. 在子块中搜索以获得精确匹配
 * 2. 检索对应的父块 StoryBlock
 * 3. 按父块去重，保留最高分子块
 * 4. 当父块超过上下文窗口时提取相关窗口
 * 5. 按章节顺序和块顺序排序构建上下文
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParentChildSearchService {

    private final EmbeddingService embeddingService;
    private final SemanticChunkingService semanticChunkingService;
    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final StoryBlockRepository storyBlockRepository;
    private final ChapterRepository chapterRepository;
    private final RagProperties ragProperties;

    private static final int DEFAULT_CHILD_LIMIT = 20;
    private static final int DEFAULT_PARENT_LIMIT = 5;

    // ==================== 索引构建 ====================

    /**
     * 为内容创建父子块索引
     *
     * @param projectId 项目ID
     * @param sourceType 来源类型
     * @param sourceId 来源ID
     * @param content 完整内容
     * @param metadata 元数据
     * @return 创建的父块
     */
    @Transactional
    public Mono<KnowledgeChunk> createParentChildIndex(
            UUID projectId,
            String sourceType,
            UUID sourceId,
            String content,
            Map<String, Object> metadata) {

        if (content == null || content.isBlank()) {
            return Mono.empty();
        }

        // 1. 删除旧的索引（确保更新时替换旧块）
        int deletedCount = knowledgeChunkRepository.deleteBySourceId(sourceId);
        if (deletedCount > 0) {
            log.debug("删除旧索引: sourceId={}, deletedCount={}", sourceId, deletedCount);
        }

        // 2. 创建父块 (存储完整内容，不生成embedding)
        KnowledgeChunk parentChunk = KnowledgeChunk.builder()
                .projectId(projectId)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .content(content)
                .chunkLevel(KnowledgeChunk.CHUNK_LEVEL_PARENT)
                .metadata(metadata != null ? metadata : new HashMap<>())
                .build();

        KnowledgeChunk savedParent = knowledgeChunkRepository.save(parentChunk);
        log.debug("创建父块: id={}, sourceId={}", savedParent.getId(), sourceId);

        // 3. 语义分块并创建子块
        return semanticChunkingService.chunkText(content)
                .flatMap(chunks -> createChildChunks(savedParent, chunks))
                .thenReturn(savedParent);
    }


    /**
     * 创建子块
     */
    private Mono<Void> createChildChunks(KnowledgeChunk parent, List<String> chunks) {
        if (chunks.isEmpty()) {
            return Mono.empty();
        }

        // 批量生成embedding
        return embeddingService.generateEmbeddingsBatch(chunks)
                .flatMap(embeddings -> {
                    List<KnowledgeChunk> childChunks = new ArrayList<>();

                    for (int i = 0; i < chunks.size(); i++) {
                        KnowledgeChunk child = KnowledgeChunk.builder()
                                .projectId(parent.getProjectId())
                                .sourceType(parent.getSourceType())
                                .sourceId(parent.getSourceId())
                                .parentId(parent.getId())
                                .content(chunks.get(i))
                                .embedding(embeddings.get(i))
                                .chunkLevel(KnowledgeChunk.CHUNK_LEVEL_CHILD)
                                .chunkOrder(i + 1)
                                .metadata(parent.getMetadata())
                                .build();
                        childChunks.add(child);
                    }

                    knowledgeChunkRepository.saveAll(childChunks);
                    log.debug("创建 {} 个子块，父块ID: {}", childChunks.size(), parent.getId());
                    return Mono.empty();
                });
    }

    // ==================== 检索服务 ====================

    /**
     * 父子块检索
     * 在子块中搜索，返回对应的父块内容
     * 实现"小块检索，大块返回"策略：
     * 1. 在子块中搜索以获得精确匹配
     * 2. 检索对应的父块 StoryBlock
     * 3. 按父块去重，保留最高分子块
     *
     * @param projectId 项目ID
     * @param query 查询文本
     * @param limit 返回的父块数量
     * @return 父块搜索结果
     */
    public Mono<List<SearchResult>> search(UUID projectId, String query, Integer limit) {
        int parentLimit = limit != null ? limit : DEFAULT_PARENT_LIMIT;
        int childLimit = parentLimit * ragProperties.hybridSearch().recallMultiplier();

        return embeddingService.generateEmbedding(query)
                .publishOn(Schedulers.boundedElastic())
                .map(queryEmbedding -> {
                    String vectorString = toVectorString(queryEmbedding);

                    // 1. 在子块中搜索 (Requirements 6.1)
                    List<KnowledgeChunk> childResults = knowledgeChunkRepository
                            .findSimilarChildChunks(projectId, vectorString, childLimit);

                    if (childResults.isEmpty()) {
                        log.debug("No child chunks found for query in project {}", projectId);
                        return Collections.<SearchResult>emptyList();
                    }

                    // 2. 按父块去重，保留最高分子块
                    Map<UUID, ChildChunkWithScore> bestChildByParent = deduplicateByParent(childResults, queryEmbedding);

                    if (bestChildByParent.isEmpty()) {
                        return Collections.<SearchResult>emptyList();
                    }

                    // 3. 获取父块内容
                    List<KnowledgeChunk> parents = knowledgeChunkRepository.findAllById(bestChildByParent.keySet());
                    Map<UUID, KnowledgeChunk> parentMap = parents.stream()
                            .collect(Collectors.toMap(KnowledgeChunk::getId, p -> p));

                    // 4. 构建结果，按相似度排序
                    List<SearchResult> results = bestChildByParent.entrySet().stream()
                            .filter(entry -> parentMap.containsKey(entry.getKey()))
                            .sorted((a, b) -> Double.compare(b.getValue().score(), a.getValue().score()))
                            .limit(parentLimit)
                            .map(entry -> {
                                KnowledgeChunk parent = parentMap.get(entry.getKey());
                                ChildChunkWithScore childInfo = entry.getValue();
                                
                                return SearchResult.builder()
                                        .id(parent.getId())
                                        .sourceType(parent.getSourceType())
                                        .sourceId(parent.getSourceId())
                                        .content(parent.getContent())
                                        .similarity(childInfo.score())
                                        .metadata(parent.getMetadata())
                                        .chunkLevel(parent.getChunkLevel())
                                        .parentId(parent.getParentId())
                                        .build();
                            })
                            .collect(Collectors.toList());

                    return results;
                })
                .doOnSuccess(results -> log.debug("父子块检索返回 {} 个结果", results.size()));
    }

    /**
     * 按父块去重，保留最高分子块
     *
     * @param childResults 子块检索结果
     * @param queryEmbedding 查询向量
     * @return 每个父块对应的最高分子块信息
     */
    private Map<UUID, ChildChunkWithScore> deduplicateByParent(
            List<KnowledgeChunk> childResults, 
            float[] queryEmbedding) {
        
        Map<UUID, ChildChunkWithScore> bestChildByParent = new LinkedHashMap<>();
        
        for (KnowledgeChunk child : childResults) {
            UUID parentId = child.getParentId();
            if (parentId == null) {
                continue;
            }
            
            double score = calculateSimilarity(queryEmbedding, child.getEmbedding());
            
            ChildChunkWithScore existing = bestChildByParent.get(parentId);
            if (existing == null || score > existing.score()) {
                bestChildByParent.put(parentId, new ChildChunkWithScore(child, score));
            }
        }
        
        return bestChildByParent;
    }

    /**
     * 子块与得分的记录类
     */
    private record ChildChunkWithScore(KnowledgeChunk chunk, double score) {}

    /**
     * 按来源类型过滤的父子块检索
     * 
     * @param projectId 项目ID
     * @param sourceType 来源类型
     * @param query 查询文本
     * @param limit 返回结果数量
     * @return 搜索结果列表
     */
    public Mono<List<SearchResult>> searchBySourceType(
            UUID projectId,
            String sourceType,
            String query,
            Integer limit) {

        int parentLimit = limit != null ? limit : DEFAULT_PARENT_LIMIT;
        int childLimit = parentLimit * ragProperties.hybridSearch().recallMultiplier();

        return embeddingService.generateEmbedding(query)
                .publishOn(Schedulers.boundedElastic())
                .map(queryEmbedding -> {
                    String vectorString = toVectorString(queryEmbedding);

                    // 在指定类型的子块中搜索
                    List<KnowledgeChunk> childResults = knowledgeChunkRepository
                            .findSimilarByProjectIdAndSourceType(projectId, sourceType, vectorString, childLimit);

                    // 过滤出子块
                    List<KnowledgeChunk> children = childResults.stream()
                            .filter(KnowledgeChunk::isChildChunk)
                            .toList();

                    if (children.isEmpty()) {
                        // 如果没有子块，直接返回父块结果
                        return childResults.stream()
                                .filter(KnowledgeChunk::isParentChunk)
                                .limit(parentLimit)
                                .map(this::toSearchResult)
                                .collect(Collectors.toList());
                    }

                    // 按父块去重并获取结果
                    Map<UUID, ChildChunkWithScore> bestChildByParent = deduplicateByParent(children, queryEmbedding);
                    
                    List<KnowledgeChunk> parents = knowledgeChunkRepository.findAllById(bestChildByParent.keySet());
                    Map<UUID, KnowledgeChunk> parentMap = parents.stream()
                            .collect(Collectors.toMap(KnowledgeChunk::getId, p -> p));
                    
                    return bestChildByParent.entrySet().stream()
                            .filter(entry -> parentMap.containsKey(entry.getKey()))
                            .sorted((a, b) -> Double.compare(b.getValue().score(), a.getValue().score()))
                            .limit(parentLimit)
                            .map(entry -> {
                                KnowledgeChunk parent = parentMap.get(entry.getKey());
                                return SearchResult.builder()
                                        .id(parent.getId())
                                        .sourceType(parent.getSourceType())
                                        .sourceId(parent.getSourceId())
                                        .content(parent.getContent())
                                        .similarity(entry.getValue().score())
                                        .metadata(parent.getMetadata())
                                        .chunkLevel(parent.getChunkLevel())
                                        .build();
                            })
                            .collect(Collectors.toList());
                });
    }

    /**
     * 构建AI生成上下文
     * 使用父子块策略检索相关内容
     * 实现：
     * - 按章节顺序和块顺序排序
     * - 按来源类型分组格式化
     * - 当父块超过上下文窗口时提取相关窗口
     *
     * @param projectId 项目ID
     * @param query 查询文本
     * @param limit 最大结果数
     * @return 格式化的上下文字符串
     */
    public Mono<String> buildContextForGeneration(UUID projectId, String query, int limit) {
        int parentLimit = limit > 0 ? limit : DEFAULT_PARENT_LIMIT;
        int childLimit = parentLimit * ragProperties.hybridSearch().recallMultiplier();
        
        return embeddingService.generateEmbedding(query)
                .publishOn(Schedulers.boundedElastic())
                .map(queryEmbedding -> {
                    String vectorString = toVectorString(queryEmbedding);

                    // 1. 在子块中搜索
                    List<KnowledgeChunk> childResults = knowledgeChunkRepository
                            .findSimilarChildChunks(projectId, vectorString, childLimit);

                    if (childResults.isEmpty()) {
                        return "";
                    }

                    // 2. 按父块去重
                    Map<UUID, ChildChunkWithScore> bestChildByParent = deduplicateByParent(childResults, queryEmbedding);

                    if (bestChildByParent.isEmpty()) {
                        return "";
                    }

                    // 3. 获取父块内容
                    List<KnowledgeChunk> parents = knowledgeChunkRepository.findAllById(bestChildByParent.keySet());
                    Map<UUID, KnowledgeChunk> parentMap = parents.stream()
                            .collect(Collectors.toMap(KnowledgeChunk::getId, p -> p));

                    // 4. 构建带排序信息的结果
                    List<SearchResult> results = buildResultsWithSortInfo(
                            bestChildByParent, parentMap, parentLimit, queryEmbedding);

                    // 5. 按章节顺序和块顺序排序 (Requirements 6.5)
                    results = sortByChapterAndBlockOrder(results);

                    // 6. 格式化上下文，按来源类型分组
                    return formatContextWithSourceTypeGrouping(results, bestChildByParent);
                });
    }

    /**
     * 构建带排序信息的搜索结果
     */
    private List<SearchResult> buildResultsWithSortInfo(
            Map<UUID, ChildChunkWithScore> bestChildByParent,
            Map<UUID, KnowledgeChunk> parentMap,
            int limit,
            float[] queryEmbedding) {
        
        // 收集所有 sourceId 以批量查询章节信息
        Set<UUID> sourceIds = parentMap.values().stream()
                .map(KnowledgeChunk::getSourceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        // 批量查询 StoryBlock 以获取章节信息
        Map<UUID, StoryBlock> storyBlockMap = new HashMap<>();
        Map<UUID, Chapter> chapterMap = new HashMap<>();
        
        if (!sourceIds.isEmpty()) {
            List<StoryBlock> storyBlocks = storyBlockRepository.findAllById(sourceIds);
            storyBlockMap = storyBlocks.stream()
                    .collect(Collectors.toMap(StoryBlock::getId, sb -> sb));
            
            // 获取章节信息
            Set<UUID> chapterIds = storyBlocks.stream()
                    .map(StoryBlock::getChapterId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            
            if (!chapterIds.isEmpty()) {
                List<Chapter> chapters = chapterRepository.findAllById(chapterIds);
                chapterMap = chapters.stream()
                        .collect(Collectors.toMap(Chapter::getId, c -> c));
            }
        }
        
        final Map<UUID, StoryBlock> finalStoryBlockMap = storyBlockMap;
        final Map<UUID, Chapter> finalChapterMap = chapterMap;
        
        return bestChildByParent.entrySet().stream()
                .filter(entry -> parentMap.containsKey(entry.getKey()))
                .sorted((a, b) -> Double.compare(b.getValue().score(), a.getValue().score()))
                .limit(limit)
                .map(entry -> {
                    KnowledgeChunk parent = parentMap.get(entry.getKey());
                    ChildChunkWithScore childInfo = entry.getValue();
                    
                    // 获取排序信息
                    Integer chapterOrder = null;
                    Integer blockOrder = null;
                    
                    StoryBlock storyBlock = finalStoryBlockMap.get(parent.getSourceId());
                    if (storyBlock != null) {
                        Chapter chapter = finalChapterMap.get(storyBlock.getChapterId());
                        if (chapter != null) {
                            chapterOrder = chapter.getOrderIndex();
                        }
                        // 使用 rank 字符串的哈希值作为块顺序（简化处理）
                        blockOrder = storyBlock.getRank() != null ? storyBlock.getRank().hashCode() : 0;
                    }
                    
                    return SearchResult.builder()
                            .id(parent.getId())
                            .sourceType(parent.getSourceType())
                            .sourceId(parent.getSourceId())
                            .content(parent.getContent())
                            .similarity(childInfo.score())
                            .metadata(parent.getMetadata())
                            .chunkLevel(parent.getChunkLevel())
                            .parentId(parent.getParentId())
                            .chapterOrder(chapterOrder)
                            .blockOrder(blockOrder)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 按章节顺序和块顺序排序
     */
    private List<SearchResult> sortByChapterAndBlockOrder(List<SearchResult> results) {
        return results.stream()
                .sorted(Comparator
                        .comparing(SearchResult::getChapterOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(SearchResult::getBlockOrder, Comparator.nullsLast(Integer::compareTo)))
                .collect(Collectors.toList());
    }

    /**
     * 按来源类型分组格式化上下文
     */
    private String formatContextWithSourceTypeGrouping(
            List<SearchResult> results,
            Map<UUID, ChildChunkWithScore> bestChildByParent) {
        
        if (results.isEmpty()) {
            return "";
        }
        
        // 按来源类型分组
        Map<String, List<SearchResult>> groupedBySourceType = results.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getSourceType() != null ? r.getSourceType() : "unknown",
                        LinkedHashMap::new,
                        Collectors.toList()));
        
        StringBuilder context = new StringBuilder();
        int contextWindowSize = ragProperties.chunking().contextWindowSize();
        
        for (Map.Entry<String, List<SearchResult>> group : groupedBySourceType.entrySet()) {
            String sourceType = group.getKey();
            List<SearchResult> groupResults = group.getValue();
            
            // 添加来源类型标题
            context.append("=== ").append(getSourceTypeDisplayName(sourceType)).append(" ===\n\n");
            
            for (SearchResult result : groupResults) {
                String title = result.getMetadata() != null
                        ? result.getMetadata().getOrDefault("title", "").toString()
                        : "";

                if (!title.isEmpty()) {
                    context.append("【").append(title).append("】");
                    if (result.getChapterOrder() != null) {
                        context.append(" (章节 ").append(result.getChapterOrder()).append(")");
                    }
                    context.append("\n");
                }
                
                // 提取上下文窗口
                String content = result.getContent();
                if (content != null && content.length() > contextWindowSize) {
                    ChildChunkWithScore childInfo = bestChildByParent.get(result.getId());
                    if (childInfo != null && childInfo.chunk().getContent() != null) {
                        content = extractContextWindow(content, childInfo.chunk().getContent(), contextWindowSize);
                        context.append("[相关片段] ");
                    } else {
                        content = content.substring(0, contextWindowSize) + "...";
                    }
                }
                
                context.append(content).append("\n\n");
            }
        }

        return context.toString().trim();
    }

    /**
     * 提取上下文窗口
     *
     * @param parentContent 父块完整内容
     * @param matchedChildContent 匹配的子块内容
     * @param windowSize 窗口大小
     * @return 提取的上下文窗口
     */
    public String extractContextWindow(String parentContent, String matchedChildContent, int windowSize) {
        if (parentContent == null || matchedChildContent == null) {
            return parentContent;
        }
        
        // 查找匹配子块在父块中的位置
        int matchPosition = parentContent.indexOf(matchedChildContent);
        if (matchPosition == -1) {
            // 如果找不到精确匹配，返回前面部分
            return parentContent.length() > windowSize 
                ? parentContent.substring(0, windowSize) + "..."
                : parentContent;
        }
        
        // 计算上下文窗口的起始和结束位置
        int overlapSize = ragProperties.chunking().contextOverlapSize();
        int start = Math.max(0, matchPosition - overlapSize);
        int end = Math.min(parentContent.length(), 
                          matchPosition + matchedChildContent.length() + overlapSize);
        
        // 确保不超过窗口大小限制
        if (end - start > windowSize) {
            // 优先保留匹配的子块
            int matchEnd = matchPosition + matchedChildContent.length();
            start = Math.max(0, matchEnd - windowSize);
            end = Math.min(parentContent.length(), start + windowSize);
        }
        
        String extracted = parentContent.substring(start, end);
        
        // 添加省略号提示
        if (start > 0) {
            extracted = "..." + extracted;
        }
        if (end < parentContent.length()) {
            extracted = extracted + "...";
        }
        
        return extracted;
    }

    /**
     * 获取来源类型的显示名称
     */
    private String getSourceTypeDisplayName(String sourceType) {
        return switch (sourceType) {
            case KnowledgeChunk.SOURCE_TYPE_STORY_BLOCK -> "剧情内容";
            case KnowledgeChunk.SOURCE_TYPE_WIKI_ENTRY -> "世界设定";
            case KnowledgeChunk.SOURCE_TYPE_CHARACTER -> "角色设定";
            case KnowledgeChunk.SOURCE_TYPE_CHAPTER_SUMMARY -> "章节摘要";
            default -> sourceType;
        };
    }

    // ==================== 辅助方法 ====================

    private String toVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private double calculateSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private SearchResult toSearchResult(KnowledgeChunk chunk) {
        return SearchResult.builder()
                .id(chunk.getId())
                .sourceType(chunk.getSourceType())
                .sourceId(chunk.getSourceId())
                .content(chunk.getContent())
                .metadata(chunk.getMetadata())
                .chunkLevel(chunk.getChunkLevel())
                .build();
    }
}
