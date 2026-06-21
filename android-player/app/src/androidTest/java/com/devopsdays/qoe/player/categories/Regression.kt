package com.devopsdays.qoe.player.categories

/**
 * Regression test marker — full suite covering edge cases and error paths.
 * Run on-demand or nightly; not required to gate every deployment.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Regression
