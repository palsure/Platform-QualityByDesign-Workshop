package com.devopsdays.qoe.api.pipeline;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pipeline_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, unique = true, length = 64)
    private String runId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "overall_pass_rate")
    private Double overallPassRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PipelineRunStatus status;

    @Column(name = "github_run_id", length = 64)
    private String githubRunId;

    @Column(name = "gate_threshold", nullable = false)
    private double gateThreshold;

    @OneToMany(mappedBy = "pipelineRun", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PipelinePlatformResult> platformResults = new ArrayList<>();
}
