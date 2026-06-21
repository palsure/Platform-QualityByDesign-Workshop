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
@Table(name = "validation_results")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String videoId;

    @Column(nullable = false)
    private String platform;

    @Column(nullable = false)
    private String sessionId;

    @Column(nullable = false)
    private Boolean passed;

    private Double qualityScore; // 0.0 to 1.0
    private String failureReason;

    @Column(columnDefinition = "TEXT")
    private String validationDetails; // JSON

    @Column(nullable = false)
    private Instant validatedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
