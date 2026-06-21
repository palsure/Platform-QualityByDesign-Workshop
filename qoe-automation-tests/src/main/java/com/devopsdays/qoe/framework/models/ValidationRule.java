package com.devopsdays.qoe.framework.models;

public class ValidationRule {
    private String ruleId;
    private String name;
    private String type;
    private double threshold;
    private String description;
    
    public ValidationRule() {}
    
    public ValidationRule(String ruleId, String name, String type, double threshold, String description) {
        this.ruleId = ruleId;
        this.name = name;
        this.type = type;
        this.threshold = threshold;
        this.description = description;
    }
    
    // Getters and setters
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
