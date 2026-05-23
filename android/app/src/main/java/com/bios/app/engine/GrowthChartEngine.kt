package com.bios.app.engine

import com.bios.app.model.GrowthChartReference
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Computes z-score and percentile for a paediatric anthropometric
 * measurement against the WHO / CDC growth-chart LMS tables (#199, audit
 * gap §2.7).
 *
 * **Method.** The LMS (lambda/mu/sigma) method (Cole & Green 1992) is the
 * established way to summarise a growth-curve distribution. For a
 * measurement `x` at age `t`, the z-score is:
 *   z = ((x/mu)^lambda - 1) / (lambda * sigma)   when lambda != 0
 *   z = ln(x/mu) / sigma                          when lambda == 0
 * The percentile is the standard normal CDF of `z` × 100.
 *
 * For ages between the LMS rows the engine linearly interpolates each of
 * the three parameters independently — the WHO/CDC published tables are
 * smoothed enough that linear interpolation over a one-month gap closes
 * to well within the published z-score precision.
 *
 * **Coverage today.** WHO 0–24 month weight-for-age + length-for-age, both
 * sexes. Returns null for any other combination — the data layer is shaped
 * to accept further references (CDC 2–20y, WHO 5–19y, Fenton premature,
 * head-circumference, BMI-for-age) without changes to this file.
 *
 * **Output framing.** Percentile only. No diagnostic statement, no
 * "stunted" / "wasted" / "failure to thrive" verdict — the
 * [GrowthAndCompositionPatterns.failureToThriveScreen] alert pattern is
 * the surface that turns percentile trajectories into a screening signal;
 * its text obeys [com.bios.app.alerts.AlertContentPolicy] (data statement
 * + professional referral; no diagnosis).
 *
 * Lives in its own file ([GrowthChartLmsTables] holds the table data) so
 * the engine stays under the 500-line cap and remains testable without a
 * DB.
 */
object GrowthChartEngine {

    /** A computed percentile/z-score outcome for a single measurement. */
    data class GrowthPoint(
        /** Standard normal z-score against the LMS row at this age. */
        val zScore: Double,
        /** Percentile 0–100, derived from the standard normal CDF of zScore. */
        val percentile: Double,
    )

    /**
     * Compute z-score and percentile for a measurement against the
     * requested reference + sex + indicator. Returns null when:
     *  - the (reference, sex, indicator) tuple is not in the shipped LMS subset
     *  - `ageInDays` falls outside the LMS row range
     *  - `measurement` is non-positive (BMI / weight / length must be > 0)
     */
    fun compute(
        reference: GrowthChartReference,
        sex: GrowthSex,
        indicator: GrowthIndicator,
        ageInDays: Int,
        measurement: Double,
    ): GrowthPoint? {
        if (measurement <= 0.0) return null
        val series = GrowthChartLmsTables.seriesFor(reference, sex, indicator) ?: return null
        if (series.isEmpty()) return null
        if (ageInDays < series.first().ageInDays) return null
        if (ageInDays > series.last().ageInDays) return null

        val (lambda, mu, sigma) = interpolateLms(series, ageInDays)
        val z = zScoreFromLms(measurement, lambda, mu, sigma)
        return GrowthPoint(zScore = z, percentile = percentileFromZ(z))
    }

    /**
     * Linear interpolation between the two LMS rows that bracket
     * [ageInDays]. Each of L, M, S is interpolated independently — that's
     * the WHO-recommended approach for ages that fall between published
     * rows.
     */
    internal fun interpolateLms(series: List<LmsRow>, ageInDays: Int): Triple<Double, Double, Double> {
        // Exact match first — cheaper than the binary search edge case.
        val exact = series.firstOrNull { it.ageInDays == ageInDays }
        if (exact != null) return Triple(exact.lambda, exact.mu, exact.sigma)

        // series is sorted ascending by ageInDays; find the bracketing pair.
        var loIdx = 0
        for (i in series.indices) {
            if (series[i].ageInDays <= ageInDays) loIdx = i
            else break
        }
        val lo = series[loIdx]
        val hi = series.getOrNull(loIdx + 1) ?: lo
        if (hi.ageInDays == lo.ageInDays) {
            return Triple(lo.lambda, lo.mu, lo.sigma)
        }
        val fraction = (ageInDays - lo.ageInDays).toDouble() / (hi.ageInDays - lo.ageInDays).toDouble()
        val lambda = lo.lambda + fraction * (hi.lambda - lo.lambda)
        val mu = lo.mu + fraction * (hi.mu - lo.mu)
        val sigma = lo.sigma + fraction * (hi.sigma - lo.sigma)
        return Triple(lambda, mu, sigma)
    }

    /**
     * Apply the LMS transform. The standard Cole-Green formula has the
     * lambda-zero special case (logarithmic transform) when lambda is
     * exactly zero; in practice WHO/CDC tables rarely ship lambda = 0
     * exactly, but the special case is included for correctness and tested.
     */
    internal fun zScoreFromLms(measurement: Double, lambda: Double, mu: Double, sigma: Double): Double {
        return if (lambda == 0.0) {
            ln(measurement / mu) / sigma
        } else {
            ((measurement / mu).pow(lambda) - 1.0) / (lambda * sigma)
        }
    }

    /**
     * Standard normal CDF approximation (Abramowitz & Stegun 26.2.17),
     * accurate to ~7.5e-8 — well within the precision of the LMS tables.
     * Returns the percentile (0–100), clamped to [0, 100] for numerical
     * safety at the tails.
     */
    internal fun percentileFromZ(z: Double): Double {
        val cdf = standardNormalCdf(z)
        return (cdf * 100.0).coerceIn(0.0, 100.0)
    }

    /**
     * Standard normal CDF via the error function approximation
     * (Abramowitz & Stegun 7.1.26 → erfcc-style).
     */
    private fun standardNormalCdf(z: Double): Double {
        return 0.5 * (1.0 + erf(z / sqrt(2.0)))
    }

    /**
     * Error function approximation (Abramowitz & Stegun 7.1.26).
     * Max error ~1.5e-7.
     */
    private fun erf(x: Double): Double {
        val sign = if (x < 0.0) -1.0 else 1.0
        val ax = kotlin.math.abs(x)
        val a1 = 0.254829592
        val a2 = -0.284496736
        val a3 = 1.421413741
        val a4 = -1.453152027
        val a5 = 1.061405429
        val p = 0.3275911
        val t = 1.0 / (1.0 + p * ax)
        val y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * exp(-ax * ax)
        return sign * y
    }
}
