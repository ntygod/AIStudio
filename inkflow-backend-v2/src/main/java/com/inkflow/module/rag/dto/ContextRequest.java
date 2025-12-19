package com.inkflow.module.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * AI上下文构建请求DTO
 * 
 * @author zsg
 * @date 2025/12/17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextRequest {

    /**
     * 项目ID
     */
    @NotNull(message = "项目ID不能为空")
    private UUID projectId;

    /**
     * 查询文本
     */
    @NotBlank(message = "查询文本不能为空")
    private String query;

    /**
     * 返回结果数量限制
     */
    @Builder.Default
    private Integer limit = 5;

    /**
     * 是否使用父子块检索策略
     */
    @Builder.Default
    private Boolean useParentChild = true;
}
