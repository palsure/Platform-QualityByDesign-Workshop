package com.devopsdays.qoe.player.e2e

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.devopsdays.qoe.player.MainActivity
import com.devopsdays.qoe.player.R
import com.devopsdays.qoe.player.categories.Regression
import com.devopsdays.qoe.player.data.VIDEO_CATALOG
import com.devopsdays.qoe.player.data.findVideo
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression Tests — edge cases, error paths, and guards against known issues.
 *
 * Scope: empty inputs, malformed URLs, long values, catalog invariants, and
 * UI resilience under unexpected input. Run on-demand and nightly.
 */
@Regression
@RunWith(AndroidJUnit4::class)
class RegressionTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    // ── Empty / blank input guards ────────────────────────────────────────────

    @Test
    fun clicking_play_with_empty_url_does_not_crash() {
        onView(withId(R.id.url_input)).perform(clearText(), closeSoftKeyboard())
        onView(withId(R.id.play_button)).perform(click())
        onView(withId(R.id.play_button)).check(matches(isDisplayed()))
    }

    @Test
    fun status_text_unchanged_when_play_pressed_with_empty_url() {
        onView(withId(R.id.url_input)).perform(clearText(), closeSoftKeyboard())
        onView(withId(R.id.play_button)).perform(click())
        // Status should remain "Ready" — no blank URL should trigger playback
        onView(withId(R.id.status_text)).check(matches(withText("Ready")))
    }

    @Test
    fun empty_video_id_is_handled_gracefully() {
        onView(withId(R.id.video_id_input)).perform(clearText(), closeSoftKeyboard())
        onView(withId(R.id.play_button)).perform(click())
        onView(withId(R.id.video_id_input)).check(matches(isDisplayed()))
    }

    // ── Long / special character input ────────────────────────────────────────

    @Test
    fun very_long_video_id_does_not_crash_the_app() {
        val longId = "a".repeat(200)
        onView(withId(R.id.video_id_input))
            .perform(clearText(), typeText(longId), closeSoftKeyboard())
        onView(withId(R.id.play_button)).perform(click())
        onView(withId(R.id.play_button)).check(matches(isDisplayed()))
    }

    @Test
    fun special_characters_in_video_id_are_handled() {
        onView(withId(R.id.video_id_input))
            .perform(clearText(), typeText("video!@#$%^&*()"), closeSoftKeyboard())
        onView(withId(R.id.play_button)).perform(click())
        onView(withId(R.id.status_text)).check(matches(isDisplayed()))
    }

    @Test
    fun malformed_url_does_not_crash_the_app() {
        onView(withId(R.id.url_input))
            .perform(clearText(), typeText("not-a-valid-url"), closeSoftKeyboard())
        onView(withId(R.id.play_button)).perform(click())
        onView(withId(R.id.play_button)).check(matches(isDisplayed()))
    }

    @Test
    fun http_url_instead_of_https_is_handled() {
        onView(withId(R.id.url_input))
            .perform(clearText(), typeText("http://example.com/stream.m3u8"), closeSoftKeyboard())
        onView(withId(R.id.play_button)).perform(click())
        onView(withId(R.id.status_text)).check(matches(isDisplayed()))
    }

    // ── Catalog data contract ─────────────────────────────────────────────────

    @Test
    fun catalog_contains_expected_number_of_videos() {
        assertEquals(3, VIDEO_CATALOG.size)
    }

    @Test
    fun find_video_returns_null_for_nonexistent_id() {
        assertNull(findVideo("nonexistent-video-id"))
    }

    @Test
    fun find_video_returns_correct_entry_for_each_catalog_id() {
        VIDEO_CATALOG.forEach { expected ->
            val actual = findVideo(expected.id)
            assertNotNull("findVideo must not return null for '${expected.id}'", actual)
            assertEquals(expected.hlsUrl, actual!!.hlsUrl)
        }
    }

    @Test
    fun all_catalog_urls_are_unique() {
        val urls = VIDEO_CATALOG.map { it.hlsUrl }
        assertEquals("Catalog HLS URLs must be unique", urls.size, urls.distinct().size)
    }

    // ── UI resilience ─────────────────────────────────────────────────────────

    @Test
    fun rapid_play_button_clicks_do_not_crash() {
        repeat(5) {
            onView(withId(R.id.play_button)).perform(click())
        }
        onView(withId(R.id.play_button)).check(matches(isDisplayed()))
    }

    @Test
    fun url_input_replaces_text_on_retype() {
        onView(withId(R.id.url_input))
            .perform(clearText(), typeText("https://first.com/stream.m3u8"), closeSoftKeyboard())
        onView(withId(R.id.url_input))
            .perform(clearText(), typeText("https://second.com/stream.m3u8"), closeSoftKeyboard())
        onView(withId(R.id.url_input))
            .check(matches(withText("https://second.com/stream.m3u8")))
    }
}
