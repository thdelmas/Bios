package com.bios.app.engine

import com.bios.app.model.CyclePhase
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType

/**
 * Pure-function cycle-phase inference over BASAL_BODY_TEMPERATURE readings.
 *
 * Uses the classic biphasic 3-day rule (Marshall 1968; Barron & Fehring 2005):
 * establish a follicular-phase baseline as the mean of the first six daily
 * BBTs, place a coverline 0.1°C above it, and look for the earliest day where
 * three consecutive daily BBTs sit strictly above the coverline. The day
 * before that sustained rise is the inferred ovulation day; days before it
 * are follicular, days from it onward are luteal.
 *
 * The engine sits next to [SleepDerivations] in `engine/` so the derivation
 * applies regardless of which adapter (or manual entry) sourced the BBT
 * rows. Routing of the derived readings to the encrypted reproductive-health
 * DB is the caller's responsibility — this function is unopinionated about
 * persistence.
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

    private const val MS_PER_DAY = 86_400_000L

    /**
     * Emit one CYCLE_PHASE reading per day in the input window when a
     * biphasic shift is detected. Returns an empty list when the input has
     * fewer than nine daily BBTs or no sustained rise above the coverline —
     * we don't fabricate a phase classification without the data to support
     * it.
     *
     * Multiple BBT readings on the same calendar day are aggregated by taking
     * the earliest (morning reading is the clinically canonical sample).
     * Days are bucketed in UTC; timezone-aware bucketing is a separate
     * refinement when needed.
     *
     * MENSTRUAL is not emitted: distinguishing it from early follicular
     * requires a menstruation-onset signal that BBT alone cannot provide.
     */
    fun deriveCyclePhases(
        readings: List<MetricReading>,
        sourceId: String
    ): List<MetricReading> {
        val daily = readings
            .filter { it.metricType == MetricType.BASAL_BODY_TEMPERATURE.key }
            .groupBy { it.timestamp / MS_PER_DAY }
            .map { (_, sameDay) -> sameDay.minBy { it.timestamp } }
            .sortedBy { it.timestamp }
        if (daily.size < MIN_TOTAL_DAYS) return emptyList()

        val baseline = daily.take(MIN_BASELINE_DAYS).map { it.value }.average()
        val coverline = baseline + COVERLINE_OFFSET_C

        val riseStart = (MIN_BASELINE_DAYS..daily.size - MIN_RISE_DAYS)
            .firstOrNull { idx ->
                (idx until idx + MIN_RISE_DAYS).all { daily[it].value > coverline }
            }
            ?: return emptyList()

        return daily.mapIndexed { i, day ->
            val phase = when {
                i < riseStart - 1 -> CyclePhase.FOLLICULAR
                i == riseStart - 1 -> CyclePhase.OVULATORY
                else -> CyclePhase.LUTEAL
            }
            MetricReading(
                metricType = MetricType.CYCLE_PHASE.key,
                value = phase.value.toDouble(),
                timestamp = day.timestamp,
                sourceId = sourceId,
                confidence = day.confidence
            )
        }
    }
}
