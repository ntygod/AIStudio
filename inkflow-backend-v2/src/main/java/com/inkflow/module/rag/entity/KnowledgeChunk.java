package com.inkflow.module.rag.entity;

import com.inkflow.common.util.HalfVecType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * RAG知识块实体
 * 支持版本控制和父子索引策略
 *
 * @author zsg
 * @date 2025/12/17
 */
@Entity
@Table(name = "knowledge_chunks", indexes = {
    @Index(name = "idx_knowledge_chunks_project", columnList = "project_id"),
    @Index(name = "idx_knowledge_chunks_source", columnList = "source_type, source_id"),
    @Index(name = "idx_knowledge_chunks_parent", columnList = "parent_id"),
    @Index(name = "idx_knowledge_chunks_dirty", columnList = "is_dirty"),
    @Index(name = "idx_knowledge_chunks_version", columnList = "source_id, version")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * 所属项目ID
     */
    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    /**
     * 来源类型: story_block, wiki_entry, character, chapter_summary
     */
    @Column(name = "source_type", nullable = false, length = 50)
    private String sourceType;

    /**
     * 来源实体ID
     */
    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    /**
     * 父块ID (用于parent-child索引策略)
     */
    @Column(name = "parent_id")
    private UUID parentId;

    /**
     * 文本内容
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * 向量嵌入 (1024维 - BGE-M3, 半精度存储)
     */
    @Column(columnDefinition = "halfvec(1024)")
    @Type(HalfVecType.class)
    private float[] embedding;


    /**
     * 块级别: parent, child
     */
    @Column(name = "chunk_level", length = 20)
    @Builder.Default
    private String chunkLevel = CHUNK_LEVEL_PARENT;

    /**
     * 块顺序 (子块在父块中的顺序)
     */
    @Column(name = "chunk_order")
    @Builder.Default
    private Integer chunkOrder = 0;

    /**
     * 版本号 (用于脏数据原子切换)
     */
    @Column(name = "version")
    @Builder.Default
    private Integer version = 1;

    /**
     * 是否为当前活跃版本
     */
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * 内容是否已变更待重新索引
     */
    @Column(name = "is_dirty")
    @Builder.Default
    private Boolean isDirty = false;

    /**
     * 元数据 (JSONB格式)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    // ==================== 常量定义 ====================

    /** 来源类型: 剧情块 */
    public static final String SOURCE_TYPE_STORY_BLOCK = "story_block";
    /** 来源类型: 知识条目 */
    public static final String SOURCE_TYPE_WIKI_ENTRY = "wiki_entry";
    /** 来源类型: 角色 */
    public static final String SOURCE_TYPE_CHARACTER = "character";
    /** 来源类型: 章节摘要 */
    public static final String SOURCE_TYPE_CHAPTER_SUMMARY = "chapter_summary";

    /** 块级别: 父块 */
    public static final String CHUNK_LEVEL_PARENT = "parent";
    /** 块级别: 子块 */
    public static final String CHUNK_LEVEL_CHILD = "child";

    // ==================== 业务方法 ====================

    /**
     * 标记为脏数据
     */
    public void markDirty() {
        this.isDirty = true;
    }

    /**
     * 清除脏标记
     */
    public void clearDirty() {
        this.isDirty = false;
    }

    /**
     * 递增版本号
     */
    public void incrementVersion() {
        this.version = this.version + 1;
    }

    /**
     * 停用当前版本
     */
    public void deactivate() {
        this.isActive = false;
    }

    /**
     * 激活当前版本
     */
    public void activate() {
        this.isActive = true;
    }

    /**
     * 判断是否为子块
     */
    public boolean isChildChunk() {
        return CHUNK_LEVEL_CHILD.equals(this.chunkLevel);
    }

    /**
     * 判断是否为父块
     */
    public boolean isParentChunk() {
        return CHUNK_LEVEL_PARENT.equals(this.chunkLevel);
    }
}
