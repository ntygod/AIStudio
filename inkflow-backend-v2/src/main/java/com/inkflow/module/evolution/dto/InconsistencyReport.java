package com.inkflow.module.evolution.dto;

import com.inkflow.module.evolution.entity.EntityType;

import java.util.UUID;

/**
 * 一致性检查报告
 */
public record InconsistencyReport(
    UUID entityId,
    EntityType entityType,
    String entityName,
    String fieldPath,
    String expectedValue,
    String actualValue,
    String description,
    String suggestion,
    Severity severity
) {
    public enum Severity {
        INFO,      // 信息提示
        WARNING,   // 警告
        ERROR      // 错误
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID entityId;
        private EntityType entityType;
        private String entityName;
        private String fieldPath;
        private String expectedValue;
        private String actualValue;
        private String description;
        private String suggestion;
        private Severity severity = Severity.WARNING;

        public Builder entityId(UUID entityId) {
            this.entityId = entityId;
            return this;
        }

        public Builder entityType(EntityType entityType) {
            this.entityType = entityType;
            return this;
        }

        public Builder entityName(String entityName) {
            this.entityName = entityName;
            return this;
        }

        public Builder fieldPath(String fieldPath) {
            this.fieldPath = fieldPath;
            return this;
        }

        public Builder expectedValue(String expectedValue) {
            this.expectedValue = expectedValue;
            return this;
        }

        public Builder actualValue(String actualValue) {
            this.actualValue = actualValue;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder suggestion(String suggestion) {
            this.suggestion = suggestion;
            return this;
        }

        public Builder severity(Severity severity) {
            this.severity = severity;
            return this;
        }

        public InconsistencyReport build() {
            return new InconsistencyReport(
                entityId, entityType, entityName, fieldPath,
                expectedValue, actualValue, description, suggestion, severity
            );
        }
    }
}
