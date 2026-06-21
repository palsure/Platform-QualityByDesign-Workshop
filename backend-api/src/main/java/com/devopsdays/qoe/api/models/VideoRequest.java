package com.devopsdays.qoe.api.models;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Schema(description = "Payload for creating or updating a video item")
public record VideoRequest(

        @NotBlank
        @Schema(description = "Unique video identifier used by client apps", example = "vid-hls-001")
        String videoId,

        @NotBlank
        @Schema(description = "Display title", example = "Big Buck Bunny")
        String title,

        @Schema(description = "Short synopsis shown in UI", example = "A giant rabbit befriends small creatures.")
        String description,

        @Schema(description = "URL of the poster / thumbnail image")
        String thumbnailUrl,

        @Schema(description = "HLS (.m3u8) manifest URL for adaptive streaming")
        String hlsManifestUrl,

        @Schema(description = "MPEG-DASH (.mpd) manifest URL")
        String dashManifestUrl,

        @Positive
        @Schema(description = "Total duration in seconds", example = "596")
        Long duration,

        @Schema(description = "Native resolution, e.g. 1920x1080", example = "1920x1080")
        String resolution,

        @Positive
        @Schema(description = "Peak bitrate in bits per second", example = "5000000")
        Long bitrate,

        @Schema(description = "Content category: movie | show | live | sports | documentary", example = "movie")
        String category,

        @Schema(description = "Genre tag: drama | comedy | action | news | animation | etc.", example = "animation")
        String genre
) {}
