package com.bios.app.engine

import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType

/**
 * Computes heart-rate recovery (HRR) from an exercise session and the
 * surrounding HR timeseries.
 *
 * HRR is one of the strongest non-invasive cardiology prognostics (Cole
 * 1999, NEJM 341:1351 — HRR1 ≤ 12 bpm doubled 6-year mortality). The
 * canonical definition:
 *
 * ```
 * HRR(τ) = HR_peak − HR(end + τ)
 * ```
 *
 * with `HR_peak` taken across the EXERCISE_SESSION interval and `HR(end +
 * τ)` taken from the post-exercise window. Clinical conventions sample
 * at τ = 60 s (HRR1) and τ = 120 s (HRR2). A larger delta is healthier
 * — vagal reactivation drops the rate quickly in fit, parasympathetically
 * intact owners; a small delta is the autonomic-dysfunction signature.
 *
 * This computer is a pure function — no DAO access, no coroutines. The
 * caller supplies the session bounds plus the HR readings inside the
 * relevant window. Downstream wiring (a session-trigger worker that
 * persists the result as a `HR_RECOVERY_1MIN` / `HR_RECOVERY_2MIN`
 * [MetricReading]) is a separate concern; this file ships the algorithm
 * and its unit tests.
 *
 * ## Tolerances
 *
 * - Post-exercise sample tolerance is ±[POST_EXERCISE_SAMPLE_TOLERANCE_MS]
 *   around the target instant. Continuous HR feeds (HC, Fitbit) typically
 *   sample at 1 Hz; the tolerance window absorbs irregular sampling
 *   without picking up a stale reading from before the session ended.
 * - Returns null when fewer than [MIN_SESSION_READINGS] HR samples fall
 *   inside the session interval — peak HR is too noisy to anchor against
 *   on a very short session.
 */
object HrRecoveryComputer {

    const val HRR1_TAU_MS: Long = 60_000L
    const val HRR2_TAU_MS: Long = 120_000L

    /**
     * Window (±) around the target post-exercise sample instant the
     * computer accepts. Wider than 5 s lets the closest stale reading win
     * on an irregular feed; tighter than 5 s drops legitimate samples on
     * a 1-Hz Fitbit feed that lands a beat or two off the boundary.
     */
    const val POST_EXERCISE_SAMPLE_TOLERANCE_MS: Long = 5_000L

    /** Minimum HR samples needed within the session to anchor peak HR. */
    const val MIN_SESSION_READINGS: Int = 5

    data class HrrResult(
        val peakHrBpm: Double,
        val hrAt60sBpm: Double?,
        val hrAt120sBpm: Double?,
        val hrr1BpmDelta: Double?,
        val hrr2BpmDelta: Double?,
    )

    /**
     * Compute HRR1 / HRR2 for a session bounded by [sessionStartMs] and
     * [sessionEndMs]. [hrReadings] are the HR samples available across
     * the session + post-session window — the caller decides the query
     * range (typically session start through end + 130 s).
     *
     * Returns null when the session has too few HR samples to identify a
     * credible peak.
     */
    fun compute(
        sessionStartMs: Long,
        sessionEndMs: Long,
        hrReadings: List<MetricReading>,
    ): HrrResult? {
        val hrOnly = hrReadings.filter { it.metricType == MetricType.HEART_RATE.key }
        val duringSession = hrOnly.filter { it.timestamp in sessionStartMs..sessionEndMs }
        if (duringSession.size < MIN_SESSION_READINGS) return null

        val peakHr = duringSession.maxOf { it.value }

        val hr1 = nearestReadingNear(hrOnly, sessionEndMs + HRR1_TAU_MS)?.value
        val hr2 = nearestReadingNear(hrOnly, sessionEndMs + HRR2_TAU_MS)?.value

        return HrrResult(
            peakHrBpm = peakHr,
            hrAt60sBpm = hr1,
            hrAt120sBpm = hr2,
            hrr1BpmDelta = hr1?.let { peakHr - it },
            hrr2BpmDelta = hr2?.let { peakHr - it },
        )
    }

    /**
     * Pick the HR reading closest in time to [targetMs] inside the
     * tolerance window. Returns null if no reading falls inside the
     * window — fabricating an interpolated value would be worse than
     * declaring "we don't know yet."
     */
    internal fun nearestReadingNear(
        readings: List<MetricReading>,
        targetMs: Long,
    ): MetricReading? {
        val candidates = readings.filter {
            kotlin.math.abs(it.timestamp - targetMs) <= POST_EXERCISE_SAMPLE_TOLERANCE_MS
        }
        if (candidates.isEmpty()) return null
        return candidates.minBy { kotlin.math.abs(it.timestamp - targetMs) }
    }
}
