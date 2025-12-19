package com.inkflow.module.evolution.entity;

/**
 * 可追踪演进的实体类型
 */
public enum EntityType {
    CHARACTER("character"),
    WIKI_ENTRY("wiki_entry"),
    RELATIONSHIP("relationship");

    private final String value;

    EntityType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static EntityType fromValue(String value) {
        for (EntityType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown entity type: " + value);
    }
}
