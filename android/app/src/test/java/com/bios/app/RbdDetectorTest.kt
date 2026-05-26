package com.bios.app

import com.bios.app.engine.RbdDetector
import com.bios.app.engine.RbdDetector.RemInterval
import com.bios.app.engine.RbdDetector.StageSegment
import com.bios.app.model.SleepStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the REM-sleep-behaviour-disorder (RBD) wearable-proxy detector
 * (#206, NEUROLOGY_POV §2.7).
 *
 * Synthetic fixtures:
 *  - A 10-minute REM window with a single inserted burst of accelerometer
 *    activity must produce exactly one burst event.
 *  - Quiet (atonic) REM must produce zero bursts.
 *  - Bursts during non-REM stages must not be counted.
 *  - Multiple bursts inside the same REM window must aggregate into the
 *    night summary correctly.
 */
class RbdDetectorTest {

    private val sampleRateHz = 50.0
    private val nightStart: Long = 1_700_000_000_000L  // arbitrary epoch ms

    /** 1 minute of atonic noise (sub-threshold gravity-removed magnitudes). */
    private fun atonicSegment(seconds: Int): DoubleArray =
        DoubleArray((seconds * sampleRateHz).toInt()) { 0.02 }

    /** Burst patch: above-threshold magnitudes for [n] samples. */
    private fun burstPatch(n: Int = 30, amp: Double = 1.0): DoubleArray =
        DoubleArray(n) { amp }

    /** Stitches segments together. */
    private fun stitch(vararg segments: DoubleArray): DoubleArray {
        val total = segments.sumOf { it.size }
        val out = DoubleArray(total)
        var idx = 0
        for (seg in segments) {
            seg.copyInto(out, idx)
            idx += seg.size
        }
        return out
    }

    // ---- single REM burst ----

    @Test
    fun flags_a_burst_during_synthetic_REM_sleep() {
        // 60s atonic + 0.6s burst + 60s atonic = 121s of REM data.
        val magnitudes = stitch(
            atonicSegment(60),
            burstPatch(n = 30, amp = 1.0),
            atonicSegment(60),
        )
        val accelStart = nightStart
        val remEnd = accelStart + (magnitudes.size * 1000.0 / sampleRateHz).toLong()
        val rem = RemInterval(startMillis = accelStart, endMillis = remEnd)

        val events = RbdDetector.detectBursts(
            remIntervals = listOf(rem),
            accelerometerMagnitudes = magnitudes,
            accelerometerStartMillis = accelStart,
            sampleRateHz = sampleRateHz,
        )

        assertEquals("must detect exactly one burst in this REM window", 1, events.size)
        val ev = events.first()
        assertTrue("peak amplitude must be near the synthetic 1.0 m/s²", ev.peakAmplitude >= 0.99)
        assertTrue("duration must be at least the minimum samples",
            ev.durationSamples >= RbdDetector.MIN_BURST_DURATION_SAMPLES)
    }

    // ---- atonic REM yields no events ----

    @Test
    fun atonic_REM_window_produces_zero_bursts() {
        val magnitudes = atonicSegment(120)
        val accelStart = nightStart
        val remEnd = accelStart + (magnitudes.size * 1000.0 / sampleRateHz).toLong()
        val rem = RemInterval(startMillis = accelStart, endMillis = remEnd)

        val events = RbdDetector.detectBursts(
            remIntervals = listOf(rem),
            accelerometerMagnitudes = magnitudes,
            accelerometerStartMillis = accelStart,
            sampleRateHz = sampleRateHz,
        )
        assertTrue("normal atonic REM must not produce any RBD events", events.isEmpty())
    }

    // ---- bursts outside REM are ignored ----

    @Test
    fun bursts_during_NREM_stages_are_not_counted() {
        // Whole-night data: deep sleep with bursts, no REM window scanned.
        val magnitudes = stitch(
            atonicSegment(60), burstPatch(30, 1.0), atonicSegment(60),
        )
        val accelStart = nightStart

        // Stage segments: a single DEEP stage covering the whole night,
        // no REM intervals at all.
        val stages = listOf(
            StageSegment(
                stage = SleepStage.DEEP,
                startMillis = accelStart,
                endMillis = accelStart + (magnitudes.size * 1000.0 / sampleRateHz).toLong(),
            ),
        )
        val summary = RbdDetector.analyseNight(
            nightStartMillis = accelStart,
            nightEndMillis = accelStart + (magnitudes.size * 1000.0 / sampleRateHz).toLong(),
            stages = stages,
            accelerometerMagnitudes = magnitudes,
            accelerometerStartMillis = accelStart,
            sampleRateHz = sampleRateHz,
        )

        assertEquals(0, summary.burstCount)
        assertFalse("a deep-sleep burst must not flag RBD", summary.hasRbdEvent)
    }

    // ---- multiple REM bursts aggregate correctly ----

    @Test
    fun multiple_bursts_in_one_REM_window_aggregate_into_the_night_summary() {
        // Three bursts separated by atonic gaps in a single REM window.
        val magnitudes = stitch(
            atonicSegment(30), burstPatch(30, 1.0),
            atonicSegment(30), burstPatch(30, 1.2),
            atonicSegment(30), burstPatch(30, 0.9),
            atonicSegment(30),
        )
        val accelStart = nightStart
        val remEnd = accelStart + (magnitudes.size * 1000.0 / sampleRateHz).toLong()
        val stages = listOf(
            StageSegment(SleepStage.REM, startMillis = accelStart, endMillis = remEnd),
        )

        val summary = RbdDetector.analyseNight(
            nightStartMillis = accelStart,
            nightEndMillis = remEnd,
            stages = stages,
            accelerometerMagnitudes = magnitudes,
            accelerometerStartMillis = accelStart,
            sampleRateHz = sampleRateHz,
        )

        assertEquals(3, summary.burstCount)
        assertTrue(summary.hasRbdEvent)
        assertTrue("REM duration must be the full window",
            summary.remDurationMillis > 0)
        assertEquals("events list must hold each burst", 3, summary.events.size)
        // Events sorted by start time.
        val starts = summary.events.map { it.timestampMillis }
        assertEquals(starts.sorted(), starts)
    }

    @Test
    fun rem_intervals_from_stages_filters_to_REM_only() {
        val stages = listOf(
            StageSegment(SleepStage.AWAKE, 0L, 1_000L),
            StageSegment(SleepStage.LIGHT, 1_000L, 5_000L),
            StageSegment(SleepStage.REM, 5_000L, 9_000L),
            StageSegment(SleepStage.DEEP, 9_000L, 15_000L),
            StageSegment(SleepStage.REM, 15_000L, 20_000L),
        )
        val rem = RbdDetector.remIntervalsFromStages(stages)
        assertEquals(2, rem.size)
        assertEquals(5_000L, rem[0].startMillis)
        assertEquals(9_000L, rem[0].endMillis)
        assertEquals(15_000L, rem[1].startMillis)
        assertEquals(20_000L, rem[1].endMillis)
    }

    @Test
    fun empty_accelerometer_input_returns_empty_event_list() {
        val rem = listOf(RemInterval(0L, 10_000L))
        val events = RbdDetector.detectBursts(
            remIntervals = rem,
            accelerometerMagnitudes = DoubleArray(0),
            accelerometerStartMillis = 0L,
            sampleRateHz = sampleRateHz,
        )
        assertTrue(events.isEmpty())
    }
}
