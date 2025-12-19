package com.inkflow.module.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 子块DTO
 * 用于语义分块服务，表示从父块（StoryBlock）切分出的语义子块。
 * 支持"小块检索，大块返回"策略。
 *
 * @author zsg
 * @date 2025/12/17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChildChunk {

    /**
     * 子块内容
     */
    private String content;

    /**
     * 子块在父块中的顺序（从0开始）
     */
    private int order;

    /**
     * 在父块内容中的起始位置
     */
    private int startPosition;

    /**
     * 在父块内容中的结束位置
     */
    private int endPosition;

    /**
     * 父块ID（StoryBlock ID）
     */
    private UUID parentId;

    /**
     * 父块来源类型
     */
    private String sourceType;

    /**
     * 父块来源实体ID
     */
    private UUID sourceId;

    /**
     * 获取子块长度
     */
    public int getLength() {
        return content != null ? content.length() : 0;
    }

    /**
     * 检查子块是否有效（非空且有内容）
     */
    public boolean isValid() {
        return content != null && !content.isBlank();
    }
}
