package com.bios.app.engine

import com.bios.app.alerts.AlertDetailContent
import com.bios.app.alerts.MeasuredSignal
import com.bios.app.data.dao.MetricReadingDao
import com.bios.app.data.dao.PersonalBaselineDao
import com.bios.app.model.ReadingKind
import kotlin.math.abs

/**
 * Inputs and wording for the holistic (ML / heuristic) detector, kept out of
 * [AnomalyDetector] for the 500-line ceiling.
 */
internal object HolisticSignals {

    /** Each watched metric's 24 h signal plus its newest reading time (evidence reuse check). */
    suspend fun current(
        readingDao: MetricReadingDao,
        baselineDao: PersonalBaselineDao,
        now: Long,
    ): Map<String, Pair<MeasuredSignal, Long>> {
        val start = now - AlertDetailContent.WINDOW_MILLIS
        return AlertDetailContent.WATCHED_METRICS.mapNotNull { metric ->
            val baseline = baselineDao.fetch(metric)
            // SENSOR-only z-scores: docs/SELF_REPORTED_DATA_HOME.md decision 3.
            val readings = readingDao.fetchTimedValues(metric, start, now, ReadingKind.SENSOR.name)
            if (baseline == null || readings.isEmpty()) {
                null
            } else {
                val signal = AlertDetailContent.signal(metric, readings.map { it.value }.average(), baseline)
                metric to (signal to readings.maxOf { it.timestamp })
            }
        }.toMap()
    }

    /** Data statements in real units ("Resting heart rate 66 bpm … usual 53–60 bpm."). */
    fun explanation(deviations: List<MeasuredSignal>): String {
        if (deviations.isEmpty()) return "Multiple health metrics deviate from your baselines."
        return deviations
            .sortedByDescending { abs(it.zScore) }
            .take(3)
            .joinToString(" ") { AlertDetailContent.notificationLine(it) }
    }
}
