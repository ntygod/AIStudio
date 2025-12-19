package com.inkflow.module.wiki.service;

import com.inkflow.common.exception.BusinessException;
import com.inkflow.common.exception.ResourceNotFoundException;
import com.inkflow.module.wiki.dto.*;
import com.inkflow.module.wiki.entity.WikiEntry;
import com.inkflow.module.wiki.event.WikiEntryChangedEvent;
import com.inkflow.module.wiki.repository.WikiEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 知识条目服务
 * 
 * 提供知识条目的CRUD操作，并在内容变更时触发事件
 */
@Service
@Transactional
public class WikiEntryService {

    private static final Logger log = LoggerFactory.getLogger(WikiEntryService.class);

    private final WikiEntryRepository wikiEntryRepository;
    private final ApplicationEventPublisher eventPublisher;

    public WikiEntryService(WikiEntryRepository wikiEntryRepository,
                           ApplicationEventPublisher eventPublisher) {
        this.wikiEntryRepository = wikiEntryRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 创建知识条目
     */
    public WikiEntryDto create(CreateWikiEntryRequest request) {
        // 检查标题是否已存在
        if (wikiEntryRepository.existsByProjectIdAndTitle(request.projectId(), request.title())) {
            throw new BusinessException("知识条目标题已存在: " + request.title());
        }

        WikiEntry entry = new WikiEntry();
        entry.setProjectId(request.projectId());
        entry.setTitle(request.title());
        entry.setType(request.type());
        entry.setContent(request.content());
        entry.setAliases(request.aliases());
        entry.setTags(request.tags());
        entry.setTimeVersion(request.timeVersion());

        WikiEntry saved = wikiEntryRepository.save(entry);
        log.info("创建知识条目: {} (项目: {})", saved.getTitle(), saved.getProjectId());

        // 发布创建事件，触发embedding生成
        eventPublisher.publishEvent(new WikiEntryChangedEvent(
            this, saved.getId(), saved.getProjectId(), "CREATED"
        ));

        return WikiEntryDto.from(saved);
    }

    /**
     * 根据ID查询条目
     */
    @Transactional(readOnly = true)
    public WikiEntryDto findById(UUID id) {
        WikiEntry entry = wikiEntryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("知识条目不存在: " + id));
        return WikiEntryDto.from(entry);
    }

    /**
     * 根据项目ID查询所有条目
     */
    @Transactional(readOnly = true)
    public List<WikiEntryDto> findByProjectId(UUID projectId) {
        return wikiEntryRepository.findByProjectIdOrderByUpdatedAtDesc(projectId)
            .stream()
            .map(WikiEntryDto::from)
            .toList();
    }

    /**
     * 根据项目ID和类型查询
     */
    @Transactional(readOnly = true)
    public List<WikiEntryDto> findByProjectIdAndType(UUID projectId, String type) {
        return wikiEntryRepository.findByProjectIdAndType(projectId, type)
            .stream()
            .map(WikiEntryDto::from)
            .toList();
    }

    /**
     * 搜索条目 (关键词搜索)
     */
    @Transactional(readOnly = true)
    public List<WikiEntryDto> search(UUID projectId, String keyword) {
        return wikiEntryRepository.searchByKeyword(projectId, keyword)
            .stream()
            .map(WikiEntryDto::from)
            .toList();
    }

    /**
     * 根据别名搜索
     */
    @Transactional(readOnly = true)
    public List<WikiEntryDto> findByAlias(UUID projectId, String alias) {
        return wikiEntryRepository.findByAlias(projectId, alias)
            .stream()
            .map(WikiEntryDto::from)
            .toList();
    }

    /**
     * 根据标签搜索
     */
    @Transactional(readOnly = true)
    public List<WikiEntryDto> findByTag(UUID projectId, String tag) {
        return wikiEntryRepository.findByTag(projectId, tag)
            .stream()
            .map(WikiEntryDto::from)
            .toList();
    }

    /**
     * 根据时间版本查询
     */
    @Transactional(readOnly = true)
    public List<WikiEntryDto> findByTimeVersion(UUID projectId, String timeVersion) {
        return wikiEntryRepository.findByProjectIdAndTimeVersion(projectId, timeVersion)
            .stream()
            .map(WikiEntryDto::from)
            .toList();
    }

    /**
     * 更新条目
     */
    public WikiEntryDto update(UUID id, UpdateWikiEntryRequest request) {
        WikiEntry entry = wikiEntryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("知识条目不存在: " + id));

        boolean contentChanged = false;

        // 如果更新标题，检查是否与其他条目重名
        if (request.title() != null && !request.title().equals(entry.getTitle())) {
            if (wikiEntryRepository.existsByProjectIdAndTitle(entry.getProjectId(), request.title())) {
                throw new BusinessException("知识条目标题已存在: " + request.title());
            }
            entry.setTitle(request.title());
            contentChanged = true;
        }

        if (request.type() != null) {
            entry.setType(request.type());
        }
        if (request.content() != null) {
            if (!request.content().equals(entry.getContent())) {
                contentChanged = true;
            }
            entry.setContent(request.content());
        }
        if (request.aliases() != null) {
            entry.setAliases(request.aliases());
        }
        if (request.tags() != null) {
            entry.setTags(request.tags());
        }
        if (request.timeVersion() != null) {
            entry.setTimeVersion(request.timeVersion());
        }

        WikiEntry saved = wikiEntryRepository.save(entry);
        log.info("更新知识条目: {} (ID: {})", saved.getTitle(), saved.getId());

        // 如果内容变更，发布事件触发embedding重新生成和一致性检查
        if (contentChanged) {
            eventPublisher.publishEvent(new WikiEntryChangedEvent(
                this, saved.getId(), saved.getProjectId(), "UPDATED"
            ));
        }

        return WikiEntryDto.from(saved);
    }

    /**
     * 添加别名
     */
    public WikiEntryDto addAlias(UUID id, String alias) {
        WikiEntry entry = wikiEntryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("知识条目不存在: " + id));

        entry.addAlias(alias);
        WikiEntry saved = wikiEntryRepository.save(entry);
        
        log.info("添加别名: {} -> {}", entry.getTitle(), alias);
        return WikiEntryDto.from(saved);
    }

    /**
     * 添加标签
     */
    public WikiEntryDto addTag(UUID id, String tag) {
        WikiEntry entry = wikiEntryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("知识条目不存在: " + id));

        entry.addTag(tag);
        WikiEntry saved = wikiEntryRepository.save(entry);
        
        log.info("添加标签: {} -> {}", entry.getTitle(), tag);
        return WikiEntryDto.from(saved);
    }

    /**
     * 删除条目 (软删除)
     */
    public void delete(UUID id) {
        WikiEntry entry = wikiEntryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("知识条目不存在: " + id));

        entry.softDelete();
        wikiEntryRepository.save(entry);
        
        log.info("删除知识条目: {} (ID: {})", entry.getTitle(), id);

        // 发布删除事件
        eventPublisher.publishEvent(new WikiEntryChangedEvent(
            this, id, entry.getProjectId(), "DELETED"
        ));
    }

    /**
     * 统计项目中的条目数量
     */
    @Transactional(readOnly = true)
    public long countByProjectId(UUID projectId) {
        return wikiEntryRepository.countByProjectId(projectId);
    }

    /**
     * 按类型统计条目数量
     */
    @Transactional(readOnly = true)
    public long countByType(UUID projectId, String type) {
        return wikiEntryRepository.countByProjectIdAndType(projectId, type);
    }

    /**
     * 获取项目中所有不同的类型
     */
    @Transactional(readOnly = true)
    public List<String> getDistinctTypes(UUID projectId) {
        return wikiEntryRepository.findDistinctTypesByProjectId(projectId);
    }

    /**
     * 获取项目中所有不同的时间版本
     */
    @Transactional(readOnly = true)
    public List<String> getDistinctTimeVersions(UUID projectId) {
        return wikiEntryRepository.findDistinctTimeVersionsByProjectId(projectId);
    }
}
