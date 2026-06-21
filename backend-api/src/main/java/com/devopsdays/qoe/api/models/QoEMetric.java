package com.devopsdays.qoe.api.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "qoe_metrics", indexes = {
    @Index(name = "idx_platform_video", columnList = "platform,videoId"),
    @Index(name = "idx_session", columnList = "sessionId"),
    @Index(name = "idx_timestamp", columnList = "timestamp")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QoEMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String platform;

    @Column(nullable = false)
    private String videoId;

    @Column(nullable = false)
    private String sessionId;

    @Column(nullable = false)
    private Instant timestamp;

    // Device info
    private String deviceType;
    private String os;
    private String browser;
    private String screenResolution;

    // Playback metrics
    @Column(nullable = false)
    private String playbackState;

    @Column(name = "playback_position", nullable = false)
    private Double currentTime;

    @Column(nullable = false)
    private Double duration;

    private Double totalBufferingTime;
    private Long startupTime; // milliseconds
    private Long currentBitrate; // bits per second
    private String currentResolution;
    private Integer bitrateSwitches;
    private Integer errorCount;
    private Integer framesDropped;
    private Integer framesRendered;
    private Long networkSpeed; // bits per second
    private String playbackQuality;

    // JSON fields for complex data
    @Column(columnDefinition = "TEXT")
    private String bufferingEvents; // JSON array

    @Column(columnDefinition = "TEXT")
    private String errors; // JSON array

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
