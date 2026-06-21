package com.devopsdays.qoe.framework.models;

public class QoEMetric {
    private String platform;
    private String videoId;
    private String sessionId;
    private String playbackState;
    private Double currentTime;
    private Double duration;
    private Double totalBufferingTime;
    private Long startupTime;
    private Long currentBitrate;
    private Integer bitrateSwitches;
    private Integer errorCount;
    private String playbackQuality;
    
    // Getters and setters
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    
    public String getVideoId() { return videoId; }
    public void setVideoId(String videoId) { this.videoId = videoId; }
    
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    
    public String getPlaybackState() { return playbackState; }
    public void setPlaybackState(String playbackState) { this.playbackState = playbackState; }
    
    public Double getCurrentTime() { return currentTime; }
    public void setCurrentTime(Double currentTime) { this.currentTime = currentTime; }
    
    public Double getDuration() { return duration; }
    public void setDuration(Double duration) { this.duration = duration; }
    
    public Double getTotalBufferingTime() { return totalBufferingTime; }
    public void setTotalBufferingTime(Double totalBufferingTime) { this.totalBufferingTime = totalBufferingTime; }
    
    public Long getStartupTime() { return startupTime; }
    public void setStartupTime(Long startupTime) { this.startupTime = startupTime; }
    
    public Long getCurrentBitrate() { return currentBitrate; }
    public void setCurrentBitrate(Long currentBitrate) { this.currentBitrate = currentBitrate; }
    
    public Integer getBitrateSwitches() { return bitrateSwitches; }
    public void setBitrateSwitches(Integer bitrateSwitches) { this.bitrateSwitches = bitrateSwitches; }
    
    public Integer getErrorCount() { return errorCount; }
    public void setErrorCount(Integer errorCount) { this.errorCount = errorCount; }
    
    public String getPlaybackQuality() { return playbackQuality; }
    public void setPlaybackQuality(String playbackQuality) { this.playbackQuality = playbackQuality; }
}
