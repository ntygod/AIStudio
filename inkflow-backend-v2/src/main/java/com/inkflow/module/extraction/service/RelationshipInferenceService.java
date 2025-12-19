package com.inkflow.module.extraction.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.module.ai_bridge.chat.DynamicChatModelFactory;
import com.inkflow.module.extraction.dto.ExtractedEntity;
import com.inkflow.module.extraction.dto.ExtractedRelationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 关系推断服务
 * 基于实体和上下文推断隐含关系
 */
@Service
public class RelationshipInferenceService {

    private static final Logger log = LoggerFactory.getLogger(RelationshipInferenceService.class);

    private final DynamicChatModelFactory chatModelFactory;
    private final ObjectMapper objectMapper;

    public RelationshipInferenceService(
            DynamicChatModelFactory chatModelFactory,
            ObjectMapper objectMapper) {
        this.chatModelFactory = chatModelFactory;
        this.objectMapper = objectMapper;
    }

    /**
     * 推断实体之间的关系
     */
    public List<ExtractedRelationship> inferRelationships(
            List<ExtractedEntity> entities,
            List<ExtractedRelationship> existingRelationships,
            String context) {

        if (entities == null || entities.size() < 2) {
            return Collections.emptyList();
        }

        // 获取角色实体
        List<ExtractedEntity> characters = entities.stream()
                .filter(e -> e.category() == ExtractedEntity.EntityCategory.CHARACTER)
                .toList();

        if (characters.size() < 2) {
            return Collections.emptyList();
        }

        try {
            return inferWithAI(characters, existingRelationships, context);
        } catch (Exception e) {
            log.error("Relationship inference failed", e);
            return Collections.emptyList();
        }
    }

    /**
     * 使用AI推断关系
     */
    private List<ExtractedRelationship> inferWithAI(
            List<ExtractedEntity> characters,
            List<ExtractedRelationship> existingRelationships,
            String context) {

        String prompt = buildInferencePrompt(characters, existingRelationships, context);

        ChatModel model = chatModelFactory.getDefaultModel();
        ChatClient chatClient = ChatClient.builder(model).build();
        String response = chatClient.prompt()
                .system("""
                    你是一个专业的小说分析助手，负责推断角色之间的隐含关系。
                    
                    基于已知的角色信息和上下文，推断可能存在但未明确提及的关系。
                    
                    请以JSON数组格式返回推断的关系，每个关系包含：
                    - sourceEntity: 关系主体
                    - targetEntity: 关系客体
                    - relationshipType: 关系类型
                    - description: 关系描述
                    - sourceText: 推断依据
                    - confidence: 置信度 (0.0-1.0)
                    
                    注意：
                    1. 只返回新推断的关系，不要重复已知关系
                    2. 置信度应反映推断的可靠程度
                    3. 如果没有可推断的关系，返回空数组 []
                    """)
                .user(prompt)
                .call()
                .content();

        return parseInferenceResponse(response);
    }

    /**
     * 构建推断提示词
     */
    private String buildInferencePrompt(
            List<ExtractedEntity> characters,
            List<ExtractedRelationship> existingRelationships,
            String context) {

        StringBuilder sb = new StringBuilder();
        sb.append("## 角色列表\n");
        for (ExtractedEntity character : characters) {
            sb.append("- ").append(character.name());
            if (character.attributes() != null && !character.attributes().isEmpty()) {
                sb.append(": ").append(character.attributes());
            }
            sb.append("\n");
        }

        sb.append("\n## 已知关系\n");
        if (existingRelationships != null && !existingRelationships.isEmpty()) {
            for (ExtractedRelationship rel : existingRelationships) {
                sb.append("- ").append(rel.sourceEntity())
                  .append(" -> ").append(rel.targetEntity())
                  .append(" (").append(rel.relationshipType()).append(")\n");
            }
        } else {
            sb.append("无\n");
        }

        sb.append("\n## 上下文\n");
        if (context != null && !context.isBlank()) {
            sb.append(context.length() > 2000 ? context.substring(0, 2000) + "..." : context);
        } else {
            sb.append("无额外上下文\n");
        }

        sb.append("\n## 任务\n");
        sb.append("基于以上信息，推断角色之间可能存在的其他关系。\n");
        sb.append("考虑：\n");
        sb.append("1. 角色属性暗示的关系（如同一组织、相似背景）\n");
        sb.append("2. 上下文中暗示的关系\n");
        sb.append("3. 常见的小说关系模式\n");

        return sb.toString();
    }

    /**
     * 解析推断响应
     */
    private List<ExtractedRelationship> parseInferenceResponse(String response) {
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
            log.error("Failed to parse inference response: {}", response, e);
            return Collections.emptyList();
        }
    }

    /**
     * 构建关系图谱
     */
    public Map<String, List<RelationshipEdge>> buildRelationshipGraph(
            List<ExtractedRelationship> relationships) {

        Map<String, List<RelationshipEdge>> graph = new HashMap<>();

        for (ExtractedRelationship rel : relationships) {
            // 添加正向边
            graph.computeIfAbsent(rel.sourceEntity(), k -> new ArrayList<>())
                    .add(new RelationshipEdge(rel.targetEntity(), rel.relationshipType(), 
                            rel.description(), rel.confidence()));

            // 添加反向边（用于双向查询）
            String reverseType = reverseRelationshipType(rel.relationshipType());
            graph.computeIfAbsent(rel.targetEntity(), k -> new ArrayList<>())
                    .add(new RelationshipEdge(rel.sourceEntity(), reverseType, 
                            rel.description(), rel.confidence()));
        }

        return graph;
    }

    /**
     * 获取关系的反向类型
     */
    private String reverseRelationshipType(String type) {
        return switch (type.toLowerCase()) {
            case "师傅" -> "徒弟";
            case "徒弟" -> "师傅";
            case "父亲", "母亲" -> "子女";
            case "子女" -> "父母";
            case "兄长", "姐姐" -> "弟妹";
            case "弟弟", "妹妹" -> "兄姐";
            case "上级" -> "下属";
            case "下属" -> "上级";
            default -> type + "(反向)";
        };
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

    /**
     * 关系边
     */
    public record RelationshipEdge(
            String target,
            String type,
            String description,
            double confidence
    ) {}
}
