package com.bios.app.alerts

import com.bios.app.data.BiosDatabase
import com.bios.app.model.Anomaly
import com.bios.app.model.ReadingKind

/** An alert plus the measured signals around it, ready for the detail screen. */
data class AlertDetail(
    val anomaly: Anomaly,
    /** Every watched metric with a baseline and data in the window, deviating first. */
    val signals: List<MeasuredSignal>,
)

/**
 * Rebuilds what the detector saw when an alert fired: the SENSOR average over
 * the 24 h before `detectedAt` for each watched metric, against the current
 * baseline. No schema change: values are recomputed from stored readings
 * (baselines may have moved slightly since detection; the range is labelled
 * "usually", not "at the time").
 */
class AlertDetailLoader(db: BiosDatabase) {
    private val anomalyDao = db.anomalyDao()
    private val readingDao = db.metricReadingDao()
    private val baselineDao = db.personalBaselineDao()

    suspend fun load(anomalyId: String): AlertDetail? {
        val anomaly = anomalyDao.fetchAll().firstOrNull { it.id == anomalyId } ?: return null
        val end = anomaly.detectedAt
        val start = end - AlertDetailContent.WINDOW_MILLIS
        val signals = AlertDetailContent.WATCHED_METRICS.mapNotNull { key ->
            val baseline = baselineDao.fetch(key) ?: return@mapNotNull null
            val values = readingDao.fetchValues(key, start, end, ReadingKind.SENSOR.name)
            if (values.isEmpty()) null else AlertDetailContent.signal(key, values.average(), baseline)
        }.sortedWith(compareByDescending<MeasuredSignal> { it.deviating }.thenByDescending { kotlin.math.abs(it.zScore) })
        return AlertDetail(anomaly, signals)
    }
}
