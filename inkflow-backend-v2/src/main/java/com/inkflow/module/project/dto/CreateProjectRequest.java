package com.inkflow.module.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 创建项目请求DTO
 */
public record CreateProjectRequest(
    /**
     * 项目标题
     */
    @NotBlank(message = "项目标题不能为空")
    @Size(max = 200, message = "项目标题不能超过200个字符")
    String title,
    
    /**
     * 项目简介
     */
    @Size(max = 5000, message = "项目简介不能超过5000个字符")
    String description,
    
    /**
     * 封面图片URL
     */
    @Size(max = 500, message = "封面URL不能超过500个字符")
    String coverUrl,
    
    /**
     * 项目元数据
     */
    Map<String, Object> metadata
) {}
