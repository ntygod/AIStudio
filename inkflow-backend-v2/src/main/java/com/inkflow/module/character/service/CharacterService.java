package com.inkflow.module.character.service;

import com.inkflow.common.exception.BusinessException;
import com.inkflow.common.exception.ResourceNotFoundException;
import com.inkflow.module.character.dto.*;
import com.inkflow.module.character.entity.Character;
import com.inkflow.module.character.entity.CharacterRelationship;
import com.inkflow.module.character.event.CharacterChangedEvent;
import com.inkflow.module.character.repository.CharacterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 角色服务
 * 
 * 提供角色的CRUD操作和关系管理功能
 */
@Service
@Transactional
public class CharacterService {

    private static final Logger log = LoggerFactory.getLogger(CharacterService.class);

    private final CharacterRepository characterRepository;
    private final ApplicationEventPublisher eventPublisher;

    public CharacterService(CharacterRepository characterRepository, 
                           ApplicationEventPublisher eventPublisher) {
        this.characterRepository = characterRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 创建角色
     */
    public CharacterDto create(CreateCharacterRequest request) {
        // 检查名称是否已存在
        if (characterRepository.existsByProjectIdAndName(request.projectId(), request.name())) {
            throw new BusinessException("角色名称已存在: " + request.name());
        }

        Character character = new Character();
        character.setProjectId(request.projectId());
        character.setName(request.name());
        character.setRole(request.role());
        character.setDescription(request.description());
        character.setPersonality(request.personality());
        character.setRelationships(request.relationships() != null ? request.relationships() : new ArrayList<>());
        character.setArchetype(request.archetype());

        Character saved = characterRepository.save(character);
        log.info("创建角色: {} (项目: {})", saved.getName(), saved.getProjectId());
        
        // 发布角色创建事件
        eventPublisher.publishEvent(CharacterChangedEvent.created(
                this, saved.getProjectId(), saved.getId(), saved.getName(), buildStateMap(saved)));
        
        return CharacterDto.from(saved);
    }

    /**
     * 根据ID查询角色
     */
    @Transactional(readOnly = true)
    public CharacterDto findById(UUID id) {
        Character character = characterRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("角色不存在: " + id));
        return CharacterDto.from(character);
    }

    /**
     * 根据项目ID查询所有角色
     */
    @Transactional(readOnly = true)
    public List<CharacterDto> findByProjectId(UUID projectId) {
        return characterRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
            .stream()
            .map(CharacterDto::from)
            .toList();
    }

    /**
     * 根据项目ID和角色类型查询
     */
    @Transactional(readOnly = true)
    public List<CharacterDto> findByProjectIdAndRole(UUID projectId, String role) {
        return characterRepository.findByProjectIdAndRole(projectId, role)
            .stream()
            .map(CharacterDto::from)
            .toList();
    }

    /**
     * 查询项目中的活跃角色
     */
    @Transactional(readOnly = true)
    public List<CharacterDto> findActiveByProjectId(UUID projectId) {
        return characterRepository.findByProjectIdAndIsActiveTrue(projectId)
            .stream()
            .map(CharacterDto::from)
            .toList();
    }

    /**
     * 搜索角色
     */
    @Transactional(readOnly = true)
    public List<CharacterDto> search(UUID projectId, String keyword) {
        return characterRepository.searchByKeyword(projectId, keyword)
            .stream()
            .map(CharacterDto::from)
            .toList();
    }

    /**
     * 更新角色
     */
    public CharacterDto update(UUID id, UpdateCharacterRequest request) {
        Character character = characterRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("角色不存在: " + id));

        // 保存更新前的状态
        Map<String, Object> previousState = buildStateMap(character);

        // 如果更新名称，检查是否与其他角色重名
        if (request.name() != null && !request.name().equals(character.getName())) {
            if (characterRepository.existsByProjectIdAndName(character.getProjectId(), request.name())) {
                throw new BusinessException("角色名称已存在: " + request.name());
            }
            character.setName(request.name());
        }

        if (request.role() != null) {
            character.setRole(request.role());
        }
        if (request.description() != null) {
            character.setDescription(request.description());
        }
        if (request.personality() != null) {
            character.setPersonality(request.personality());
        }
        if (request.relationships() != null) {
            character.setRelationships(request.relationships());
        }
        if (request.status() != null) {
            character.updateStatus(request.status());
        }
        if (request.archetype() != null) {
            character.setArchetype(request.archetype());
        }

        Character saved = characterRepository.save(character);
        log.info("更新角色: {} (ID: {})", saved.getName(), saved.getId());
        
        // 发布角色更新事件
        eventPublisher.publishEvent(CharacterChangedEvent.updated(
                this, saved.getProjectId(), saved.getId(), saved.getName(), 
                buildStateMap(saved), previousState));
        
        return CharacterDto.from(saved);
    }

    /**
     * 添加角色关系
     */
    public CharacterDto addRelationship(UUID characterId, AddRelationshipRequest request) {
        Character character = characterRepository.findById(characterId)
            .orElseThrow(() -> new ResourceNotFoundException("角色不存在: " + characterId));

        // 验证目标角色存在
        if (!characterRepository.existsById(request.targetId())) {
            throw new ResourceNotFoundException("目标角色不存在: " + request.targetId());
        }

        // 检查是否已存在相同关系
        boolean exists = character.getRelationships().stream()
            .anyMatch(r -> r.getTargetId().equals(request.targetId()));
        if (exists) {
            throw new BusinessException("与该角色的关系已存在");
        }

        CharacterRelationship relationship = new CharacterRelationship(
            request.targetId(),
            request.type(),
            request.description()
        );
        if (request.strength() != null) {
            relationship.setStrength(request.strength());
        }
        if (request.bidirectional() != null) {
            relationship.setBidirectional(request.bidirectional());
        }

        character.addRelationship(relationship);

        // 如果是双向关系，也为目标角色添加反向关系
        if (Boolean.TRUE.equals(request.bidirectional())) {
            addReverseRelationship(request.targetId(), characterId, request);
        }

        Character saved = characterRepository.save(character);
        log.info("添加角色关系: {} -> {} ({})", character.getName(), request.targetId(), request.type());
        
        return CharacterDto.from(saved);
    }

    /**
     * 添加反向关系
     */
    private void addReverseRelationship(UUID targetId, UUID sourceId, AddRelationshipRequest request) {
        Character target = characterRepository.findById(targetId).orElse(null);
        if (target == null) return;

        boolean exists = target.getRelationships().stream()
            .anyMatch(r -> r.getTargetId().equals(sourceId));
        if (exists) return;

        CharacterRelationship reverse = new CharacterRelationship(
            sourceId,
            getReverseRelationType(request.type()),
            request.description()
        );
        if (request.strength() != null) {
            reverse.setStrength(request.strength());
        }
        reverse.setBidirectional(true);

        target.addRelationship(reverse);
        characterRepository.save(target);
    }

    /**
     * 获取反向关系类型
     */
    private String getReverseRelationType(String type) {
        return switch (type) {
            case "mentor" -> "student";
            case "student" -> "mentor";
            case "parent" -> "child";
            case "child" -> "parent";
            default -> type; // friend, enemy, rival等双向关系类型相同
        };
    }

    /**
     * 移除角色关系
     */
    public CharacterDto removeRelationship(UUID characterId, UUID targetId) {
        Character character = characterRepository.findById(characterId)
            .orElseThrow(() -> new ResourceNotFoundException("角色不存在: " + characterId));

        character.removeRelationship(targetId);
        Character saved = characterRepository.save(character);
        
        log.info("移除角色关系: {} -> {}", character.getName(), targetId);
        return CharacterDto.from(saved);
    }

    /**
     * 更新角色状态
     */
    public CharacterDto updateStatus(UUID id, String status) {
        Character character = characterRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("角色不存在: " + id));

        character.updateStatus(status);
        Character saved = characterRepository.save(character);
        
        log.info("更新角色状态: {} -> {}", character.getName(), status);
        return CharacterDto.from(saved);
    }

    /**
     * 删除角色 (软删除)
     */
    public void delete(UUID id) {
        Character character = characterRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("角色不存在: " + id));

        UUID projectId = character.getProjectId();
        String characterName = character.getName();
        
        character.softDelete();
        characterRepository.save(character);
        
        log.info("删除角色: {} (ID: {})", characterName, id);
        
        // 发布角色删除事件
        eventPublisher.publishEvent(CharacterChangedEvent.deleted(this, projectId, id, characterName));
    }

    /**
     * 统计项目中的角色数量
     */
    @Transactional(readOnly = true)
    public long countByProjectId(UUID projectId) {
        return characterRepository.countByProjectId(projectId);
    }

    /**
     * 构建角色状态映射（用于演进快照）
     */
    private Map<String, Object> buildStateMap(Character character) {
        Map<String, Object> state = new HashMap<>();
        state.put("name", character.getName());
        state.put("role", character.getRole());
        state.put("description", character.getDescription());
        state.put("personality", character.getPersonality());
        state.put("status", character.getStatus());
        state.put("archetype", character.getArchetype());
        state.put("isActive", character.getIsActive());
        if (character.getRelationships() != null) {
            state.put("relationshipCount", character.getRelationships().size());
        }
        return state;
    }
}
