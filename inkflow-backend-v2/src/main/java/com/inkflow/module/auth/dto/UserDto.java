package com.inkflow.module.auth.dto;

import com.inkflow.module.auth.entity.User;
import com.inkflow.module.auth.entity.UserStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户信息DTO
 */
public record UserDto(
    UUID id,
    String username,
    String email,
    String displayName,
    String avatarUrl,
    String bio,
    UserStatus status,
    boolean emailVerified,
    LocalDateTime createdAt,
    LocalDateTime lastLoginAt
) {
    /**
     * 从实体转换为DTO
     */
    public static UserDto fromEntity(User user) {
        return new UserDto(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getDisplayName(),
            user.getAvatarUrl(),
            user.getBio(),
            user.getStatus(),
            user.isEmailVerified(),
            user.getCreatedAt(),
            user.getLastLoginAt()
        );
    }
}
