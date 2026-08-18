package com.bios.app.ui.sleep

import com.bios.app.model.MetricReading
import com.bios.app.model.SleepStage
import com.bios.contracts.MetricType

/**
 * Pure band computation for the per-night hypnogram — mirrors the
 * ClusterPeriodicityHistogram split: testable non-Compose logic here,
 * dumb Canvas in [HypnogramCard].
 *
 * Input is the raw SLEEP_STAGE rows of one night's window. A night can
 * mix sources (isPrimary dedup runs upstream but sources interleave), so
 * bands render the dominant source only — mixing two devices' stage
 * timelines produces nonsense overlaps. Span comes from `durationSec`
 * when the adapter wrote it; Gadgetbridge leaves it null, so the next
 * row's start is the fallback, then a fixed span for the last row.
 */
object HypnogramBands {

    data class StageBand(val stage: SleepStage, val startMs: Long, val endMs: Long)

    /** Last-row fallback span when the adapter wrote no duration (Oura epoch length). */
    internal const val FALLBACK_SPAN_MS = 5L * 60_000L

    /** Clinical hypnogram lane order, top to bottom. */
    val LANE_ORDER = listOf(SleepStage.AWAKE, SleepStage.REM, SleepStage.LIGHT, SleepStage.DEEP)

    fun bands(readings: List<MetricReading>): List<StageBand> {
        val stageRows = readings.filter { it.metricType == MetricType.SLEEP_STAGE.key }
        if (stageRows.isEmpty()) return emptyList()
        val dominant = stageRows.groupBy { it.sourceId }.maxByOrNull { it.value.size }!!.value
        val sorted = dominant.sortedBy { it.timestamp }
        return sorted.mapIndexedNotNull { i, row ->
            val stage = SleepStage.entries.getOrNull(row.value.toInt())
                ?: return@mapIndexedNotNull null
            val nextStart = sorted.getOrNull(i + 1)?.timestamp
            val rawEnd = row.durationSec?.let { row.timestamp + it * 1000L }
                ?: nextStart
                ?: (row.timestamp + FALLBACK_SPAN_MS)
            // Clamp into the next band's start — overlaps render as nonsense,
            // while genuine gaps (unrecorded stretches) are kept as gaps.
            val end = nextStart?.let { minOf(rawEnd, it) } ?: rawEnd
            if (end <= row.timestamp) null else StageBand(stage, row.timestamp, end)
        }
    }

    /**
     * Lanes actually present, in [LANE_ORDER]. Phone-inferred nights carry
     * only AWAKE/LIGHT by design — a fixed 4-lane legend would look broken
     * on them, so the chart keys off this.
     */
    fun stagesPresent(bands: List<StageBand>): List<SleepStage> =
        LANE_ORDER.filter { lane -> bands.any { it.stage == lane } }
}
