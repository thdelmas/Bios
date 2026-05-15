package com.bios.app

import com.bios.app.engine.CycleInference
import com.bios.app.model.CyclePhase
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import org.junit.Assert.*
import org.junit.Test

class CycleInferenceTest {

    private val sourceId = "test-src"
    private val msPerDay = 86_400_000L
    private val day0 = 1_700_000_000_000L

    private fun bbt(dayIndex: Int, valueC: Double, offsetMs: Long = 0L) = MetricReading(
        metricType = MetricType.BASAL_BODY_TEMPERATURE.key,
        value = valueC,
        timestamp = day0 + dayIndex * msPerDay + offsetMs,
        sourceId = sourceId,
        confidence = 2
    )

    @Test
    fun `returns empty when input is empty`() {
        assertTrue(CycleInference.deriveCyclePhases(emptyList(), sourceId).isEmpty())
    }

    @Test
    fun `returns empty when only non-BBT readings are present`() {
        val readings = (0 until 12).map { i ->
            MetricReading(
                metricType = MetricType.HEART_RATE.key,
                value = 60.0,
                timestamp = day0 + i * msPerDay,
                sourceId = sourceId,
                confidence = 2
            )
        }
        assertTrue(CycleInference.deriveCyclePhases(readings, sourceId).isEmpty())
    }

    @Test
    fun `returns empty when fewer than nine daily BBTs`() {
        val readings = (0 until 8).map { bbt(it, 36.5) }
        assertTrue(CycleInference.deriveCyclePhases(readings, sourceId).isEmpty())
    }

    @Test
    fun `returns empty when BBT stays flat with no sustained rise`() {
        val readings = (0 until 14).map { bbt(it, 36.5) }
        assertTrue(CycleInference.deriveCyclePhases(readings, sourceId).isEmpty())
    }

    @Test
    fun `detects biphasic shift and assigns follicular ovulatory luteal phases`() {
        // Days 0-5 follicular at 36.5°C → baseline 36.5, coverline 36.6.
        // Days 6-11 luteal at 36.8°C → sustained rise starts at day 6.
        // Day 5 is the inferred ovulation day.
        val readings = (0 until 12).map { i ->
            bbt(i, if (i < 6) 36.5 else 36.8)
        }
        val phases = CycleInference.deriveCyclePhases(readings, sourceId)
        assertEquals(12, phases.size)

        for (i in 0 until 5) {
            assertEquals(
                "day $i should be FOLLICULAR",
                CyclePhase.FOLLICULAR.value.toDouble(), phases[i].value, 0.0
            )
        }
        assertEquals(
            "day 5 should be OVULATORY",
            CyclePhase.OVULATORY.value.toDouble(), phases[5].value, 0.0
        )
        for (i in 6 until 12) {
            assertEquals(
                "day $i should be LUTEAL",
                CyclePhase.LUTEAL.value.toDouble(), phases[i].value, 0.0
            )
        }
    }

    @Test
    fun `emitted readings are CYCLE_PHASE with the inputs sourceId and timestamp`() {
        val readings = (0 until 12).map { i ->
            bbt(i, if (i < 6) 36.5 else 36.8)
        }
        val phases = CycleInference.deriveCyclePhases(readings, "src-xyz")

        phases.forEach {
            assertEquals(MetricType.CYCLE_PHASE.key, it.metricType)
            assertEquals("src-xyz", it.sourceId)
        }
        // Per-day timestamps match the underlying BBT row used for that day.
        for (i in phases.indices) {
            assertEquals(readings[i].timestamp, phases[i].timestamp)
        }
    }

    @Test
    fun `ignores brief one-day spikes that fall back below the coverline`() {
        // Day 6 spikes to 36.9 then days 7-8 drop back. No 3-day sustained
        // rise → no inference. Days 9-11 rise but only 3 days, starting after
        // the dip — still valid? Let's verify the formal rule: with the first
        // 6 days baselining at 36.5 (coverline 36.6), days 9,10,11 are at
        // 36.8 so they DO meet the rule, with rise starting at day 9.
        val temps = doubleArrayOf(
            36.5, 36.5, 36.5, 36.5, 36.5, 36.5,  // baseline
            36.9, 36.5, 36.5,                    // spike then dip
            36.8, 36.8, 36.8                     // sustained rise
        )
        val readings = temps.mapIndexed { i, t -> bbt(i, t) }
        val phases = CycleInference.deriveCyclePhases(readings, sourceId)
        assertEquals(12, phases.size)
        // The spike at day 6 should not be classified as the rise start.
        // Sustained rise starts at day 9 → ovulation = day 8.
        assertEquals(CyclePhase.OVULATORY.value.toDouble(), phases[8].value, 0.0)
        assertEquals(CyclePhase.LUTEAL.value.toDouble(), phases[9].value, 0.0)
        assertEquals(CyclePhase.FOLLICULAR.value.toDouble(), phases[7].value, 0.0)
    }

    @Test
    fun `aggregates multiple same-day BBTs to the earliest reading`() {
        // Same biphasic shape, but each day has two BBTs (morning + later).
        // Only the morning reading should drive both the daily aggregate and
        // the timestamp on the emitted phase row.
        val readings = (0 until 12).flatMap { i ->
            val morningT = if (i < 6) 36.5 else 36.8
            listOf(
                bbt(i, morningT, offsetMs = 6 * 3_600_000L),    // 06:00
                bbt(i, morningT + 0.3, offsetMs = 14 * 3_600_000L) // 14:00 (warmer)
            )
        }
        val phases = CycleInference.deriveCyclePhases(readings, sourceId)
        assertEquals(12, phases.size)
        // Phase timestamps come from the morning rows.
        for (i in 0 until 12) {
            assertEquals(
                day0 + i * msPerDay + 6 * 3_600_000L,
                phases[i].timestamp
            )
        }
        // Phase classification still matches the biphasic shape.
        assertEquals(CyclePhase.OVULATORY.value.toDouble(), phases[5].value, 0.0)
        assertEquals(CyclePhase.LUTEAL.value.toDouble(), phases[6].value, 0.0)
    }
}
