package com.bios.app.engine

import com.bios.app.data.dao.MetricReadingDao
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Heart rate before sleep (#309). Reads recent `SLEEP_DURATION` rows + the
 * heart-rate stream surrounding each night's bedtime, and emits one
 * `HEART_RATE_BEFORE_SLEEP` reading per night via the median of the HR
 * samples in the [PRE_SLEEP_WINDOW_MIN]-minute window preceding bedtime.
 *
 * Bedtime is derived from `SLEEP_DURATION`: `timestamp` is the wake instant
 * and `durationSec` is total time-in-bed, so `bedtime = timestamp − durationSec`.
 * Sleep latency is not factored in (we want HR while the owner is in bed
 * preparing to sleep, which is what the source essay calls out — moving from
 * "lying still" to "asleep" is exactly the transition this metric captures).
 *
 * Pipeline:
 *  1. Locate the most-recent SLEEP_DURATION row (one HRBS per night, keyed on
 *     wake timestamp so re-syncs collapse).
 *  2. Fetch HEART_RATE samples in `[bedtime − PRE_SLEEP_WINDOW_MIN, bedtime]`.
 *  3. Require [MIN_HR_SAMPLES_REQUIRED] samples; below that the median is
 *     too noisy to anchor a single nightly value.
 *  4. Emit the median bpm as a HEART_RATE_BEFORE_SLEEP reading timestamped
 *     at `bedtime`, inheriting the sleep row's confidence + sourceId.
 *
 * Idempotency mirrors [CircadianEngine] — skip emission when a HRBS reading
 * already exists at or after the current night's bedtime.
 */
class HrbsEngine(
    private val readingDao: MetricReadingDao,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {

    suspend fun derive(now: Instant = Instant.now()): MetricReading? {
        val sleepRows = readingDao.fetch(
            metricType = MetricType.SLEEP_DURATION.key,
            startMillis = now.minus(LOOKBACK_DAYS.toLong(), ChronoUnit.DAYS).toEpochMilli(),
            endMillis = now.toEpochMilli(),
        ).filter { (it.durationSec ?: 0) > 0 }
        if (sleepRows.isEmpty()) return null

        // Pick the highest-confidence row per local wake-date so a watch
        // session beats a manual entry, matching CircadianEngine semantics.
        val byDate = sleepRows.groupBy {
            Instant.ofEpochMilli(it.timestamp).atZone(zoneId).toLocalDate()
        }
        val mostRecentDate = byDate.keys.maxOrNull() ?: return null
        val best = byDate.getValue(mostRecentDate).maxBy { it.confidence }

        val bedtimeMs = best.timestamp - best.durationSec!!.toLong() * 1000L
        val windowStartMs = bedtimeMs - PRE_SLEEP_WINDOW_MIN * 60L * 1000L

        val lastEmitted = readingDao.lastTimestampFor(MetricType.HEART_RATE_BEFORE_SLEEP.key)
        if (lastEmitted != null && lastEmitted >= bedtimeMs) return null

        val hrSamples = readingDao.fetch(
            metricType = MetricType.HEART_RATE.key,
            startMillis = windowStartMs,
            endMillis = bedtimeMs,
        )
        if (hrSamples.size < MIN_HR_SAMPLES_REQUIRED) return null

        val median = medianOf(hrSamples.map { it.value })

        return MetricReading(
            metricType = MetricType.HEART_RATE_BEFORE_SLEEP.key,
            value = median,
            timestamp = bedtimeMs,
            sourceId = best.sourceId,
            confidence = best.confidence,
        )
    }

    companion object {
        private const val LOOKBACK_DAYS = 2

        /** Pre-sleep window the median is taken over, in minutes. 15 mirrors
         *  the 15s × 4 owner-pulse-count method described in the source
         *  essay (Bryan Johnson, May 2026) — a steady "lying quietly"
         *  interval long enough for HR to settle, short enough to predate
         *  sleep onset on most schedules. */
        internal const val PRE_SLEEP_WINDOW_MIN = 15

        /** Minimum HR samples in the window before the median is trusted.
         *  At 1 sample/min (the Health Connect spot-write cadence on most
         *  wearables) this is a third of the window; at 1 sample/5s
         *  (continuous wearable streams) it's a few seconds. */
        internal const val MIN_HR_SAMPLES_REQUIRED = 5

        internal fun medianOf(values: List<Double>): Double {
            val sorted = values.sorted()
            val n = sorted.size
            return if (n % 2 == 0) (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0 else sorted[n / 2]
        }
    }
}
