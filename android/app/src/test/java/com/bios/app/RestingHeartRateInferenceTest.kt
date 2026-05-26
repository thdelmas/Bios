package com.bios.app

import com.bios.app.engine.RestingHeartRateInference
import com.bios.app.engine.RestingHeartRateInference.HrSample
import com.bios.app.engine.RestingHeartRateInference.StepsBucket
import com.bios.app.model.ConfidenceTier
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestingHeartRateInferenceTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val baseDate: LocalDate = LocalDate.of(2026, 5, 25)
    private val dayStartMs: Long = baseDate.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun atOffsetMs(hours: Int, minutes: Int = 0): Long =
        dayStartMs + hours * 3_600_000L + minutes * 60_000L

    private fun quietHrSeries(
        startHour: Int,
        endHourExclusive: Int,
        bpmFloor: Double = 55.0,
        bpmCeiling: Double = 70.0,
        sampleEveryMin: Int = 1,
    ): List<HrSample> {
        val out = mutableListOf<HrSample>()
        val totalMinutes = (endHourExclusive - startHour) * 60
        var i = 0
        var minute = 0
        while (minute < totalMinutes) {
            // Sweep deterministically across the range so the 10th
            // percentile lands at a predictable value.
            val bpm = bpmFloor + ((i % 10) / 9.0) * (bpmCeiling - bpmFloor)
            out += HrSample(atOffsetMs(startHour, minute), bpm)
            minute += sampleEveryMin
            i++
        }
        return out
    }

    @Test
    fun `quiet night emits one RHR at day start`() {
        val hr = quietHrSeries(startHour = 1, endHourExclusive = 6, bpmFloor = 50.0, bpmCeiling = 60.0)
        val steps = emptyList<StepsBucket>()

        val candidates = RestingHeartRateInference.infer(hr, steps, zone)

        assertEquals(1, candidates.size)
        val c = candidates.first()
        assertEquals(dayStartMs, c.dayStartMs)
        assertEquals(ConfidenceTier.MEDIUM, c.confidence)
        assertTrue("RHR should sit in the resting band, got ${c.rhrBpm}", c.rhrBpm in 50.0..60.0)
        // The 10th percentile of a 50-60 ramp should land near 51.
        assertEquals(51.0, c.rhrBpm, 1.5)
    }

    @Test
    fun `samples during steps activity are excluded`() {
        // 30 minutes of resting HR + 30 minutes during a walk (high HR).
        val resting = quietHrSeries(startHour = 2, endHourExclusive = 3, bpmFloor = 55.0, bpmCeiling = 60.0)
            .take(30)
        val walking = (0 until 30).map { i ->
            HrSample(atOffsetMs(3, i), 110.0 + (i % 10))
        }
        val stepsBucket = StepsBucket(
            timestamp = atOffsetMs(3, 0),
            value = 1500.0,
            durationSec = 30 * 60,
        )

        val candidates = RestingHeartRateInference.infer(
            hrSamples = resting + walking,
            stepsBuckets = listOf(stepsBucket),
            zone = zone,
        )

        // Only 30 quiet samples remain — exactly the minimum, so emission
        // is expected and the percentile must reflect resting HR only.
        assertEquals(1, candidates.size)
        assertTrue(
            "RHR should reflect resting samples only, got ${candidates.first().rhrBpm}",
            candidates.first().rhrBpm < 70.0,
        )
    }

    @Test
    fun `cooldown after activity excludes post-exertion samples`() {
        // Steps bucket from 02:00-02:10. HR samples at 02:15 (within 15-min
        // cooldown) should be excluded; samples at 02:30 (past cooldown)
        // should count.
        val stepsBucket = StepsBucket(
            timestamp = atOffsetMs(2, 0),
            value = 600.0,
            durationSec = 10 * 60,
        )
        val cooldownSample = HrSample(atOffsetMs(2, 15), 90.0)
        val recoveredSamples = (0 until 50).map { i ->
            HrSample(atOffsetMs(2, 30 + i), 58.0 + (i % 5))
        }

        val candidates = RestingHeartRateInference.infer(
            hrSamples = listOf(cooldownSample) + recoveredSamples,
            stepsBuckets = listOf(stepsBucket),
            zone = zone,
        )

        assertEquals(1, candidates.size)
        val rhr = candidates.first().rhrBpm
        // 90.0 from the cooldown sample would skew the percentile up; if
        // it were included alongside the 58-62 recovered band, the 10th
        // percentile of 51 mixed samples would still land near 58 but
        // the sample count would not match. We verify both.
        assertEquals("recovered samples should be the only contributors", 50, candidates.first().sampleCount)
        assertTrue("RHR should reflect recovered samples, got $rhr", rhr < 65.0)
    }

    @Test
    fun `sparse day below minimum quiet samples emits nothing`() {
        // 29 quiet samples — one below MIN_QUIET_SAMPLES.
        val hr = (0 until 29).map { i -> HrSample(atOffsetMs(3, i), 60.0) }
        assertTrue(hr.size < RestingHeartRateInference.MIN_QUIET_SAMPLES)
        val candidates = RestingHeartRateInference.infer(hr, emptyList(), zone)
        assertTrue("expected no candidate for sparse day", candidates.isEmpty())
    }

    @Test
    fun `multiple days each get their own RHR`() {
        val day1Hr = quietHrSeries(startHour = 1, endHourExclusive = 4, bpmFloor = 52.0, bpmCeiling = 58.0)
        val day2Offset = 24 * 60
        val day2Hr = (0 until 180).map { i ->
            HrSample(atOffsetMs(0, day2Offset + i), 62.0 + (i % 5))
        }

        val candidates = RestingHeartRateInference.infer(
            hrSamples = day1Hr + day2Hr,
            stepsBuckets = emptyList(),
            zone = zone,
        )

        assertEquals(2, candidates.size)
        val day1 = candidates.first { it.dayStartMs == dayStartMs }
        val day2 = candidates.first { it.dayStartMs == dayStartMs + 24 * 3_600_000L }
        assertNotNull(day1)
        assertNotNull(day2)
        assertTrue(
            "day-2 RHR ($day2.rhrBpm) should sit above day-1 RHR (${day1.rhrBpm})",
            day2.rhrBpm > day1.rhrBpm,
        )
    }

    @Test
    fun `all-day activity yields no RHR`() {
        val hr = quietHrSeries(startHour = 8, endHourExclusive = 22, bpmFloor = 100.0, bpmCeiling = 130.0)
        // One long active bucket covering the whole HR window.
        val steps = listOf(
            StepsBucket(
                timestamp = atOffsetMs(8, 0),
                value = 12_000.0,
                durationSec = 14 * 60 * 60,
            )
        )
        val candidates = RestingHeartRateInference.infer(hr, steps, zone)
        assertTrue("expected no candidate when every sample is gated out", candidates.isEmpty())
    }

    @Test
    fun `percentile interpolates linearly between adjacent samples`() {
        val values = listOf(60.0, 62.0, 64.0, 66.0, 68.0, 70.0, 72.0, 74.0, 76.0, 78.0, 80.0)
        // 10th percentile of an 11-sample 60..80 ramp = sample at rank 1 = 62.0.
        assertEquals(62.0, RestingHeartRateInference.percentile(values, 10.0), 0.001)
        // 50th percentile = sample at rank 5 = 70.0.
        assertEquals(70.0, RestingHeartRateInference.percentile(values, 50.0), 0.001)
    }

    @Test
    fun `empty input is silent`() {
        assertTrue(RestingHeartRateInference.infer(emptyList(), emptyList(), zone).isEmpty())
    }

    @Test
    fun `zero-step buckets do not gate HR samples`() {
        val hr = quietHrSeries(startHour = 1, endHourExclusive = 3)
        val zeroSteps = listOf(
            StepsBucket(timestamp = atOffsetMs(1, 30), value = 0.0, durationSec = 5 * 60),
            StepsBucket(timestamp = atOffsetMs(2, 30), value = 0.0, durationSec = 5 * 60),
        )
        val candidates = RestingHeartRateInference.infer(hr, zeroSteps, zone)
        assertEquals(1, candidates.size)
        // All 120 minutes' worth of samples should qualify — zero-step
        // buckets carry no activity gating.
        assertEquals(120, candidates.first().sampleCount)
    }

    @Test
    fun `local zone boundary groups samples to the right day`() {
        val parisZone = ZoneId.of("Europe/Paris")
        // 22:30 UTC on baseDate = 00:30 Paris on the NEXT day — must be
        // grouped under baseDate + 1, not baseDate.
        val parisDayStart = baseDate.plusDays(1).atStartOfDay(parisZone).toInstant().toEpochMilli()
        val baseUtcMidnight = baseDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        val hr = (0 until 60).map { i ->
            HrSample(baseUtcMidnight + 22 * 3_600_000L + 30 * 60_000L + i * 60_000L, 56.0 + (i % 4))
        }

        val candidates = RestingHeartRateInference.infer(hr, emptyList(), parisZone)

        assertEquals(1, candidates.size)
        assertEquals(parisDayStart, candidates.first().dayStartMs)
    }
}
