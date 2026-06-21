package com.devopsdays.qoe.player.data

import io.qameta.allure.Feature
import io.qameta.allure.Story
import org.junit.Assert.*
import org.junit.Test

@Feature("Video Catalog")
class VideoCatalogTest {

    @Test
    @Story("Catalog content")
    fun `catalog contains at least one video`() {
        assertTrue("Catalog must not be empty", VIDEO_CATALOG.isNotEmpty())
    }

    @Test
    @Story("Catalog content")
    fun `catalog contains three videos`() {
        assertEquals(3, VIDEO_CATALOG.size)
    }

    @Test
    @Story("Catalog content")
    fun `all videos have non-blank ids`() {
        VIDEO_CATALOG.forEach { video ->
            assertTrue("Video id must not be blank: $video", video.id.isNotBlank())
        }
    }

    @Test
    @Story("Catalog content")
    fun `all videos have non-blank titles`() {
        VIDEO_CATALOG.forEach { video ->
            assertTrue("Video title must not be blank: $video", video.title.isNotBlank())
        }
    }

    @Test
    @Story("Catalog content")
    fun `all videos have non-blank HLS URLs`() {
        VIDEO_CATALOG.forEach { video ->
            assertTrue("HLS URL must not be blank: $video", video.hlsUrl.isNotBlank())
        }
    }

    @Test
    @Story("Catalog content")
    fun `all HLS URLs start with https`() {
        VIDEO_CATALOG.forEach { video ->
            assertTrue(
                "HLS URL must use HTTPS: ${video.hlsUrl}",
                video.hlsUrl.startsWith("https://"),
            )
        }
    }

    @Test
    @Story("Catalog content")
    fun `all HLS URLs point to m3u8 playlists`() {
        VIDEO_CATALOG.forEach { video ->
            assertTrue(
                "HLS URL must reference an m3u8 playlist: ${video.hlsUrl}",
                video.hlsUrl.endsWith(".m3u8"),
            )
        }
    }

    @Test
    @Story("Catalog IDs are unique")
    fun `catalog video ids are unique`() {
        val ids = VIDEO_CATALOG.map { it.id }
        assertEquals("Video IDs must be unique", ids.size, ids.distinct().size)
    }

    @Test
    @Story("findVideo helper")
    fun `findVideo returns matching video`() {
        val video = findVideo("baseline")
        assertNotNull(video)
        assertEquals("baseline", video!!.id)
    }

    @Test
    @Story("findVideo helper")
    fun `findVideo returns null for unknown id`() {
        assertNull(findVideo("does-not-exist"))
    }

    @Test
    @Story("findVideo helper")
    fun `findVideo returns correct video for each catalog entry`() {
        VIDEO_CATALOG.forEach { expected ->
            val actual = findVideo(expected.id)
            assertNotNull("findVideo must find ${expected.id}", actual)
            assertEquals(expected, actual)
        }
    }

    @Test
    @Story("Catalog content")
    fun `baseline video has expected HLS URL`() {
        val video = findVideo("baseline")
        assertNotNull(video)
        assertEquals(
            "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
            video!!.hlsUrl,
        )
    }

    @Test
    @Story("Catalog content")
    fun `StreamVideo data class copy works`() {
        val original = VIDEO_CATALOG.first()
        val copy = original.copy(title = "Modified Title")
        assertEquals("Modified Title", copy.title)
        assertEquals(original.id, copy.id)
        assertEquals(original.hlsUrl, copy.hlsUrl)
    }
}
