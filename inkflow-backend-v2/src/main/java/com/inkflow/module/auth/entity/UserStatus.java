package com.inkflow.module.auth.entity;

/**
 * 用户账户状态枚举
 */
public enum UserStatus {
    
    /**
     * 活跃状态 - 正常使用
     */
    ACTIVE,
    
    /**
     * 未激活 - 等待邮箱验证
     */
    INACTIVE,
    
    /**
     * 已禁用 - 管理员禁用
     */
    DISABLED,
    
    /**
     * 已删除 - 软删除状态
     */
    DELETED
}
