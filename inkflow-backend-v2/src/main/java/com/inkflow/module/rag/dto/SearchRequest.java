package com.inkflow.module.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * RAG搜索请求DTO
 * 
 * @author zsg
 * @date 2025/12/17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchRequest {

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
     * 来源类型过滤（可选）
     */
    private String sourceType;

    /**
     * 返回结果数量限制
     */
    @Builder.Default
    private Integer limit = 10;
}
