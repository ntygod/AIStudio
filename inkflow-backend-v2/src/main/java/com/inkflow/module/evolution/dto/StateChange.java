package com.inkflow.module.evolution.dto;

import java.util.UUID;

/**
 * 状态变更记录
 */
public record StateChange(
    String fieldPath,
    Object oldValue,
    Object newValue,
    String changeReason,
    String sourceText
) {
    public static StateChange of(String fieldPath, Object oldValue, Object newValue) {
        return new StateChange(fieldPath, oldValue, newValue, null, null);
    }

    public StateChange withReason(String reason) {
        return new StateChange(fieldPath, oldValue, newValue, reason, sourceText);
    }

    public StateChange withSourceText(String source) {
        return new StateChange(fieldPath, oldValue, newValue, changeReason, source);
    }
}
