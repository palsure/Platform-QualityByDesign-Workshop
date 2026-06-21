package com.devopsdays.qoe.api.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "pipeline_platform_results",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_pipeline_platform",
                columnNames = {"pipeline_run_id", "platform"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelinePlatformResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pipeline_run_id", nullable = false)
    private PipelineRun pipelineRun;

    @Column(name = "platform", nullable = false, length = 32)
    private String platform;

    @Column(name = "passed", nullable = false)
    private int passed;

    @Column(name = "failed", nullable = false)
    private int failed;

    @Column(name = "skipped", nullable = false)
    private int skipped;

    @Column(name = "total", nullable = false)
    private int total;
}
