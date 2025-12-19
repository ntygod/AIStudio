package com.inkflow.module.rag.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 语义分块请求DTO
 * 
 * @author zsg
 * @date 2025/12/17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkRequest {

    /**
     * 待分块的文本内容
     */
    @NotBlank(message = "文本内容不能为空")
    private String content;

    /**
     * 是否使用语义分块（默认true）
     * 如果为false，则使用简单分块
     */
    @Builder.Default
    private Boolean useSemantic = true;

    /**
     * 自定义最大块大小（可选）
     */
    private Integer maxChunkSize;

    /**
     * 自定义重叠大小（可选）
     */
    private Integer overlapSize;
}
