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

@Entity
@Table(name = "videos")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Video {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String videoId;

    @Column(nullable = false)
    private String title;

    private String description;
    private String thumbnailUrl;
    private String hlsManifestUrl;
    private String dashManifestUrl;
    private Long duration;      // seconds
    private String resolution;
    private Long bitrate;       // bits per second

    /** Broad content type: movie | show | live | sports | documentary */
    private String category;

    /** Descriptive genre: drama, comedy, action, news, etc. */
    private String genre;

    /** Soft-delete / visibility flag */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
