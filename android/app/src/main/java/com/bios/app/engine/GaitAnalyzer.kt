package com.bios.app.engine

import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Phone-sensor gait analysis (#208, GERIATRICS_PALLIATIVE_POV §2.3 +
 * Neurology MCI audit).
 *
 * Computes stride-time coefficient of variation (CoV) from a series of
 * step-event timestamps. The anchor literature:
 *
 *  - **Hausdorff 2007** — *Gait variability: methods, modeling and meaning*.
 *    Defines stride-time CoV as the canonical measure of gait timing
 *    irregularity. CoV ≥ ~3 % is the broadly-cited cohort threshold above
 *    which fall risk in older adults rises non-linearly.
 *  - **Verghese 2009** — *Quantitative gait dysfunction and risk of cognitive
 *    decline and dementia*. Establishes the link between stride-time
 *    variability and incident MCI / dementia in community-dwelling older
 *    adults.
 *  - **Maki 1994** — postural sway amplitude correlates with future falls
 *    in older adults; informs the companion POSTURAL_SWAY_AP_AMPLITUDE_MM
 *    metric (computed by a separate one-shot standing-still capture).
 *
 * ## Manifesto framing
 *
 * GaitAnalyzer is a **data substrate**. It writes the [MetricType.GAIT_VARIABILITY_COV]
 * reading and stops. It does not classify, score the person, or push a
 * notification. The geriatric trajectory advisories
 * ([com.bios.app.alerts.GeriatricTrajectoryPatterns]) read this signal
 * alongside frailty + polypharmacy + age, but every one of those advisories
 * is `pullSideOnly = true` — the owner navigates into the trajectory view
 * to read their own trend. Bios never says "your fall risk increased".
 *
 * ## Algorithm
 *
 * Input: a list of step-event timestamps (epoch-millis), typically from a
 * rolling 24 h window of [MetricType.STEPS] readings or step-counter
 * deltas.
 *
 * 1. Compute inter-step intervals (ms between consecutive timestamps).
 * 2. Drop intervals outside the physiologically plausible band
 *    ([MIN_STRIDE_MS]..[MAX_STRIDE_MS]) — anything tighter is double-tap
 *    noise; anything looser is a pause between walking bouts, not a stride.
 * 3. Require at least [MIN_INTERVALS] surviving intervals; otherwise the
 *    sample is insufficient and the analyzer returns `null`.
 * 4. Compute the coefficient of variation: `stdDev / mean × 100`. Reported
 *    as a percent (CoV × 100), per the convention in the Hausdorff
 *    literature.
 *
 * The speed-corrected variant ([gaitVariabilitySpeedCorrected]) divides
 * the raw CoV by the mean inter-step interval normalised against a
 * reference 1-second cadence — this removes the slow-walking inflation
 * effect Hausdorff 2007 calls out (slow gait has structurally higher
 * CoV regardless of pathology).
 *
 * ## Calling shape
 *
 * The engine is pure-Kotlin and side-effect-free — no Room, no
 * SharedPreferences, no Android context. The caller (typically a
 * SyncWorker stage) decides how often to run it, how to persist the
 * resulting reading, and which source-id to tag.
 */
object GaitAnalyzer {

    /**
     * Minimum stride-time interval, in milliseconds. Anything tighter is
     * not a stride — it's accelerometer noise or a step-counter double-fire.
     * 250 ms = 240 steps/min ceiling (sprinting); even elite sprinters
     * rarely exceed this for sustained windows.
     */
    const val MIN_STRIDE_MS: Long = 250L

    /**
     * Maximum stride-time interval, in milliseconds. Anything looser is a
     * pause between walking bouts and shouldn't count as a single stride.
     * 2000 ms = 30 steps/min floor. Older adults with parkinsonian gait
     * can dip near the floor; the trajectory signal is the trend, not
     * any single sample.
     */
    const val MAX_STRIDE_MS: Long = 2_000L

    /**
     * Minimum number of in-band intervals required to compute a stable CoV.
     * Hausdorff 2007 recommends ≥30 strides; the wearable / phone-derived
     * literature uses ≥20 for shorter capture windows. We pick 20 as the
     * conservative floor that still gives a meaningful variance estimate.
     */
    const val MIN_INTERVALS: Int = 20

    /** Reference cadence used by the speed-corrected CoV. 1 stride/sec is
     *  the rough adult comfortable-walking cadence (Hausdorff 2007). */
    private const val REFERENCE_INTERVAL_MS: Double = 1_000.0

    /**
     * Computes stride-time CoV as a percent (CoV × 100) from a list of
     * step-event epoch-millis timestamps. Returns `null` when the input
     * doesn't have enough in-band intervals to be meaningful.
     *
     * @param stepTimestampsMillis ascending-sorted (or unsorted; the
     *   function sorts defensively) epoch-millisecond step events.
     */
    fun strideTimeCoVPercent(stepTimestampsMillis: List<Long>): Double? {
        val intervals = inBandIntervals(stepTimestampsMillis) ?: return null
        val mean = intervals.average()
        if (mean <= 0.0) return null
        val variance = intervals.fold(0.0) { acc, v -> acc + (v - mean) * (v - mean) } / intervals.size
        val stdDev = sqrt(variance)
        return stdDev / mean * 100.0
    }

    /**
     * Speed-corrected stride-time CoV. Normalises the raw CoV against the
     * deviation of the mean inter-step interval from the 1 s/stride
     * reference cadence. Removes the slow-walking inflation effect
     * (Hausdorff 2007). Returns `null` under the same conditions as
     * [strideTimeCoVPercent].
     */
    fun gaitVariabilitySpeedCorrected(stepTimestampsMillis: List<Long>): Double? {
        val intervals = inBandIntervals(stepTimestampsMillis) ?: return null
        val mean = intervals.average()
        if (mean <= 0.0) return null
        val variance = intervals.fold(0.0) { acc, v -> acc + (v - mean) * (v - mean) } / intervals.size
        val stdDev = sqrt(variance)
        val rawCov = stdDev / mean * 100.0
        // Speed correction: divide by the ratio of mean to reference.
        // Slow gait (mean > 1000 ms) shrinks the corrected value; fast
        // gait (mean < 1000 ms) inflates it modestly. The intent is a
        // metric that's comparable across owners with different
        // baseline cadences.
        val speedFactor = mean / REFERENCE_INTERVAL_MS
        return if (speedFactor > 0.0) rawCov / speedFactor else null
    }

    /**
     * Wraps [strideTimeCoVPercent] in a [MetricReading] tagged with the
     * caller-supplied [sourceId]. Returns `null` when there isn't enough
     * data; the caller decides whether to persist or skip.
     */
    fun toReading(
        stepTimestampsMillis: List<Long>,
        sourceId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): MetricReading? {
        val cov = strideTimeCoVPercent(stepTimestampsMillis) ?: return null
        return MetricReading(
            metricType = MetricType.GAIT_VARIABILITY_COV.key,
            value = cov,
            timestamp = nowMillis,
            sourceId = sourceId,
            confidence = ConfidenceTier.LOW.level,
        )
    }

    /**
     * Returns the in-band intervals (in ms) between consecutive step
     * timestamps, or `null` when there aren't enough surviving intervals.
     */
    private fun inBandIntervals(stepTimestampsMillis: List<Long>): List<Double>? {
        if (stepTimestampsMillis.size < MIN_INTERVALS + 1) return null
        val sorted = stepTimestampsMillis.sorted()
        val intervals = mutableListOf<Double>()
        for (i in 1 until sorted.size) {
            val dt = sorted[i] - sorted[i - 1]
            if (dt in MIN_STRIDE_MS..MAX_STRIDE_MS) {
                intervals.add(dt.toDouble())
            }
        }
        if (intervals.size < MIN_INTERVALS) return null
        return intervals
    }

    /**
     * Computes an anterior-posterior postural-sway amplitude estimate
     * (in millimetres) from a stream of accelerometer samples taken
     * during a standing-still capture. The owner holds the phone against
     * their chest, eyes open, ~30 s — the standard "Romberg-on-phone"
     * surrogate. The amplitude is approximated by double-integrating the
     * AP-axis acceleration over the capture window after removing the
     * gravity offset, and taking the peak-to-peak displacement.
     *
     * This is a coarse phone-sensor proxy for laboratory force-plate
     * postural sway (Maki 1994). It is offered as a substrate metric;
     * the geriatric trajectory advisories do not currently fire on it,
     * but the pull-side view shows its trend.
     *
     * Returns `null` when the input is too short to estimate.
     *
     * @param apAccelerationsMps2 anterior-posterior axis acceleration
     *   samples in m/s² (already gravity-removed by the caller).
     * @param sampleRateHz sampling rate; typical 50–100 Hz from phone
     *   accelerometers.
     */
    fun posturalSwayAmplitudeMm(
        apAccelerationsMps2: List<Double>,
        sampleRateHz: Double,
    ): Double? {
        if (apAccelerationsMps2.size < 50) return null
        if (sampleRateHz <= 0.0) return null
        val dt = 1.0 / sampleRateHz
        var v = 0.0
        var x = 0.0
        var maxX = Double.NEGATIVE_INFINITY
        var minX = Double.POSITIVE_INFINITY
        for (a in apAccelerationsMps2) {
            v += a * dt
            x += v * dt
            if (x > maxX) maxX = x
            if (x < minX) minX = x
        }
        // m → mm (×1000), peak-to-peak amplitude.
        val amplitudeMm = abs(maxX - minX) * 1000.0
        return amplitudeMm
    }
}
