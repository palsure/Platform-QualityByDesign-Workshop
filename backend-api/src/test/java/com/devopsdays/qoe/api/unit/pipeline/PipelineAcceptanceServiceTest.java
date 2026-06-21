package com.devopsdays.qoe.api.unit.pipeline;

import com.devopsdays.qoe.api.pipeline.PipelineAcceptanceService;
import com.devopsdays.qoe.api.pipeline.PipelinePlatformResult;
import com.devopsdays.qoe.api.pipeline.PipelinePlatformResultRepository;
import com.devopsdays.qoe.api.pipeline.PipelineRun;
import com.devopsdays.qoe.api.pipeline.PipelineRunRepository;
import com.devopsdays.qoe.api.pipeline.PipelineRunStatus;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.qameta.allure.junit5.AllureJunit5;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@Epic("Pipeline Acceptance")
@ExtendWith({MockitoExtension.class, AllureJunit5.class})
@DisplayName("PipelineAcceptanceService")
class PipelineAcceptanceServiceTest {

    @Mock
    private PipelineRunRepository pipelineRunRepository;

    @Mock
    private PipelinePlatformResultRepository platformResultRepository;

    private PipelineAcceptanceService service;

    @BeforeEach
    void setUp() {
        service = new PipelineAcceptanceService(pipelineRunRepository, platformResultRepository, 0.8);
    }

    // ── finalizeRun ───────────────────────────────────────────────────────────

    @Test
    @Feature("Finalize run")
    @Story("Run is RELEASED when pass rate meets threshold")
    @DisplayName("finalizeRun → RELEASED when pass rate ≥ 0.8 (17/20 = 0.85)")
    void finalizeReleasedWhenPassRateMeetsThreshold() {
        PipelineRun run = Allure.step("Given: a PENDING pipeline run 'run-1'", () ->
                PipelineRun.builder().id(1L).runId("run-1")
                        .createdAt(Instant.now()).status(PipelineRunStatus.PENDING)
                        .gateThreshold(0.8).build());

        Allure.step("Given: mock — repository returns run-1", () ->
                when(pipelineRunRepository.findByRunId("run-1")).thenReturn(Optional.of(run)));

        Allure.step("Given: mock — two platforms: api(8/10) + web(9/10) = 17/20 = 85%", () ->
                when(platformResultRepository.findByPipelineRun_Id(1L)).thenReturn(List.of(
                        platformRow(run, "api", 8, 2, 0, 10),
                        platformRow(run, "web", 9, 1, 0, 10))));

        Allure.step("Given: mock — repository.save returns the passed entity", () ->
                when(pipelineRunRepository.save(any())).thenAnswer(inv -> inv.getArgument(0)));

        PipelineRun out = Allure.step("When: finalize run 'run-1'",
                () -> service.finalizeRun("run-1"));

        Allure.step("Then: status is RELEASED", () ->
                assertThat(out.getStatus()).isEqualTo(PipelineRunStatus.RELEASED));

        Allure.step("Then: overallPassRate is 0.85 (17/20)", () ->
                assertThat(out.getOverallPassRate()).isEqualTo(17.0 / 20.0));
    }

    @Test
    @Feature("Finalize run")
    @Story("Run is BLOCKED when pass rate is below threshold")
    @DisplayName("finalizeRun → BLOCKED when pass rate < 0.8 (5/10 = 0.50)")
    void finalizeBlockedBelowThreshold() {
        PipelineRun run = Allure.step("Given: a PENDING pipeline run 'run-2'", () ->
                PipelineRun.builder().id(2L).runId("run-2")
                        .createdAt(Instant.now()).status(PipelineRunStatus.PENDING)
                        .gateThreshold(0.8).build());

        Allure.step("Given: mock — one platform: api(5/10) = 50%", () -> {
            when(pipelineRunRepository.findByRunId("run-2")).thenReturn(Optional.of(run));
            when(platformResultRepository.findByPipelineRun_Id(2L)).thenReturn(
                    List.of(platformRow(run, "api", 5, 5, 0, 10)));
            when(pipelineRunRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        });

        PipelineRun out = Allure.step("When: finalize run 'run-2'",
                () -> service.finalizeRun("run-2"));

        Allure.step("Then: status is BLOCKED", () ->
                assertThat(out.getStatus()).isEqualTo(PipelineRunStatus.BLOCKED));

        Allure.step("Then: overallPassRate is 0.50", () ->
                assertThat(out.getOverallPassRate()).isEqualTo(0.5));
    }

    // ── recordPlatform ────────────────────────────────────────────────────────

    @Test
    @Feature("Record platform")
    @Story("Unknown platform key is rejected")
    @DisplayName("recordPlatform throws IllegalArgumentException for unknown key 'tvos'")
    void recordPlatformRejectsUnknownName() {
        Allure.step("Assert: recordPlatform with key 'tvos' throws IllegalArgumentException", () ->
                assertThatThrownBy(() -> service.recordPlatform("run-3", "tvos", 1, 0, 0, 1))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("Unknown platform"));
    }

    @ParameterizedTest(name = "recordPlatform accepts {0}")
    @ValueSource(strings = {
            "web", "iphone", "ipad", "androidphone", "androidtablet",
            "appletv", "androidtv", "firetv", "samsungtv", "lgtv",
            "roku", "chromecast", "xbox", "playstation",
            "desktop_macos", "desktop_windows", "desktop_linux",
            "api", "automation"
    })
    @Feature("Record platform")
    @Story("All known platform keys are accepted")
    @DisplayName("recordPlatform accepts all known platform keys")
    void recordPlatformAcceptsAllKnownPlatforms(String platformKey) {
        PipelineRun run = Allure.step("Given: PENDING run 'run-99'", () ->
                PipelineRun.builder().id(99L).runId("run-99")
                        .createdAt(Instant.now()).status(PipelineRunStatus.PENDING)
                        .gateThreshold(0.8).build());

        Allure.step("Given: mock — repository returns run + empty existing result for " + platformKey, () -> {
            when(pipelineRunRepository.findByRunId("run-99")).thenReturn(Optional.of(run));
            when(platformResultRepository.findByPipelineRun_IdAndPlatform(99L, platformKey))
                    .thenReturn(Optional.empty());
            when(platformResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        });

        var result = Allure.step("When: record platform '" + platformKey + "' (9/10 passed)",
                () -> service.recordPlatform("run-99", platformKey, 9, 1, 0, 10));

        Allure.step("Then: returned result has platform = '" + platformKey + "'", () ->
                assertThat(result.getPlatform()).isEqualTo(platformKey));
    }

    // ── createRun ─────────────────────────────────────────────────────────────

    @Test
    @Feature("Create run")
    @Story("New run is persisted with correct threshold and status")
    @DisplayName("createRun persists gate threshold = 0.8 and status = PENDING")
    void createRunPersistsThreshold() {
        Allure.step("Given: mock — repository.save returns the passed entity", () ->
                when(pipelineRunRepository.save(any())).thenAnswer(inv -> inv.getArgument(0)));

        PipelineRun created = Allure.step("When: createRun('gh-99')",
                () -> service.createRun("gh-99"));

        ArgumentCaptor<PipelineRun> cap = ArgumentCaptor.forClass(PipelineRun.class);

        Allure.step("Then: repository.save was called once", () ->
                verify(pipelineRunRepository).save(cap.capture()));

        Allure.step("Then: saved entity has gateThreshold = 0.8", () ->
                assertThat(cap.getValue().getGateThreshold()).isEqualTo(0.8));

        Allure.step("Then: saved entity has githubRunId = 'gh-99'", () ->
                assertThat(cap.getValue().getGithubRunId()).isEqualTo("gh-99"));

        Allure.step("Then: returned run has status = PENDING", () ->
                assertThat(created.getStatus()).isEqualTo(PipelineRunStatus.PENDING));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static PipelinePlatformResult platformRow(
            PipelineRun run, String platform, int p, int f, int s, int t
    ) {
        return PipelinePlatformResult.builder()
                .pipelineRun(run).platform(platform)
                .passed(p).failed(f).skipped(s).total(t)
                .build();
    }
}
