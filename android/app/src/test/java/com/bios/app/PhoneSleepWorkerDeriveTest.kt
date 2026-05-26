package com.bios.app

import com.bios.app.ingest.PhoneSleepWorker
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.app.model.SleepStage
import com.bios.contracts.MetricType
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for the bug where phone-inferred SLEEP_STAGE rows
 * landed in the DB without running through SleepDerivations, leaving
 * the sleep efficiency / TIB / latency / WASO / fragmentation / score
 * cards empty on phone-only nights.
 */
class PhoneSleepWorkerDeriveTest {

    @Test
    fun `deriveSleep produces efficiency and TIB from phone stage rows`() {
        val readings = listOf(
            stage(SleepStage.AWAKE, 1_000_000L, 300),
            stage(SleepStage.LIGHT, 1_300_000L, 3600),
            stage(SleepStage.AWAKE, 4_900_000L, 120),
            stage(SleepStage.LIGHT, 5_020_000L, 3600),
        )
        val derived = PhoneSleepWorker.deriveSleep(readings, "phone_src")
        val kinds = derived.map { it.metricType }.toSet()
        assertTrue("expected SLEEP_EFFICIENCY in $kinds", MetricType.SLEEP_EFFICIENCY.key in kinds)
        assertTrue("expected TIME_IN_BED in $kinds", MetricType.TIME_IN_BED.key in kinds)
        assertTrue("expected SLEEP_LATENCY in $kinds", MetricType.SLEEP_LATENCY.key in kinds)
    }

    @Test
    fun `deriveSleep returns empty for empty input`() {
        val derived = PhoneSleepWorker.deriveSleep(emptyList(), "phone_src")
        assertTrue(derived.isEmpty())
    }

    private fun stage(s: SleepStage, ts: Long, durationSec: Int): MetricReading = MetricReading(
        metricType = MetricType.SLEEP_STAGE.key,
        value = s.value.toDouble(),
        timestamp = ts,
        durationSec = durationSec,
        sourceId = "phone_src",
        confidence = ConfidenceTier.MEDIUM.level,
    )
}
