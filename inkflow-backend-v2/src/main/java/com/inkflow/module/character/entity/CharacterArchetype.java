package com.inkflow.module.character.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 角色原型模板实体
 * 
 * 预定义的角色原型，用于快速创建符合特定模式的角色
 */
@Entity
@Table(name = "character_archetypes")
public class CharacterArchetype {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * 原型英文名称
     */
    @Column(nullable = false, unique = true)
    private String name;

    /**
     * 原型中文名称
     */
    @Column(name = "name_cn", nullable = false)
    private String nameCn;

    /**
     * 原型描述
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * 典型特征模板 (JSONB)
     * 包含: traits(特征), function(叙事功能)
     */
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> template;

    /**
     * 示例角色
     */
    @Column(columnDefinition = "TEXT[]")
    private String[] examples;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNameCn() {
        return nameCn;
    }

    public void setNameCn(String nameCn) {
        this.nameCn = nameCn;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getTemplate() {
        return template;
    }

    public void setTemplate(Map<String, Object> template) {
        this.template = template;
    }

    public String[] getExamples() {
        return examples;
    }

    public void setExamples(String[] examples) {
        this.examples = examples;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
