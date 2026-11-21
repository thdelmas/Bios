package com.bios.app

import com.bios.app.alerts.AlertDetailContent
import com.bios.app.engine.DeviationScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The score table behind every alert: count and mean size of metrics beyond 1.5 σ. */
class DeviationScoreTest {

    private fun zeros() = MutableList(AlertDetailContent.WATCHED_METRICS.size) { 0.0 }

    @Test
    fun `no deviation scores below the threshold`() {
        assertEquals(0f, DeviationScore.of(zeros()), 0f)
        assertEquals(0f, DeviationScore.of(emptyList()), 0f)
    }

    @Test
    fun `many large deviations score above the threshold`() {
        assertTrue(DeviationScore.of(List(9) { 3.0 }) > DeviationScore.THRESHOLD)
    }

    @Test
    fun `three metrics beyond the cut reach the threshold exactly`() {
        val z = zeros().also { it[0] = 1.6; it[1] = -1.7; it[4] = 1.8 }
        assertEquals(DeviationScore.THRESHOLD, DeviationScore.of(z), 0f)
    }

    @Test
    fun `two metrics beyond the cut stay below the threshold however large`() {
        val z = zeros().also { it[0] = 6.0; it[1] = -6.0 }
        assertEquals(0.6f, DeviationScore.of(z), 0f)
        assertTrue(DeviationScore.of(z) < DeviationScore.THRESHOLD)
    }

    @Test
    fun `the table rises with count and with mean size`() {
        fun score(count: Int, size: Double) = DeviationScore.of(zeros().also { z -> repeat(count) { z[it] = size } })
        assertEquals(0.5f, score(1, 2.6), 0f)
        assertEquals(0.4f, score(2, 1.8), 0f)
        assertEquals(0.65f, score(3, 1.8), 0f)
        assertEquals(0.75f, score(3, 2.5), 0f)
        assertEquals(0.8f, score(4, 1.8), 0f)
        assertEquals(0.9f, score(4, 2.5), 0f)
    }

    @Test
    fun `direction does not matter, only distance`() {
        assertEquals(DeviationScore.of(List(9) { 3.0 }), DeviationScore.of(List(9) { -3.0 }), 0f)
    }

    @Test
    fun `quiet metrics score a tenth of the mean distance, capped`() {
        assertEquals(0.1f, DeviationScore.of(List(9) { 1.0 }), 1e-6f)
        val oneBelowSingleCut = zeros().also { it[0] = 2.4 }
        assertTrue(DeviationScore.of(oneBelowSingleCut) <= 0.3f)
    }

    @Test
    fun `score stays between 0 and 1`() {
        for (z in listOf(zeros(), List(9) { 5.0 }, List(9) { -3.0 }, zeros().also { it[4] = 4.0 })) {
            assertTrue(DeviationScore.of(z) in 0f..1f)
        }
    }

    @Test
    fun `keyed scores fill missing watched metrics with zero and ignore unknown keys`() {
        val keyed = mapOf("heart_rate" to 2.0, "heart_rate_variability" to -2.5, "respiratory_rate" to 1.8, "unknown" to 9.0)
        val positional = zeros().also { it[0] = 2.0; it[1] = -2.5; it[4] = 1.8 }
        assertEquals(DeviationScore.of(positional), DeviationScore.of(keyed), 0f)
        assertEquals(0.75f, DeviationScore.of(keyed), 0f)
    }

    @Test
    fun `nine metrics are watched`() {
        assertEquals(9, AlertDetailContent.WATCHED_METRICS.size)
    }
}
