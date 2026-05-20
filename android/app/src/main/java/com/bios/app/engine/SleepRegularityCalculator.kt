package com.bios.app.engine

import com.bios.app.model.EventPayloadField
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Computes [MetricType.SLEEP_REGULARITY] from a window of recent
 * `SLEEP_DURATION` readings.
 *
 * Sleep researchers (Roenneberg, Walker, Phyllis Zee group) consistently
 * rank regularity above absolute duration for long-term outcomes. Bios
 * already tracks duration / latency / score; regularity is the gap this
 * calculator fills. Implementation note: variability in *midpoint* is the
 * strongest single regularity signal in the literature (Phillips et al.
 * 2017), so the composite score keys off midpoint variance while bedtime
 * and wake variance ride as sidecar fields.
 *
 * Time-of-day comparison has a wrap-around problem — 23:30 one night and
 * 00:30 the next are 1h apart, not 23h. Standard fix is **circular
 * statistics**: map each time-of-day to an angle on the unit circle, take
 * the mean vector, and derive the circular std-deviation from its length
 * (Mardia & Jupp). This gives a well-defined "spread" no matter where the
 * cluster sits around the clock.
 *
 * Confidence on the composite is the **minimum** of the input readings'
 * confidence tiers. A regularity computed over a window that includes
 * LOW-confidence W2F-screen-off nights cannot honestly claim better than
 * LOW itself. Engines that filter by confidence (BaselineEngine) keep the
 * existing semantics unchanged.
 */
object SleepRegularityCalculator {

    /**
     * Sidecar field keys for the SLEEP_REGULARITY composite. Centralized so
     * adapters, readers, and tests cannot drift on spelling. New keys belong
     * in docs/DATA_MODEL.md alongside the metric they describe.
     */
    object Fields {
        const val BEDTIME_VARIANCE_MIN = "bedtime_variance_min"
        const val WAKE_VARIANCE_MIN = "wake_variance_min"
        const val MIDPOINT_VARIANCE_MIN = "midpoint_variance_min"
        const val WINDOW_DAYS = "window_days"
    }

    /** Parent reading + the four sidecar payload rows that ride beside it. */
    data class Result(
        val reading: MetricReading,
        val payload: List<EventPayloadField>,
    )

    /**
     * Compute regularity over the most recent [windowDays] of sleep readings.
     * Returns null when fewer than [MIN_NIGHTS] usable nights exist — a
     * std-deviation over two samples is meaningless and would mis-rank
     * thin-data weeks against full ones.
     */
    fun derive(
        sleepDurations: List<MetricReading>,
        sourceId: String,
        windowDays: Int = DEFAULT_WINDOW_DAYS,
        nowMs: Long = System.currentTimeMillis(),
    ): Result? {
        val cutoff = nowMs - windowDays.toLong() * MILLIS_PER_DAY
        val nights = sleepDurations
            .asSequence()
            .filter { it.metricType == MetricType.SLEEP_DURATION.key }
            .filter { it.timestamp >= cutoff && it.timestamp <= nowMs }
            .filter { (it.durationSec ?: 0) > 0 }
            .sortedBy { it.timestamp }
            .toList()
        if (nights.size < MIN_NIGHTS) return null

        val midpointVarMin = circularStdMinutes(nights.map { midnightAngle(it.timestamp) })
        val bedtimeVarMin = circularStdMinutes(nights.map {
            midnightAngle(it.timestamp - (it.durationSec!! * 1000L) / 2L)
        })
        val wakeVarMin = circularStdMinutes(nights.map {
            midnightAngle(it.timestamp + (it.durationSec!! * 1000L) / 2L)
        })

        val score = scoreFromMidpointVariance(midpointVarMin)
        val confidence = nights.minOf { it.confidence }

        val reading = MetricReading(
            metricType = MetricType.SLEEP_REGULARITY.key,
            value = score,
            timestamp = nowMs,
            durationSec = windowDays * SECONDS_PER_DAY,
            sourceId = sourceId,
            confidence = confidence
        )
        val payload = listOf(
            EventPayloadField(
                readingId = reading.id, fieldKey = Fields.BEDTIME_VARIANCE_MIN,
                longValue = bedtimeVarMin
            ),
            EventPayloadField(
                readingId = reading.id, fieldKey = Fields.WAKE_VARIANCE_MIN,
                longValue = wakeVarMin
            ),
            EventPayloadField(
                readingId = reading.id, fieldKey = Fields.MIDPOINT_VARIANCE_MIN,
                longValue = midpointVarMin
            ),
            EventPayloadField(
                readingId = reading.id, fieldKey = Fields.WINDOW_DAYS,
                longValue = windowDays.toLong()
            ),
        )
        return Result(reading, payload)
    }

    /** Epoch-ms timestamp → angle on the 24h unit circle, in radians. */
    private fun midnightAngle(epochMs: Long): Double {
        // Modulo into [0, 86_400_000) ms-since-midnight (UTC). The
        // calculator works in UTC because that's where all timestamps in
        // the Bios bus live; cross-timezone owners get the same
        // dispersion shape, just rotated. (A future UI/dashboard layer
        // can render in local time without changing the math here.)
        var msOfDay = epochMs % MILLIS_PER_DAY
        if (msOfDay < 0) msOfDay += MILLIS_PER_DAY
        return 2.0 * Math.PI * (msOfDay.toDouble() / MILLIS_PER_DAY.toDouble())
    }

    /**
     * Circular std-deviation of the given angles, returned in clock-minutes.
     *
     * Standard derivation: R = ||mean unit vector||, circular σ in radians
     * is sqrt(-2 ln R). Convert back to minutes via 24h / 2π. When R ≈ 1
     * (perfectly clustered) the formula goes to 0; when R ≈ 0 (uniform)
     * it diverges, so we clamp at a half-day.
     */
    private fun circularStdMinutes(angles: List<Double>): Long {
        if (angles.isEmpty()) return 0L
        val meanCos = angles.sumOf { cos(it) } / angles.size
        val meanSin = angles.sumOf { sin(it) } / angles.size
        val r = sqrt(meanCos * meanCos + meanSin * meanSin).coerceIn(R_FLOOR, 1.0)
        val sigmaRad = sqrt(-2.0 * ln(r))
        val sigmaMin = sigmaRad * MINUTES_PER_DAY / (2.0 * Math.PI)
        return sigmaMin.coerceAtMost(MINUTES_PER_DAY / 2.0).roundToLong()
    }

    /**
     * Map midpoint variance (clock-minutes) to a 0–100 score. 100 at ≤ 15
     * min spread (very-regular cluster), drops linearly to 0 at ≥ 90 min.
     * Anchored to literature ranges: ≤ 15 min midpoint variability is the
     * "very regular" band; > 90 min is the highly-irregular band associated
     * with elevated cardiometabolic risk (Phillips 2017, Lunsford-Avery 2018).
     */
    private fun scoreFromMidpointVariance(varianceMin: Long): Double {
        val v = varianceMin.toDouble()
        return when {
            v <= REGULAR_THRESHOLD_MIN -> 100.0
            v >= IRREGULAR_THRESHOLD_MIN -> 0.0
            else -> ((IRREGULAR_THRESHOLD_MIN - v) /
                (IRREGULAR_THRESHOLD_MIN - REGULAR_THRESHOLD_MIN) * 100.0)
        }
    }

    internal const val DEFAULT_WINDOW_DAYS = 7
    internal const val MIN_NIGHTS = 3
    internal const val REGULAR_THRESHOLD_MIN = 15.0
    internal const val IRREGULAR_THRESHOLD_MIN = 90.0
    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    private const val MINUTES_PER_DAY = 24.0 * 60.0
    private const val SECONDS_PER_DAY = 24 * 60 * 60
    private const val R_FLOOR = 1e-9
}
