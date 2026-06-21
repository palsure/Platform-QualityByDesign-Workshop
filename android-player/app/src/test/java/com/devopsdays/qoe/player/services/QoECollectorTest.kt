package com.devopsdays.qoe.player.services

import android.content.Context
import android.util.DisplayMetrics
import io.mockk.every
import io.mockk.mockk
import io.qameta.allure.Feature
import io.qameta.allure.Story
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@Feature("QoE Collector")
class QoECollectorTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = mockk(relaxed = true) {
            every { resources.displayMetrics } returns DisplayMetrics().apply {
                widthPixels = 1080
                heightPixels = 1920
            }
        }
    }

    // ── Session ID ────────────────────────────────────────────────────────────

    @Test
    @Story("Session ID")
    fun `session id starts with android prefix`() {
        val collector = QoECollector("vid-001", context)
        assertTrue(
            "Session ID must start with 'android-'",
            collector.state.sessionId.startsWith("android-"),
        )
    }

    @Test
    @Story("Session ID")
    fun `each collector gets a unique session id`() {
        val c1 = QoECollector("vid-001", context)
        val c2 = QoECollector("vid-001", context)
        assertNotEquals(c1.state.sessionId, c2.state.sessionId)
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    @Story("Initial state")
    fun `initial total buffering time is zero`() {
        val collector = QoECollector("vid-001", context)
        assertEquals(0.0, collector.state.totalBufferingTime, 0.0)
    }

    @Test
    @Story("Initial state")
    fun `initial bitrate switches count is zero`() {
        val collector = QoECollector("vid-001", context)
        assertEquals(0, collector.state.bitrateSwitches)
    }

    @Test
    @Story("Initial state")
    fun `initial error count is zero`() {
        val collector = QoECollector("vid-001", context)
        assertEquals(0, collector.state.errorCount)
    }

    // ── Buffering tracking ────────────────────────────────────────────────────

    @Test
    @Story("Buffering tracking")
    fun `buffering end without start does not crash`() {
        val collector = QoECollector("vid-001", context)
        collector.recordBufferingEnd()
        assertEquals(0.0, collector.state.totalBufferingTime, 0.0)
    }

    @Test
    @Story("Buffering tracking")
    fun `buffering time increases after start-end cycle`() {
        val collector = QoECollector("vid-001", context)
        collector.recordBufferingStart()
        Thread.sleep(150)
        collector.recordBufferingEnd()
        assertTrue(
            "Total buffering time must be > 0 after a start-end cycle",
            collector.state.totalBufferingTime > 0.0,
        )
    }

    @Test
    @Story("Buffering tracking")
    fun `multiple buffering cycles accumulate time`() {
        val collector = QoECollector("vid-001", context)

        collector.recordBufferingStart()
        Thread.sleep(100)
        collector.recordBufferingEnd()

        val afterFirst = collector.state.totalBufferingTime

        collector.recordBufferingStart()
        Thread.sleep(100)
        collector.recordBufferingEnd()

        assertTrue(
            "Second cycle must increase accumulated buffering time",
            collector.state.totalBufferingTime > afterFirst,
        )
    }

    @Test
    @Story("Buffering tracking")
    fun `duplicate recordBufferingStart calls do not reset start time`() {
        val collector = QoECollector("vid-001", context)
        collector.recordBufferingStart()
        Thread.sleep(100)
        collector.recordBufferingStart() // second call should be ignored
        Thread.sleep(100)
        collector.recordBufferingEnd()
        // Total should reflect the full 200 ms, not just the second 100 ms
        assertTrue(collector.state.totalBufferingTime >= 0.19)
    }

    // ── Error tracking ────────────────────────────────────────────────────────

    @Test
    @Story("Error tracking")
    fun `recording one error increments error count`() {
        val collector = QoECollector("vid-001", context)
        collector.recordError("ERR_001", "Network timeout")
        assertEquals(1, collector.state.errorCount)
    }

    @Test
    @Story("Error tracking")
    fun `recording multiple errors increments count accordingly`() {
        val collector = QoECollector("vid-001", context)
        collector.recordError("ERR_001", "Network timeout")
        collector.recordError("ERR_002", "Decode error")
        collector.recordError("ERR_003", "Server unreachable")
        assertEquals(3, collector.state.errorCount)
    }

    // ── Bitrate tracking ──────────────────────────────────────────────────────

    @Test
    @Story("Bitrate tracking")
    fun `first bitrate change does not increment switch counter`() {
        val collector = QoECollector("vid-001", context)
        collector.recordBitrateChange(4500)
        assertEquals(0, collector.state.bitrateSwitches)
    }

    @Test
    @Story("Bitrate tracking")
    fun `switching to a different bitrate increments counter`() {
        val collector = QoECollector("vid-001", context)
        collector.recordBitrateChange(4500)
        collector.recordBitrateChange(2000)
        assertEquals(1, collector.state.bitrateSwitches)
    }

    @Test
    @Story("Bitrate tracking")
    fun `same bitrate repeated does not increment counter`() {
        val collector = QoECollector("vid-001", context)
        collector.recordBitrateChange(4500)
        collector.recordBitrateChange(4500)
        assertEquals(0, collector.state.bitrateSwitches)
    }

    @Test
    @Story("Bitrate tracking")
    fun `multiple distinct bitrate changes are counted individually`() {
        val collector = QoECollector("vid-001", context)
        collector.recordBitrateChange(4500)
        collector.recordBitrateChange(2000)
        collector.recordBitrateChange(800)
        collector.recordBitrateChange(4500)
        assertEquals(3, collector.state.bitrateSwitches)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Test
    @Story("Lifecycle")
    fun `stopCollecting without startCollecting does not throw`() {
        val collector = QoECollector("vid-001", context)
        collector.stopCollecting()
    }

    @Test
    @Story("Lifecycle")
    fun `stopCollecting can be called multiple times safely`() {
        val collector = QoECollector("vid-001", context)
        collector.stopCollecting()
        collector.stopCollecting()
    }
}
