package com.bios.app

import com.bios.app.engine.SleepDerivations
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.app.model.SleepStage
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // MARK: - Time in bed

    @Test
    fun `time in bed sums all stage durations including AWAKE`() {
        val readings = listOf(
            stage(SleepStage.AWAKE, 1_000_000L),  // 300s awake
            stage(SleepStage.LIGHT, 1_300_000L),  // 300s
            stage(SleepStage.DEEP, 1_600_000L),   // 300s
            stage(SleepStage.AWAKE, 1_900_000L),  // 300s awake
        )
        val tib = SleepDerivations.deriveTimeInBed(readings, "src")!!
        assertEquals(MetricType.TIME_IN_BED.key, tib.metricType)
        assertEquals(1200.0, tib.value, 0.0)
        assertEquals(1200, tib.durationSec)
        assertEquals(1_000_000L, tib.timestamp)
    }

    @Test
    fun `time in bed is at least sleep duration (TIB greater-equal TST invariant)`() {
        val readings = listOf(
            stage(SleepStage.AWAKE, 1_000_000L),
            stage(SleepStage.LIGHT, 1_300_000L),
            stage(SleepStage.DEEP, 1_600_000L),
            stage(SleepStage.REM, 1_900_000L),
            stage(SleepStage.AWAKE, 2_200_000L),
        )
        val tib = SleepDerivations.deriveTimeInBed(readings, "src")!!
        val efficiency = SleepDerivations.deriveSleepEfficiency(readings, "src")!!
        val tst = efficiency.value / 100.0 * efficiency.durationSec!!
        assertTrue("TIB (${tib.value}) must be >= TST ($tst)", tib.value >= tst)
    }

    @Test
    fun `time in bed is null when no stage rows`() {
        assertNull(SleepDerivations.deriveTimeInBed(emptyList(), "src"))
    }

    @Test
    fun `time in bed is null when all durations are zero`() {
        val readings = listOf(
            MetricReading(
                metricType = MetricType.SLEEP_STAGE.key,
                value = SleepStage.LIGHT.value.toDouble(),
                timestamp = 1_000_000L,
                durationSec = 0,
                sourceId = "src",
                confidence = ConfidenceTier.MEDIUM.level,
            )
        )
        assertNull(SleepDerivations.deriveTimeInBed(readings, "src"))
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

    // --- WASO ---

    @Test
    fun `WASO sums post-onset AWAKE durations only`() {
        // Initial AWAKE blocks before sleep onset count toward latency, not
        // WASO. Only awake durations *after* the first non-AWAKE stage do.
        val readings = listOf(
            stage(SleepStage.AWAKE, 1_000_000L),      // pre-onset, ignored
            stage(SleepStage.AWAKE, 1_300_000L),      // pre-onset, ignored
            stage(SleepStage.LIGHT, 1_900_000L),      // onset
            stage(SleepStage.DEEP, 2_500_000L),
            stage(SleepStage.AWAKE, 3_100_000L),      // counts: 300s
            stage(SleepStage.LIGHT, 3_400_000L),
            stage(SleepStage.AWAKE, 4_000_000L),      // counts: 300s
            stage(SleepStage.REM, 4_300_000L),
        )
        val waso = SleepDerivations.deriveWakeAfterSleepOnset(readings, "src")!!
        assertEquals(MetricType.WAKE_AFTER_SLEEP_ONSET.key, waso.metricType)
        assertEquals(600.0, waso.value, 0.0)
        assertEquals(600, waso.durationSec)
    }

    @Test
    fun `WASO is zero (not null) when sleep is uninterrupted`() {
        // WASO = 0 is a real, meaningful value — collapsing to null would
        // lose the distinction between "uninterrupted night" and
        // "couldn't measure."
        val readings = listOf(
            stage(SleepStage.LIGHT, 1_000_000L),
            stage(SleepStage.DEEP, 1_300_000L),
            stage(SleepStage.REM, 1_600_000L),
        )
        val waso = SleepDerivations.deriveWakeAfterSleepOnset(readings, "src")!!
        assertEquals(0.0, waso.value, 0.0)
    }

    @Test
    fun `WASO is null when session never reaches sleep`() {
        val readings = listOf(
            stage(SleepStage.AWAKE, 1_000_000L),
            stage(SleepStage.AWAKE, 1_300_000L),
        )
        assertNull(SleepDerivations.deriveWakeAfterSleepOnset(readings, "src"))
    }

    @Test
    fun `WASO is null when readings list is empty`() {
        assertNull(SleepDerivations.deriveWakeAfterSleepOnset(emptyList(), "src"))
    }

    // --- Sleep score ---

    @Test
    fun `sleep score is high for a textbook 8h session with one brief awakening`() {
        // 8h total, ~5 min latency, 1 brief awakening — should score near
        // the top of the scale. Test pins the rough ceiling rather than
        // the exact value to allow sub-score weight tuning later.
        val readings = textbookNight()
        val score = SleepDerivations.deriveSleepScore(readings, "src")!!
        assertEquals(MetricType.SLEEP_SCORE.key, score.metricType)
        assertTrue("Score for textbook night should be ≥ 85, got ${score.value}", score.value >= 85.0)
        assertTrue("Score must stay ≤ 100", score.value <= 100.0)
    }

    @Test
    fun `sleep score penalises a 4h session vs an 8h session of equal quality`() {
        // Same efficiency / latency / fragmentation profile, different
        // duration. The duration sub-score must move the composite.
        val shortNight = sessionOf(
            durations = listOf(SleepStage.LIGHT to 3600, SleepStage.DEEP to 3600,
                SleepStage.REM to 3600, SleepStage.LIGHT to 3600),
            prefix = listOf(SleepStage.AWAKE to 300),
        )
        val fullNight = sessionOf(
            durations = listOf(SleepStage.LIGHT to 3600, SleepStage.DEEP to 3600,
                SleepStage.REM to 3600, SleepStage.LIGHT to 3600,
                SleepStage.DEEP to 3600, SleepStage.REM to 3600,
                SleepStage.LIGHT to 3600, SleepStage.LIGHT to 3600),
            prefix = listOf(SleepStage.AWAKE to 300),
        )
        val short = SleepDerivations.deriveSleepScore(shortNight, "src")!!
        val full = SleepDerivations.deriveSleepScore(fullNight, "src")!!
        assertTrue(
            "4h session must score lower than 8h: short=${short.value} full=${full.value}",
            short.value < full.value,
        )
    }

    @Test
    fun `sleep score is null when fragmentation cannot be derived (never reached sleep)`() {
        // The composite-or-null rule: partial scores would mis-rank
        // nights with missing components against complete ones.
        val readings = listOf(
            stage(SleepStage.AWAKE, 1_000_000L),
            stage(SleepStage.AWAKE, 1_300_000L),
        )
        assertNull(SleepDerivations.deriveSleepScore(readings, "src"))
    }

    // --- Sleep-score fixture helpers ---

    private fun sessionOf(
        durations: List<Pair<SleepStage, Int>>,
        prefix: List<Pair<SleepStage, Int>> = emptyList(),
        startMillis: Long = 1_000_000L,
    ): List<MetricReading> {
        val rows = mutableListOf<MetricReading>()
        var t = startMillis
        for ((s, dur) in prefix + durations) {
            rows.add(
                MetricReading(
                    metricType = MetricType.SLEEP_STAGE.key,
                    value = s.value.toDouble(),
                    timestamp = t,
                    durationSec = dur,
                    sourceId = "src",
                    confidence = ConfidenceTier.MEDIUM.level,
                )
            )
            t += dur * 1000L
        }
        return rows
    }

    private fun textbookNight(): List<MetricReading> = sessionOf(
        prefix = listOf(SleepStage.AWAKE to 300), // 5 min latency
        durations = listOf(
            SleepStage.LIGHT to 3600,
            SleepStage.DEEP to 3600,
            SleepStage.REM to 3600,
            SleepStage.LIGHT to 3600,
            SleepStage.AWAKE to 120,             // 2 min mid-night awakening
            SleepStage.DEEP to 3600,
            SleepStage.REM to 3600,
            SleepStage.LIGHT to 3360,
        ),
    )
}
