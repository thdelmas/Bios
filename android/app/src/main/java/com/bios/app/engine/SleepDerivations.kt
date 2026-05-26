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
     * Time in bed (TIB): total session span as the sum of all SLEEP_STAGE
     * durations. AWAKE rows at session boundaries and mid-session count
     * toward TIB — matching the in-bed-but-awake semantics of the clinical
     * definition. TIB >= SLEEP_DURATION is structurally guaranteed because
     * the asleep span is a subset of the stage sum.
     *
     * Fallback derivation only: adapters that emit TIME_IN_BED natively
     * (Oura, WHOOP, Garmin) still take precedence via Deduplicator. We
     * derive here so nights from stage-only sources (Health Connect,
     * Gadgetbridge) and HR-gap-uninferable nights (device worn through)
     * still get a TIB row instead of leaving it null.
     *
     * Returns null when [readings] has no stage rows or all durations
     * are zero — there is no session to bound.
     */
    fun deriveTimeInBed(
        readings: List<MetricReading>,
        sourceId: String
    ): MetricReading? {
        val stages = readings
            .filter { it.metricType == MetricType.SLEEP_STAGE.key }
            .sortedBy { it.timestamp }
        if (stages.isEmpty()) return null

        val total = stages.sumOf { it.durationSec ?: 0 }
        if (total == 0) return null

        return MetricReading(
            metricType = MetricType.TIME_IN_BED.key,
            value = total.toDouble(),
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

    /**
     * Wake After Sleep Onset (WASO): total seconds spent AWAKE after the first
     * non-AWAKE stage, before the session ends. Standard polysomnography
     * metric — distinguishes "couldn't fall asleep" (latency) from "kept
     * waking up" (WASO). Lower is better.
     *
     * Returns null when the session has no stage rows, no sleep onset, or
     * stages lack a `durationSec` field (some adapters emit point events
     * instead of duration-bound segments — those don't produce a WASO).
     */
    fun deriveWakeAfterSleepOnset(
        readings: List<MetricReading>,
        sourceId: String
    ): MetricReading? {
        val stages = readings
            .filter { it.metricType == MetricType.SLEEP_STAGE.key }
            .sortedBy { it.timestamp }
        if (stages.isEmpty()) return null

        val firstSleepIdx = stages.indexOfFirst { it.value.toInt() != SleepStage.AWAKE.value }
        if (firstSleepIdx < 0) return null

        val postOnsetAwakeSec = stages.drop(firstSleepIdx)
            .filter { it.value.toInt() == SleepStage.AWAKE.value }
            .sumOf { it.durationSec ?: 0 }
        if (postOnsetAwakeSec == 0) {
            // No post-onset awakenings → emit a zero rather than null. WASO=0
            // is a real, meaningful value (uninterrupted sleep); collapsing
            // it to null would lose that signal.
            return MetricReading(
                metricType = MetricType.WAKE_AFTER_SLEEP_ONSET.key,
                value = 0.0,
                timestamp = stages.first().timestamp,
                durationSec = 0,
                sourceId = sourceId,
                confidence = stages.first().confidence
            )
        }

        return MetricReading(
            metricType = MetricType.WAKE_AFTER_SLEEP_ONSET.key,
            value = postOnsetAwakeSec.toDouble(),
            timestamp = stages.first().timestamp,
            durationSec = postOnsetAwakeSec,
            sourceId = sourceId,
            confidence = stages.first().confidence
        )
    }

    /**
     * Composite sleep score on a 0–100 scale, averaging four sub-scores
     * (each weighted equally, each clamped to 0–100):
     *
     *  - **Duration**: 100 in the 7–9h "recommended" band (Hirshkowitz et al.
     *    2015 / NSF 2015), drops linearly to 0 at 4h below / 11h above.
     *  - **Efficiency**: the SLEEP_EFFICIENCY value itself (already 0–100).
     *  - **Latency**: 100 if ≤ 15 min, drops linearly to 0 at 60 min
     *    (clinical cut-offs: ≤ 30 min normal, > 45 min abnormal).
     *  - **Fragmentation**: 100 if 0 post-onset awakenings, drops linearly
     *    to 0 at 8 awakenings (NSF: < 5 awakenings is normal).
     *
     * The score is descriptive, not evaluative — it aggregates published
     * clinical norms into one number for trending. Bios does not push
     * judgments on top of this value (AlertContentPolicy unchanged); the
     * Sleep Disruption ConditionPattern consumes the underlying components
     * directly, not the score.
     *
     * Returns null when any of the four components can't be derived from
     * the input — partial scores would mis-rank nights with missing
     * components against complete ones.
     */
    fun deriveSleepScore(
        readings: List<MetricReading>,
        sourceId: String
    ): MetricReading? {
        val latency = deriveSleepLatency(readings, sourceId) ?: return null
        val efficiency = deriveSleepEfficiency(readings, sourceId) ?: return null
        val fragmentation = deriveSleepFragmentation(readings, sourceId) ?: return null
        val sessionDurationSec = efficiency.durationSec ?: return null
        if (sessionDurationSec <= 0) return null

        val durationScore = durationSubScore(sessionDurationSec)
        val efficiencyScore = efficiency.value.coerceIn(0.0, 100.0)
        val latencyScore = latencySubScore(latency.value)
        val fragmentationScore = fragmentationSubScore(fragmentation.value)

        val composite = (durationScore + efficiencyScore + latencyScore + fragmentationScore) / 4.0

        return MetricReading(
            metricType = MetricType.SLEEP_SCORE.key,
            value = composite,
            timestamp = latency.timestamp,
            durationSec = sessionDurationSec,
            sourceId = sourceId,
            confidence = efficiency.confidence
        )
    }

    private fun durationSubScore(sessionDurationSec: Int): Double {
        val hours = sessionDurationSec / 3600.0
        return when {
            hours in MIN_RECOMMENDED_HOURS..MAX_RECOMMENDED_HOURS -> 100.0
            hours < MIN_RECOMMENDED_HOURS -> {
                // 100 at MIN_RECOMMENDED_HOURS, 0 at MIN_DURATION_HOURS_FLOOR.
                ((hours - MIN_DURATION_HOURS_FLOOR) /
                    (MIN_RECOMMENDED_HOURS - MIN_DURATION_HOURS_FLOOR) * 100.0)
                    .coerceIn(0.0, 100.0)
            }
            else -> {
                // 100 at MAX_RECOMMENDED_HOURS, 0 at MAX_DURATION_HOURS_CEIL.
                ((MAX_DURATION_HOURS_CEIL - hours) /
                    (MAX_DURATION_HOURS_CEIL - MAX_RECOMMENDED_HOURS) * 100.0)
                    .coerceIn(0.0, 100.0)
            }
        }
    }

    private fun latencySubScore(latencySec: Double): Double {
        val minutes = latencySec / 60.0
        return when {
            minutes <= LATENCY_OPTIMAL_MIN -> 100.0
            minutes >= LATENCY_ABNORMAL_MIN -> 0.0
            else -> ((LATENCY_ABNORMAL_MIN - minutes) /
                (LATENCY_ABNORMAL_MIN - LATENCY_OPTIMAL_MIN) * 100.0)
        }
    }

    private fun fragmentationSubScore(awakenings: Double): Double = when {
        awakenings <= 0.0 -> 100.0
        awakenings >= FRAGMENTATION_FLOOR_AWAKENINGS -> 0.0
        else -> ((FRAGMENTATION_FLOOR_AWAKENINGS - awakenings) /
            FRAGMENTATION_FLOOR_AWAKENINGS * 100.0)
    }

    private const val MIN_RECOMMENDED_HOURS = 7.0
    private const val MAX_RECOMMENDED_HOURS = 9.0
    private const val MIN_DURATION_HOURS_FLOOR = 4.0
    private const val MAX_DURATION_HOURS_CEIL = 11.0
    private const val LATENCY_OPTIMAL_MIN = 15.0
    private const val LATENCY_ABNORMAL_MIN = 60.0
    private const val FRAGMENTATION_FLOOR_AWAKENINGS = 8.0
}
