package com.bios.app

import com.bios.app.ingest.SeizureDetectionBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM coverage of [SeizureDetectionBuffer] (#269 Cut 3b). The
 * Android-bound [com.bios.app.ingest.SeizureDetectionService] hosts
 * the SensorManager listener; the buffer is the only piece of that
 * hot path that's testable without an emulator.
 */
class SeizureDetectionBufferTest {

    @Test
    fun new_buffer_is_empty_and_has_no_start_timestamp() {
        val b = SeizureDetectionBuffer(sampleRateHz = 50.0, capacitySec = 60)
        assertTrue(b.isEmpty())
        assertEquals(0, b.size())
        assertNull(b.startTimestampMs)
    }

    @Test
    fun append_records_the_first_timestamp_as_buffer_start() {
        val b = SeizureDetectionBuffer(sampleRateHz = 50.0, capacitySec = 60)
        b.append(0f, 0f, 9.81f, nowMs = 1_700_000_000_000L)
        assertEquals(1_700_000_000_000L, b.startTimestampMs)
        assertEquals(1, b.size())
    }

    @Test
    fun magnitude_is_norm_minus_gravity() {
        // Resting phone with gravity on z should record near-zero magnitude.
        val b = SeizureDetectionBuffer(sampleRateHz = 50.0, capacitySec = 60)
        b.append(0f, 0f, 9.81f, nowMs = 0L)
        val snap = b.snapshot()
        assertEquals(1, snap.size)
        // norm = 9.81, minus gravity = ~0
        assertTrue("expected ~0 for resting sample, got ${snap.single()}", kotlin.math.abs(snap.single()) < 1e-6)
    }

    @Test
    fun magnitude_for_strong_motion_is_positive_after_gravity_subtraction() {
        val b = SeizureDetectionBuffer(sampleRateHz = 50.0, capacitySec = 60)
        // norm = sqrt(20^2) = 20, minus 9.81 = 10.19
        b.append(20f, 0f, 0f, nowMs = 0L)
        val v = b.snapshot().single()
        assertEquals(10.19, v, 0.01)
    }

    @Test
    fun appending_past_capacity_evicts_the_oldest_sample() {
        // Capacity = 1 s at 50 Hz = 50 samples.
        val b = SeizureDetectionBuffer(sampleRateHz = 50.0, capacitySec = 1)
        repeat(60) { i ->
            b.append(0f, 0f, 9.81f, nowMs = i.toLong())
        }
        assertEquals(50, b.size())
    }

    @Test
    fun start_timestamp_advances_when_the_buffer_evicts() {
        // Capacity = 1 s at 50 Hz = 50 samples. Period per sample = 20 ms.
        val b = SeizureDetectionBuffer(sampleRateHz = 50.0, capacitySec = 1)
        repeat(50) { i ->
            b.append(0f, 0f, 9.81f, nowMs = i * 20L) // 50 samples, no eviction
        }
        val beforeEvict = b.startTimestampMs
        assertEquals(0L, beforeEvict)

        b.append(0f, 0f, 9.81f, nowMs = 1_000L) // 51st append triggers one eviction
        val afterOneEvict = b.startTimestampMs
        assertNotNull(afterOneEvict)
        assertEquals(20L, afterOneEvict)

        repeat(10) { b.append(0f, 0f, 9.81f, nowMs = 2_000L) } // 10 more evictions
        assertEquals(20L + 10L * 20L, b.startTimestampMs)
    }

    @Test
    fun snapshot_returns_a_defensive_copy() {
        val b = SeizureDetectionBuffer(sampleRateHz = 50.0, capacitySec = 60)
        b.append(1f, 0f, 0f, nowMs = 0L)
        val s1 = b.snapshot()
        b.append(2f, 0f, 0f, nowMs = 20L)
        // s1 must not have grown; the implementation returns a copy.
        assertEquals(1, s1.size)
        assertEquals(2, b.snapshot().size)
    }
}
