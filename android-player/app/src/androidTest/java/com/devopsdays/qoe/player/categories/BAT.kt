package com.devopsdays.qoe.player.categories

/**
 * Build Acceptance Test marker — sanity gate that must pass before any deployment.
 * Covers: app launches, critical UI elements are present, no immediate crash.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class BAT
