package com.devopsdays.qoe.api.e2e;

import com.devopsdays.qoe.api.models.Platform;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.qameta.allure.junit5.AllureJunit5;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.RestAssured;
import io.restassured.config.JsonConfig;
import io.restassured.http.ContentType;
import io.restassured.path.json.config.JsonPathConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;

/**
 * QoE API — E2E test suite.
 *
 * Test groups:
 *  @Tag("BAT")        Build Acceptance Tests — run first, gate deployment.
 *  @Tag("Smoke")      Smoke tests — run after BAT gate passes.
 *  @Tag("Regression") Full regression — run on-demand / nightly.
 */
@Tag("e2e")
@Epic("QoE API")
@Execution(ExecutionMode.SAME_THREAD)   // shared Testcontainers DB — keep sequential within each JVM fork
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(AllureJunit5.class)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class QoEApiE2EIT {

    static {
        if (System.getProperty("DOCKER_HOST") == null && System.getenv("DOCKER_HOST") == null) {
            String rawSocket = System.getProperty("user.home")
                    + "/Library/Containers/com.docker.docker/Data/docker.raw.sock";
            if (new File(rawSocket).exists()) {
                System.setProperty("DOCKER_HOST", "unix://" + rawSocket);
                System.setProperty("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock");
            }
        }
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("qoe_db")
            .withUsername("qoe_user")
            .withPassword("qoe_password");

    @LocalServerPort
    int port;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void configureRestAssured() {
        RestAssured.reset();
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        // Label HTTP response attachments as "Response" instead of "HTTP/1.1 200"
        RestAssured.filters(new AllureRestAssured()
                .setRequestAttachmentName("Request")
                .setResponseAttachmentName("Response"));
        RestAssured.config = RestAssured.config().jsonConfig(
                JsonConfig.jsonConfig().numberReturnType(JsonPathConfig.NumberReturnType.DOUBLE));
    }

    // ── Step helpers ──────────────────────────────────────────────────────────

    private static Map<String, Object> metricPayload(String platform, String videoId, String sessionId) {
        return Map.of(
                "platform",  platform,
                "videoId",   videoId,
                "sessionId", sessionId,
                "timestamp", "2024-06-01T10:00:00Z",
                "metrics", Map.of(
                        "playbackState",      "playing",
                        "currentTime",        30.0,
                        "duration",           300.0,
                        "startupTime",        600,
                        "totalBufferingTime", 0.5,
                        "errorCount",         0));
    }

    private String ingestMetric(String platform, String videoId, String sessionId) {
        return Allure.step("POST /api/v1/metrics — ingest metric for platform '" + platform + "'", () ->
                given().contentType(ContentType.JSON)
                       .body(metricPayload(platform, videoId, sessionId))
                       .when().post("/api/v1/metrics")
                       .then()
                       .statusCode(201)
                       .body("sessionId", equalTo(sessionId))
                       .body("platform",  equalTo(platform))
                       .extract().path("sessionId"));
    }

    private void queryMetricsBySession(String sessionId) {
        Allure.step("GET /api/v1/metrics?sessionId=" + sessionId + " — verify metric stored", () ->
                given().queryParam("sessionId", sessionId)
                       .when().get("/api/v1/metrics")
                       .then()
                       .statusCode(200)
                       .body("$", hasSize(greaterThanOrEqualTo(1))));
    }

    private void queryMetricsSummary(String platform, String videoId) {
        Allure.step("GET /api/v1/metrics/summary — verify summary for " + platform + "/" + videoId, () ->
                given().queryParam("platform", platform)
                       .queryParam("videoId",  videoId)
                       .when().get("/api/v1/metrics/summary")
                       .then().statusCode(200));
    }

    private String createPipelineRun(String githubRunId) {
        return Allure.step("POST /api/v1/pipeline-runs — create run '" + githubRunId + "'", () ->
                given().contentType(ContentType.JSON)
                       .body(Map.of("githubRunId", githubRunId))
                       .when().post("/api/v1/pipeline-runs")
                       .then()
                       .statusCode(201)
                       .body("status", equalTo("PENDING"))
                       .extract().path("runId"));
    }

    private void recordPlatformResult(String runId, String platform, int passed, int failed, int total) {
        Allure.step("POST /pipeline-runs/{runId}/platforms — record " + platform
                    + " (" + passed + "/" + total + " passed)", () ->
                given().pathParam("runId", runId)
                       .contentType(ContentType.JSON)
                       .body(Map.of("platform", platform,
                               "passed", passed, "failed", failed, "skipped", 0, "total", total))
                       .when().post("/api/v1/pipeline-runs/{runId}/platforms")
                       .then()
                       .statusCode(200)
                       .body("platform", equalTo(platform)));
    }

    private void finalizePipelineRun(String runId, String expectedStatus, double expectedPassRate) {
        Allure.step("POST /pipeline-runs/{runId}/finalize — expect " + expectedStatus, () ->
                given().pathParam("runId", runId)
                       .when().post("/api/v1/pipeline-runs/{runId}/finalize")
                       .then()
                       .statusCode(200)
                       .body("status", equalTo(expectedStatus))
                       .body("overallPassRate", closeTo(expectedPassRate, 0.001)));
    }

    // ── BAT — Build Acceptance Tests ──────────────────────────────────────────

    @Test
    @Tag("BAT")
    @Feature("Actuator")
    @Story("Health")
    @DisplayName("[BAT] Health endpoint returns UP")
    void healthEndpointReturnsUp() {
        Allure.step("GET /actuator/health — assert status is UP", () ->
                given().when().get("/actuator/health")
                       .then()
                       .statusCode(200)
                       .body("status", equalTo("UP")));
    }

    @Test
    @Tag("BAT")
    @Feature("Platforms")
    @Story("Full list")
    @DisplayName("[BAT] Platform catalogue lists all known platforms")
    void listPlatformsReturnsAllPlatforms() {
        Allure.step("GET /api/v1/platforms — assert all " + Platform.values().length + " platforms returned", () ->
                given().accept(ContentType.JSON)
                       .when().get("/api/v1/platforms")
                       .then()
                       .statusCode(200)
                       .body("$", hasSize(Platform.values().length)));
    }

    @Test
    @Tag("BAT")
    @Feature("Videos")
    @Story("Catalog")
    @DisplayName("[BAT] Video catalog endpoint returns a JSON array")
    void listVideosReturnsJsonArray() {
        Allure.step("GET /api/v1/videos — assert JSON array response", () ->
                given().accept(ContentType.JSON)
                       .when().get("/api/v1/videos")
                       .then()
                       .statusCode(200)
                       .contentType(ContentType.JSON)
                       .body("$", instanceOf(Iterable.class)));
    }

    @Test
    @Tag("BAT")
    @Feature("Pipeline acceptance")
    @Story("Multi-platform aggregate gate")
    @DisplayName("[BAT] Multi-platform pipeline run meets quality gate (≥80%)")
    void pipelineMultiPlatformAggregateMeetsGate() {
        String runId = createPipelineRun("bat-multi-" + UUID.randomUUID().toString().substring(0, 8));

        for (Platform p : new Platform[]{
                Platform.WEB, Platform.IPHONE, Platform.ANDROID_PHONE,
                Platform.APPLE_TV, Platform.ANDROID_TV
        }) {
            recordPlatformResult(runId, p.getKey(), 85, 15, 100);
        }

        Allure.step("Finalize run — expect RELEASED with 5 platforms", () ->
                given().pathParam("runId", runId)
                       .when().post("/api/v1/pipeline-runs/{runId}/finalize")
                       .then()
                       .statusCode(200)
                       .body("status",    equalTo("RELEASED"))
                       .body("platforms", hasSize(5)));
    }

    // ── Smoke Tests ───────────────────────────────────────────────────────────

    @Test
    @Tag("Smoke")
    @Feature("OpenAPI")
    @Story("Discovery")
    @DisplayName("[Smoke] OpenAPI docs endpoint is available")
    void openApiDocsAvailable() {
        Allure.step("GET /v3/api-docs — assert OpenAPI spec is present", () ->
                given().when().get("/v3/api-docs")
                       .then()
                       .statusCode(200)
                       .contentType(containsString("json"))
                       .body("openapi", notNullValue())
                       .body("paths",   notNullValue()));
    }

    @Test
    @Tag("Smoke")
    @Feature("Metrics")
    @Story("Unknown platform rejected")
    @DisplayName("[Smoke] Ingesting metric for unknown platform returns 400")
    void ingestMetricUnknownPlatformReturns400() {
        Allure.step("POST /api/v1/metrics with platform='tvos' — assert 400 with error", () ->
                given().contentType(ContentType.JSON)
                       .body(metricPayload("tvos", "demo-1", "bad-session"))
                       .when().post("/api/v1/metrics")
                       .then()
                       .statusCode(400)
                       .body("error", containsString("Unknown platform")));
    }

    @Test
    @Tag("Smoke")
    @Feature("Pipeline acceptance")
    @Story("Unknown platform rejected")
    @DisplayName("[Smoke] Recording unknown platform in pipeline run returns 400")
    void pipelinePlatformUnknownReturns400() {
        String runId = createPipelineRun("smoke-bad-" + UUID.randomUUID().toString().substring(0, 8));

        Allure.step("POST /pipeline-runs/{runId}/platforms with platform='tvos' — assert 400", () ->
                given().pathParam("runId", runId)
                       .contentType(ContentType.JSON)
                       .body(Map.of("platform", "tvos", "passed", 5, "failed", 0, "skipped", 0, "total", 5))
                       .when().post("/api/v1/pipeline-runs/{runId}/platforms")
                       .then()
                       .statusCode(400)
                       .body("error", containsString("Unknown platform")));
    }

    @ParameterizedTest(name = "metrics ingest + query for platform {0}")
    @EnumSource(Platform.class)
    @Tag("Smoke")
    @Feature("Metrics")
    @Story("Multi-platform ingest and query")
    @DisplayName("[Smoke] Ingest + query metric for each platform")
    void ingestAndQueryMetricForEachPlatform(Platform platform) {
        String sessionId = "smoke-" + platform.getKey() + "-" + UUID.randomUUID();

        ingestMetric(platform.getKey(), "demo-1", sessionId);
        queryMetricsBySession(sessionId);
        queryMetricsSummary(platform.getKey(), "demo-1");
    }

    // ── Regression Tests ──────────────────────────────────────────────────────

    @ParameterizedTest(name = "platform list filtered by category {0}")
    @EnumSource(Platform.Category.class)
    @Tag("Regression")
    @Feature("Platforms")
    @Story("Filter by category")
    @DisplayName("[Regression] Platform list filtered by category returns non-empty results")
    void listPlatformsByCategoryReturnsNonEmpty(Platform.Category category) {
        Allure.step("GET /api/v1/platforms?category=" + category + " — assert non-empty", () ->
                given().accept(ContentType.JSON)
                       .queryParam("category", category.name())
                       .when().get("/api/v1/platforms")
                       .then()
                       .statusCode(200)
                       .body("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @ParameterizedTest(name = "validation run for platform {0}")
    @EnumSource(value = Platform.class, names = {
            "WEB", "IPHONE", "ANDROID_PHONE", "APPLE_TV", "ANDROID_TV", "FIRE_TV", "ROKU"
    })
    @Tag("Regression")
    @Feature("Validation")
    @Story("Multi-platform validation run")
    @DisplayName("[Regression] Validation run completes for selected platforms")
    void validationRunForPlatform(Platform platform) {
        String sessionId = "reg-val-" + platform.getKey() + "-" + UUID.randomUUID();

        Allure.step("Ingest metric for " + platform, () ->
                given().contentType(ContentType.JSON)
                       .body(metricPayload(platform.getKey(), "demo-2", sessionId))
                       .when().post("/api/v1/metrics")
                       .then().statusCode(201));

        Allure.step("Create validation rule for " + platform, () ->
                given().contentType(ContentType.JSON)
                       .body(Map.of("ruleId", "startup-" + platform.getKey(),
                               "type", "startupTime", "threshold", 5000))
                       .when().post("/api/v1/validation/rules")
                       .then().statusCode(201));

        Allure.step("Run validation — expect session " + sessionId + " in result", () ->
                given().contentType(ContentType.JSON)
                       .body(Map.of("videoId", "demo-2",
                               "platform", platform.getKey(), "sessionId", sessionId))
                       .when().post("/api/v1/validation/run")
                       .then()
                       .statusCode(201)
                       .body("sessionId", equalTo(sessionId)));
    }

    @ParameterizedTest(name = "pipeline gate for platform {0}")
    @EnumSource(value = Platform.class, names = {
            "WEB", "IPHONE", "IPAD", "ANDROID_PHONE", "ANDROID_TABLET",
            "APPLE_TV", "ANDROID_TV", "FIRE_TV", "SAMSUNG_TV", "LG_TV",
            "ROKU", "CHROMECAST", "XBOX", "PLAYSTATION",
            "DESKTOP_MACOS", "DESKTOP_WINDOWS", "DESKTOP_LINUX",
            "API", "AUTOMATION"
    })
    @Tag("Regression")
    @Feature("Pipeline acceptance")
    @Story("Single platform gate")
    @DisplayName("[Regression] Single-platform pipeline run is RELEASED with 90% pass rate")
    void pipelineRunPerPlatform(Platform platform) {
        String runId = createPipelineRun("reg-" + platform.getKey());

        recordPlatformResult(runId, platform.getKey(), 90, 10, 100);
        finalizePipelineRun(runId, "RELEASED", 0.9);
    }
}
