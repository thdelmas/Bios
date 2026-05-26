package com.bios.app

import com.bios.app.engine.HrGapTibInference
import com.bios.app.engine.HrGapTibInference.HrSample
import com.bios.app.engine.HrGapTibInference.SleepWindow
import com.bios.app.model.ConfidenceTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pins the HR-gap → TIME_IN_BED inference. The owner removes their wearable
 * to sleep; the resulting multi-hour HR-stream gap is the only on-device
 * signal that they were lying down. These tests fix the contract: which
 * gaps emit TIB, which don't, and how companion-written sleep_duration
 * upgrades the confidence.
 */
class HrGapTibInferenceTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val anchorDate: LocalDate = LocalDate.of(2026, 5, 25)
    private val hourMs = 3_600_000L

    private fun at(hour: Int, minute: Int = 0, dayOffset: Long = 0L): Long =
        ZonedDateTime.of(anchorDate.plusDays(dayOffset), java.time.LocalTime.of(hour, minute), zone)
            .toInstant().toEpochMilli()

    @Test
    fun `empty input produces no candidates`() {
        assertTrue(HrGapTibInference.infer(emptyList(), zone = zone).isEmpty())
    }

    @Test
    fun `single sample produces no candidates`() {
        val candidates = HrGapTibInference.infer(listOf(HrSample(at(12))), zone = zone)
        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `2-hour gap is below the minimum and is ignored`() {
        val samples = listOf(HrSample(at(2)), HrSample(at(4)))
        assertTrue(HrGapTibInference.infer(samples, zone = zone).isEmpty())
    }

    @Test
    fun `overnight gap crossing the nocturnal anchor emits a LOW-confidence candidate`() {
        // Watch removed at 23:00, put back on at 07:00 next day → 8h gap
        // that includes 02:00–05:00 on day+1.
        val samples = listOf(
            HrSample(at(23)),
            HrSample(at(7, dayOffset = 1)),
        )
        val candidates = HrGapTibInference.infer(samples, zone = zone)
        assertEquals(1, candidates.size)
        val c = candidates.single()
        assertEquals(8 * 3600, c.durationSec)
        assertEquals(ConfidenceTier.LOW, c.confidence)
        assertFalse(c.corroboratedByCompanionSleep)
    }

    @Test
    fun `4-hour afternoon gap does not overlap the anchor and is ignored`() {
        // 12:00 to 17:00 — five-hour gap, but entirely daytime.
        val samples = listOf(HrSample(at(12)), HrSample(at(17)))
        assertTrue(HrGapTibInference.infer(samples, zone = zone).isEmpty())
    }

    @Test
    fun `corroborating companion sleep_duration upgrades confidence to MEDIUM`() {
        val gapStart = at(23)
        val gapEnd = at(7, dayOffset = 1)
        val samples = listOf(HrSample(gapStart), HrSample(gapEnd))
        // W2F-style midpoint write covering roughly the same window.
        val midpoint = gapStart + (gapEnd - gapStart) / 2
        val companion = SleepWindow(midpointMs = midpoint, durationSec = 7 * 3600)
        val candidates = HrGapTibInference.infer(
            samples, companionSleepWindows = listOf(companion), zone = zone,
        )
        assertEquals(1, candidates.size)
        assertEquals(ConfidenceTier.MEDIUM, candidates.single().confidence)
        assertTrue(candidates.single().corroboratedByCompanionSleep)
    }

    @Test
    fun `companion window with no temporal overlap does not corroborate`() {
        val samples = listOf(HrSample(at(23)), HrSample(at(7, dayOffset = 1)))
        // Companion sleep window from a different night entirely.
        val priorNight = SleepWindow(
            midpointMs = at(3, dayOffset = -1),
            durationSec = 7 * 3600,
        )
        val candidates = HrGapTibInference.infer(
            samples, companionSleepWindows = listOf(priorNight), zone = zone,
        )
        assertEquals(1, candidates.size)
        assertEquals(ConfidenceTier.LOW, candidates.single().confidence)
        assertFalse(candidates.single().corroboratedByCompanionSleep)
    }

    @Test
    fun `gap ending right at 02_00 still overlaps the anchor`() {
        // Watch off 21:30, put back on at 02:30 — gap = 5h, ends inside
        // the 02:00–05:00 anchor.
        val start = at(21, 30, dayOffset = -1)
        val end = at(2, 30)
        val candidates = HrGapTibInference.infer(
            listOf(HrSample(start), HrSample(end)), zone = zone,
        )
        assertEquals(1, candidates.size)
    }

    @Test
    fun `repeated nights emit one candidate per night`() {
        // Three consecutive nights of watch-off-overnight.
        val samples = listOf(
            HrSample(at(22)),
            HrSample(at(7, dayOffset = 1)),
            HrSample(at(22, dayOffset = 1)),
            HrSample(at(7, dayOffset = 2)),
            HrSample(at(22, dayOffset = 2)),
            HrSample(at(7, dayOffset = 3)),
        )
        val candidates = HrGapTibInference.infer(samples, zone = zone)
        assertEquals(3, candidates.size)
        candidates.forEach { assertEquals(9 * 3600, it.durationSec) }
    }

    @Test
    fun `unsorted input is sorted before scanning`() {
        // Same overnight gap, samples passed in reverse order.
        val samples = listOf(
            HrSample(at(7, dayOffset = 1)),
            HrSample(at(23)),
        )
        val candidates = HrGapTibInference.infer(samples, zone = zone)
        assertEquals(1, candidates.size)
    }

    @Test
    fun `minimum gap of exactly 4 hours overlapping anchor emits a candidate`() {
        val samples = listOf(HrSample(at(1)), HrSample(at(5)))
        val candidates = HrGapTibInference.infer(samples, zone = zone)
        assertEquals(1, candidates.size)
        assertEquals(4 * 3600, candidates.single().durationSec)
    }

    @Test
    fun `dense daytime samples around the gap do not produce extra candidates`() {
        val samples = buildList {
            // Daytime hourly samples on day-before
            for (h in 8..22) add(HrSample(at(h, dayOffset = -1)))
            // Overnight gap (watch off)
            // Daytime hourly samples on next day
            for (h in 8..22) add(HrSample(at(h)))
        }
        val candidates = HrGapTibInference.infer(samples, zone = zone)
        assertEquals(1, candidates.size)
        // 22:00 prior day to 08:00 current day = 10h
        assertEquals(10 * 3600, candidates.single().durationSec)
    }
}
