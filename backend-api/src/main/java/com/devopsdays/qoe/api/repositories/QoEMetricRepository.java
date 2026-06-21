package com.devopsdays.qoe.api.repositories;

import com.devopsdays.qoe.api.models.QoEMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface QoEMetricRepository extends JpaRepository<QoEMetric, Long> {

    List<QoEMetric> findByPlatformAndVideoId(String platform, String videoId);

    List<QoEMetric> findBySessionId(String sessionId);

    List<QoEMetric> findByPlatformAndVideoIdAndTimestampBetween(
        String platform, String videoId, Instant startTime, Instant endTime
    );

    @Query("SELECT m FROM QoEMetric m WHERE m.platform = :platform AND m.timestamp >= :startTime AND m.timestamp <= :endTime")
    List<QoEMetric> findByPlatformAndTimeRange(
        @Param("platform") String platform,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );
}
