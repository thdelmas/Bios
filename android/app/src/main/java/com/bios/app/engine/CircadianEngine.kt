package com.bios.app.engine

import com.bios.app.data.dao.MetricReadingDao
import com.bios.app.engine.CircadianCalculator.DailySleepSummary
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Reads recent `SLEEP_DURATION` rows off the bus, builds per-night summaries,
 * and emits a `CIRCADIAN_PHASE_SHIFT` reading via [CircadianCalculator].
 *
 * Inputs are SLEEP_DURATION only (universal across adapters) — `timestamp` is
 * the wake instant, `durationSec` is total time-in-bed, `value` is time-asleep
 * in seconds. From those three we derive both the local-clock sleep-onset hour
 * (wake − time-in-bed) and the sleep-hours figure the calculator needs. This
 * keeps the engine source-agnostic: Health Connect, Oura, Withings, and the
 * manual-entry path all feed it without special-casing.
 *
 * Idempotency: skips emission when a CIRCADIAN_PHASE_SHIFT reading already
 * exists at or after today's wake instant. The append-only readings table makes
 * a "have we already written today?" check the simplest correct gate.
 *
 * Cadence: invoked once per sync. The calculator returns null until at least
 * 3 days of reference history exist, so early-install runs are silent by
 * design rather than producing noisy zero-history estimates.
 */
class CircadianEngine(
    private val readingDao: MetricReadingDao,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {

    suspend fun derive(now: Instant = Instant.now()): MetricReading? {
        val windowStart = now.minus(LOOKBACK_DAYS.toLong(), ChronoUnit.DAYS)
        val sleepRows = readingDao.fetch(
            metricType = MetricType.SLEEP_DURATION.key,
            startMillis = windowStart.toEpochMilli(),
            endMillis = now.toEpochMilli(),
        )
        if (sleepRows.isEmpty()) return null

        val summaries = toDailySummaries(sleepRows, zoneId)
        if (summaries.size < MIN_SUMMARIES_TO_EMIT) return null

        val today = summaries.last()
        val history = summaries.dropLast(1)

        val lastEmitted = readingDao.lastTimestampFor(MetricType.CIRCADIAN_PHASE_SHIFT.key)
        if (lastEmitted != null && lastEmitted >= today.wakeEpochMillis) return null

        val features = CircadianCalculator.calculate(
            recent = history.map { it.summary },
            todayOnsetHour = today.summary.sleepOnsetHour,
        )
        val phaseShift = features.phaseShift ?: return null

        return MetricReading(
            metricType = MetricType.CIRCADIAN_PHASE_SHIFT.key,
            value = phaseShift,
            timestamp = today.wakeEpochMillis,
            sourceId = today.sourceId,
            confidence = today.confidence,
        )
    }

    internal data class DatedSummary(
        val date: LocalDate,
        val summary: DailySleepSummary,
        val wakeEpochMillis: Long,
        val sourceId: String,
        val confidence: Int,
    )

    companion object {
        private const val LOOKBACK_DAYS = 30

        // Need at least 4 nights total: 3 for the reference history + today.
        // Matches CircadianCalculator.MIN_SAMPLES_PHASE_SHIFT = 3 on `recent`.
        internal const val MIN_SUMMARIES_TO_EMIT = 4

        private const val SECONDS_PER_HOUR = 3600.0

        /**
         * Groups SLEEP_DURATION rows by local wake-date. Multiple adapters can
         * report the same night; the highest-confidence row per date wins, so
         * a watch-derived session beats a manual entry and the emitted
         * CIRCADIAN_PHASE_SHIFT inherits the strongest available source.
         */
        internal fun toDailySummaries(
            rows: List<MetricReading>,
            zoneId: ZoneId,
        ): List<DatedSummary> {
            val byDate = rows
                .filter { it.durationSec != null && it.durationSec > 0 }
                .groupBy { LocalDate.ofInstant(Instant.ofEpochMilli(it.timestamp), zoneId) }

            return byDate.entries
                .map { (date, sameDate) ->
                    val best = sameDate.maxBy { it.confidence }
                    val wakeInstant = Instant.ofEpochMilli(best.timestamp)
                    val inBedMs = best.durationSec!!.toLong() * 1000L
                    val onsetInstant = wakeInstant.minusMillis(inBedMs)
                    DatedSummary(
                        date = date,
                        summary = DailySleepSummary(
                            date = date.toString(),
                            sleepOnsetHour = localHourOf(onsetInstant, zoneId),
                            sleepHours = best.value / SECONDS_PER_HOUR,
                        ),
                        wakeEpochMillis = best.timestamp,
                        sourceId = best.sourceId,
                        confidence = best.confidence,
                    )
                }
                .sortedBy { it.date }
        }

        private fun localHourOf(instant: Instant, zoneId: ZoneId): Double {
            val ldt = instant.atZone(zoneId).toLocalDateTime()
            return ldt.hour + ldt.minute / 60.0 + ldt.second / 3600.0
        }
    }
}
