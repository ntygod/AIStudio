package com.inkflow.module.agent.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 后处理结果
 * 用于封装后处理阶段的结果，如一致性检查结果
 * 
 * @see Requirements 3.4, 3.5
 */
public record PostProcessingResult(
    String type,
    String content,
    List<ConsistencyWarning> warnings
) {
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    /**
     * 创建一致性检查结果
     */
    public static PostProcessingResult consistencyCheck(String content, List<ConsistencyWarning> warnings) {
        return new PostProcessingResult("consistency_check", content, warnings);
    }
    
    /**
     * 创建关系图更新结果
     */
    public static PostProcessingResult relationshipUpdate(String content) {
        return new PostProcessingResult("relationship_update", content, List.of());
    }
    
    /**
     * 创建知识库更新结果
     */
    public static PostProcessingResult knowledgeUpdate(String content) {
        return new PostProcessingResult("knowledge_update", content, List.of());
    }
    
    /**
     * 转换为 JSON 字符串
     */
    public String toJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{\"type\":\"" + type + "\",\"content\":\"" + content + "\",\"error\":\"JSON serialization failed\"}";
        }
    }
    
    /**
     * 检查是否有警告
     */
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }
    
    /**
     * 获取警告数量
     */
    public int warningCount() {
        return warnings != null ? warnings.size() : 0;
    }
    
    /**
     * 一致性警告
     */
    public record ConsistencyWarning(
        String level,
        String message,
        String location
    ) {
        public static ConsistencyWarning error(String message, String location) {
            return new ConsistencyWarning("error", message, location);
        }
        
        public static ConsistencyWarning warning(String message, String location) {
            return new ConsistencyWarning("warning", message, location);
        }
        
        public static ConsistencyWarning info(String message, String location) {
            return new ConsistencyWarning("info", message, location);
        }
    }
}
