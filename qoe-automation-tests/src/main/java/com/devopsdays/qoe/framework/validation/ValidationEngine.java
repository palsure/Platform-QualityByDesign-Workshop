package com.devopsdays.qoe.framework.validation;

import com.devopsdays.qoe.framework.models.QoEMetric;
import com.devopsdays.qoe.framework.models.ValidationRule;
import com.devopsdays.qoe.framework.models.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ValidationEngine {
    
    public ValidationResult validate(List<QoEMetric> metrics, List<ValidationRule> rules) {
        ValidationResult result = new ValidationResult();
        result.setPassed(true);
        List<String> failures = new ArrayList<>();
        
        if (metrics == null || metrics.isEmpty()) {
            result.setPassed(false);
            result.setFailureReason("No metrics provided for validation");
            return result;
        }
        
        for (ValidationRule rule : rules) {
            boolean rulePassed = validateRule(metrics, rule);
            if (!rulePassed) {
                result.setPassed(false);
                failures.add(rule.getName() + ": " + rule.getDescription());
            }
        }
        
        if (!failures.isEmpty()) {
            result.setFailureReason(String.join("; ", failures));
        }
        
        result.setQualityScore(calculateQualityScore(metrics));
        return result;
    }
    
    private boolean validateRule(List<QoEMetric> metrics, ValidationRule rule) {
        String ruleType = rule.getType();
        double threshold = rule.getThreshold();
        
        switch (ruleType) {
            case "startupTime":
                double avgStartup = metrics.stream()
                        .filter(m -> m.getStartupTime() != null)
                        .mapToLong(m -> m.getStartupTime())
                        .average()
                        .orElse(0.0);
                return avgStartup <= threshold;
                
            case "bufferingTime":
                double avgBuffering = metrics.stream()
                        .filter(m -> m.getTotalBufferingTime() != null)
                        .mapToDouble(m -> m.getTotalBufferingTime())
                        .average()
                        .orElse(0.0);
                return avgBuffering <= threshold;
                
            case "errorCount":
                int totalErrors = metrics.stream()
                        .filter(m -> m.getErrorCount() != null)
                        .mapToInt(m -> m.getErrorCount())
                        .sum();
                return totalErrors <= threshold;
                
            case "qualityScore":
                double qualityScore = calculateQualityScore(metrics);
                return qualityScore >= threshold;
                
            default:
                return true;
        }
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
                .mapToLong(m -> m.getStartupTime())
                .average()
                .orElse(0.0);
        if (avgStartup > 3000) {
            startupScore = Math.max(0.0, 1.0 - (avgStartup - 3000) / 5000);
        }
        
        // Buffering score (penalize if > 5% of duration)
        double avgDuration = metrics.stream()
                .mapToDouble(m -> m.getDuration())
                .average()
                .orElse(1.0);
        double avgBuffering = metrics.stream()
                .filter(m -> m.getTotalBufferingTime() != null)
                .mapToDouble(m -> m.getTotalBufferingTime())
                .average()
                .orElse(0.0);
        double bufferingRatio = avgBuffering / avgDuration;
        if (bufferingRatio > 0.05) {
            bufferingScore = Math.max(0.0, 1.0 - (bufferingRatio - 0.05) * 10);
        }
        
        // Error score
        int totalErrors = metrics.stream()
                .filter(m -> m.getErrorCount() != null)
                .mapToInt(m -> m.getErrorCount())
                .sum();
        if (totalErrors > 0) {
            errorScore = Math.max(0.0, 1.0 - (totalErrors * 0.1));
        }
        
        // Weighted average
        return (startupScore * 0.3 + bufferingScore * 0.5 + errorScore * 0.2);
    }
}
