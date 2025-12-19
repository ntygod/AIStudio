package com.inkflow.module.ai_bridge.adapter;

import com.inkflow.module.character.dto.CharacterDto;
import com.inkflow.module.character.dto.CreateCharacterRequest;
import com.inkflow.module.character.dto.UpdateCharacterRequest;
import com.inkflow.module.character.service.CharacterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 角色领域适配器
 * 
 * Requirements: 10.2, 10.4
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@Component("character")
@RequiredArgsConstructor
public class CharacterDomainAdapter implements DomainAdapter<CharacterDto> {

    private final CharacterService characterService;

    @Override
    public String getEntityType() {
        return "角色";
    }

    @Override
    public CharacterDto create(Map<String, Object> params) {
        UUID projectId = (UUID) params.get("projectId");
        String name = (String) params.get("name");
        String role = (String) params.getOrDefault("role", "配角");
        String description = (String) params.getOrDefault("description", "");

        CreateCharacterRequest request = new CreateCharacterRequest(
                projectId, name, role, description, null, null, null);

        return characterService.create(request);
    }

    @Override
    public CharacterDto update(UUID id, Map<String, Object> params) {
        UpdateCharacterRequest request = new UpdateCharacterRequest(
                (String) params.get("name"),
                (String) params.get("role"),
                (String) params.get("description"),
                null, null,
                (String) params.get("status"),
                (String) params.get("archetype")
        );

        return characterService.update(id, request);
    }

    @Override
    public Optional<CharacterDto> findById(UUID id) {
        try {
            return Optional.of(characterService.findById(id));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public List<CharacterDto> findByProjectId(UUID projectId) {
        return characterService.findByProjectId(projectId);
    }

    @Override
    public void delete(UUID id) {
        characterService.delete(id);
    }
}
