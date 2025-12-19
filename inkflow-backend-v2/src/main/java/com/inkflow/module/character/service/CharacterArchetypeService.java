package com.inkflow.module.character.service;

import com.inkflow.common.exception.ResourceNotFoundException;
import com.inkflow.module.character.dto.CharacterArchetypeDto;
import com.inkflow.module.character.entity.CharacterArchetype;
import com.inkflow.module.character.repository.CharacterArchetypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 角色原型服务
 * 
 * 提供预定义角色原型的查询和应用功能
 */
@Service
@Transactional(readOnly = true)
public class CharacterArchetypeService {

    private final CharacterArchetypeRepository archetypeRepository;

    public CharacterArchetypeService(CharacterArchetypeRepository archetypeRepository) {
        this.archetypeRepository = archetypeRepository;
    }

    /**
     * 获取所有角色原型
     */
    public List<CharacterArchetypeDto> findAll() {
        return archetypeRepository.findAll()
            .stream()
            .map(CharacterArchetypeDto::from)
            .toList();
    }

    /**
     * 根据ID查询原型
     */
    public CharacterArchetypeDto findById(UUID id) {
        CharacterArchetype archetype = archetypeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("角色原型不存在: " + id));
        return CharacterArchetypeDto.from(archetype);
    }

    /**
     * 根据英文名称查询原型
     */
    public CharacterArchetypeDto findByName(String name) {
        CharacterArchetype archetype = archetypeRepository.findByName(name)
            .orElseThrow(() -> new ResourceNotFoundException("角色原型不存在: " + name));
        return CharacterArchetypeDto.from(archetype);
    }

    /**
     * 根据中文名称查询原型
     */
    public CharacterArchetypeDto findByNameCn(String nameCn) {
        CharacterArchetype archetype = archetypeRepository.findByNameCn(nameCn)
            .orElseThrow(() -> new ResourceNotFoundException("角色原型不存在: " + nameCn));
        return CharacterArchetypeDto.from(archetype);
    }

    /**
     * 获取原型的特征模板
     */
    public Map<String, Object> getTemplate(String archetypeName) {
        CharacterArchetype archetype = archetypeRepository.findByName(archetypeName)
            .or(() -> archetypeRepository.findByNameCn(archetypeName))
            .orElseThrow(() -> new ResourceNotFoundException("角色原型不存在: " + archetypeName));
        return archetype.getTemplate();
    }

    /**
     * 生成基于原型的角色提示词
     * 
     * 用于AI生成角色时提供原型特征参考
     */
    public String generatePrompt(String archetypeName) {
        CharacterArchetype archetype = archetypeRepository.findByName(archetypeName)
            .or(() -> archetypeRepository.findByNameCn(archetypeName))
            .orElseThrow(() -> new ResourceNotFoundException("角色原型不存在: " + archetypeName));

        StringBuilder prompt = new StringBuilder();
        prompt.append("角色原型: ").append(archetype.getNameCn()).append("\n");
        prompt.append("描述: ").append(archetype.getDescription()).append("\n");
        
        Map<String, Object> template = archetype.getTemplate();
        if (template != null) {
            if (template.containsKey("traits")) {
                prompt.append("典型特征: ").append(template.get("traits")).append("\n");
            }
            if (template.containsKey("function")) {
                prompt.append("叙事功能: ").append(template.get("function")).append("\n");
            }
        }

        String[] examples = archetype.getExamples();
        if (examples != null && examples.length > 0) {
            prompt.append("示例: ").append(String.join(", ", examples)).append("\n");
        }

        return prompt.toString();
    }
}
