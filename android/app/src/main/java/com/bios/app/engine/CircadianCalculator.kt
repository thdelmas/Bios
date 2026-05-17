package com.bios.app.engine

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Computes circadian rhythm features from sleep timing data.
 *
 * Based on the Seoul National University npj Digital Medicine 2024 paper:
 * "Accurately predicting mood episodes using wearable sleep and circadian rhythm features".
 * Key insight: phase advance correlates with mania prodrome, phase delay with
 * depression prodrome. Sleep midpoint shift is the strongest single predictor.
 *
 * Hoisted from W2F's domain layer per the producer-by-capture-surface rule
 * (docs/ECOSYSTEM_BOUNDARIES.md): chronobiology math is universal, not mood-
 * specific, so it belongs in Bios. W2F becomes a read-side consumer.
 *
 * All time values use circular statistics to handle midnight wraparound
 * (e.g., 23:30 and 00:30 are 1h apart, not 23h).
 */
object CircadianCalculator {

    private const val PERIOD = 24.0
    private const val TWO_PI = 2.0 * Math.PI
    private const val MIN_SAMPLES_PHASE_SHIFT = 3
    private const val MIN_SAMPLES_REGULARITY = 7

    /**
     * One night of sleep summary, keyed by the wake-day local-calendar date.
     *
     * `sleepOnsetHour` is the local-clock decimal hour the sleep period began
     * (e.g. 23.5 for 23:30); `sleepHours` is duration in decimal hours. Either
     * may be null when an adapter only reports one side (e.g. manual sleep
     * entry with a duration but no onset).
     */
    data class DailySleepSummary(
        val date: String,
        val sleepOnsetHour: Double?,
        val sleepHours: Double?,
    )

    /**
     * `phaseShift`: hours. Positive = phase advance (earlier bedtime, mania
     * signal). Negative = phase delay (later bedtime, depression signal).
     * Null when fewer than 3 reference midpoints are available, or today's
     * onset / duration is missing.
     *
     * `sleepRegularity`: circular std of onset times over the input. Lower =
     * more regular schedule. Null when fewer than 7 onset samples exist.
     */
    data class CircadianFeatures(
        val phaseShift: Double?,
        val sleepRegularity: Double?,
    )

    /**
     * Compute circadian features from recent daily summaries.
     *
     * @param recent oldest→newest list of summaries
     * @param todayOnsetHour today's sleep onset hour (decimal, e.g. 23.5);
     *   pass separately because today's data may not yet be in [recent]
     */
    fun calculate(
        recent: List<DailySleepSummary>,
        todayOnsetHour: Double?,
    ): CircadianFeatures {
        val onsetHours = recent.mapNotNull { it.sleepOnsetHour }
        val sleepDurations = recent.mapNotNull { it.sleepHours }

        val midpoints = recent.mapNotNull { m ->
            val onset = m.sleepOnsetHour ?: return@mapNotNull null
            val duration = m.sleepHours ?: return@mapNotNull null
            wrapHour(onset + duration / 2.0)
        }

        val phaseShift = computePhaseShift(midpoints, todayOnsetHour, sleepDurations.lastOrNull())
        val sleepRegularity = computeSleepRegularity(onsetHours)

        return CircadianFeatures(phaseShift, sleepRegularity)
    }

    /**
     * How far today's sleep midpoint deviates from the recent 7-day circular mean.
     * Positive = advance (earlier), negative = delay (later). Sign is inverted
     * from raw circular diff because earlier bedtime is a *negative* clock shift
     * but a *positive* phase advance in chronobiology convention.
     */
    private fun computePhaseShift(
        midpoints: List<Double>,
        todayOnset: Double?,
        todaySleepHours: Double?,
    ): Double? {
        if (midpoints.size < MIN_SAMPLES_PHASE_SHIFT) return null
        if (todayOnset == null || todaySleepHours == null) return null

        val todayMidpoint = wrapHour(todayOnset + todaySleepHours / 2.0)
        val referenceMean = circularMean(midpoints.takeLast(7))

        return -circularDiff(todayMidpoint, referenceMean)
    }

    private fun computeSleepRegularity(onsetHours: List<Double>): Double? {
        if (onsetHours.size < MIN_SAMPLES_REGULARITY) return null
        return circularStd(onsetHours)
    }

    // ---- Circular statistics (handle midnight wraparound) ----

    /** e.g. circularMean([23.0, 1.0]) = 0.0 (midnight), not 12.0. */
    internal fun circularMean(hours: List<Double>): Double {
        val n = hours.size
        var sinSum = 0.0
        var cosSum = 0.0
        for (h in hours) {
            val rad = h * TWO_PI / PERIOD
            sinSum += sin(rad)
            cosSum += cos(rad)
        }
        val meanRad = atan2(sinSum / n, cosSum / n)
        return ((meanRad * PERIOD / TWO_PI) % PERIOD + PERIOD) % PERIOD
    }

    /** std = sqrt(-2 * ln R) * (period / 2π), where R is the mean resultant length. */
    internal fun circularStd(hours: List<Double>): Double {
        val n = hours.size
        var sinSum = 0.0
        var cosSum = 0.0
        for (h in hours) {
            val rad = h * TWO_PI / PERIOD
            sinSum += sin(rad)
            cosSum += cos(rad)
        }
        val r = sqrt((sinSum / n) * (sinSum / n) + (cosSum / n) * (cosSum / n))
        if (r >= 1.0) return 0.0
        return sqrt(-2.0 * ln(r)) * PERIOD / TWO_PI
    }

    /** Signed circular difference wrapped to [-12, +12]. */
    internal fun circularDiff(phiNew: Double, phiOld: Double): Double {
        var diff = phiNew - phiOld
        diff = ((diff + PERIOD / 2) % PERIOD + PERIOD) % PERIOD - PERIOD / 2
        return diff
    }

    private fun wrapHour(h: Double): Double = ((h % PERIOD) + PERIOD) % PERIOD
}
