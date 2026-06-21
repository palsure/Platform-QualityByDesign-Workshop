package com.devopsdays.qoe.api.pipeline;

import com.devopsdays.qoe.api.models.Platform;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pipeline-runs")
@RequiredArgsConstructor
@Tag(name = "Pipeline acceptance", description = "Record CI platform results and gate releases")
public class PipelineAcceptanceController {

    private final PipelineAcceptanceService pipelineAcceptanceService;

    public record CreateRunRequest(String githubRunId) {}

    public record CreateRunResponse(String runId, String status, double gateThreshold) {}

    @PostMapping
    public ResponseEntity<CreateRunResponse> createRun(@RequestBody(required = false) CreateRunRequest body) {
        PipelineRun run = pipelineAcceptanceService.createRun(body != null ? body.githubRunId() : null);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new CreateRunResponse(run.getRunId(), run.getStatus().name(), run.getGateThreshold())
        );
    }

    public record PlatformResultRequest(
            @NotBlank
            @Schema(description = "Platform key — see GET /api/v1/platforms for the full list",
                    example = "web",
                    allowableValues = {"web","iphone","ipad","androidphone","androidtablet",
                            "appletv","androidtv","firetv","samsungtv","lgtv","roku","chromecast",
                            "xbox","playstation","desktop_macos","desktop_windows","desktop_linux",
                            "api","automation"})
            String platform,
            @NotNull @Min(0) Integer passed,
            @NotNull @Min(0) Integer failed,
            @NotNull @Min(0) Integer skipped,
            @NotNull @Min(0) Integer total
    ) {}

    @PostMapping("/{runId}/platforms")
    public ResponseEntity<PlatformResultDto> addPlatform(
            @PathVariable String runId,
            @Valid @RequestBody PlatformResultRequest req
    ) {
        PipelinePlatformResult saved = pipelineAcceptanceService.recordPlatform(
                runId, req.platform(), req.passed(), req.failed(), req.skipped(), req.total()
        );
        return ResponseEntity.ok(toDto(saved));
    }

    @PostMapping("/{runId}/finalize")
    public ResponseEntity<PipelineRunDto> finalize(@PathVariable String runId) {
        PipelineRun run = pipelineAcceptanceService.finalizeRun(runId);
        return ResponseEntity.ok(toRunDto(run));
    }

    @GetMapping("/{runId}")
    public ResponseEntity<PipelineRunDto> get(@PathVariable String runId) {
        return ResponseEntity.ok(toRunDto(pipelineAcceptanceService.getRun(runId)));
    }

    @GetMapping("/latest")
    public ResponseEntity<PipelineRunDto> latest() {
        return pipelineAcceptanceService.findLatest()
                .map(run -> ResponseEntity.ok(toRunDto(run)))
                .orElse(ResponseEntity.notFound().build());
    }

    private static PlatformResultDto toDto(PipelinePlatformResult r) {
        return new PlatformResultDto(r.getPlatform(), r.getPassed(), r.getFailed(), r.getSkipped(), r.getTotal());
    }

    private static PipelineRunDto toRunDto(PipelineRun run) {
        List<PlatformResultDto> platforms = run.getPlatformResults().stream()
                .map(PipelineAcceptanceController::toDto)
                .toList();
        Double ratePct = run.getOverallPassRate() != null ? run.getOverallPassRate() * 100.0 : null;
        return new PipelineRunDto(
                run.getRunId(),
                run.getStatus().name(),
                run.getGithubRunId(),
                run.getGateThreshold(),
                run.getOverallPassRate(),
                ratePct,
                run.getCreatedAt(),
                run.getFinalizedAt(),
                platforms
        );
    }

    public record PlatformResultDto(String platform, int passed, int failed, int skipped, int total) {}

    public record PipelineRunDto(
            String runId,
            String status,
            String githubRunId,
            double gateThreshold,
            Double overallPassRate,
            Double overallPassRatePercent,
            java.time.Instant createdAt,
            java.time.Instant finalizedAt,
            List<PlatformResultDto> platforms
    ) {}
}
