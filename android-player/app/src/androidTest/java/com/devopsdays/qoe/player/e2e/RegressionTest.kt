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
import com.devopsdays.qoe.player.categories.Regression
import com.devopsdays.qoe.player.data.VIDEO_CATALOG
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Regression
@RunWith(AndroidJUnit4::class)
class RegressionTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun catalog_entries_have_unique_ids() {
        val ids = VIDEO_CATALOG.map { it.id }
        assert(ids.size == ids.toSet().size)
    }

    @Test
    fun rapid_catalog_navigation_does_not_crash() {
        repeat(3) {
            onView(withText("Baseline Stream")).perform(click())
            onView(withId(R.id.player_view)).check(matches(existsWithSize()))
            pressBack()
        }
        onView(withId(R.id.catalog_list)).check(matches(isDisplayed()))
    }

    @Test
    fun player_does_not_show_error_on_valid_stream() {
        onView(withText("Apple Basic HLS")).perform(click())
        onView(withId(R.id.status_text)).check(matches(not(withText(containsString("Error")))))
    }
}
