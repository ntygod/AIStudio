package com.inkflow.module.progress.entity;

import com.inkflow.module.project.entity.CreationPhase;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 阶段转换历史实体
 */
@Entity
@Table(name = "phase_transitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PhaseTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "from_phase")
    @Enumerated(EnumType.STRING)
    private CreationPhase fromPhase;

    @Column(name = "to_phase", nullable = false)
    @Enumerated(EnumType.STRING)
    private CreationPhase toPhase;

    @Column(name = "reason")
    private String reason;

    @Column(name = "triggered_by")
    @Builder.Default
    private String triggeredBy = "USER";

    @Column(name = "transitioned_at", nullable = false)
    private LocalDateTime transitionedAt;

    @PrePersist
    protected void onCreate() {
        if (transitionedAt == null) {
            transitionedAt = LocalDateTime.now();
        }
    }
}
