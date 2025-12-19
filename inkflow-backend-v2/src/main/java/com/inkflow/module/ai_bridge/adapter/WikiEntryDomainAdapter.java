package com.inkflow.module.ai_bridge.adapter;

import com.inkflow.module.wiki.dto.CreateWikiEntryRequest;
import com.inkflow.module.wiki.dto.UpdateWikiEntryRequest;
import com.inkflow.module.wiki.dto.WikiEntryDto;
import com.inkflow.module.wiki.service.WikiEntryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 百科条目领域适配器
 * 
 * Requirements: 10.2, 10.4
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@Component("wiki_entry")
@RequiredArgsConstructor
public class WikiEntryDomainAdapter implements DomainAdapter<WikiEntryDto> {

    private final WikiEntryService wikiEntryService;

    @Override
    public String getEntityType() {
        return "百科条目";
    }

    @Override
    public WikiEntryDto create(Map<String, Object> params) {
        UUID projectId = (UUID) params.get("projectId");
        String title = (String) params.get("title");
        String type = (String) params.getOrDefault("type", "concept");
        String content = (String) params.getOrDefault("content", "");

        CreateWikiEntryRequest request = new CreateWikiEntryRequest(
                projectId, title, type, content, null, null, null);

        return wikiEntryService.create(request);
    }

    @Override
    public WikiEntryDto update(UUID id, Map<String, Object> params) {
        UpdateWikiEntryRequest request = new UpdateWikiEntryRequest(
                (String) params.get("title"),
                (String) params.get("type"),
                (String) params.get("content"),
                null, null, null
        );

        return wikiEntryService.update(id, request);
    }

    @Override
    public Optional<WikiEntryDto> findById(UUID id) {
        try {
            return Optional.of(wikiEntryService.findById(id));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public List<WikiEntryDto> findByProjectId(UUID projectId) {
        return wikiEntryService.findByProjectId(projectId);
    }

    @Override
    public void delete(UUID id) {
        wikiEntryService.delete(id);
    }
}
