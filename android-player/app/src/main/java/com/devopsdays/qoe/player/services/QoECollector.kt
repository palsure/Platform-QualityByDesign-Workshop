package com.devopsdays.qoe.player.services

import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.devopsdays.qoe.player.models.QoEMetricPayload
import com.devopsdays.qoe.player.network.QoEApiService
import com.devopsdays.qoe.player.utils.QoEQualityCalculator
import com.newrelic.agent.android.NewRelic
import kotlinx.coroutines.*
import java.util.*

data class QoECollectorState(
    val sessionId: String,
    val totalBufferingTime: Double,
    val bitrateSwitches: Int,
    val errorCount: Int,
)

class QoECollector(
    private val videoId: String,
    private val context: Context,
) {
    private val sessionId =
        "android-${System.currentTimeMillis()}-${UUID.randomUUID().toString().substring(0, 8)}"
    private val bufferingEvents = mutableListOf<Map<String, Any>>()
    private val errors = mutableListOf<Map<String, Any>>()
    private var startupTime: Long = 0
    private var lastBufferingStart: Long? = null
    private var totalBufferingTime: Double = 0.0
    private var bitrateSwitches: Int = 0
    private var lastBitrate: Long? = null
    private var collectingJob: Job? = null
    private val apiService = QoEApiService()

    val state: QoECollectorState
        get() = QoECollectorState(
            sessionId = sessionId,
            totalBufferingTime = totalBufferingTime,
            bitrateSwitches = bitrateSwitches,
            errorCount = errors.size,
        )

    fun startCollecting(player: Player) {
        startupTime = System.currentTimeMillis()
        collectingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(5000)
                sendMetrics(player)
            }
        }
    }

    fun stopCollecting() {
        collectingJob?.cancel()
    }

    fun recordBufferingStart() {
        if (lastBufferingStart == null) {
            lastBufferingStart = System.currentTimeMillis()
        }
    }

    fun recordBufferingEnd() {
        lastBufferingStart?.let { start ->
            val end = System.currentTimeMillis()
            val duration = (end - start) / 1000.0
            totalBufferingTime += duration
            bufferingEvents.add(
                mapOf(
                    "startTime" to (start / 1000.0),
                    "endTime" to (end / 1000.0),
                    "duration" to duration,
                )
            )
            lastBufferingStart = null
        }
    }

    fun recordError(code: String, message: String) {
        errors.add(
            mapOf(
                "code" to code,
                "message" to message,
                "timestamp" to (System.currentTimeMillis() / 1000.0),
            )
        )
    }

    fun recordBitrateChange(newBitrate: Long) {
        lastBitrate?.let { last ->
            if (last != newBitrate) bitrateSwitches++
        }
        lastBitrate = newBitrate
    }

    @OptIn(UnstableApi::class)
    private suspend fun sendMetrics(player: Player) {
        val currentTime = player.currentPosition / 1000.0
        val duration = if (player.duration > 0) player.duration / 1000.0 else 0.0
        val playbackState = when (player.playbackState) {
            Player.STATE_IDLE      -> "idle"
            Player.STATE_BUFFERING -> "buffering"
            Player.STATE_READY     -> if (player.isPlaying) "playing" else "paused"
            Player.STATE_ENDED     -> "ended"
            else                   -> "unknown"
        }

        val exoPlayer = player as? ExoPlayer
        val videoFormat = exoPlayer?.videoFormat
        val currentBitrate = videoFormat?.bitrate?.takeIf { it > 0 }?.toLong()
        val currentResolution = videoFormat?.let { "${it.width}x${it.height}" }

        currentBitrate?.let { recordBitrateChange(it) }

        val payload = QoEMetricPayload(
            platform = "android",
            videoId = videoId,
            sessionId = sessionId,
            timestamp = java.time.Instant.now().toString(),
            deviceInfo = mapOf(
                "deviceType" to getDeviceType(),
                "os" to "Android ${Build.VERSION.RELEASE}",
                "screenResolution" to getScreenResolution(),
            ),
            metrics = mapOf(
                "playbackState" to playbackState,
                "currentTime" to currentTime,
                "duration" to duration,
                "bufferingEvents" to bufferingEvents,
                "totalBufferingTime" to totalBufferingTime,
                "startupTime" to startupTime,
                "currentBitrate" to (currentBitrate ?: 0),
                "currentResolution" to (currentResolution ?: ""),
                "bitrateSwitches" to bitrateSwitches,
                "errors" to errors,
                "errorCount" to errors.size,
                "playbackQuality" to calculateQuality(),
            ),
        )

        try {
            apiService.sendMetrics(payload)
        } catch (e: Exception) {
            android.util.Log.e("QoECollector", "Failed to send metrics", e)
        }

        recordNewRelicEvent(
            playbackState = playbackState,
            currentTime   = currentTime,
            duration      = duration,
            currentBitrate = currentBitrate,
            currentResolution = currentResolution,
        )
    }

    /**
     * Mirror the metric to New Relic Mobile as a `QoEMetric` custom event.
     *
     * Safe to call when the agent is disabled (no token / not started) — the
     * facade is a no-op in that case so we don't need to null-check the agent
     * state. Only scalar attributes are sent; lists (bufferingEvents, errors)
     * are summarised as counts because Mobile drops complex types.
     */
    private fun recordNewRelicEvent(
        playbackState: String,
        currentTime: Double,
        duration: Double,
        currentBitrate: Long?,
        currentResolution: String?,
    ) {
        try {
            val attrs = mutableMapOf<String, Any>(
                "platform"           to "android",
                "videoId"            to videoId,
                "sessionId"          to sessionId,
                "playbackState"      to playbackState,
                "playbackQuality"    to calculateQuality(),
                "currentTime"        to currentTime,
                "duration"           to duration,
                "totalBufferingTime" to totalBufferingTime,
                "bufferingEvents"    to bufferingEvents.size,
                "bitrateSwitches"    to bitrateSwitches,
                "errorCount"         to errors.size,
                "startupTimeMs"      to startupTime,
                "deviceType"         to getDeviceType(),
                "os"                 to "Android ${Build.VERSION.RELEASE}",
            )
            currentBitrate?.let    { attrs["currentBitrate"] = it }
            currentResolution?.let { if (it.isNotEmpty()) attrs["currentResolution"] = it }

            NewRelic.recordCustomEvent("QoEMetric", "Mobile", attrs)
        } catch (e: Throwable) {
            android.util.Log.d("QoECollector", "NewRelic.recordCustomEvent failed", e)
        }
    }

    private fun getDeviceType(): String {
        val screenWidth = context.resources.displayMetrics.widthPixels
        return when {
            screenWidth < 600  -> "mobile"
            screenWidth < 1024 -> "tablet"
            else               -> "tv"
        }
    }

    private fun getScreenResolution(): String {
        val metrics = context.resources.displayMetrics
        return "${metrics.widthPixels}x${metrics.heightPixels}"
    }

    private fun calculateQuality(): String =
        QoEQualityCalculator.calculate(totalBufferingTime, errors.size)
}
