package com.inkflow.module.extraction.dto;

/**
 * 提取的关系
 */
public record ExtractedRelationship(
    String sourceEntity,
    String targetEntity,
    String relationshipType,
    String description,
    String sourceText,
    double confidence
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String sourceEntity;
        private String targetEntity;
        private String relationshipType;
        private String description;
        private String sourceText;
        private double confidence;

        public Builder sourceEntity(String sourceEntity) {
            this.sourceEntity = sourceEntity;
            return this;
        }

        public Builder targetEntity(String targetEntity) {
            this.targetEntity = targetEntity;
            return this;
        }

        public Builder relationshipType(String relationshipType) {
            this.relationshipType = relationshipType;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
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

        public ExtractedRelationship build() {
            return new ExtractedRelationship(
                sourceEntity, targetEntity, relationshipType, 
                description, sourceText, confidence
            );
        }
    }
}
