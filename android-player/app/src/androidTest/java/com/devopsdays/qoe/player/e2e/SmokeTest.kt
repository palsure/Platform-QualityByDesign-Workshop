package com.devopsdays.qoe.player.e2e

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.devopsdays.qoe.player.MainActivity
import com.devopsdays.qoe.player.R
import com.devopsdays.qoe.player.categories.Smoke
import org.hamcrest.Matchers.anyOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Smoke
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun player_starts_playback_for_baseline_stream() {
        onView(withText("Baseline Stream")).perform(click())
        onView(withId(R.id.status_text)).check(matches(anyOf(withText("Playing"), withText("Buffering..."), withText("Ready"))))
        onView(withId(R.id.player_view)).check(matches(existsWithSize()))
    }

    @Test
    fun back_navigation_returns_to_catalog() {
        onView(withText("Apple Advanced HLS")).perform(click())
        onView(withId(R.id.player_view)).check(matches(existsWithSize()))
        pressBack()
        onView(withId(R.id.catalog_list)).check(matches(isDisplayed()))
        onView(withText("Apple Advanced HLS")).check(matches(isDisplayed()))
    }

    @Test
    fun each_catalog_item_is_clickable() {
        onView(withText("Apple Basic HLS")).perform(click())
        onView(withId(R.id.player_toolbar)).check(matches(isDisplayed()))
        pressBack()
        onView(withText("Baseline Stream")).perform(click())
        onView(withId(R.id.player_view)).check(matches(existsWithSize()))
    }
}
