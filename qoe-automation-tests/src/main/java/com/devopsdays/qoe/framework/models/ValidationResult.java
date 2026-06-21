package com.devopsdays.qoe.framework.models;

public class ValidationResult {
    private boolean passed;
    private double qualityScore;
    private String failureReason;
    
    // Getters and setters
    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }
    
    public double getQualityScore() { return qualityScore; }
    public void setQualityScore(double qualityScore) { this.qualityScore = qualityScore; }
    
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
}
