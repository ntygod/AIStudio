package com.inkflow.module.extraction.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.module.ai_bridge.chat.DynamicChatModelFactory;
import com.inkflow.module.extraction.dto.*;
import com.inkflow.module.extraction.dto.ExtractedEntity.EntityCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * 内容提取服务
 * 从正文反推角色和设定
 */
@Service
public class ContentExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ContentExtractionService.class);

    private final DynamicChatModelFactory chatModelFactory;
    private final ObjectMapper objectMapper;

    public ContentExtractionService(
            DynamicChatModelFactory chatModelFactory,
            ObjectMapper objectMapper) {
        this.chatModelFactory = chatModelFactory;
        this.objectMapper = objectMapper;
    }

    /**
     * 从章节内容提取实体和关系
     */
    public ExtractionResult extractFromContent(UUID projectId, UUID chapterId, String content) {
        try {
            var executor = Executors.newVirtualThreadPerTaskExecutor();

            // 并行提取实体和关系
            var entitiesFuture = CompletableFuture.supplyAsync(
                    () -> extractEntities(content), executor);
            var relationshipsFuture = CompletableFuture.supplyAsync(
                    () -> extractRelationships(content), executor);

            CompletableFuture.allOf(entitiesFuture, relationshipsFuture).join();

            List<ExtractedEntity> entities = entitiesFuture.join();
            List<ExtractedRelationship> relationships = relationshipsFuture.join();

            return ExtractionResult.success(projectId, chapterId, entities, relationships);

        } catch (Exception e) {
            log.error("Content extraction failed", e);
            return ExtractionResult.failed(projectId, chapterId, e.getMessage());
        }
    }

    /**
     * 提取实体
     */
    private List<ExtractedEntity> extractEntities(String content) {
        String prompt = buildEntityExtractionPrompt(content);

        try {
            ChatModel model = chatModelFactory.getDefaultModel();
            ChatClient chatClient = ChatClient.builder(model).build();
            String response = chatClient.prompt()
                    .system("""
                        你是一个专业的小说内容分析助手，负责从正文中提取角色、地点、物品等实体。
                        
                        请以JSON数组格式返回提取的实体，每个实体包含：
                        - name: 实体名称
                        - category: 类别 (CHARACTER/LOCATION/ITEM/ORGANIZATION/EVENT/CONCEPT)
                        - attributes: 属性对象（如性格、外貌、功能等）
                        - aliases: 别名数组
                        - sourceText: 提取依据的原文片段
                        - confidence: 置信度 (0.0-1.0)
                        
                        如果没有发现实体，返回空数组 []
                        """)
                    .user(prompt)
                    .call()
                    .content();

            return parseEntityResponse(response);

        } catch (Exception e) {
            log.error("Entity extraction failed", e);
            return Collections.emptyList();
        }
    }

    /**
     * 提取关系
     */
    private List<ExtractedRelationship> extractRelationships(String content) {
        String prompt = buildRelationshipExtractionPrompt(content);

        try {
            ChatModel model = chatModelFactory.getDefaultModel();
            ChatClient chatClient = ChatClient.builder(model).build();
            String response = chatClient.prompt()
                    .system("""
                        你是一个专业的小说内容分析助手，负责从正文中提取角色之间的关系。
                        
                        请以JSON数组格式返回提取的关系，每个关系包含：
                        - sourceEntity: 关系主体
                        - targetEntity: 关系客体
                        - relationshipType: 关系类型（如师徒、恋人、敌对、同门等）
                        - description: 关系描述
                        - sourceText: 提取依据的原文片段
                        - confidence: 置信度 (0.0-1.0)
                        
                        如果没有发现关系，返回空数组 []
                        """)
                    .user(prompt)
                    .call()
                    .content();

            return parseRelationshipResponse(response);

        } catch (Exception e) {
            log.error("Relationship extraction failed", e);
            return Collections.emptyList();
        }
    }

    /**
     * 构建实体提取提示词
     */
    private String buildEntityExtractionPrompt(String content) {
        return String.format("""
            ## 提取任务
            从以下小说正文中提取所有实体（角色、地点、物品、组织等）。
            
            ## 正文内容
            %s
            
            ## 要求
            1. 识别所有出现的角色，提取其名称、外貌、性格等属性
            2. 识别重要地点和场景
            3. 识别关键物品（武器、法宝等）
            4. 识别组织和势力
            5. 为每个实体标注置信度
            """,
                content.length() > 5000 ? content.substring(0, 5000) + "..." : content
        );
    }

    /**
     * 构建关系提取提示词
     */
    private String buildRelationshipExtractionPrompt(String content) {
        return String.format("""
            ## 提取任务
            从以下小说正文中提取角色之间的关系。
            
            ## 正文内容
            %s
            
            ## 要求
            1. 识别角色之间的各种关系（亲属、师徒、朋友、敌对等）
            2. 提取关系的具体描述
            3. 标注关系的置信度
            4. 引用支持该关系的原文片段
            """,
                content.length() > 5000 ? content.substring(0, 5000) + "..." : content
        );
    }

    /**
     * 解析实体响应
     */
    @SuppressWarnings("unchecked")
    private List<ExtractedEntity> parseEntityResponse(String response) {
        try {
            String json = extractJson(response);
            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> rawEntities = objectMapper.readValue(
                    json, new TypeReference<>() {});

            return rawEntities.stream()
                    .map(map -> ExtractedEntity.builder()
                            .name((String) map.get("name"))
                            .category(parseCategory((String) map.get("category")))
                            .attributes((Map<String, Object>) map.getOrDefault("attributes", Map.of()))
                            .aliases((List<String>) map.getOrDefault("aliases", List.of()))
                            .sourceText((String) map.get("sourceText"))
                            .confidence(parseDouble(map.get("confidence")))
                            .build())
                    .toList();

        } catch (Exception e) {
            log.error("Failed to parse entity response: {}", response, e);
            return Collections.emptyList();
        }
    }

    /**
     * 解析关系响应
     */
    private List<ExtractedRelationship> parseRelationshipResponse(String response) {
        try {
            String json = extractJson(response);
            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> rawRelationships = objectMapper.readValue(
                    json, new TypeReference<>() {});

            return rawRelationships.stream()
                    .map(map -> ExtractedRelationship.builder()
                            .sourceEntity((String) map.get("sourceEntity"))
                            .targetEntity((String) map.get("targetEntity"))
                            .relationshipType((String) map.get("relationshipType"))
                            .description((String) map.get("description"))
                            .sourceText((String) map.get("sourceText"))
                            .confidence(parseDouble(map.get("confidence")))
                            .build())
                    .toList();

        } catch (Exception e) {
            log.error("Failed to parse relationship response: {}", response, e);
            return Collections.emptyList();
        }
    }

    private EntityCategory parseCategory(String value) {
        if (value == null) return EntityCategory.CONCEPT;
        try {
            return EntityCategory.valueOf(value.toUpperCase());
        } catch (Exception e) {
            return EntityCategory.CONCEPT;
        }
    }

    private double parseDouble(Object value) {
        if (value == null) return 0.5;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception e) {
            return 0.5;
        }
    }

    private String extractJson(String response) {
        int start = response.indexOf('[');
        int end = response.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return null;
    }
}
