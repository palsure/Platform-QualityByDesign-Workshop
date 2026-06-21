package com.devopsdays.qoe.api.controllers;

import com.devopsdays.qoe.api.models.Video;
import com.devopsdays.qoe.api.models.VideoRequest;
import com.devopsdays.qoe.api.services.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/videos")
@RequiredArgsConstructor
@Tag(name = "Videos", description = "Video catalog — consumed by web, Android and iOS player apps")
public class VideoController {

    private final VideoService videoService;

    // ── Read ─────────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "List active videos",
            description = "Returns all active videos. Optionally filter by category.")
    public ResponseEntity<List<Video>> listVideos(
            @Parameter(description = "Filter by category: movie | show | live | sports | documentary")
            @RequestParam(required = false) String category
    ) {
        List<Video> result = (category != null && !category.isBlank())
                ? videoService.getByCategory(category)
                : videoService.getAllVideos();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/search")
    @Operation(summary = "Search videos",
            description = "Full-text search across title, description and genre.")
    public ResponseEntity<List<Video>> searchVideos(
            @Parameter(description = "Search term", required = true)
            @RequestParam String q
    ) {
        return ResponseEntity.ok(videoService.searchVideos(q));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single video by its videoId")
    public ResponseEntity<Video> getVideo(@PathVariable String id) {
        return videoService.getVideoById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/manifest")
    @Operation(summary = "Get HLS and DASH manifest URLs for a video")
    public ResponseEntity<Map<String, String>> getManifest(@PathVariable String id) {
        return videoService.getVideoById(id)
                .map(video -> ResponseEntity.ok(Map.of(
                        "videoId", video.getVideoId(),
                        "title",   video.getTitle(),
                        "hls",     video.getHlsManifestUrl()  != null ? video.getHlsManifestUrl()  : "",
                        "dash",    video.getDashManifestUrl() != null ? video.getDashManifestUrl() : ""
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Write ────────────────────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Create a new video entry")
    public ResponseEntity<Video> createVideo(@Valid @RequestBody VideoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(videoService.createVideo(req));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing video entry (full replace)")
    public ResponseEntity<Video> updateVideo(
            @PathVariable String id,
            @Valid @RequestBody VideoRequest req
    ) {
        return ResponseEntity.ok(videoService.updateVideo(id, req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a video (marks it inactive)")
    public ResponseEntity<Void> deleteVideo(@PathVariable String id) {
        videoService.deleteVideo(id);
        return ResponseEntity.noContent().build();
    }
}
