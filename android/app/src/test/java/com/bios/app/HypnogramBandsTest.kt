package com.bios.app

import com.bios.app.model.MetricReading
import com.bios.app.model.SleepStage
import com.bios.app.ui.sleep.HypnogramBands
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HypnogramBandsTest {

    private fun stageRow(
        stage: SleepStage,
        timestamp: Long,
        durationSec: Int? = null,
        sourceId: String = "src-a",
    ) = MetricReading(
        metricType = MetricType.SLEEP_STAGE.key,
        value = stage.value.toDouble(),
        timestamp = timestamp,
        durationSec = durationSec,
        sourceId = sourceId,
        confidence = 2,
    )

    @Test
    fun `bands use durationSec when present`() {
        val bands = HypnogramBands.bands(
            listOf(stageRow(SleepStage.LIGHT, 0L, durationSec = 600))
        )
        assertEquals(1, bands.size)
        assertEquals(600_000L, bands[0].endMs)
        assertEquals(SleepStage.LIGHT, bands[0].stage)
    }

    @Test
    fun `null duration falls back to next row start`() {
        val bands = HypnogramBands.bands(
            listOf(
                stageRow(SleepStage.LIGHT, 0L),
                stageRow(SleepStage.DEEP, 900_000L, durationSec = 600),
            )
        )
        assertEquals(900_000L, bands[0].endMs)
    }

    @Test
    fun `last row with null duration gets the fallback span`() {
        val bands = HypnogramBands.bands(listOf(stageRow(SleepStage.AWAKE, 0L)))
        assertEquals(HypnogramBands.FALLBACK_SPAN_MS, bands[0].endMs)
    }

    @Test
    fun `overlapping duration is clamped to next start but gaps are kept`() {
        val bands = HypnogramBands.bands(
            listOf(
                stageRow(SleepStage.LIGHT, 0L, durationSec = 1200), // overlaps next
                stageRow(SleepStage.REM, 600_000L, durationSec = 300), // gap after
                stageRow(SleepStage.LIGHT, 1_800_000L, durationSec = 300),
            )
        )
        assertEquals(600_000L, bands[0].endMs)
        assertEquals(900_000L, bands[1].endMs)
    }

    @Test
    fun `dominant source wins over interleaved minority source`() {
        val bands = HypnogramBands.bands(
            listOf(
                stageRow(SleepStage.LIGHT, 0L, 600, sourceId = "watch"),
                stageRow(SleepStage.DEEP, 600_000L, 600, sourceId = "watch"),
                stageRow(SleepStage.AWAKE, 300_000L, 600, sourceId = "phone"),
            )
        )
        assertEquals(2, bands.size)
        assertTrue(bands.none { it.stage == SleepStage.AWAKE })
    }

    @Test
    fun `unknown stage values are dropped`() {
        val bogus = stageRow(SleepStage.AWAKE, 0L, 600).copy(value = 9.0)
        assertEquals(emptyList<HypnogramBands.StageBand>(), HypnogramBands.bands(listOf(bogus)))
    }

    @Test
    fun `stages present follows lane order and skips absent`() {
        val bands = HypnogramBands.bands(
            listOf(
                stageRow(SleepStage.LIGHT, 0L, 600),
                stageRow(SleepStage.AWAKE, 600_000L, 600),
            )
        )
        assertEquals(
            listOf(SleepStage.AWAKE, SleepStage.LIGHT),
            HypnogramBands.stagesPresent(bands),
        )
    }
}
