package com.inkflow.module.extraction.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 提取的实体
 */
public record ExtractedEntity(
    String name,
    EntityCategory category,
    Map<String, Object> attributes,
    List<String> aliases,
    String sourceText,
    double confidence
) {
    public enum EntityCategory {
        CHARACTER,      // 角色
        LOCATION,       // 地点
        ITEM,           // 物品
        ORGANIZATION,   // 组织
        EVENT,          // 事件
        CONCEPT,        // 概念/设定
        RELATIONSHIP    // 关系
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private EntityCategory category;
        private Map<String, Object> attributes;
        private List<String> aliases;
        private String sourceText;
        private double confidence;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder category(EntityCategory category) {
            this.category = category;
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            this.attributes = attributes;
            return this;
        }

        public Builder aliases(List<String> aliases) {
            this.aliases = aliases;
            return this;
        }

        public Builder sourceText(String sourceText) {
            this.sourceText = sourceText;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public ExtractedEntity build() {
            return new ExtractedEntity(name, category, attributes, aliases, sourceText, confidence);
        }
    }
}
