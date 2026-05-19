package com.bios.app

import com.bios.app.engine.GlucoseVariability
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the four CGM-variability derivations (#28). Each derivation
 * is exercised through happy-path, edge-case (sparse window, flat trace,
 * mixed metrics), and a clinical sanity-check against a known-stable and
 * known-spiky pattern.
 */
class GlucoseVariabilityTest {

    private val sourceId = "dexcom"
    private val baseTimestamp = 1_700_000_000_000L

    /** Build a 96-sample 8h window at the 5-min CGM cadence. */
    private fun glucoseSeries(values: List<Double>): List<MetricReading> =
        values.mapIndexed { i, v ->
            MetricReading(
                metricType = MetricType.BLOOD_GLUCOSE.key,
                value = v,
                timestamp = baseTimestamp + i * 5L * 60_000L,
                durationSec = 300,
                sourceId = sourceId,
                confidence = ConfidenceTier.HIGH.level,
            )
        }

    /** Replicate a single value n times. */
    private fun flat(value: Double, n: Int = 96): List<Double> = List(n) { value }

    @Test
    fun `derivations all return null below the minimum-samples threshold`() {
        // Fewer than 96 samples — every derivation should refuse to emit.
        val sparse = glucoseSeries(flat(110.0, n = 60))
        assertNull(GlucoseVariability.deriveCv(sparse, sourceId))
        assertNull(GlucoseVariability.deriveTimeInRange(sparse, sourceId))
        assertNull(GlucoseVariability.derivePeak(sparse, sourceId))
        assertNull(GlucoseVariability.deriveMage(sparse, sourceId))
    }

    @Test
    fun `derivations ignore non-glucose readings in the input`() {
        // Mix in 200 HR samples on top of a 96-sample glucose window —
        // the HR rows must not perturb the glucose derivations.
        val mixed: List<MetricReading> = glucoseSeries(flat(100.0)) +
            List(200) {
                MetricReading(
                    metricType = MetricType.HEART_RATE.key,
                    value = 70.0,
                    timestamp = baseTimestamp + it * 1_000L,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.HIGH.level,
                )
            }
        val cv = GlucoseVariability.deriveCv(mixed, sourceId)
        assertNotNull(cv)
        // Flat 100.0 series → CV = 0.
        assertEquals(0.0, cv!!.value, 1e-9)
    }

    @Test
    fun `CV is zero on a perfectly flat trace`() {
        val flat = glucoseSeries(flat(120.0))
        val cv = GlucoseVariability.deriveCv(flat, sourceId)!!
        assertEquals(MetricType.GLUCOSE_CV.key, cv.metricType)
        assertEquals(0.0, cv.value, 1e-9)
    }

    @Test
    fun `CV reflects SD over mean as percent`() {
        // Two-value oscillation: 80, 120, 80, 120... mean=100, SD=20, CV=20%.
        val osc = glucoseSeries(List(96) { if (it % 2 == 0) 80.0 else 120.0 })
        val cv = GlucoseVariability.deriveCv(osc, sourceId)!!
        assertEquals(20.0, cv.value, 1e-9)
    }

    @Test
    fun `TIR is 100 percent when every reading is in the 70 to 140 window`() {
        val inRange = glucoseSeries(flat(100.0))
        val tir = GlucoseVariability.deriveTimeInRange(inRange, sourceId)!!
        assertEquals(MetricType.GLUCOSE_TIME_IN_RANGE.key, tir.metricType)
        assertEquals(100.0, tir.value, 1e-9)
    }

    @Test
    fun `TIR is zero when every reading is out of range`() {
        val highSpike = glucoseSeries(flat(200.0))
        val tir = GlucoseVariability.deriveTimeInRange(highSpike, sourceId)!!
        assertEquals(0.0, tir.value, 1e-9)
    }

    @Test
    fun `TIR window endpoints 70 and 140 are inclusive`() {
        val onEdges = glucoseSeries(List(96) { if (it % 2 == 0) 70.0 else 140.0 })
        val tir = GlucoseVariability.deriveTimeInRange(onEdges, sourceId)!!
        assertEquals(100.0, tir.value, 1e-9)
    }

    @Test
    fun `TIR is approximately the fraction in 70 to 140`() {
        // 72 readings at 100 mg/dL, 24 readings at 200 mg/dL → 75% in range.
        val mixed = glucoseSeries(List(72) { 100.0 } + List(24) { 200.0 })
        val tir = GlucoseVariability.deriveTimeInRange(mixed, sourceId)!!
        assertEquals(75.0, tir.value, 1e-9)
    }

    @Test
    fun `peak reports the maximum value in the window`() {
        val withSpike = glucoseSeries(flat(100.0).toMutableList().also { it[50] = 220.0 })
        val peak = GlucoseVariability.derivePeak(withSpike, sourceId)!!
        assertEquals(MetricType.GLUCOSE_PEAK_24H.key, peak.metricType)
        assertEquals(220.0, peak.value, 1e-9)
    }

    @Test
    fun `MAGE is null on a flat trace where no excursion exceeds the SD`() {
        // SD is zero, so the > SD filter rejects every excursion — null is
        // the right answer (a confident zero would falsely rank flat days
        // against truly variable ones).
        val flat = glucoseSeries(flat(120.0))
        assertNull(GlucoseVariability.deriveMage(flat, sourceId))
    }

    @Test
    fun `MAGE averages excursions exceeding 1 SD`() {
        // Construct a saw-tooth: alternating 80 ↔ 160. SD ≈ 40, every
        // peak↔nadir transition is |Δ|=80 > SD, so MAGE = 80.
        val sawtooth = glucoseSeries(List(96) { if (it % 2 == 0) 80.0 else 160.0 })
        val mage = GlucoseVariability.deriveMage(sawtooth, sourceId)!!
        assertEquals(MetricType.GLUCOSE_MAGE.key, mage.metricType)
        assertEquals(80.0, mage.value, 1e-9)
    }

    @Test
    fun `derived readings inherit confidence from the source rows`() {
        val series = glucoseSeries(flat(100.0))
        val cv = GlucoseVariability.deriveCv(series, sourceId)!!
        assertEquals(ConfidenceTier.HIGH.level, cv.confidence)
        assertEquals(sourceId, cv.sourceId)
    }

    @Test
    fun `derived reading timestamp anchors at the first sample`() {
        val series = glucoseSeries(flat(100.0))
        val peak = GlucoseVariability.derivePeak(series, sourceId)!!
        assertEquals(series.first().timestamp, peak.timestamp)
        // durationSec spans the window — first to last sample, in seconds.
        val expectedDuration = (series.last().timestamp - series.first().timestamp) / 1000L
        assertEquals(expectedDuration.toInt(), peak.durationSec)
    }

    @Test
    fun `unsorted input still produces correct derivations`() {
        // Same data shuffled — derivations sort internally, so the output
        // matches the sorted case.
        val sorted = glucoseSeries(List(96) { 80.0 + it })
        val shuffled = sorted.shuffled()
        val sortedCv = GlucoseVariability.deriveCv(sorted, sourceId)!!.value
        val shuffledCv = GlucoseVariability.deriveCv(shuffled, sourceId)!!.value
        assertTrue(
            "shuffled CV ($shuffledCv) should equal sorted CV ($sortedCv)",
            kotlin.math.abs(sortedCv - shuffledCv) < 1e-9
        )
    }
}
