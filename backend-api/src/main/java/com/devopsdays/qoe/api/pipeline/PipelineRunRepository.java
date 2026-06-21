package com.devopsdays.qoe.api.pipeline;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PipelineRunRepository extends JpaRepository<PipelineRun, Long> {

    @EntityGraph(attributePaths = "platformResults")
    Optional<PipelineRun> findByRunId(String runId);

    @EntityGraph(attributePaths = "platformResults")
    Optional<PipelineRun> findTopByOrderByCreatedAtDesc();
}
