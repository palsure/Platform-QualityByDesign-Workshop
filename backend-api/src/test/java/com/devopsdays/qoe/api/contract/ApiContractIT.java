package com.devopsdays.qoe.api.contract;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

/**
 * API contract tests — verify stable response shapes across releases.
 * Runs before integration (BAT) in the Quality-by-Design gate chain.
 */
@Tag("contract")
@Epic("StreamApp API")
@Feature("API contract")
@Execution(ExecutionMode.SAME_THREAD)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(AllureJunit5.class)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class ApiContractIT {

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
    }

    @Test
    @DisplayName("GET /actuator/health returns UP status contract")
    void actuatorHealthContract() {
        given()
                .when().get("/actuator/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    @DisplayName("GET /api/v1/videos returns stable catalog shape")
    void videosListContract() {
        given()
                .when().get("/api/v1/videos")
                .then()
                .statusCode(200)
                .body("$", hasSize(org.hamcrest.Matchers.greaterThan(0)))
                .body("[0].id", notNullValue())
                .body("[0].title", notNullValue())
                .body("[0].videoId", notNullValue())
                .body("[0].hlsManifestUrl", notNullValue());
    }

    @Test
    @DisplayName("GET /v3/api-docs exposes OpenAPI contract")
    void openApiDocsContract() {
        given()
                .when().get("/v3/api-docs")
                .then()
                .statusCode(200)
                .body("openapi", notNullValue())
                .body("info.title", notNullValue())
                .body("paths", notNullValue());
    }
}
