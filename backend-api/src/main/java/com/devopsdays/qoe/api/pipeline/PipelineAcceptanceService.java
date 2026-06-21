package com.devopsdays.qoe.api.pipeline;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devopsdays.qoe.api.models.Platform;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class PipelineAcceptanceService {

    private final PipelineRunRepository pipelineRunRepository;
    private final PipelinePlatformResultRepository platformResultRepository;
    private final double minPassRate;

    public PipelineAcceptanceService(
            PipelineRunRepository pipelineRunRepository,
            PipelinePlatformResultRepository platformResultRepository,
            @Value("${pipeline.gate.min-pass-rate:0.8}") double minPassRate
    ) {
        this.pipelineRunRepository = pipelineRunRepository;
        this.platformResultRepository = platformResultRepository;
        this.minPassRate = minPassRate;
    }

    @Transactional
    public PipelineRun createRun(String githubRunId) {
        String runId = java.util.UUID.randomUUID().toString();
        Instant now = Instant.now();
        PipelineRun run = PipelineRun.builder()
                .runId(runId)
                .createdAt(now)
                .status(PipelineRunStatus.PENDING)
                .githubRunId(githubRunId)
                .gateThreshold(minPassRate)
                .build();
        return pipelineRunRepository.save(run);
    }

    @Transactional
    public PipelinePlatformResult recordPlatform(String runId, String platform, int passed, int failed, int skipped, int total) {
        // Delegates validation + normalisation to the Platform enum (throws IllegalArgumentException on unknown)
        String p = Platform.fromKey(platform).getKey();
        if (passed < 0 || failed < 0 || skipped < 0 || total < 0) {
            throw new IllegalArgumentException("Counts must be non-negative");
        }
        if (total < passed + failed + skipped) {
            throw new IllegalArgumentException("total must be >= passed + failed + skipped");
        }

        PipelineRun run = pipelineRunRepository.findByRunId(runId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown runId: " + runId));
        if (run.getStatus() != PipelineRunStatus.PENDING) {
            throw new IllegalStateException("Run is already finalized: " + run.getStatus());
        }

        PipelinePlatformResult row = platformResultRepository
                .findByPipelineRun_IdAndPlatform(run.getId(), p)
                .orElseGet(() -> PipelinePlatformResult.builder()
                        .pipelineRun(run)
                        .platform(p)
                        .build());

        row.setPassed(passed);
        row.setFailed(failed);
        row.setSkipped(skipped);
        row.setTotal(total);
        return platformResultRepository.save(row);
    }

    @Transactional
    public PipelineRun finalizeRun(String runId) {
        PipelineRun run = pipelineRunRepository.findByRunId(runId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown runId: " + runId));
        if (run.getStatus() != PipelineRunStatus.PENDING) {
            return run;
        }

        List<PipelinePlatformResult> rows = platformResultRepository.findByPipelineRun_Id(run.getId());
        if (rows.isEmpty()) {
            run.setOverallPassRate(0.0);
            run.setStatus(PipelineRunStatus.BLOCKED);
            run.setFinalizedAt(Instant.now());
            return pipelineRunRepository.save(run);
        }

        int sumPassed = rows.stream().mapToInt(PipelinePlatformResult::getPassed).sum();
        int sumTotal = rows.stream().mapToInt(PipelinePlatformResult::getTotal).sum();
        double rate = sumTotal > 0 ? (double) sumPassed / (double) sumTotal : 0.0;

        run.setOverallPassRate(rate);
        run.setFinalizedAt(Instant.now());
        run.setStatus(rate >= run.getGateThreshold() ? PipelineRunStatus.RELEASED : PipelineRunStatus.BLOCKED);
        pipelineRunRepository.save(run);
        return pipelineRunRepository.findByRunId(runId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public PipelineRun getRun(String runId) {
        PipelineRun run = pipelineRunRepository.findByRunId(runId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown runId: " + runId));
        run.getPlatformResults().size();
        return run;
    }

    @Transactional(readOnly = true)
    public Optional<PipelineRun> findLatest() {
        Optional<PipelineRun> opt = pipelineRunRepository.findTopByOrderByCreatedAtDesc();
        opt.ifPresent(run -> run.getPlatformResults().size());
        return opt;
    }

}
