package com.devopsdays.qoe.api.unit.video;

import com.devopsdays.qoe.api.exceptions.VideoNotFoundException;
import com.devopsdays.qoe.api.models.Video;
import com.devopsdays.qoe.api.models.VideoRequest;
import com.devopsdays.qoe.api.repositories.VideoRepository;
import com.devopsdays.qoe.api.services.VideoService;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.qameta.allure.junit5.AllureJunit5;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@Epic("Video Catalog")
@ExtendWith({MockitoExtension.class, AllureJunit5.class})
@DisplayName("VideoService")
class VideoServiceTest {

    @Mock
    private VideoRepository repository;

    private VideoService service;

    @BeforeEach
    void setUp() {
        service = new VideoService(repository);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static Video activeVideo(String videoId, String title, String category) {
        return Video.builder()
                .id(1L).videoId(videoId).title(title)
                .description("Test description").category(category).genre("animation")
                .active(true)
                .hlsManifestUrl("https://example.com/" + videoId + ".m3u8")
                .dashManifestUrl("https://example.com/" + videoId + ".mpd")
                .duration(600L).resolution("1920x1080").bitrate(5000000L)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
    }

    private static VideoRequest sampleRequest(String videoId) {
        return new VideoRequest(
                videoId, "Test Title", "Test description",
                "https://example.com/thumb.jpg",
                "https://example.com/video.m3u8",
                "https://example.com/video.mpd",
                600L, "1920x1080", 5000000L, "movie", "drama");
    }

    // ── getAllVideos ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAllVideos")
    @Feature("Get all videos")
    class GetAllVideos {

        @Test
        @Story("Returns only active videos from repository")
        @DisplayName("returns only active videos")
        void returnsActiveVideos() {
            List<Video> expected = List.of(
                    activeVideo("v1", "Video 1", "movie"),
                    activeVideo("v2", "Video 2", "show"));

            Allure.step("Given: repository.findByActiveTrue() returns 2 active videos", () ->
                    when(repository.findByActiveTrue()).thenReturn(expected));

            List<Video> result = Allure.step("When: getAllVideos()",
                    () -> service.getAllVideos());

            Allure.step("Then: result has 2 items matching the mocked list", () ->
                    assertThat(result).hasSize(2).isEqualTo(expected));

            Allure.step("Then: repository.findByActiveTrue() was called once", () ->
                    verify(repository).findByActiveTrue());
        }

        @Test
        @Story("Returns empty list when no active videos exist")
        @DisplayName("returns empty list when repository is empty")
        void returnsEmptyList() {
            Allure.step("Given: repository.findByActiveTrue() returns empty list", () ->
                    when(repository.findByActiveTrue()).thenReturn(List.of()));

            List<Video> result = Allure.step("When: getAllVideos()",
                    () -> service.getAllVideos());

            Allure.step("Then: result is empty", () ->
                    assertThat(result).isEmpty());
        }
    }

    // ── getVideoById ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getVideoById")
    @Feature("Get video by ID")
    class GetVideoById {

        @Test
        @Story("Returns video when found and active")
        @DisplayName("returns video when found and active")
        void returnsVideoWhenFoundAndActive() {
            Video video = activeVideo("vid-001", "Big Buck Bunny", "movie");

            Allure.step("Given: repository.findByVideoId('vid-001') returns an active video", () ->
                    when(repository.findByVideoId("vid-001")).thenReturn(Optional.of(video)));

            Optional<Video> result = Allure.step("When: getVideoById('vid-001')",
                    () -> service.getVideoById("vid-001"));

            Allure.step("Then: result is present and contains the expected video", () ->
                    assertThat(result).isPresent().contains(video));
        }

        @Test
        @Story("Returns empty when video is soft-deleted")
        @DisplayName("returns empty for soft-deleted (inactive) video")
        void returnsEmptyForInactiveVideo() {
            Video inactive = activeVideo("vid-002", "Deleted Video", "movie");

            Allure.step("Given: repository returns a video with active=false", () -> {
                inactive.setActive(false);
                when(repository.findByVideoId("vid-002")).thenReturn(Optional.of(inactive));
            });

            Optional<Video> result = Allure.step("When: getVideoById('vid-002')",
                    () -> service.getVideoById("vid-002"));

            Allure.step("Then: result is empty (soft-deleted video is hidden)", () ->
                    assertThat(result).isEmpty());
        }

        @Test
        @Story("Returns empty when video does not exist")
        @DisplayName("returns empty when video ID is not found")
        void returnsEmptyWhenNotFound() {
            Allure.step("Given: repository.findByVideoId('missing') returns empty", () ->
                    when(repository.findByVideoId("missing")).thenReturn(Optional.empty()));

            Optional<Video> result = Allure.step("When: getVideoById('missing')",
                    () -> service.getVideoById("missing"));

            Allure.step("Then: result is empty", () ->
                    assertThat(result).isEmpty());
        }
    }

    // ── searchVideos ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("searchVideos")
    @Feature("Search videos")
    class SearchVideos {

        @Test
        @Story("Non-blank query is trimmed and forwarded to repository.search")
        @DisplayName("delegates trimmed query to repository.search")
        void delegatesToRepository() {
            List<Video> results = List.of(activeVideo("vid-001", "Big Buck Bunny", "movie"));

            Allure.step("Given: repository.search('bunny') returns 1 result", () ->
                    when(repository.search("bunny")).thenReturn(results));

            List<Video> actual = Allure.step("When: searchVideos('  bunny  ') — with surrounding spaces",
                    () -> service.searchVideos("  bunny  "));

            Allure.step("Then: result matches mocked results", () ->
                    assertThat(actual).isEqualTo(results));

            Allure.step("Then: repository.search was called with trimmed query 'bunny'", () ->
                    verify(repository).search("bunny"));
        }

        @Test
        @Story("Blank query falls back to returning all active videos")
        @DisplayName("returns all active videos when query is blank")
        void returnsAllWhenBlankQuery() {
            List<Video> all = List.of(activeVideo("v1", "Title", "movie"));

            Allure.step("Given: repository.findByActiveTrue() returns 1 video", () ->
                    when(repository.findByActiveTrue()).thenReturn(all));

            List<Video> result = Allure.step("When: searchVideos('   ') — blank query",
                    () -> service.searchVideos("   "));

            Allure.step("Then: result equals the full active list", () ->
                    assertThat(result).isEqualTo(all));

            Allure.step("Then: repository.search was never called", () ->
                    verify(repository, never()).search(anyString()));
        }

        @Test
        @Story("Null query falls back to returning all active videos")
        @DisplayName("returns all active videos when query is null")
        void returnsAllWhenNullQuery() {
            Allure.step("Given: repository.findByActiveTrue() returns empty", () ->
                    when(repository.findByActiveTrue()).thenReturn(List.of()));

            Allure.step("When: searchVideos(null)", () ->
                    service.searchVideos(null));

            Allure.step("Then: repository.findByActiveTrue() was called", () ->
                    verify(repository).findByActiveTrue());

            Allure.step("Then: repository.search was never called", () ->
                    verify(repository, never()).search(anyString()));
        }
    }

    // ── getByCategory ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getByCategory")
    @Feature("Filter by category")
    class GetByCategory {

        @Test
        @Story("Delegates category filter to repository")
        @DisplayName("delegates category string to repository.findByCategoryIgnoreCaseAndActiveTrue")
        void delegatesToRepository() {
            List<Video> movies = List.of(activeVideo("vid-001", "Sintel", "movie"));

            Allure.step("Given: repository returns 1 movie for category 'movie'", () ->
                    when(repository.findByCategoryIgnoreCaseAndActiveTrue("movie")).thenReturn(movies));

            List<Video> result = Allure.step("When: getByCategory('movie')",
                    () -> service.getByCategory("movie"));

            Allure.step("Then: result matches mocked list of movies", () ->
                    assertThat(result).isEqualTo(movies));
        }
    }

    // ── createVideo ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createVideo")
    @Feature("Create video")
    class CreateVideo {

        @Test
        @Story("New video is persisted and returned")
        @DisplayName("persists a new video and returns the saved entity")
        void persistsVideo() {
            VideoRequest req = sampleRequest("new-vid-001");

            Allure.step("Given: videoId 'new-vid-001' does not exist yet", () ->
                    when(repository.existsByVideoId("new-vid-001")).thenReturn(false));

            Allure.step("Given: repository.save returns the passed entity", () ->
                    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0)));

            Video saved = Allure.step("When: createVideo(request for 'new-vid-001')",
                    () -> service.createVideo(req));

            Allure.step("Then: saved video has correct videoId, title, category, genre", () -> {
                assertThat(saved.getVideoId()).isEqualTo("new-vid-001");
                assertThat(saved.getTitle()).isEqualTo("Test Title");
                assertThat(saved.getCategory()).isEqualTo("movie");
                assertThat(saved.getGenre()).isEqualTo("drama");
            });

            Allure.step("Then: saved video is active", () ->
                    assertThat(saved.getActive()).isTrue());

            ArgumentCaptor<Video> cap = ArgumentCaptor.forClass(Video.class);
            Allure.step("Then: repository.save was called with entity having duration = 600", () -> {
                verify(repository).save(cap.capture());
                assertThat(cap.getValue().getDuration()).isEqualTo(600L);
            });
        }

        @Test
        @Story("Duplicate videoId is rejected")
        @DisplayName("throws IllegalArgumentException when videoId already exists")
        void throwsWhenDuplicate() {
            Allure.step("Given: videoId 'dup-vid' already exists in repository", () ->
                    when(repository.existsByVideoId("dup-vid")).thenReturn(true));

            Allure.step("Assert: createVideo throws IllegalArgumentException containing 'dup-vid'", () ->
                    assertThatThrownBy(() -> service.createVideo(sampleRequest("dup-vid")))
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessageContaining("dup-vid"));

            Allure.step("Then: repository.save was never called", () ->
                    verify(repository, never()).save(any()));
        }
    }

    // ── updateVideo ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateVideo")
    @Feature("Update video")
    class UpdateVideo {

        @Test
        @Story("All mutable fields are replaced")
        @DisplayName("updates all mutable fields and returns saved entity")
        void updatesAllFields() {
            Video existing = activeVideo("vid-update", "Old Title", "show");

            Allure.step("Given: repository returns existing video 'vid-update'", () ->
                    when(repository.findByVideoId("vid-update")).thenReturn(Optional.of(existing)));

            Allure.step("Given: repository.save returns the passed entity", () ->
                    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0)));

            VideoRequest req = new VideoRequest(
                    "vid-update", "New Title", "New desc",
                    "https://example.com/new-thumb.jpg",
                    "https://example.com/new.m3u8",
                    "https://example.com/new.mpd",
                    900L, "3840x2160", 12000000L, "documentary", "nature");

            Video result = Allure.step("When: updateVideo('vid-update', new request)",
                    () -> service.updateVideo("vid-update", req));

            Allure.step("Then: title, category and duration reflect the updated values", () -> {
                assertThat(result.getTitle()).isEqualTo("New Title");
                assertThat(result.getCategory()).isEqualTo("documentary");
                assertThat(result.getDuration()).isEqualTo(900L);
            });
        }

        @Test
        @Story("Updating non-existent video throws VideoNotFoundException")
        @DisplayName("throws VideoNotFoundException when video does not exist")
        void throwsWhenNotFound() {
            Allure.step("Given: repository returns empty for 'ghost'", () ->
                    when(repository.findByVideoId("ghost")).thenReturn(Optional.empty()));

            Allure.step("Assert: updateVideo('ghost', …) throws VideoNotFoundException containing 'ghost'", () ->
                    assertThatThrownBy(() -> service.updateVideo("ghost", sampleRequest("ghost")))
                            .isInstanceOf(VideoNotFoundException.class)
                            .hasMessageContaining("ghost"));
        }
    }

    // ── deleteVideo ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteVideo")
    @Feature("Delete video")
    class DeleteVideo {

        @Test
        @Story("Video is soft-deleted by setting active=false")
        @DisplayName("sets active=false and saves the entity")
        void setsActiveFalse() {
            Video video = activeVideo("vid-del", "To Delete", "movie");

            Allure.step("Given: repository returns video 'vid-del' (active=true)", () ->
                    when(repository.findByVideoId("vid-del")).thenReturn(Optional.of(video)));

            Allure.step("Given: repository.save returns the passed entity", () ->
                    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0)));

            Allure.step("When: deleteVideo('vid-del')", () ->
                    service.deleteVideo("vid-del"));

            ArgumentCaptor<Video> cap = ArgumentCaptor.forClass(Video.class);
            Allure.step("Then: repository.save was called with entity having active=false", () -> {
                verify(repository).save(cap.capture());
                assertThat(cap.getValue().getActive()).isFalse();
            });
        }

        @Test
        @Story("Deleting non-existent video throws VideoNotFoundException")
        @DisplayName("throws VideoNotFoundException when video does not exist")
        void throwsWhenNotFound() {
            Allure.step("Given: repository returns empty for 'gone'", () ->
                    when(repository.findByVideoId("gone")).thenReturn(Optional.empty()));

            Allure.step("Assert: deleteVideo('gone') throws VideoNotFoundException containing 'gone'", () ->
                    assertThatThrownBy(() -> service.deleteVideo("gone"))
                            .isInstanceOf(VideoNotFoundException.class)
                            .hasMessageContaining("gone"));
        }
    }
}
