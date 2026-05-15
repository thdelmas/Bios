package com.bios.app

import com.bios.app.engine.SleepDerivations
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.app.model.SleepStage
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SleepDerivationsTest {

    private fun stage(stage: SleepStage, timestamp: Long): MetricReading = MetricReading(
        metricType = MetricType.SLEEP_STAGE.key,
        value = stage.value.toDouble(),
        timestamp = timestamp,
        durationSec = 300,
        sourceId = "src",
        confidence = ConfidenceTier.MEDIUM.level
    )

    @Test
    fun `latency is the gap from first AWAKE to first non-AWAKE`() {
        val readings = listOf(
            stage(SleepStage.AWAKE, 1_000_000L),
            stage(SleepStage.AWAKE, 1_300_000L),
            stage(SleepStage.LIGHT, 1_900_000L),
            stage(SleepStage.DEEP, 2_500_000L),
        )
        val latency = SleepDerivations.deriveSleepLatency(readings, "src")!!
        assertEquals(MetricType.SLEEP_LATENCY.key, latency.metricType)
        assertEquals(900.0, latency.value, 0.0)
        assertEquals(1_000_000L, latency.timestamp)
        assertEquals("src", latency.sourceId)
    }

    @Test
    fun `null when no AWAKE rows are present`() {
        val readings = listOf(
            stage(SleepStage.LIGHT, 1_000_000L),
            stage(SleepStage.DEEP, 1_600_000L),
        )
        assertNull(SleepDerivations.deriveSleepLatency(readings, "src"))
    }

    @Test
    fun `null when AWAKE never transitions to sleep`() {
        val readings = listOf(
            stage(SleepStage.AWAKE, 1_000_000L),
            stage(SleepStage.AWAKE, 1_300_000L),
        )
        assertNull(SleepDerivations.deriveSleepLatency(readings, "src"))
    }

    @Test
    fun `null when no stage rows are present`() {
        val readings = listOf<MetricReading>()
        assertNull(SleepDerivations.deriveSleepLatency(readings, "src"))
    }

    @Test
    fun `unordered input is sorted before deriving`() {
        val readings = listOf(
            stage(SleepStage.LIGHT, 1_900_000L),
            stage(SleepStage.AWAKE, 1_000_000L),
            stage(SleepStage.DEEP, 2_500_000L),
        )
        val latency = SleepDerivations.deriveSleepLatency(readings, "src")!!
        assertEquals(900.0, latency.value, 0.0)
    }

    @Test
    fun `non-sleep readings are filtered out before deriving`() {
        val noise = MetricReading(
            metricType = MetricType.HEART_RATE.key,
            value = 60.0,
            timestamp = 1_500_000L,
            sourceId = "src",
            confidence = ConfidenceTier.MEDIUM.level
        )
        val readings = listOf(
            noise,
            stage(SleepStage.AWAKE, 1_000_000L),
            stage(SleepStage.REM, 1_600_000L),
        )
        val latency = SleepDerivations.deriveSleepLatency(readings, "src")!!
        assertEquals(600.0, latency.value, 0.0)
    }

    // MARK: - Efficiency

    @Test
    fun `efficiency is asleep over total session duration`() {
        val readings = listOf(
            stage(SleepStage.AWAKE, 1_000_000L),  // 300s awake
            stage(SleepStage.LIGHT, 1_300_000L),  // 300s
            stage(SleepStage.DEEP, 1_600_000L),   // 300s
            stage(SleepStage.AWAKE, 1_900_000L),  // 300s awake
        )
        val efficiency = SleepDerivations.deriveSleepEfficiency(readings, "src")!!
        assertEquals(MetricType.SLEEP_EFFICIENCY.key, efficiency.metricType)
        assertEquals(50.0, efficiency.value, 0.0001)
        assertEquals(1_000_000L, efficiency.timestamp)
        assertEquals(1200, efficiency.durationSec)
    }

    @Test
    fun `efficiency is null when only AWAKE rows present`() {
        val readings = listOf(
            stage(SleepStage.AWAKE, 1_000_000L),
            stage(SleepStage.AWAKE, 1_300_000L),
        )
        assertNull(SleepDerivations.deriveSleepEfficiency(readings, "src"))
    }

    @Test
    fun `efficiency is 100 percent when no AWAKE rows`() {
        val readings = listOf(
            stage(SleepStage.LIGHT, 1_000_000L),
            stage(SleepStage.DEEP, 1_300_000L),
        )
        val efficiency = SleepDerivations.deriveSleepEfficiency(readings, "src")!!
        assertEquals(100.0, efficiency.value, 0.0001)
    }

    @Test
    fun `efficiency is null when no stage rows`() {
        assertNull(SleepDerivations.deriveSleepEfficiency(emptyList(), "src"))
    }

    // MARK: - Fragmentation

    @Test
    fun `fragmentation counts post-onset awakenings only`() {
        val readings = listOf(
            stage(SleepStage.AWAKE, 1_000_000L),  // pre-onset, not counted
            stage(SleepStage.LIGHT, 1_300_000L),  // onset
            stage(SleepStage.AWAKE, 1_600_000L),  // awakening 1
            stage(SleepStage.LIGHT, 1_900_000L),
            stage(SleepStage.DEEP, 2_200_000L),
            stage(SleepStage.AWAKE, 2_500_000L),  // awakening 2
        )
        val frag = SleepDerivations.deriveSleepFragmentation(readings, "src")!!
        assertEquals(MetricType.SLEEP_FRAGMENTATION_INDEX.key, frag.metricType)
        assertEquals(2.0, frag.value, 0.0)
    }

    @Test
    fun `fragmentation contiguous AWAKE block counts once`() {
        val readings = listOf(
            stage(SleepStage.LIGHT, 1_000_000L),
            stage(SleepStage.AWAKE, 1_300_000L),
            stage(SleepStage.AWAKE, 1_600_000L),
            stage(SleepStage.AWAKE, 1_900_000L),
            stage(SleepStage.LIGHT, 2_200_000L),
        )
        val frag = SleepDerivations.deriveSleepFragmentation(readings, "src")!!
        assertEquals(1.0, frag.value, 0.0)
    }

    @Test
    fun `fragmentation is zero for unbroken sleep`() {
        val readings = listOf(
            stage(SleepStage.LIGHT, 1_000_000L),
            stage(SleepStage.DEEP, 1_300_000L),
            stage(SleepStage.REM, 1_600_000L),
        )
        val frag = SleepDerivations.deriveSleepFragmentation(readings, "src")!!
        assertEquals(0.0, frag.value, 0.0)
    }

    @Test
    fun `fragmentation is null when session never reaches sleep`() {
        val readings = listOf(
            stage(SleepStage.AWAKE, 1_000_000L),
            stage(SleepStage.AWAKE, 1_300_000L),
        )
        assertNull(SleepDerivations.deriveSleepFragmentation(readings, "src"))
    }
}
