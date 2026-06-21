package com.devopsdays.qoe.api.services;

import com.devopsdays.qoe.api.models.Platform;
import com.devopsdays.qoe.api.models.QoEMetric;
import com.devopsdays.qoe.api.models.ValidationResult;
import com.devopsdays.qoe.api.repositories.QoEMetricRepository;
import com.devopsdays.qoe.api.repositories.ValidationResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationService {

    private final ValidationResultRepository validationRepository;
    private final QoEMetricRepository metricsRepository;
    private final Map<String, Map<String, Object>> rules = new HashMap<>();

    @Transactional
    public Map<String, Object> createRule(Map<String, Object> ruleData) {
        String ruleId = (String) ruleData.getOrDefault("ruleId", UUID.randomUUID().toString());
        rules.put(ruleId, ruleData);
        log.info("Created validation rule: ruleId={}", ruleId);
        return Map.of("ruleId", ruleId, "rule", ruleData);
    }

    public List<Map<String, Object>> getAllRules() {
        return rules.entrySet().stream()
                .map(entry -> Map.<String, Object>of("ruleId", entry.getKey(), "rule", entry.getValue()))
                .collect(Collectors.toList());
    }

    @Transactional
    public ValidationResult runValidation(Map<String, Object> validationRequest) {
        String videoId = (String) validationRequest.get("videoId");
        Platform pEnum = Platform.fromKey((String) validationRequest.get("platform"));
        String platform = pEnum.getKey();
        String sessionId = (String) validationRequest.get("sessionId");

        List<QoEMetric> metrics = metricsRepository.findBySessionId(sessionId);
        if (metrics.isEmpty()) {
            throw new IllegalArgumentException("No metrics found for session: " + sessionId);
        }

        // Calculate quality score
        double qualityScore = calculateQualityScore(metrics);
        
        // Apply validation rules
        boolean passed = true;
        String failureReason = null;
        Map<String, Object> validationDetails = new HashMap<>();

        for (Map.Entry<String, Map<String, Object>> ruleEntry : rules.entrySet()) {
            Map<String, Object> rule = ruleEntry.getValue();
            String ruleType = (String) rule.get("type");
            Object threshold = rule.get("threshold");

            boolean rulePassed = true;
            switch (ruleType) {
                case "startupTime":
                    double avgStartup = metrics.stream()
                            .filter(m -> m.getStartupTime() != null)
                            .mapToLong(QoEMetric::getStartupTime)
                            .average()
                            .orElse(0.0);
                    rulePassed = avgStartup <= ((Number) threshold).doubleValue();
                    validationDetails.put("startupTime", avgStartup);
                    break;
                case "bufferingTime":
                    double avgBuffering = metrics.stream()
                            .filter(m -> m.getTotalBufferingTime() != null)
                            .mapToDouble(QoEMetric::getTotalBufferingTime)
                            .average()
                            .orElse(0.0);
                    rulePassed = avgBuffering <= ((Number) threshold).doubleValue();
                    validationDetails.put("bufferingTime", avgBuffering);
                    break;
                case "errorCount":
                    int totalErrors = metrics.stream()
                            .filter(m -> m.getErrorCount() != null)
                            .mapToInt(QoEMetric::getErrorCount)
                            .sum();
                    rulePassed = totalErrors <= ((Number) threshold).intValue();
                    validationDetails.put("errorCount", totalErrors);
                    break;
                case "qualityScore":
                    rulePassed = qualityScore >= ((Number) threshold).doubleValue();
                    validationDetails.put("qualityScore", qualityScore);
                    break;
            }

            if (!rulePassed) {
                passed = false;
                failureReason = String.format("Rule %s failed: %s", ruleEntry.getKey(), ruleType);
                break;
            }
        }

        ValidationResult result = ValidationResult.builder()
                .videoId(videoId)
                .platform(platform)
                .sessionId(sessionId)
                .passed(passed)
                .qualityScore(qualityScore)
                .failureReason(failureReason)
                .validationDetails(validationDetails.toString())
                .validatedAt(Instant.now())
                .build();

        ValidationResult saved = validationRepository.save(result);
        log.info("Validation completed: sessionId={}, passed={}, score={}", 
                sessionId, passed, qualityScore);
        return saved;
    }

    public List<ValidationResult> getResults(String videoId, String platform, String sessionId) {
        if (sessionId != null) {
            return validationRepository.findBySessionId(sessionId);
        }
        if (videoId != null && platform != null) {
            return validationRepository.findByVideoIdAndPlatform(videoId, platform);
        }
        return validationRepository.findAll();
    }

    private double calculateQualityScore(List<QoEMetric> metrics) {
        if (metrics.isEmpty()) {
            return 0.0;
        }

        double startupScore = 1.0;
        double bufferingScore = 1.0;
        double errorScore = 1.0;

        // Startup time score (penalize if > 3 seconds)
        double avgStartup = metrics.stream()
                .filter(m -> m.getStartupTime() != null)
                .mapToLong(QoEMetric::getStartupTime)
                .average()
                .orElse(0.0);
        if (avgStartup > 3000) {
            startupScore = Math.max(0.0, 1.0 - (avgStartup - 3000) / 5000);
        }

        // Buffering score (penalize if > 5% of duration)
        double avgDuration = metrics.stream()
                .mapToDouble(QoEMetric::getDuration)
                .average()
                .orElse(1.0);
        double avgBuffering = metrics.stream()
                .filter(m -> m.getTotalBufferingTime() != null)
                .mapToDouble(QoEMetric::getTotalBufferingTime)
                .average()
                .orElse(0.0);
        double bufferingRatio = avgBuffering / avgDuration;
        if (bufferingRatio > 0.05) {
            bufferingScore = Math.max(0.0, 1.0 - (bufferingRatio - 0.05) * 10);
        }

        // Error score
        int totalErrors = metrics.stream()
                .filter(m -> m.getErrorCount() != null)
                .mapToInt(QoEMetric::getErrorCount)
                .sum();
        if (totalErrors > 0) {
            errorScore = Math.max(0.0, 1.0 - (totalErrors * 0.1));
        }

        // Weighted average
        return (startupScore * 0.3 + bufferingScore * 0.5 + errorScore * 0.2);
    }
}
