package com.inkflow.module.evolution.entity;

/**
 * 状态变更类型
 */
public enum ChangeType {
    INITIAL("initial"),      // 初始状态
    UPDATE("update"),        // 普通更新
    MAJOR_CHANGE("major_change"); // 重大变更

    private final String value;

    ChangeType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
