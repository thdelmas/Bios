package com.bios.app

import com.bios.app.ingest.SleepDerivationsBackfill
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.app.model.SleepStage
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-function coverage for the session-splitter that drives the
 * backfill. The full [SleepDerivationsBackfill.run] path needs a Room
 * DAO and is exercised by manual sync on-device; here we only guard
 * the split heuristic against drift.
 */
class SleepDerivationsBackfillTest {

    @Test
    fun `consecutive stages within an hour stay one session`() {
        val stages = listOf(
            stage(SleepStage.LIGHT, ts = 0L, durationSec = 1800),
            stage(SleepStage.DEEP, ts = hours(0.5), durationSec = 1800),
            stage(SleepStage.REM, ts = hours(1.0), durationSec = 1800),
        )
        val sessions = SleepDerivationsBackfill.splitSessions(stages)
        assertEquals(1, sessions.size)
        assertEquals(3, sessions[0].size)
    }

    @Test
    fun `gap larger than an hour splits sessions`() {
        val stages = listOf(
            stage(SleepStage.LIGHT, ts = 0L, durationSec = 1800),
            stage(SleepStage.DEEP, ts = hours(0.5), durationSec = 1800),
            // 20h gap — clearly a different night
            stage(SleepStage.LIGHT, ts = hours(21.0), durationSec = 1800),
            stage(SleepStage.REM, ts = hours(21.5), durationSec = 1800),
        )
        val sessions = SleepDerivationsBackfill.splitSessions(stages)
        assertEquals(2, sessions.size)
        assertEquals(2, sessions[0].size)
        assertEquals(2, sessions[1].size)
    }

    @Test
    fun `gap exactly at threshold stays in same session`() {
        val stages = listOf(
            stage(SleepStage.LIGHT, ts = 0L, durationSec = 0),
            // Gap == 60min, threshold is "> 60min", so still same session
            stage(SleepStage.DEEP, ts = hours(1.0), durationSec = 0),
        )
        val sessions = SleepDerivationsBackfill.splitSessions(stages)
        assertEquals(1, sessions.size)
    }

    @Test
    fun `empty input yields no sessions`() {
        assertEquals(0, SleepDerivationsBackfill.splitSessions(emptyList()).size)
    }

    private fun stage(s: SleepStage, ts: Long, durationSec: Int): MetricReading = MetricReading(
        metricType = MetricType.SLEEP_STAGE.key,
        value = s.value.toDouble(),
        timestamp = ts,
        durationSec = durationSec,
        sourceId = "src",
        confidence = ConfidenceTier.MEDIUM.level,
    )

    private fun hours(h: Double): Long = (h * 3600.0 * 1000.0).toLong()
}
