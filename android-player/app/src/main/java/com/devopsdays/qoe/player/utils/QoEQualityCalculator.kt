package com.devopsdays.qoe.player.utils

object QoEQualityCalculator {
    fun calculate(totalBufferingTime: Double, errorCount: Int): String = when {
        totalBufferingTime < 2.0 && errorCount == 0 -> "excellent"
        totalBufferingTime < 5.0 && errorCount < 2  -> "good"
        totalBufferingTime < 10.0 && errorCount < 5 -> "fair"
        else                                         -> "poor"
    }
}
