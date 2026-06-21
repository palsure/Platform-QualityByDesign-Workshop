package com.devopsdays.qoe.api.e2e;

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
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
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

import static java.util.Map.entry;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Video Catalog API — E2E test suite.
 *
 * Test groups:
 *  @Tag("BAT")        Build Acceptance Tests — core sanity checks after every build.
 *  @Tag("Smoke")      Smoke tests — broader coverage run after BAT gate passes.
 *  @Tag("Regression") Full regression — run on-demand / nightly.
 */
@Tag("e2e")
@Epic("Video Catalog API")
@Execution(ExecutionMode.SAME_THREAD)   // shared Testcontainers DB + @Order — keep sequential within each JVM fork
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(AllureJunit5.class)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Video Catalog API — E2E")
class VideoApiE2EIT {

    // Static initializer runs when the class is LOADED — before JUnit 5 evaluates
    // the @Testcontainers condition. Docker Desktop on Mac uses docker.raw.sock,
    // not the standard /var/run/docker.sock.
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
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void configureRestAssured() {
        RestAssured.reset();
        RestAssured.baseURI = "http://localhost";
        RestAssured.port    = port;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        // Label HTTP response attachments as "Response" instead of "HTTP/1.1 200"
        RestAssured.filters(new AllureRestAssured()
                .setRequestAttachmentName("Request")
                .setResponseAttachmentName("Response"));
        RestAssured.config = RestAssured.config().jsonConfig(
                JsonConfig.jsonConfig().numberReturnType(JsonPathConfig.NumberReturnType.DOUBLE));
    }

    // ── Step helpers ──────────────────────────────────────────────────────────

    private static Map<String, Object> videoPayload(String videoId) {
        return Map.ofEntries(
                entry("videoId",        videoId),
                entry("title",          "Test Video - " + videoId),
                entry("description",    "An E2E test video"),
                entry("thumbnailUrl",   "https://example.com/" + videoId + "/thumb.jpg"),
                entry("hlsManifestUrl", "https://example.com/" + videoId + ".m3u8"),
                entry("dashManifestUrl","https://example.com/" + videoId + ".mpd"),
                entry("duration",       596),
                entry("resolution",     "1920x1080"),
                entry("bitrate",        5000000),
                entry("category",       "movie"),
                entry("genre",          "animation")
        );
    }

    private void createVideo(String videoId) {
        Allure.step("POST /api/v1/videos — create '" + videoId + "'", () ->
                given().contentType(ContentType.JSON)
                       .body(videoPayload(videoId))
                       .when().post("/api/v1/videos")
                       .then().statusCode(201));
    }

    private ValidatableResponse listVideos() {
        return Allure.step("GET /api/v1/videos — list all active videos", () ->
                given().when().get("/api/v1/videos").then().statusCode(200));
    }

    private ValidatableResponse listVideosByCategory(String category) {
        return Allure.step("GET /api/v1/videos?category=" + category + " — filter by category", () ->
                given().queryParam("category", category)
                       .when().get("/api/v1/videos")
                       .then().statusCode(200));
    }

    private ValidatableResponse searchVideos(String query) {
        return Allure.step("GET /api/v1/videos/search?q=" + query + " — full-text search", () ->
                given().queryParam("q", query)
                       .when().get("/api/v1/videos/search")
                       .then().statusCode(200));
    }

    private ValidatableResponse getVideoById(String videoId) {
        return Allure.step("GET /api/v1/videos/" + videoId + " — get video by ID", () ->
                given().when().get("/api/v1/videos/" + videoId).then());
    }

    private ValidatableResponse getManifest(String videoId) {
        return Allure.step("GET /api/v1/videos/" + videoId + "/manifest — get streaming manifest", () ->
                given().when().get("/api/v1/videos/" + videoId + "/manifest").then());
    }

    private ValidatableResponse updateVideo(String videoId, Map<String, Object> payload) {
        return Allure.step("PUT /api/v1/videos/" + videoId + " — update video", () ->
                given().contentType(ContentType.JSON)
                       .body(payload)
                       .when().put("/api/v1/videos/" + videoId)
                       .then());
    }

    private ValidatableResponse deleteVideo(String videoId) {
        return Allure.step("DELETE /api/v1/videos/" + videoId + " — soft-delete video", () ->
                given().when().delete("/api/v1/videos/" + videoId).then());
    }

    // ── BAT — Build Acceptance Tests ──────────────────────────────────────────

    @Test
    @Tag("BAT")
    @Order(1)
    @Feature("List Videos")
    @Story("List all videos returns non-empty catalog")
    @DisplayName("[BAT] GET /videos returns 200 with at least one video")
    void listVideos_returnsNonEmptyCatalog() {
        String videoId = "bat-list-" + UUID.randomUUID().toString().substring(0, 8);

        Allure.step("Setup: seed one video into the catalog", () -> createVideo(videoId));
        Allure.step("Assert: GET /videos returns 200 with ≥1 result", () ->
                listVideos().contentType(ContentType.JSON)
                            .body("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @Tag("BAT")
    @Order(6)
    @Feature("Get Video")
    @Story("Get existing video by ID returns full details")
    @DisplayName("[BAT] GET /videos/{id} returns 200 with correct fields")
    void getVideo_returnsVideoDetails() {
        String videoId = "bat-get-" + UUID.randomUUID().toString().substring(0, 8);

        Allure.step("Setup: create test video", () -> createVideo(videoId));
        Allure.step("Assert: GET /videos/{id} returns all fields correctly", () ->
                getVideoById(videoId)
                        .statusCode(200)
                        .body("videoId",   equalTo(videoId))
                        .body("title",     containsString("Test Video"))
                        .body("category",  equalTo("movie"))
                        .body("genre",     equalTo("animation"))
                        .body("duration",  equalTo(596))
                        .body("bitrate",   equalTo(5000000))
                        .body("active",    equalTo(true))
                        .body("createdAt", notNullValue()));
    }

    @Test
    @Tag("BAT")
    @Order(7)
    @Feature("Get Video")
    @Story("Get non-existent video returns 404")
    @DisplayName("[BAT] GET /videos/{id} returns 404 for unknown id")
    void getVideo_notFound() {
        Allure.step("Assert: unknown video ID returns 404", () ->
                getVideoById("does-not-exist-xyz").statusCode(404));
    }

    @Test
    @Tag("BAT")
    @Order(10)
    @Feature("Create Video")
    @Story("Create a new video returns 201 with persisted data")
    @DisplayName("[BAT] POST /videos creates video and returns 201")
    void createVideo_success() {
        String videoId = "bat-create-" + UUID.randomUUID().toString().substring(0, 8);

        Allure.step("POST /api/v1/videos — create video and assert 201 response", () ->
                given().contentType(ContentType.JSON)
                       .body(videoPayload(videoId))
                       .when().post("/api/v1/videos")
                       .then()
                       .statusCode(201)
                       .body("videoId",  equalTo(videoId))
                       .body("active",   equalTo(true))
                       .body("category", equalTo("movie"))
                       .body("genre",    equalTo("animation")));
    }

    // ── Smoke Tests ───────────────────────────────────────────────────────────

    @Test
    @Tag("Smoke")
    @Order(2)
    @Feature("List Videos")
    @Story("Filter by category returns matching videos only")
    @DisplayName("[Smoke] GET /videos?category=movie returns only movies")
    void listVideos_filterByCategory() {
        String videoId = "smoke-cat-" + UUID.randomUUID().toString().substring(0, 8);

        Allure.step("Setup: create a 'movie' category video", () -> createVideo(videoId));
        Allure.step("Assert: filter by category=movie returns only movies", () ->
                listVideosByCategory("movie")
                        .body("$",          hasSize(greaterThanOrEqualTo(1)))
                        .body("[0].category", equalTo("movie")));
    }

    @Test
    @Tag("Smoke")
    @Order(3)
    @Feature("Search Videos")
    @Story("Search by title keyword returns matching videos")
    @DisplayName("[Smoke] GET /videos/search?q=bunny returns results containing 'bunny'")
    void searchVideos_byTitleKeyword() {
        String videoId = "smoke-search-" + UUID.randomUUID().toString().substring(0, 8);

        Allure.step("Setup: create video titled 'Bunny Tales'", () ->
                Allure.step("POST /api/v1/videos — create searchable video", () ->
                        given().contentType(ContentType.JSON)
                               .body(Map.of(
                                       "videoId", videoId, "title", "Bunny Tales Episode 1",
                                       "description", "A story about rabbits",
                                       "hlsManifestUrl", "https://example.com/bunny.m3u8",
                                       "duration", 300, "category", "show", "genre", "animation"))
                               .when().post("/api/v1/videos")
                               .then().statusCode(201)));

        Allure.step("Assert: search 'Bunny' returns video with matching title", () ->
                searchVideos("Bunny")
                        .body("$",        hasSize(greaterThanOrEqualTo(1)))
                        .body("[0].title", containsString("Bunny")));
    }

    @Test
    @Tag("Smoke")
    @Order(8)
    @Feature("Manifest")
    @Story("Get manifest returns HLS and DASH URLs")
    @DisplayName("[Smoke] GET /videos/{id}/manifest returns hls and dash URLs")
    void getManifest_returnsUrls() {
        String videoId = "smoke-manifest-" + UUID.randomUUID().toString().substring(0, 8);

        Allure.step("Setup: create video with HLS and DASH manifests", () -> createVideo(videoId));
        Allure.step("Assert: manifest returns hls and dash URLs", () ->
                getManifest(videoId)
                        .statusCode(200)
                        .body("videoId", equalTo(videoId))
                        .body("hls",     containsString(".m3u8"))
                        .body("dash",    containsString(".mpd")));
    }

    @Test
    @Tag("Smoke")
    @Order(11)
    @Feature("Create Video")
    @Story("Duplicate videoId returns 400 Bad Request")
    @DisplayName("[Smoke] POST /videos with duplicate videoId returns 400")
    void createVideo_duplicateId_returns400() {
        String videoId = "smoke-dup-" + UUID.randomUUID().toString().substring(0, 8);

        Allure.step("Setup: create video once", () -> createVideo(videoId));
        Allure.step("Assert: creating same video ID again returns 400", () ->
                given().contentType(ContentType.JSON)
                       .body(videoPayload(videoId))
                       .when().post("/api/v1/videos")
                       .then()
                       .statusCode(400)
                       .body("error", containsString(videoId)));
    }

    @Test
    @Tag("Smoke")
    @Order(13)
    @Feature("Update Video")
    @Story("Update an existing video replaces all fields")
    @DisplayName("[Smoke] PUT /videos/{id} updates title, category and genre")
    void updateVideo_success() {
        String videoId = "smoke-upd-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> updated = Map.of(
                "videoId",        videoId,
                "title",          "Updated Title",
                "description",    "Updated description",
                "hlsManifestUrl", "https://example.com/updated.m3u8",
                "duration",       999,
                "category",       "documentary",
                "genre",          "nature");

        Allure.step("Setup: create video to be updated", () -> createVideo(videoId));
        Allure.step("Assert: PUT /videos/{id} updates all mutable fields", () ->
                updateVideo(videoId, updated)
                        .statusCode(200)
                        .body("title",    equalTo("Updated Title"))
                        .body("category", equalTo("documentary"))
                        .body("genre",    equalTo("nature"))
                        .body("duration", equalTo(999)));
    }

    @Test
    @Tag("Smoke")
    @Order(15)
    @Feature("Delete Video")
    @Story("Delete video returns 204 and video no longer appears in listings")
    @DisplayName("[Smoke] DELETE /videos/{id} soft-deletes the video")
    void deleteVideo_success() {
        String videoId = "smoke-del-" + UUID.randomUUID().toString().substring(0, 8);

        Allure.step("Setup: create video to be deleted", () -> createVideo(videoId));
        Allure.step("Delete: DELETE /videos/{id} returns 204", () ->
                deleteVideo(videoId).statusCode(204));
        Allure.step("Assert: deleted video returns 404 on GET", () ->
                getVideoById(videoId).statusCode(404));
    }

    // ── Regression Tests ──────────────────────────────────────────────────────

    @Test
    @Tag("Regression")
    @Order(4)
    @Feature("Search Videos")
    @Story("Search by description keyword works")
    @DisplayName("[Regression] GET /videos/search?q=rabbits finds videos by description")
    void searchVideos_byDescriptionKeyword() {
        Allure.step("Assert: search by description keyword 'rabbits' returns results", () ->
                searchVideos("rabbits").body("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @Tag("Regression")
    @Order(5)
    @Feature("Search Videos")
    @Story("Empty search term returns all active videos")
    @DisplayName("[Regression] GET /videos/search?q= (blank) returns all active videos")
    void searchVideos_blankQueryReturnsAll() {
        Allure.step("Assert: blank search query returns all active videos", () ->
                searchVideos("").body("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @Tag("Regression")
    @Order(9)
    @Feature("Manifest")
    @Story("Get manifest for non-existent video returns 404")
    @DisplayName("[Regression] GET /videos/{id}/manifest returns 404 for unknown id")
    void getManifest_notFound() {
        Allure.step("Assert: manifest for unknown video ID returns 404", () ->
                getManifest("unknown-manifest").statusCode(404));
    }

    @Test
    @Tag("Regression")
    @Order(12)
    @Feature("Create Video")
    @Story("Missing required fields returns 400 validation error")
    @DisplayName("[Regression] POST /videos with missing videoId returns 400")
    void createVideo_missingRequiredField_returns400() {
        Allure.step("Assert: creating video without required 'videoId' returns 400", () ->
                given().contentType(ContentType.JSON)
                       .body(Map.of("title", "No ID Video"))
                       .when().post("/api/v1/videos")
                       .then().statusCode(400));
    }

    @Test
    @Tag("Regression")
    @Order(14)
    @Feature("Update Video")
    @Story("Update non-existent video returns 404")
    @DisplayName("[Regression] PUT /videos/{id} returns 404 for unknown id")
    void updateVideo_notFound() {
        Allure.step("Assert: updating a video that doesn't exist returns 404", () ->
                updateVideo("ghost-id", videoPayload("ghost-id"))
                        .statusCode(404)
                        .body("error", containsString("ghost-id")));
    }

    @Test
    @Tag("Regression")
    @Order(16)
    @Feature("Delete Video")
    @Story("Deleted video no longer appears in list")
    @DisplayName("[Regression] GET /videos does not return soft-deleted videos")
    void deleteVideo_disappearsFromList() {
        String videoId = "reg-del-list-" + UUID.randomUUID().toString().substring(0, 8);

        Allure.step("Setup: create video", () -> createVideo(videoId));
        Allure.step("Delete: soft-delete the video", () -> deleteVideo(videoId).statusCode(204));
        Allure.step("Assert: deleted video is absent from GET /videos listing", () ->
                listVideos().body("videoId.flatten()",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(videoId))));
    }

    @Test
    @Tag("Regression")
    @Order(17)
    @Feature("Delete Video")
    @Story("Delete non-existent video returns 404")
    @DisplayName("[Regression] DELETE /videos/{id} returns 404 for unknown id")
    void deleteVideo_notFound() {
        Allure.step("Assert: deleting a video that doesn't exist returns 404", () ->
                deleteVideo("no-such-video").statusCode(404));
    }
}
