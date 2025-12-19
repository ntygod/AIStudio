package com.inkflow.module.progress.entity;

import com.inkflow.module.project.entity.CreationPhase;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 进度快照实体
 */
@Entity
@Table(name = "progress_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgressSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "phase")
    @Enumerated(EnumType.STRING)
    private CreationPhase phase;

    @Column(name = "phase_completion")
    @Builder.Default
    private int phaseCompletion = 0;

    @Column(name = "character_count")
    @Builder.Default
    private long characterCount = 0;

    @Column(name = "wiki_entry_count")
    @Builder.Default
    private long wikiEntryCount = 0;

    @Column(name = "volume_count")
    @Builder.Default
    private long volumeCount = 0;

    @Column(name = "chapter_count")
    @Builder.Default
    private long chapterCount = 0;

    @Column(name = "word_count")
    @Builder.Default
    private long wordCount = 0;

    @Column(name = "plot_loop_count")
    @Builder.Default
    private long plotLoopCount = 0;

    @Column(name = "open_plot_loops")
    @Builder.Default
    private long openPlotLoops = 0;

    @Column(name = "closed_plot_loops")
    @Builder.Default
    private long closedPlotLoops = 0;

    @Column(name = "snapshot_at", nullable = false)
    private LocalDateTime snapshotAt;

    @PrePersist
    protected void onCreate() {
        if (snapshotAt == null) {
            snapshotAt = LocalDateTime.now();
        }
    }
}
