package com.devopsdays.qoe.player.utils

import io.qameta.allure.Feature
import io.qameta.allure.Story
import org.junit.Assert.assertEquals
import org.junit.Test

@Feature("QoE Quality Calculator")
class QoEQualityCalculatorTest {

    // ── Excellent ─────────────────────────────────────────────────────────────

    @Test
    @Story("Excellent quality")
    fun `returns excellent for zero buffering and no errors`() {
        assertEquals("excellent", QoEQualityCalculator.calculate(0.0, 0))
    }

    @Test
    @Story("Excellent quality")
    fun `returns excellent for 1 second buffering and no errors`() {
        assertEquals("excellent", QoEQualityCalculator.calculate(1.0, 0))
    }

    @Test
    @Story("Excellent quality")
    fun `returns excellent just below 2 second threshold`() {
        assertEquals("excellent", QoEQualityCalculator.calculate(1.999, 0))
    }

    // ── Good ─────────────────────────────────────────────────────────────────

    @Test
    @Story("Good quality")
    fun `returns good for 2 seconds buffering and no errors`() {
        assertEquals("good", QoEQualityCalculator.calculate(2.0, 0))
    }

    @Test
    @Story("Good quality")
    fun `returns good for 0 buffering with 1 error`() {
        assertEquals("good", QoEQualityCalculator.calculate(0.0, 1))
    }

    @Test
    @Story("Good quality")
    fun `returns good for 3 seconds buffering with 1 error`() {
        assertEquals("good", QoEQualityCalculator.calculate(3.0, 1))
    }

    @Test
    @Story("Good quality")
    fun `returns good just below 5 second threshold`() {
        assertEquals("good", QoEQualityCalculator.calculate(4.999, 0))
    }

    // ── Fair ─────────────────────────────────────────────────────────────────

    @Test
    @Story("Fair quality")
    fun `returns fair for 5 seconds buffering`() {
        assertEquals("fair", QoEQualityCalculator.calculate(5.0, 0))
    }

    @Test
    @Story("Fair quality")
    fun `returns fair for 0 buffering with 2 errors`() {
        assertEquals("fair", QoEQualityCalculator.calculate(0.0, 2))
    }

    @Test
    @Story("Fair quality")
    fun `returns fair for 7 seconds buffering with 3 errors`() {
        assertEquals("fair", QoEQualityCalculator.calculate(7.0, 3))
    }

    @Test
    @Story("Fair quality")
    fun `returns fair just below 10 second threshold`() {
        assertEquals("fair", QoEQualityCalculator.calculate(9.999, 4))
    }

    // ── Poor ─────────────────────────────────────────────────────────────────

    @Test
    @Story("Poor quality")
    fun `returns poor for 10 seconds buffering`() {
        assertEquals("poor", QoEQualityCalculator.calculate(10.0, 0))
    }

    @Test
    @Story("Poor quality")
    fun `returns poor for 0 buffering with 5 errors`() {
        assertEquals("poor", QoEQualityCalculator.calculate(0.0, 5))
    }

    @Test
    @Story("Poor quality")
    fun `returns poor for very high buffering time`() {
        assertEquals("poor", QoEQualityCalculator.calculate(999.0, 100))
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    @Story("Edge cases")
    fun `error count takes precedence over buffering when determining poor`() {
        // 5 errors → poor regardless of buffering
        assertEquals("poor", QoEQualityCalculator.calculate(0.0, 5))
    }

    @Test
    @Story("Edge cases")
    fun `negative buffering time treated as excellent`() {
        assertEquals("excellent", QoEQualityCalculator.calculate(-1.0, 0))
    }
}
