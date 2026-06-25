package com.devopsdays.qoe.player.e2e

import android.view.View
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher

/**
 * Espresso [isDisplayed] fails on [android.view.SurfaceView] children inside
 * Media3 [androidx.media3.ui.PlayerView]. Use this matcher for player chrome.
 */
fun existsWithSize(): Matcher<View> = object : TypeSafeMatcher<View>(View::class.java) {
    override fun describeTo(description: Description) {
        description.appendText("exists in hierarchy with non-zero size")
    }

    override fun matchesSafely(view: View): Boolean =
        view.visibility != View.GONE && view.width > 0 && view.height > 0
}
