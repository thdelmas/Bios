package com.bios.app.engine

import com.bios.app.model.ConfidenceTier
import com.bios.app.model.CyclePhase
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType

/**
 * Pure-function cycle-phase inference over BASAL_BODY_TEMPERATURE readings,
 * with optional MENSTRUAL overlay from owner-logged menstruation onsets.
 *
 * Two complementary derivations live here:
 *
 * 1. [deriveCyclePhases] — biphasic 3-day rule (Marshall 1968; Barron &
 *    Fehring 2005): establish a follicular-phase baseline as the mean of
 *    the first six daily BBTs, place a coverline 0.1°C above it, and look
 *    for the earliest day where three consecutive daily BBTs sit strictly
 *    above the coverline. The day before that sustained rise is the
 *    inferred ovulation day; days before it are follicular, days from it
 *    onward are luteal. When an owner logs menstruation onsets,
 *    [CyclePhase.MENSTRUAL] is overlaid on the five days starting from
 *    each onset (FIGO/Munro 2018 median menstrual duration) — owner-
 *    asserted ground truth wins over the BBT-derived classification on
 *    those days.
 *
 * 2. [deriveCycleDays] — anchors the clinical day-of-cycle numbering on
 *    logged onsets (cycle day 1 = first day of menstruation). Days before
 *    any logged onset receive no emission; we don't fabricate a numbering
 *    without an anchor.
 *
 * Both functions sit next to [SleepDerivations] in `engine/` so the
 * derivations apply regardless of which adapter (or manual entry) sourced
 * the rows. Routing the derived readings to the encrypted reproductive-
 * health DB is the caller's responsibility — these functions are
 * unopinionated about persistence.
 */
object CycleInference {

    /** Days of follicular baseline before the rise. */
    private const val MIN_BASELINE_DAYS = 6

    /** Consecutive days above the coverline required to confirm a rise. */
    private const val MIN_RISE_DAYS = 3

    /** Minimum total daily BBTs needed to attempt inference. */
    private const val MIN_TOTAL_DAYS = MIN_BASELINE_DAYS + MIN_RISE_DAYS

    /** Coverline offset above follicular baseline, in °C. */
    private const val COVERLINE_OFFSET_C = 0.1

    /**
     * Default duration of the MENSTRUAL phase in days, anchored from a
     * logged menstruation onset. Five days is the FIGO/Munro 2018 median
     * for normal menstrual bleeding (range 2–7); we use the median rather
     * than the upper bound because over-attributing MENSTRUAL to early-
     * follicular days would corrupt the BBT-derived classification more
     * often than the reverse. Owners who bleed longer (or shorter) can log
     * their next onset on its actual day; the cycle_day series resets at
     * that point.
     */
    private const val MENSTRUAL_WINDOW_DAYS = 5L

    private const val MS_PER_DAY = 86_400_000L

    /**
     * Emit one CYCLE_PHASE reading per day where a classification can be
     * made. Returns an empty list when there are no BBTs and no onsets;
     * otherwise returns at least one reading per onset window day plus
     * any BBT-derived classifications.
     *
     * Multiple BBT readings on the same calendar day are aggregated by
     * taking the earliest (morning reading is the clinically canonical
     * sample). Days are bucketed in UTC; timezone-aware bucketing is a
     * separate refinement when needed.
     *
     * MENSTRUAL emissions take precedence on overlapping days — an owner
     * who logs their period is asserting ground truth that BBT
     * thermoregulation alone cannot match.
     */
    fun deriveCyclePhases(
        readings: List<MetricReading>,
        sourceId: String,
        onsets: List<MetricReading> = emptyList()
    ): List<MetricReading> {
        val bbtPhasesByBucket = bbtDerivedPhasesByBucket(readings, sourceId)
        val menstrualBuckets = menstrualDayBuckets(onsets)

        val merged = bbtPhasesByBucket.toMutableMap()
        for (bucket in menstrualBuckets) {
            val existing = merged[bucket]
            merged[bucket] = MetricReading(
                metricType = MetricType.CYCLE_PHASE.key,
                value = CyclePhase.MENSTRUAL.value.toDouble(),
                timestamp = existing?.timestamp ?: (bucket * MS_PER_DAY),
                sourceId = sourceId,
                confidence = existing?.confidence ?: ConfidenceTier.HIGH.level
            )
        }
        return merged.values.sortedBy { it.timestamp }
    }

    /**
     * Emit one CYCLE_DAY reading per UTC day from the earliest onset
     * bucket through [upToTimestamp]'s bucket (inclusive). Cycle day 1 is
     * the day of the most recent onset on or before the bucket. Days
     * before any logged onset receive no emission — we don't fabricate a
     * numbering without an anchor.
     */
    fun deriveCycleDays(
        onsets: List<MetricReading>,
        upToTimestamp: Long,
        sourceId: String
    ): List<MetricReading> {
        val onsetBuckets = onsets
            .filter { it.metricType == MetricType.MENSTRUATION_ONSET.key }
            .map { it.timestamp / MS_PER_DAY }
            .toSortedSet()
            .toList()
        if (onsetBuckets.isEmpty()) return emptyList()

        val earliest = onsetBuckets.first()
        val latest = upToTimestamp / MS_PER_DAY
        if (latest < earliest) return emptyList()

        return (earliest..latest).map { bucket ->
            val anchor = onsetBuckets.last { it <= bucket }
            val dayNumber = (bucket - anchor) + 1
            MetricReading(
                metricType = MetricType.CYCLE_DAY.key,
                value = dayNumber.toDouble(),
                timestamp = bucket * MS_PER_DAY,
                sourceId = sourceId,
                confidence = ConfidenceTier.HIGH.level
            )
        }
    }

    private fun bbtDerivedPhasesByBucket(
        readings: List<MetricReading>,
        sourceId: String
    ): Map<Long, MetricReading> {
        val daily = readings
            .filter { it.metricType == MetricType.BASAL_BODY_TEMPERATURE.key }
            .groupBy { it.timestamp / MS_PER_DAY }
            .map { (_, sameDay) -> sameDay.minBy { it.timestamp } }
            .sortedBy { it.timestamp }
        if (daily.size < MIN_TOTAL_DAYS) return emptyMap()

        val baseline = daily.take(MIN_BASELINE_DAYS).map { it.value }.average()
        val coverline = baseline + COVERLINE_OFFSET_C

        val riseStart = (MIN_BASELINE_DAYS..daily.size - MIN_RISE_DAYS)
            .firstOrNull { idx ->
                (idx until idx + MIN_RISE_DAYS).all { daily[it].value > coverline }
            }
            ?: return emptyMap()

        return daily.mapIndexed { i, day ->
            val phase = when {
                i < riseStart - 1 -> CyclePhase.FOLLICULAR
                i == riseStart - 1 -> CyclePhase.OVULATORY
                else -> CyclePhase.LUTEAL
            }
            (day.timestamp / MS_PER_DAY) to MetricReading(
                metricType = MetricType.CYCLE_PHASE.key,
                value = phase.value.toDouble(),
                timestamp = day.timestamp,
                sourceId = sourceId,
                confidence = day.confidence
            )
        }.toMap()
    }

    private fun menstrualDayBuckets(onsets: List<MetricReading>): Set<Long> =
        onsets
            .filter { it.metricType == MetricType.MENSTRUATION_ONSET.key }
            .flatMap { onset ->
                val onsetBucket = onset.timestamp / MS_PER_DAY
                (0L until MENSTRUAL_WINDOW_DAYS).map { onsetBucket + it }
            }
            .toSet()
}
