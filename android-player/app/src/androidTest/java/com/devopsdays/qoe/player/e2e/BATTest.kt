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
import com.devopsdays.qoe.player.categories.BAT
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@BAT
@RunWith(AndroidJUnit4::class)
class BATTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun catalog_screen_launches() {
        onView(withId(R.id.catalog_list)).check(matches(isDisplayed()))
        onView(withId(R.id.catalog_title)).check(matches(withText("StreamApp")))
    }

    @Test
    fun catalog_lists_all_streams() {
        onView(withText("Baseline Stream")).check(matches(isDisplayed()))
        onView(withText("Apple Advanced HLS")).check(matches(isDisplayed()))
        onView(withText("Apple Basic HLS")).check(matches(isDisplayed()))
    }

    @Test
    fun selecting_stream_opens_player() {
        onView(withText("Baseline Stream")).perform(click())
        onView(withId(R.id.player_view)).check(matches(existsWithSize()))
        onView(withId(R.id.player_toolbar)).check(matches(isDisplayed()))
    }
}
