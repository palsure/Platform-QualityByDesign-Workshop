package com.devopsdays.qoe.player.models

data class QoEMetricPayload(
    val platform: String,
    val videoId: String,
    val sessionId: String,
    val timestamp: String,
    val deviceInfo: Map<String, String>,
    val metrics: Map<String, Any>
)
