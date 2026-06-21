package com.devopsdays.qoe.api.pipeline;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PipelinePlatformResultRepository extends JpaRepository<PipelinePlatformResult, Long> {

    List<PipelinePlatformResult> findByPipelineRun_Id(Long pipelineRunId);

    Optional<PipelinePlatformResult> findByPipelineRun_IdAndPlatform(Long pipelineRunId, String platform);
}
