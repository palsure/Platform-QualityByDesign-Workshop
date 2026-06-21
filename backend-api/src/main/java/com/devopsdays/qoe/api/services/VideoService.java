package com.devopsdays.qoe.api.services;

import com.devopsdays.qoe.api.exceptions.VideoNotFoundException;
import com.devopsdays.qoe.api.models.Video;
import com.devopsdays.qoe.api.models.VideoRequest;
import com.devopsdays.qoe.api.repositories.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoService {

    private final VideoRepository repository;

    /** All active (non-deleted) videos. */
    public List<Video> getAllVideos() {
        return repository.findByActiveTrue();
    }

    /** Single video by its business ID. Returns empty if not found or inactive. */
    public Optional<Video> getVideoById(String videoId) {
        return repository.findByVideoId(videoId)
                .filter(Video::getActive);
    }

    /** Full-text search across title, description, and genre. */
    public List<Video> searchVideos(String query) {
        if (query == null || query.isBlank()) {
            return getAllVideos();
        }
        return repository.search(query.trim());
    }

    /** Filter active videos by category (case-insensitive). */
    public List<Video> getByCategory(String category) {
        return repository.findByCategoryIgnoreCaseAndActiveTrue(category);
    }

    @Transactional
    public Video createVideo(VideoRequest req) {
        if (repository.existsByVideoId(req.videoId())) {
            throw new IllegalArgumentException("Video already exists with id: " + req.videoId());
        }
        Video video = toEntity(req, Video.builder().build());
        Video saved = repository.save(video);
        log.info("Created video: videoId={}, title={}", saved.getVideoId(), saved.getTitle());
        return saved;
    }

    @Transactional
    public Video updateVideo(String videoId, VideoRequest req) {
        Video existing = repository.findByVideoId(videoId)
                .orElseThrow(() -> new VideoNotFoundException(videoId));
        toEntity(req, existing);
        Video saved = repository.save(existing);
        log.info("Updated video: videoId={}", videoId);
        return saved;
    }

    /** Soft-delete: marks the video inactive so it disappears from all listings. */
    @Transactional
    public void deleteVideo(String videoId) {
        Video existing = repository.findByVideoId(videoId)
                .orElseThrow(() -> new VideoNotFoundException(videoId));
        existing.setActive(false);
        repository.save(existing);
        log.info("Soft-deleted video: videoId={}", videoId);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Video toEntity(VideoRequest req, Video target) {
        target.setVideoId(req.videoId());
        target.setTitle(req.title());
        target.setDescription(req.description());
        target.setThumbnailUrl(req.thumbnailUrl());
        target.setHlsManifestUrl(req.hlsManifestUrl());
        target.setDashManifestUrl(req.dashManifestUrl());
        target.setDuration(req.duration());
        target.setResolution(req.resolution());
        target.setBitrate(req.bitrate());
        target.setCategory(req.category());
        target.setGenre(req.genre());
        return target;
    }
}
