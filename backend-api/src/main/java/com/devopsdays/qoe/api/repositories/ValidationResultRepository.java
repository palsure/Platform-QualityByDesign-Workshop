package com.devopsdays.qoe.api.repositories;

import com.devopsdays.qoe.api.models.ValidationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ValidationResultRepository extends JpaRepository<ValidationResult, Long> {
    List<ValidationResult> findByVideoIdAndPlatform(String videoId, String platform);
    List<ValidationResult> findBySessionId(String sessionId);
}
