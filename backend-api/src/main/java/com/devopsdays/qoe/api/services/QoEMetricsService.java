package com.devopsdays.qoe.api.services;

import com.devopsdays.qoe.api.models.Platform;
import com.devopsdays.qoe.api.models.QoEMetric;
import com.devopsdays.qoe.api.repositories.QoEMetricRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newrelic.api.agent.NewRelic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class QoEMetricsService {

    private final QoEMetricRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public QoEMetric saveMetric(Map<String, Object> payload) {
        QoEMetric metric = mapToEntity(payload);
        QoEMetric saved = repository.save(metric);
        log.info("Saved QoE metric: platform={}, videoId={}, sessionId={}", 
                saved.getPlatform(), saved.getVideoId(), saved.getSessionId());
        recordNewRelicEvent(saved);
        return saved;
    }

    /**
     * Emit a {@code QoEMetric} custom event to New Relic Insights.
     *
     * <p>Custom events let us slice playback quality by platform, device, video, and
     * session in NRQL ({@code SELECT * FROM QoEMetric ...}) without polluting Spring
     * transaction traces with thousands of attributes. Only scalar attributes are
     * sent — JSON blobs (bufferingEvents, errors) are skipped because Insights drops
     * any attribute longer than 4096 chars.
     *
     * <p>When the New Relic agent isn't attached (e.g. local dev without a license
     * key) the {@code NewRelic} facade is wired to a no-op implementation, so this
     * call is safe to make unconditionally.
     */
    private void recordNewRelicEvent(QoEMetric m) {
        try {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("platform",            m.getPlatform());
            attrs.put("videoId",             m.getVideoId());
            attrs.put("sessionId",           m.getSessionId());
            attrs.put("playbackState",       m.getPlaybackState());
            attrs.put("playbackQuality",     m.getPlaybackQuality());
            attrs.put("deviceType",          m.getDeviceType());
            attrs.put("os",                  m.getOs());
            attrs.put("browser",             m.getBrowser());
            attrs.put("currentResolution",   m.getCurrentResolution());
            putIfNonNull(attrs, "currentTime",        m.getCurrentTime());
            putIfNonNull(attrs, "duration",           m.getDuration());
            putIfNonNull(attrs, "totalBufferingTime", m.getTotalBufferingTime());
            putIfNonNull(attrs, "startupTime",        m.getStartupTime());
            putIfNonNull(attrs, "currentBitrate",     m.getCurrentBitrate());
            putIfNonNull(attrs, "bitrateSwitches",    m.getBitrateSwitches());
            putIfNonNull(attrs, "errorCount",         m.getErrorCount());
            putIfNonNull(attrs, "framesDropped",      m.getFramesDropped());
            putIfNonNull(attrs, "framesRendered",     m.getFramesRendered());
            putIfNonNull(attrs, "networkSpeed",       m.getNetworkSpeed());
            attrs.values().removeIf(Objects::isNull);

            NewRelic.getAgent().getInsights().recordCustomEvent("QoEMetric", attrs);
        } catch (Exception e) {
            // Never let observability break the request path.
            log.debug("Failed to record New Relic QoEMetric custom event", e);
        }
    }

    private static void putIfNonNull(Map<String, Object> attrs, String key, Object value) {
        if (value != null) attrs.put(key, value);
    }

    public List<QoEMetric> getMetrics(String platform, String videoId, String sessionId, 
                                      Instant startTime, Instant endTime) {
        if (sessionId != null) {
            return repository.findBySessionId(sessionId);
        }
        if (platform != null && videoId != null && startTime != null && endTime != null) {
            return repository.findByPlatformAndVideoIdAndTimestampBetween(platform, videoId, startTime, endTime);
        }
        if (platform != null && startTime != null && endTime != null) {
            return repository.findByPlatformAndTimeRange(platform, startTime, endTime);
        }
        if (platform != null && videoId != null) {
            return repository.findByPlatformAndVideoId(platform, videoId);
        }
        return repository.findAll();
    }

    public Map<String, Object> getSummary(String platform, String videoId) {
        List<QoEMetric> metrics;
        if (platform != null && videoId != null) {
            metrics = repository.findByPlatformAndVideoId(platform, videoId);
        } else {
            metrics = repository.findAll();
        }

        if (metrics.isEmpty()) {
            return Map.of("message", "No metrics found");
        }

        double avgStartupTime = metrics.stream()
                .filter(m -> m.getStartupTime() != null)
                .mapToLong(QoEMetric::getStartupTime)
                .average()
                .orElse(0.0);

        double avgBufferingTime = metrics.stream()
                .filter(m -> m.getTotalBufferingTime() != null)
                .mapToDouble(QoEMetric::getTotalBufferingTime)
                .average()
                .orElse(0.0);

        int totalErrors = metrics.stream()
                .filter(m -> m.getErrorCount() != null)
                .mapToInt(QoEMetric::getErrorCount)
                .sum();

        double avgBitrate = metrics.stream()
                .filter(m -> m.getCurrentBitrate() != null)
                .mapToLong(QoEMetric::getCurrentBitrate)
                .average()
                .orElse(0.0);

        Map<String, Long> qualityDistribution = metrics.stream()
                .filter(m -> m.getPlaybackQuality() != null)
                .collect(Collectors.groupingBy(QoEMetric::getPlaybackQuality, Collectors.counting()));

        return Map.of(
                "totalSessions", metrics.stream().map(QoEMetric::getSessionId).distinct().count(),
                "averageStartupTime", avgStartupTime,
                "averageBufferingTime", avgBufferingTime,
                "totalErrors", totalErrors,
                "averageBitrate", avgBitrate,
                "qualityDistribution", qualityDistribution
        );
    }

    public List<Map<String, Object>> getTrends(String platform, Instant startTime, Instant endTime) {
        List<QoEMetric> metrics = repository.findByPlatformAndTimeRange(platform, startTime, endTime);
        
        // Group by hour
        Map<Instant, List<QoEMetric>> grouped = metrics.stream()
                .collect(Collectors.groupingBy(m -> {
                    Instant timestamp = m.getTimestamp();
                    return timestamp.minusSeconds(timestamp.getEpochSecond() % 3600);
                }));

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<QoEMetric> hourMetrics = entry.getValue();
                    double avgStartup = hourMetrics.stream()
                            .filter(m -> m.getStartupTime() != null)
                            .mapToLong(QoEMetric::getStartupTime)
                            .average()
                            .orElse(0.0);
                    double avgBuffering = hourMetrics.stream()
                            .filter(m -> m.getTotalBufferingTime() != null)
                            .mapToDouble(QoEMetric::getTotalBufferingTime)
                            .average()
                            .orElse(0.0);
                    long errorCount = hourMetrics.stream()
                            .filter(m -> m.getErrorCount() != null)
                            .mapToInt(QoEMetric::getErrorCount)
                            .sum();
                    double errorRate = hourMetrics.isEmpty() ? 0.0 : (double) errorCount / hourMetrics.size();
                    double avgBitrate = hourMetrics.stream()
                            .filter(m -> m.getCurrentBitrate() != null)
                            .mapToLong(QoEMetric::getCurrentBitrate)
                            .average()
                            .orElse(0.0);

                    return Map.<String, Object>of(
                            "timestamp", entry.getKey().toString(),
                            "averageStartupTime", avgStartup,
                            "averageBufferingTime", avgBuffering,
                            "errorRate", errorRate,
                            "averageBitrate", avgBitrate
                    );
                })
                .sorted(Comparator.comparing(m -> (String) m.get("timestamp")))
                .collect(Collectors.toList());
    }

    private QoEMetric mapToEntity(Map<String, Object> payload) {
        QoEMetric.QoEMetricBuilder builder = QoEMetric.builder();

        // Validate and normalise platform — throws IllegalArgumentException on unknown values
        Platform platform = Platform.fromKey((String) payload.get("platform"));
        builder.platform(platform.getKey());
        builder.videoId((String) payload.get("videoId"));
        builder.sessionId((String) payload.get("sessionId"));
        builder.timestamp(Instant.parse((String) payload.get("timestamp")));

        Map<String, Object> deviceInfo = (Map<String, Object>) payload.get("deviceInfo");
        if (deviceInfo != null) {
            builder.deviceType((String) deviceInfo.get("deviceType"));
            builder.os((String) deviceInfo.get("os"));
            builder.browser((String) deviceInfo.get("browser"));
            builder.screenResolution((String) deviceInfo.get("screenResolution"));
        }

        Map<String, Object> metrics = (Map<String, Object>) payload.get("metrics");
        if (metrics != null) {
            builder.playbackState((String) metrics.get("playbackState"));
            builder.currentTime(((Number) metrics.get("currentTime")).doubleValue());
            builder.duration(((Number) metrics.get("duration")).doubleValue());
            
            if (metrics.get("totalBufferingTime") != null) {
                builder.totalBufferingTime(((Number) metrics.get("totalBufferingTime")).doubleValue());
            }
            if (metrics.get("startupTime") != null) {
                builder.startupTime(((Number) metrics.get("startupTime")).longValue());
            }
            if (metrics.get("currentBitrate") != null) {
                builder.currentBitrate(((Number) metrics.get("currentBitrate")).longValue());
            }
            builder.currentResolution((String) metrics.get("currentResolution"));
            if (metrics.get("bitrateSwitches") != null) {
                builder.bitrateSwitches(((Number) metrics.get("bitrateSwitches")).intValue());
            }
            if (metrics.get("errorCount") != null) {
                builder.errorCount(((Number) metrics.get("errorCount")).intValue());
            }
            if (metrics.get("framesDropped") != null) {
                builder.framesDropped(((Number) metrics.get("framesDropped")).intValue());
            }
            if (metrics.get("framesRendered") != null) {
                builder.framesRendered(((Number) metrics.get("framesRendered")).intValue());
            }
            if (metrics.get("networkSpeed") != null) {
                builder.networkSpeed(((Number) metrics.get("networkSpeed")).longValue());
            }
            builder.playbackQuality((String) metrics.get("playbackQuality"));

            try {
                if (metrics.get("bufferingEvents") != null) {
                    builder.bufferingEvents(objectMapper.writeValueAsString(metrics.get("bufferingEvents")));
                }
                if (metrics.get("errors") != null) {
                    builder.errors(objectMapper.writeValueAsString(metrics.get("errors")));
                }
            } catch (JsonProcessingException e) {
                log.error("Error serializing JSON fields", e);
            }
        }

        return builder.build();
    }
}
