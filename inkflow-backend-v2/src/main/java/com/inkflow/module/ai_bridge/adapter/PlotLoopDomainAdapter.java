package com.inkflow.module.ai_bridge.adapter;

import com.inkflow.module.plotloop.dto.CreatePlotLoopRequest;
import com.inkflow.module.plotloop.dto.PlotLoopDto;
import com.inkflow.module.plotloop.service.PlotLoopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 伏笔领域适配器
 * 
 * Requirements: 10.2, 10.4
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@Component("plot_loop")
@RequiredArgsConstructor
public class PlotLoopDomainAdapter implements DomainAdapter<PlotLoopDto> {

    private final PlotLoopService plotLoopService;

    @Override
    public String getEntityType() {
        return "伏笔";
    }

    @Override
    public PlotLoopDto create(Map<String, Object> params) {
        UUID projectId = (UUID) params.get("projectId");
        String title = (String) params.get("title");
        String description = (String) params.getOrDefault("description", "");
        UUID introducedChapterId = params.containsKey("introducedChapterId") 
                ? UUID.fromString((String) params.get("introducedChapterId")) 
                : null;

        CreatePlotLoopRequest request = new CreatePlotLoopRequest(
                projectId, title, description, introducedChapterId, null);

        return plotLoopService.create(request);
    }

    @Override
    public PlotLoopDto update(UUID id, Map<String, Object> params) {
        String title = (String) params.get("title");
        String description = (String) params.get("description");

        return plotLoopService.update(id, title, description);
    }

    @Override
    public Optional<PlotLoopDto> findById(UUID id) {
        try {
            return Optional.of(plotLoopService.findById(id));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public List<PlotLoopDto> findByProjectId(UUID projectId) {
        return plotLoopService.findByProjectId(projectId);
    }

    @Override
    public void delete(UUID id) {
        plotLoopService.delete(id);
    }
}
