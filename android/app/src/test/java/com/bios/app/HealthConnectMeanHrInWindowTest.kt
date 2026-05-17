package com.bios.app

import com.bios.app.ingest.HealthConnectAdapter
import com.bios.app.ingest.HealthConnectAdapter.HrSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the mean-HR-in-window helper used to enrich EXERCISE_SESSION
 * payloads with `avg_hr_bpm`. The helper has to be a pure function so
 * the per-session intersection is unit-testable without Health Connect.
 *
 * Returning null vs. zero is load-bearing — null means "we couldn't
 * measure this session", zero means "you had no heartbeat", and the
 * pattern engine reads the difference.
 */
class HealthConnectMeanHrInWindowTest {

    @Test
    fun returns_null_when_no_samples_fall_in_window() {
        val samples = listOf(
            HrSample(timestampMs = 1_000L, bpm = 70.0),
            HrSample(timestampMs = 2_000L, bpm = 72.0),
        )
        assertNull(HealthConnectAdapter.meanHrInWindow(samples, 5_000L, 6_000L))
    }

    @Test
    fun returns_null_when_input_is_empty() {
        assertNull(HealthConnectAdapter.meanHrInWindow(emptyList(), 0L, 1_000L))
    }

    @Test
    fun computes_arithmetic_mean_for_samples_in_window() {
        val samples = listOf(
            HrSample(timestampMs = 100L, bpm = 120.0),
            HrSample(timestampMs = 200L, bpm = 140.0),
            HrSample(timestampMs = 300L, bpm = 160.0),
        )
        assertEquals(140.0, HealthConnectAdapter.meanHrInWindow(samples, 0L, 1000L)!!, 0.0001)
    }

    @Test
    fun ignores_samples_outside_window() {
        val samples = listOf(
            HrSample(timestampMs = 50L, bpm = 60.0),    // before
            HrSample(timestampMs = 150L, bpm = 100.0),  // in
            HrSample(timestampMs = 250L, bpm = 140.0),  // in
            HrSample(timestampMs = 350L, bpm = 180.0),  // after
        )
        assertEquals(120.0, HealthConnectAdapter.meanHrInWindow(samples, 100L, 300L)!!, 0.0001)
    }

    @Test
    fun window_endpoints_are_inclusive() {
        val samples = listOf(
            HrSample(timestampMs = 100L, bpm = 100.0),
            HrSample(timestampMs = 200L, bpm = 200.0),
        )
        assertEquals(150.0, HealthConnectAdapter.meanHrInWindow(samples, 100L, 200L)!!, 0.0001)
    }
}
