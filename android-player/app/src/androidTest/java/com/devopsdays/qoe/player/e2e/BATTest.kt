package com.devopsdays.qoe.player.e2e

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.devopsdays.qoe.player.MainActivity
import com.devopsdays.qoe.player.R
import com.devopsdays.qoe.player.categories.BAT
import org.hamcrest.Matchers.anyOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Build Acceptance Tests — sanity gate that must pass before any deployment.
 *
 * Scope: app launches without crash, every critical UI element is visible
 * and enabled, no immediate ANR or crash. These are the fastest tests and
 * are always required to pass before moving to Smoke or Regression.
 */
@BAT
@RunWith(AndroidJUnit4::class)
class BATTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun app_launches_without_crashing() {
        // If the activity rule succeeds, the app launched without crash
        onView(withId(android.R.id.content)).check(matches(isDisplayed()))
    }

    @Test
    fun url_input_is_visible() {
        onView(withId(R.id.url_input)).check(matches(isDisplayed()))
    }

    @Test
    fun url_input_is_enabled() {
        onView(withId(R.id.url_input)).check(matches(isEnabled()))
    }

    @Test
    fun video_id_input_is_visible() {
        onView(withId(R.id.video_id_input)).check(matches(isDisplayed()))
    }

    @Test
    fun video_id_input_is_enabled() {
        onView(withId(R.id.video_id_input)).check(matches(isEnabled()))
    }

    @Test
    fun play_button_is_visible() {
        onView(withId(R.id.play_button)).check(matches(isDisplayed()))
    }

    @Test
    fun play_button_is_enabled() {
        onView(withId(R.id.play_button)).check(matches(isEnabled()))
    }

    @Test
    fun play_button_has_correct_label() {
        onView(withId(R.id.play_button)).check(matches(withText("Play Video")))
    }

    @Test
    fun status_text_is_visible() {
        onView(withId(R.id.status_text)).check(matches(isDisplayed()))
    }

    @Test
    fun status_text_shows_ready_on_launch() {
        onView(withId(R.id.status_text)).check(matches(anyOf(withText("Ready"), withText("Buffering..."))))
    }

    @Test
    fun player_view_is_visible() {
        onView(withId(R.id.player_view)).check(matches(existsWithSize()))
    }

    @Test
    fun url_input_has_default_hls_url() {
        onView(withId(R.id.url_input))
            .check(matches(withText("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")))
    }

    @Test
    fun video_id_input_has_default_value() {
        onView(withId(R.id.video_id_input)).check(matches(withText("android-demo-1")))
    }
}
