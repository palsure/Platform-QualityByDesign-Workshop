package com.devopsdays.qoe.player.e2e

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.devopsdays.qoe.player.MainActivity
import com.devopsdays.qoe.player.R
import com.devopsdays.qoe.player.categories.Smoke
import com.devopsdays.qoe.player.data.VIDEO_CATALOG
import org.hamcrest.Matchers.not
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke Tests — broader coverage run after BAT passes.
 *
 * Scope: basic user interactions such as typing into inputs, pressing
 * the play button, and verifying the UI reacts correctly. These tests
 * do not require a live streaming server.
 */
@Smoke
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun url_input_accepts_typed_text() {
        onView(withId(R.id.url_input))
            .perform(clearText(), typeText("https://example.com/stream.m3u8"), closeSoftKeyboard())
        onView(withId(R.id.url_input))
            .check(matches(withText("https://example.com/stream.m3u8")))
    }

    @Test
    fun video_id_input_accepts_typed_text() {
        onView(withId(R.id.video_id_input))
            .perform(clearText(), typeText("my-video-42"), closeSoftKeyboard())
        onView(withId(R.id.video_id_input))
            .check(matches(withText("my-video-42")))
    }

    @Test
    fun url_input_can_be_cleared() {
        onView(withId(R.id.url_input)).perform(clearText(), closeSoftKeyboard())
        onView(withId(R.id.url_input)).check(matches(withText("")))
    }

    @Test
    fun video_id_input_can_be_cleared() {
        onView(withId(R.id.video_id_input)).perform(clearText(), closeSoftKeyboard())
        onView(withId(R.id.video_id_input)).check(matches(withText("")))
    }

    @Test
    fun clicking_play_with_default_url_does_not_crash() {
        onView(withId(R.id.play_button)).perform(click())
        // Verify the activity is still alive and UI is intact
        onView(withId(R.id.play_button)).check(matches(isDisplayed()))
    }

    @Test
    fun catalog_baseline_url_is_accepted_in_url_input() {
        val baseline = VIDEO_CATALOG.first { it.id == "baseline" }
        onView(withId(R.id.url_input))
            .perform(clearText(), typeText(baseline.hlsUrl), closeSoftKeyboard())
        onView(withId(R.id.url_input)).check(matches(withText(baseline.hlsUrl)))
    }

    @Test
    fun catalog_video_id_is_accepted_in_video_id_input() {
        val baseline = VIDEO_CATALOG.first { it.id == "baseline" }
        onView(withId(R.id.video_id_input))
            .perform(clearText(), typeText(baseline.id), closeSoftKeyboard())
        onView(withId(R.id.video_id_input)).check(matches(withText(baseline.id)))
    }

    @Test
    fun play_button_remains_enabled_after_click() {
        onView(withId(R.id.play_button)).perform(click())
        onView(withId(R.id.play_button)).check(matches(isEnabled()))
    }

    @Test
    fun status_text_is_not_empty_after_play_click() {
        onView(withId(R.id.play_button)).perform(click())
        onView(withId(R.id.status_text)).check(matches(not(withText(""))))
    }

    @Test
    fun player_view_remains_visible_after_interaction() {
        onView(withId(R.id.url_input))
            .perform(clearText(), typeText("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"), closeSoftKeyboard())
        onView(withId(R.id.play_button)).perform(click())
        onView(withId(R.id.player_view)).check(matches(existsWithSize()))
    }

    @Test
    fun url_input_hint_is_present_when_empty() {
        onView(withId(R.id.url_input)).perform(clearText(), closeSoftKeyboard())
        onView(withId(R.id.url_input)).check(matches(withHint("Enter HLS video URL")))
    }

    @Test
    fun video_id_input_hint_is_present_when_empty() {
        onView(withId(R.id.video_id_input)).perform(clearText(), closeSoftKeyboard())
        onView(withId(R.id.video_id_input)).check(matches(withHint("Video ID")))
    }
}
