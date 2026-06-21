package com.devopsdays.qoe.tests.validation;

import com.devopsdays.qoe.framework.models.QoEMetric;
import com.devopsdays.qoe.framework.models.ValidationResult;
import com.devopsdays.qoe.framework.models.ValidationRule;
import com.devopsdays.qoe.framework.validation.ValidationEngine;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

public class QoEValidationTest {
    
    @Test
    public void testValidationWithGoodMetrics() {
        ValidationEngine engine = new ValidationEngine();
        List<QoEMetric> metrics = createGoodMetrics();
        List<ValidationRule> rules = createDefaultRules();
        
        ValidationResult result = engine.validate(metrics, rules);
        
        Assert.assertTrue(result.isPassed(), "Validation should pass with good metrics");
        Assert.assertTrue(result.getQualityScore() > 0.7, "Quality score should be good");
    }
    
    @Test
    public void testValidationWithPoorMetrics() {
        ValidationEngine engine = new ValidationEngine();
        List<QoEMetric> metrics = createPoorMetrics();
        List<ValidationRule> rules = createDefaultRules();
        
        ValidationResult result = engine.validate(metrics, rules);
        
        Assert.assertFalse(result.isPassed(), "Validation should fail with poor metrics");
        Assert.assertNotNull(result.getFailureReason(), "Failure reason should be set");
    }
    
    @Test
    public void testStartupTimeRule() {
        ValidationEngine engine = new ValidationEngine();
        List<QoEMetric> metrics = new ArrayList<>();
        QoEMetric metric = new QoEMetric();
        metric.setStartupTime(5000L); // 5 seconds - should fail
        metric.setDuration(120.0);
        metrics.add(metric);
        
        List<ValidationRule> rules = new ArrayList<>();
        ValidationRule rule = new ValidationRule("startup-rule", "Startup Time", "startupTime", 3000, "Startup time must be < 3s");
        rules.add(rule);
        
        ValidationResult result = engine.validate(metrics, rules);
        Assert.assertFalse(result.isPassed(), "Should fail with startup time > 3s");
    }
    
    private List<QoEMetric> createGoodMetrics() {
        List<QoEMetric> metrics = new ArrayList<>();
        QoEMetric metric = new QoEMetric();
        metric.setPlatform("web");
        metric.setVideoId("test-1");
        metric.setStartupTime(1500L);
        metric.setTotalBufferingTime(1.0);
        metric.setErrorCount(0);
        metric.setDuration(120.0);
        metric.setPlaybackQuality("excellent");
        metrics.add(metric);
        return metrics;
    }
    
    private List<QoEMetric> createPoorMetrics() {
        List<QoEMetric> metrics = new ArrayList<>();
        QoEMetric metric = new QoEMetric();
        metric.setPlatform("web");
        metric.setVideoId("test-1");
        metric.setStartupTime(5000L);
        metric.setTotalBufferingTime(15.0);
        metric.setErrorCount(5);
        metric.setDuration(120.0);
        metric.setPlaybackQuality("poor");
        metrics.add(metric);
        return metrics;
    }
    
    private List<ValidationRule> createDefaultRules() {
        List<ValidationRule> rules = new ArrayList<>();
        rules.add(new ValidationRule("startup-rule", "Startup Time", "startupTime", 3000, "Startup time must be < 3s"));
        rules.add(new ValidationRule("buffering-rule", "Buffering Time", "bufferingTime", 5.0, "Buffering time must be < 5s"));
        rules.add(new ValidationRule("error-rule", "Error Count", "errorCount", 2, "Error count must be < 2"));
        rules.add(new ValidationRule("quality-rule", "Quality Score", "qualityScore", 0.7, "Quality score must be >= 0.7"));
        return rules;
    }
}
