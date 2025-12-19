package com.inkflow.module.consistency.service;

import com.inkflow.module.character.entity.Character;
import com.inkflow.module.character.entity.CharacterRelationship;
import com.inkflow.module.character.repository.CharacterRepository;
import com.inkflow.module.consistency.entity.ConsistencyWarning;
import com.inkflow.module.consistency.entity.ConsistencyWarning.Severity;
import com.inkflow.module.consistency.entity.ConsistencyWarning.WarningType;
import com.inkflow.module.evolution.entity.EntityType;
import com.inkflow.module.wiki.entity.WikiEntry;
import com.inkflow.module.wiki.repository.WikiEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 规则检查服务
 * 实现基于规则的一致性检查，包括：
 * - 角色名称唯一性检查
 * - 必填字段验证
 * - WikiEntry标题唯一性检查
 * - 引用完整性检查
 * - 双向关系一致性检查
 * 
 * Requirements: 2.1, 2.2, 2.3, 2.4, 2.5
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleCheckerService {

    private final CharacterRepository characterRepository;
    private final WikiEntryRepository wikiEntryRepository;

    /**
     * 检查角色规则
     * 
     * Requirements: 2.1, 2.2
     * 
     * @param projectId 项目ID
     * @param character 要检查的角色
     * @return 警告列表
     */
    public List<ConsistencyWarning> checkCharacterRules(UUID projectId, Character character) {
        List<ConsistencyWarning> warnings = new ArrayList<>();
        
        // 2.1 名称唯一性检查
        warnings.addAll(checkCharacterNameUniqueness(projectId, character));
        
        // 2.2 必填字段验证
        warnings.addAll(checkCharacterRequiredFields(projectId, character));
        
        return warnings;
    }

    /**
     * 检查角色名称唯一性
     * 
     * Requirements: 2.1 - WHEN a Character entity is updated THEN the ProactiveConsistencyService 
     * SHALL check for name uniqueness within the project
     * 
     * @param projectId 项目ID
     * @param character 要检查的角色
     * @return 警告列表
     */
    public List<ConsistencyWarning> checkCharacterNameUniqueness(UUID projectId, Character character) {
        List<ConsistencyWarning> warnings = new ArrayList<>();
        
        if (!StringUtils.hasText(character.getName())) {
            return warnings; // 空名称由必填字段检查处理
        }
        
        // 查找同名角色
        Optional<Character> existingCharacter = characterRepository.findByProjectIdAndName(projectId, character.getName());
        
        if (existingCharacter.isPresent() && !existingCharacter.get().getId().equals(character.getId())) {
            warnings.add(ConsistencyWarning.builder()
                    .projectId(projectId)
                    .entityId(character.getId())
                    .entityType(EntityType.CHARACTER)
                    .entityName(character.getName())
                    .warningType(WarningType.NAME_DUPLICATE)
                    .severity(Severity.ERROR)
                    .description("角色名称 '" + character.getName() + "' 已存在于项目中")
                    .suggestion("请修改角色名称以避免混淆")
                    .fieldPath("name")
                    .actualValue(character.getName())
                    .relatedEntityIds(List.of(existingCharacter.get().getId()))
                    .build());
        }
        
        return warnings;
    }

    /**
     * 检查角色必填字段
     * 
     * Requirements: 2.2 - WHEN a Character entity is created or updated THEN the ProactiveConsistencyService 
     * SHALL validate that required fields (name, role) are not empty
     * 
     * @param projectId 项目ID
     * @param character 要检查的角色
     * @return 警告列表
     */
    public List<ConsistencyWarning> checkCharacterRequiredFields(UUID projectId, Character character) {
        List<ConsistencyWarning> warnings = new ArrayList<>();
        
        // 检查名称
        if (!StringUtils.hasText(character.getName())) {
            warnings.add(ConsistencyWarning.builder()
                    .projectId(projectId)
                    .entityId(character.getId())
                    .entityType(EntityType.CHARACTER)
                    .entityName(character.getName())
                    .warningType(WarningType.CHARACTER_INCONSISTENCY)
                    .severity(Severity.ERROR)
                    .description("角色名称不能为空")
                    .suggestion("请为角色设置一个名称")
                    .fieldPath("name")
                    .actualValue(character.getName())
                    .build());
        }
        
        // 检查角色类型
        if (!StringUtils.hasText(character.getRole())) {
            warnings.add(ConsistencyWarning.builder()
                    .projectId(projectId)
                    .entityId(character.getId())
                    .entityType(EntityType.CHARACTER)
                    .entityName(character.getName())
                    .warningType(WarningType.CHARACTER_INCONSISTENCY)
                    .severity(Severity.ERROR)
                    .description("角色类型不能为空")
                    .suggestion("请为角色设置类型（如：主角、配角、反派等）")
                    .fieldPath("role")
                    .actualValue(character.getRole())
                    .build());
        }
        
        return warnings;
    }

    /**
     * 检查WikiEntry规则
     * 
     * Requirements: 2.3, 2.4
     * 
     * @param projectId 项目ID
     * @param wikiEntry 要检查的WikiEntry
     * @return 警告列表
     */
    public List<ConsistencyWarning> checkWikiEntryRules(UUID projectId, WikiEntry wikiEntry) {
        List<ConsistencyWarning> warnings = new ArrayList<>();
        
        // 2.3 标题唯一性检查
        warnings.addAll(checkWikiEntryTitleUniqueness(projectId, wikiEntry));
        
        // 2.4 引用完整性检查
        warnings.addAll(checkWikiEntryReferenceIntegrity(projectId, wikiEntry));
        
        return warnings;
    }

    /**
     * 检查WikiEntry标题唯一性
     * 
     * Requirements: 2.3 - WHEN a WikiEntry entity is updated THEN the ProactiveConsistencyService 
     * SHALL check for title uniqueness within the project
     * 
     * @param projectId 项目ID
     * @param wikiEntry 要检查的WikiEntry
     * @return 警告列表
     */
    public List<ConsistencyWarning> checkWikiEntryTitleUniqueness(UUID projectId, WikiEntry wikiEntry) {
        List<ConsistencyWarning> warnings = new ArrayList<>();
        
        if (!StringUtils.hasText(wikiEntry.getTitle())) {
            return warnings; // 空标题由其他检查处理
        }
        
        // 查找同标题条目
        Optional<WikiEntry> existingEntry = wikiEntryRepository.findByProjectIdAndTitle(projectId, wikiEntry.getTitle());
        
        if (existingEntry.isPresent() && !existingEntry.get().getId().equals(wikiEntry.getId())) {
            warnings.add(ConsistencyWarning.builder()
                    .projectId(projectId)
                    .entityId(wikiEntry.getId())
                    .entityType(EntityType.WIKI_ENTRY)
                    .entityName(wikiEntry.getTitle())
                    .warningType(WarningType.NAME_DUPLICATE)
                    .severity(Severity.ERROR)
                    .description("设定条目标题 '" + wikiEntry.getTitle() + "' 已存在于项目中")
                    .suggestion("请修改条目标题以避免混淆")
                    .fieldPath("title")
                    .actualValue(wikiEntry.getTitle())
                    .relatedEntityIds(List.of(existingEntry.get().getId()))
                    .build());
        }
        
        return warnings;
    }

    /**
     * 检查WikiEntry引用完整性
     * 
     * Requirements: 2.4 - WHEN a WikiEntry entity references other entities THEN the ProactiveConsistencyService 
     * SHALL verify that referenced entities exist
     * 
     * @param projectId 项目ID
     * @param wikiEntry 要检查的WikiEntry
     * @return 警告列表
     */
    public List<ConsistencyWarning> checkWikiEntryReferenceIntegrity(UUID projectId, WikiEntry wikiEntry) {
        List<ConsistencyWarning> warnings = new ArrayList<>();
        
        String content = wikiEntry.getContent();
        if (!StringUtils.hasText(content)) {
            return warnings;
        }
        
        // 解析内容中的引用 (格式: [[实体名称]] 或 @实体名称)
        Set<String> references = extractReferences(content);
        
        for (String reference : references) {
            // 检查是否存在对应的角色
            Optional<Character> character = characterRepository.findByProjectIdAndName(projectId, reference);
            // 检查是否存在对应的WikiEntry
            Optional<WikiEntry> entry = wikiEntryRepository.findByProjectIdAndTitle(projectId, reference);
            
            if (character.isEmpty() && entry.isEmpty()) {
                warnings.add(ConsistencyWarning.builder()
                        .projectId(projectId)
                        .entityId(wikiEntry.getId())
                        .entityType(EntityType.WIKI_ENTRY)
                        .entityName(wikiEntry.getTitle())
                        .warningType(WarningType.REFERENCE_BROKEN)
                        .severity(Severity.WARNING)
                        .description("设定条目 '" + wikiEntry.getTitle() + "' 引用了不存在的实体: '" + reference + "'")
                        .suggestion("请创建被引用的实体，或修正引用名称")
                        .fieldPath("content")
                        .actualValue(reference)
                        .build());
            }
        }
        
        return warnings;
    }

    /**
     * 检查关系规则
     * 
     * Requirements: 2.5
     * 
     * @param projectId 项目ID
     * @param character 要检查的角色
     * @return 警告列表
     */
    public List<ConsistencyWarning> checkRelationshipRules(UUID projectId, Character character) {
        List<ConsistencyWarning> warnings = new ArrayList<>();
        
        // 2.5 双向一致性检查
        warnings.addAll(checkBidirectionalRelationshipConsistency(projectId, character));
        
        return warnings;
    }

    /**
     * 检查双向关系一致性
     * 
     * Requirements: 2.5 - WHEN a CharacterRelationship is created THEN the ProactiveConsistencyService 
     * SHALL verify bidirectional consistency
     * 
     * @param projectId 项目ID
     * @param character 要检查的角色
     * @return 警告列表
     */
    public List<ConsistencyWarning> checkBidirectionalRelationshipConsistency(UUID projectId, Character character) {
        List<ConsistencyWarning> warnings = new ArrayList<>();
        
        List<CharacterRelationship> relationships = character.getRelationships();
        if (relationships == null || relationships.isEmpty()) {
            return warnings;
        }
        
        for (CharacterRelationship relationship : relationships) {
            // 如果关系标记为双向，检查目标角色是否有对应的反向关系
            if (Boolean.TRUE.equals(relationship.getBidirectional())) {
                UUID targetId = relationship.getTargetId();
                if (targetId == null) {
                    continue;
                }
                
                Optional<Character> targetCharacter = characterRepository.findById(targetId);
                if (targetCharacter.isEmpty()) {
                    // 目标角色不存在
                    warnings.add(ConsistencyWarning.builder()
                            .projectId(projectId)
                            .entityId(character.getId())
                            .entityType(EntityType.RELATIONSHIP)
                            .entityName(character.getName())
                            .warningType(WarningType.REFERENCE_BROKEN)
                            .severity(Severity.ERROR)
                            .description("角色 '" + character.getName() + "' 的关系目标角色不存在")
                            .suggestion("请删除无效的关系或创建目标角色")
                            .fieldPath("relationships")
                            .relatedEntityIds(List.of(targetId))
                            .build());
                    continue;
                }
                
                // 检查目标角色是否有反向关系
                Character target = targetCharacter.get();
                boolean hasReverseRelationship = hasRelationshipTo(target, character.getId());
                
                if (!hasReverseRelationship) {
                    warnings.add(ConsistencyWarning.builder()
                            .projectId(projectId)
                            .entityId(character.getId())
                            .entityType(EntityType.RELATIONSHIP)
                            .entityName(character.getName())
                            .warningType(WarningType.RELATIONSHIP_CONFLICT)
                            .severity(Severity.WARNING)
                            .description("角色 '" + character.getName() + "' 与 '" + target.getName() + 
                                    "' 的关系标记为双向，但目标角色没有对应的反向关系")
                            .suggestion("请为目标角色添加反向关系，或将此关系标记为单向")
                            .fieldPath("relationships")
                            .relatedEntityIds(List.of(targetId))
                            .build());
                }
            }
        }
        
        return warnings;
    }

    /**
     * 检查角色是否有指向目标的关系
     */
    private boolean hasRelationshipTo(Character character, UUID targetId) {
        List<CharacterRelationship> relationships = character.getRelationships();
        if (relationships == null || relationships.isEmpty()) {
            return false;
        }
        return relationships.stream()
                .anyMatch(r -> targetId.equals(r.getTargetId()));
    }

    /**
     * 从内容中提取引用
     * 支持格式: [[实体名称]] 或 @实体名称
     */
    private Set<String> extractReferences(String content) {
        Set<String> references = new HashSet<>();
        
        // 匹配 [[实体名称]] 格式
        java.util.regex.Pattern bracketPattern = java.util.regex.Pattern.compile("\\[\\[([^\\]]+)\\]\\]");
        java.util.regex.Matcher bracketMatcher = bracketPattern.matcher(content);
        while (bracketMatcher.find()) {
            references.add(bracketMatcher.group(1).trim());
        }
        
        // 匹配 @实体名称 格式 (以空格或标点结束)
        java.util.regex.Pattern atPattern = java.util.regex.Pattern.compile("@([\\u4e00-\\u9fa5a-zA-Z0-9_]+)");
        java.util.regex.Matcher atMatcher = atPattern.matcher(content);
        while (atMatcher.find()) {
            references.add(atMatcher.group(1).trim());
        }
        
        return references;
    }

    /**
     * 综合检查所有规则
     * 
     * @param projectId 项目ID
     * @param entityType 实体类型
     * @param entityId 实体ID
     * @return 警告列表
     */
    public List<ConsistencyWarning> checkAllRules(UUID projectId, EntityType entityType, UUID entityId) {
        List<ConsistencyWarning> warnings = new ArrayList<>();
        
        switch (entityType) {
            case CHARACTER -> {
                characterRepository.findById(entityId).ifPresent(character -> {
                    warnings.addAll(checkCharacterRules(projectId, character));
                    warnings.addAll(checkRelationshipRules(projectId, character));
                });
            }
            case WIKI_ENTRY -> {
                wikiEntryRepository.findById(entityId).ifPresent(wikiEntry -> {
                    warnings.addAll(checkWikiEntryRules(projectId, wikiEntry));
                });
            }
            case RELATIONSHIP -> {
                // 关系检查需要通过角色来进行
                // 这里可以扩展为检查所有角色的关系
            }
            default -> log.debug("未实现的实体类型规则检查: {}", entityType);
        }
        
        return warnings;
    }
}
