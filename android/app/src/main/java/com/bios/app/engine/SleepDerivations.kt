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

    /**
     * Sleep efficiency: (time asleep) / (total session duration) × 100, in percent.
     * Standard clinical formula (TST / TIB × 100). The session duration here is
     * approximated by the sum of all stage durations in the input — AWAKE rows at
     * the head/tail of the session count toward the denominator but not the
     * numerator, matching the in-bed-but-awake semantics of TIB.
     *
     * Returns null when the input has no stage rows, total session duration is
     * zero, or no non-AWAKE time is recorded — there is no efficiency to report
     * for a session that contains no sleep.
     */
    fun deriveSleepEfficiency(
        readings: List<MetricReading>,
        sourceId: String
    ): MetricReading? {
        val stages = readings
            .filter { it.metricType == MetricType.SLEEP_STAGE.key }
            .sortedBy { it.timestamp }
        if (stages.isEmpty()) return null

        val total = stages.sumOf { it.durationSec ?: 0 }
        if (total == 0) return null

        val asleep = stages
            .filter { it.value.toInt() != SleepStage.AWAKE.value }
            .sumOf { it.durationSec ?: 0 }
        if (asleep == 0) return null

        return MetricReading(
            metricType = MetricType.SLEEP_EFFICIENCY.key,
            value = asleep.toDouble() / total.toDouble() * 100.0,
            timestamp = stages.first().timestamp,
            durationSec = total,
            sourceId = sourceId,
            confidence = stages.first().confidence
        )
    }

    /**
     * Sleep fragmentation index: count of awakenings after initial sleep onset.
     * An awakening is a transition from a non-AWAKE stage to an AWAKE stage that
     * occurs after the first non-AWAKE row in the session. The initial AWAKE
     * period before sleep onset is not counted (that's latency, not fragmentation).
     *
     * Returns null when the input has no stage rows, or contains no non-AWAKE row
     * — there is no post-onset window to measure fragmentation over.
     */
    fun deriveSleepFragmentation(
        readings: List<MetricReading>,
        sourceId: String
    ): MetricReading? {
        val stages = readings
            .filter { it.metricType == MetricType.SLEEP_STAGE.key }
            .sortedBy { it.timestamp }
        if (stages.isEmpty()) return null

        val firstSleepIdx = stages.indexOfFirst { it.value.toInt() != SleepStage.AWAKE.value }
        if (firstSleepIdx < 0) return null

        var awakenings = 0
        var prevWasAwake = false
        for (row in stages.drop(firstSleepIdx)) {
            val isAwake = row.value.toInt() == SleepStage.AWAKE.value
            if (isAwake && !prevWasAwake) awakenings++
            prevWasAwake = isAwake
        }

        return MetricReading(
            metricType = MetricType.SLEEP_FRAGMENTATION_INDEX.key,
            value = awakenings.toDouble(),
            timestamp = stages.first().timestamp,
            sourceId = sourceId,
            confidence = stages.first().confidence
        )
    }
}
