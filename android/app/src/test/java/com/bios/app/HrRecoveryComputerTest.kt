package com.bios.app

import com.bios.app.engine.HrRecoveryComputer
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-state tests for [HrRecoveryComputer]. Synthetic HR timeseries cover
 * the canonical Cole 1999 shape (healthy autonomic recovery, blunted HRR,
 * absent post-exercise samples).
 */
class HrRecoveryComputerTest {

    private val sourceId = "test-hr"
    private val sessionStart = 1_700_000_000_000L
    private val sessionEnd = sessionStart + 30L * 60_000L // 30-minute session

    private fun hr(timestamp: Long, value: Double): MetricReading = MetricReading(
        metricType = MetricType.HEART_RATE.key,
        value = value,
        timestamp = timestamp,
        sourceId = sourceId,
        confidence = ConfidenceTier.LOW.level,
    )

    @Test
    fun healthy_recovery_yields_large_HRR_deltas() {
        // Peak 165 bpm during session, drops to 130 at +60 s (HRR1 = 35),
        // 115 at +120 s (HRR2 = 50). Both above the Cole 1999 cutoff.
        val readings = listOf(
            hr(sessionStart + 5 * 60_000L, 140.0),
            hr(sessionStart + 10 * 60_000L, 155.0),
            hr(sessionStart + 15 * 60_000L, 160.0),
            hr(sessionStart + 20 * 60_000L, 165.0),
            hr(sessionStart + 25 * 60_000L, 162.0),
            hr(sessionEnd, 158.0),
            hr(sessionEnd + 60_000L, 130.0),
            hr(sessionEnd + 120_000L, 115.0),
        )
        val result = HrRecoveryComputer.compute(sessionStart, sessionEnd, readings)
        assertNotNull(result)
        assertEquals(165.0, result!!.peakHrBpm, 1e-9)
        assertEquals(130.0, result.hrAt60sBpm!!, 1e-9)
        assertEquals(115.0, result.hrAt120sBpm!!, 1e-9)
        assertEquals(35.0, result.hrr1BpmDelta!!, 1e-9)
        assertEquals(50.0, result.hrr2BpmDelta!!, 1e-9)
    }

    @Test
    fun blunted_recovery_yields_small_HRR_deltas() {
        // Peak 150, drops only to 145 at +60 s (HRR1 = 5, below Cole cutoff).
        val readings = listOf(
            hr(sessionStart + 5 * 60_000L, 130.0),
            hr(sessionStart + 10 * 60_000L, 140.0),
            hr(sessionStart + 15 * 60_000L, 148.0),
            hr(sessionStart + 20 * 60_000L, 150.0),
            hr(sessionStart + 25 * 60_000L, 149.0),
            hr(sessionEnd, 148.0),
            hr(sessionEnd + 60_000L, 145.0),
            hr(sessionEnd + 120_000L, 142.0),
        )
        val result = HrRecoveryComputer.compute(sessionStart, sessionEnd, readings)
        assertNotNull(result)
        assertEquals(5.0, result!!.hrr1BpmDelta!!, 1e-9)
        assertEquals(8.0, result.hrr2BpmDelta!!, 1e-9)
    }

    @Test
    fun missing_post_exercise_sample_returns_null_delta_for_that_tau() {
        // Only the +60 s sample is in the feed; +120 s is absent.
        val readings = listOf(
            hr(sessionStart + 5 * 60_000L, 130.0),
            hr(sessionStart + 10 * 60_000L, 145.0),
            hr(sessionStart + 15 * 60_000L, 155.0),
            hr(sessionStart + 20 * 60_000L, 160.0),
            hr(sessionStart + 25 * 60_000L, 158.0),
            hr(sessionEnd + 60_000L, 125.0),
        )
        val result = HrRecoveryComputer.compute(sessionStart, sessionEnd, readings)
        assertNotNull(result)
        assertEquals(160.0, result!!.peakHrBpm, 1e-9)
        assertEquals(35.0, result.hrr1BpmDelta!!, 1e-9)
        assertNull(result.hrAt120sBpm)
        assertNull(result.hrr2BpmDelta)
    }

    @Test
    fun too_few_session_readings_returns_null() {
        // Two HR samples is below MIN_SESSION_READINGS. Peak HR has too little
        // anchor to be trustworthy — better to declare we don't know.
        val readings = listOf(
            hr(sessionStart + 10 * 60_000L, 150.0),
            hr(sessionStart + 20 * 60_000L, 160.0),
            hr(sessionEnd + 60_000L, 125.0),
        )
        val result = HrRecoveryComputer.compute(sessionStart, sessionEnd, readings)
        assertNull(result)
    }

    @Test
    fun post_exercise_tolerance_accepts_off_boundary_sample() {
        // 1-Hz feed lands the closest +60 s sample at +63 s (within 5 s
        // tolerance). The computer should still anchor against it.
        val readings = listOf(
            hr(sessionStart + 5 * 60_000L, 130.0),
            hr(sessionStart + 10 * 60_000L, 145.0),
            hr(sessionStart + 15 * 60_000L, 155.0),
            hr(sessionStart + 20 * 60_000L, 160.0),
            hr(sessionStart + 25 * 60_000L, 158.0),
            hr(sessionEnd + 63_000L, 130.0), // 3 s past target — still inside tolerance
        )
        val result = HrRecoveryComputer.compute(sessionStart, sessionEnd, readings)
        assertNotNull(result)
        assertEquals(130.0, result!!.hrAt60sBpm!!, 1e-9)
    }

    @Test
    fun post_exercise_sample_outside_tolerance_window_is_skipped() {
        // The only candidate +60 s sample is 12 s away from target — outside
        // the 5 s tolerance. The computer must not pick it; HRR1 is null.
        val readings = listOf(
            hr(sessionStart + 5 * 60_000L, 130.0),
            hr(sessionStart + 10 * 60_000L, 145.0),
            hr(sessionStart + 15 * 60_000L, 155.0),
            hr(sessionStart + 20 * 60_000L, 160.0),
            hr(sessionStart + 25 * 60_000L, 158.0),
            hr(sessionEnd + 72_000L, 130.0),
        )
        val result = HrRecoveryComputer.compute(sessionStart, sessionEnd, readings)
        assertNotNull(result)
        assertNull(result!!.hrAt60sBpm)
        assertNull(result.hrr1BpmDelta)
    }

    @Test
    fun non_HR_readings_are_ignored() {
        // A stray SpO2 reading at peak time should not poison peak HR.
        val readings = listOf(
            hr(sessionStart + 5 * 60_000L, 130.0),
            hr(sessionStart + 10 * 60_000L, 145.0),
            hr(sessionStart + 15 * 60_000L, 155.0),
            hr(sessionStart + 20 * 60_000L, 160.0),
            hr(sessionStart + 25 * 60_000L, 158.0),
            MetricReading(
                metricType = MetricType.BLOOD_OXYGEN.key,
                value = 99.0,
                timestamp = sessionStart + 20 * 60_000L,
                sourceId = sourceId,
                confidence = ConfidenceTier.LOW.level,
            ),
            hr(sessionEnd + 60_000L, 125.0),
        )
        val result = HrRecoveryComputer.compute(sessionStart, sessionEnd, readings)
        assertNotNull(result)
        assertEquals(160.0, result!!.peakHrBpm, 1e-9)
    }
}
