package com.devopsdays.qoe.api.controllers;

import com.devopsdays.qoe.api.models.QoEMetric;
import com.devopsdays.qoe.api.services.QoEMetricsService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/metrics")
@RequiredArgsConstructor
@Tag(name = "Metrics", description = "Ingest and query QoE session metrics")
public class QoEMetricsController {

    private final QoEMetricsService metricsService;

    @PostMapping
    public ResponseEntity<QoEMetric> ingestMetrics(@Valid @RequestBody Map<String, Object> metricPayload) {
        QoEMetric metric = metricsService.saveMetric(metricPayload);
        return ResponseEntity.status(HttpStatus.CREATED).body(metric);
    }

    @GetMapping
    public ResponseEntity<List<QoEMetric>> getMetrics(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String videoId,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime
    ) {
        List<QoEMetric> metrics = metricsService.getMetrics(platform, videoId, sessionId, startTime, endTime);
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String videoId
    ) {
        Map<String, Object> summary = metricsService.getSummary(platform, videoId);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/trends")
    public ResponseEntity<List<Map<String, Object>>> getTrends(
            @RequestParam String platform,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime
    ) {
        List<Map<String, Object>> trends = metricsService.getTrends(platform, startTime, endTime);
        return ResponseEntity.ok(trends);
    }
}
