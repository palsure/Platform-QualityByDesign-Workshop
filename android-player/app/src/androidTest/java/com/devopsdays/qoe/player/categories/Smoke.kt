package com.devopsdays.qoe.player.categories

/**
 * Smoke test marker — broader coverage run after BAT passes.
 * Covers: basic user interactions, input handling, state transitions.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Smoke
