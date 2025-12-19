package com.inkflow.module.auth.dto;

import jakarta.validation.constraints.Size;

/**
 * 更新用户资料请求
 */
public record UpdateProfileRequest(
    @Size(max = 100, message = "昵称不能超过100个字符")
    String displayName,
    
    @Size(max = 500, message = "简介不能超过500个字符")
    String bio,
    
    // Base64 Data URL 可能很长，暂不限制长度
    // 生产环境应使用文件上传到 OSS，这里只存储 URL
    String avatarUrl
) {}
