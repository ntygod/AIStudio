package com.inkflow.module.style.controller;

import com.inkflow.module.auth.security.UserPrincipal;
import com.inkflow.module.style.dto.SaveStyleSampleRequest;
import com.inkflow.module.style.dto.StyleSampleDto;
import com.inkflow.module.style.dto.StyleStats;
import com.inkflow.module.style.entity.StyleSample;
import com.inkflow.module.style.service.StyleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 风格学习控制器
 */
@Tag(name = "风格学习", description = "用户写作风格学习和检索 API")
@RestController
@RequestMapping("/api/projects/{projectId}/style")
public class StyleController {

    private final StyleService styleService;

    public StyleController(StyleService styleService) {
        this.styleService = styleService;
    }

    @Operation(summary = "获取风格统计", description = "获取项目的风格学习统计信息")
    @GetMapping("/stats")
    public ResponseEntity<StyleStats> getStyleStats(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(styleService.getStyleStats(projectId));
    }

    @Operation(summary = "获取风格样本列表", description = "获取项目的所有风格样本")
    @GetMapping("/samples")
    public ResponseEntity<List<StyleSampleDto>> getStyleSamples(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserPrincipal user) {
        List<StyleSampleDto> samples = styleService.getStyleSamples(projectId)
            .stream()
            .map(StyleSampleDto::from)
            .toList();
        return ResponseEntity.ok(samples);
    }

    @Operation(summary = "保存风格样本", description = "保存用户对 AI 内容的修改作为风格样本")
    @PostMapping("/samples")
    public Mono<ResponseEntity<StyleSampleDto>> saveStyleSample(
            @PathVariable UUID projectId,
            @Valid @RequestBody SaveStyleSampleRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        return styleService.saveStyleSample(
                projectId,
                request.chapterId(),
                request.originalAI(),
                request.userFinal())
            .map(sample -> ResponseEntity.ok(StyleSampleDto.from(sample)))
            .defaultIfEmpty(ResponseEntity.noContent().build());
    }

    @Operation(summary = "删除风格样本")
    @DeleteMapping("/samples/{sampleId}")
    public ResponseEntity<Void> deleteStyleSample(
            @PathVariable UUID projectId,
            @PathVariable UUID sampleId,
            @AuthenticationPrincipal UserPrincipal user) {
        styleService.deleteStyleSample(sampleId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "检索相似风格样本", description = "根据上下文检索相似的风格样本")
    @PostMapping("/samples/search")
    public Mono<ResponseEntity<List<StyleSampleDto>>> searchSimilarSamples(
            @PathVariable UUID projectId,
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserPrincipal user) {
        String context = (String) request.get("context");
        int limit = request.containsKey("limit") ? (Integer) request.get("limit") : 5;

        return styleService.retrieveSimilarStyleSamples(projectId, context, limit)
            .map(samples -> samples.stream().map(StyleSampleDto::from).toList())
            .map(ResponseEntity::ok);
    }

    @Operation(summary = "计算编辑比例", description = "计算两段文本的编辑比例")
    @PostMapping("/calculate-edit-ratio")
    public ResponseEntity<Map<String, Object>> calculateEditRatio(
            @PathVariable UUID projectId,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserPrincipal user) {
        String original = request.get("original");
        String modified = request.get("modified");
        
        double editRatio = styleService.calculateEditRatio(original, modified);
        boolean shouldSave = styleService.shouldSaveAsStyleSample(original, modified);
        
        return ResponseEntity.ok(Map.of(
            "editRatio", editRatio,
            "shouldSave", shouldSave
        ));
    }
}
