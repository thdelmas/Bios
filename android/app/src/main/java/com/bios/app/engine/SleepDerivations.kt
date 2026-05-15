package com.bios.app.engine

import com.bios.app.model.MetricReading
import com.bios.app.model.SleepStage
import com.bios.contracts.MetricType

/**
 * Pure-function derivations over SLEEP_STAGE readings.
 *
 * Derived metrics live next to baseline/anomaly logic in `engine/` rather than
 * in any adapter, so the same derivation applies regardless of which adapter
 * sourced the underlying stage rows (Oura, WHOOP, Health Connect, Withings).
 */
object SleepDerivations {

    /**
     * Sleep latency: time from the first AWAKE stage of a sleep session to the
     * first non-AWAKE stage that follows it. Reported in seconds.
     *
     * Returns null when [readings] contains no AWAKE→sleep transition — either
     * the recording started already asleep, or the session is incomplete. We
     * intentionally do not fabricate a zero in those cases; absent data is more
     * honest than a confidently wrong value.
     *
     * Only the first AWAKE→sleep transition in the input is considered, so a
     * single sync window with multiple sessions yields one latency reading for
     * the earliest session.
     */
    fun deriveSleepLatency(
        readings: List<MetricReading>,
        sourceId: String
    ): MetricReading? {
        val stages = readings
            .filter { it.metricType == MetricType.SLEEP_STAGE.key }
            .sortedBy { it.timestamp }
        if (stages.isEmpty()) return null

        val firstAwakeIdx = stages.indexOfFirst { it.value.toInt() == SleepStage.AWAKE.value }
        if (firstAwakeIdx < 0) return null

        val firstSleepIdx = stages
            .drop(firstAwakeIdx + 1)
            .indexOfFirst { it.value.toInt() != SleepStage.AWAKE.value }
        if (firstSleepIdx < 0) return null

        val awakeRow = stages[firstAwakeIdx]
        val sleepRow = stages[firstAwakeIdx + 1 + firstSleepIdx]
        val latencyMs = sleepRow.timestamp - awakeRow.timestamp
        if (latencyMs <= 0) return null

        return MetricReading(
            metricType = MetricType.SLEEP_LATENCY.key,
            value = latencyMs / 1000.0,
            timestamp = awakeRow.timestamp,
            durationSec = (latencyMs / 1000).toInt(),
            sourceId = sourceId,
            confidence = sleepRow.confidence
        )
    }
}
