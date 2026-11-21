package com.bios.app.alerts

import com.bios.app.engine.AnomalyDetector
import com.bios.app.model.Anomaly
import org.json.JSONArray
import kotlin.math.abs

/**
 * The measured part of a stored alert, for everything outside the alert
 * screen: file exports and the local API. It reads the metrics an alert
 * names and the detection-time snapshot, and never the stored title or the
 * journal columns of earlier versions.
 */
object AlertMeasurements {

    /** Metric keys the alert was raised on. */
    fun metricKeys(alert: Anomaly): List<String> {
        val array = runCatching { JSONArray(alert.metricTypes) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optString(it).ifEmpty { null } }
    }

    /** Those metrics as measured at detection, largest deviation first; empty when no snapshot was stored. */
    fun signals(alert: Anomaly): List<MeasuredSignal> {
        val keys = metricKeys(alert).toSet()
        return SignalSnapshot.decode(alert.signalSnapshot)
            .filter { if (keys.isEmpty()) it.deviating else it.metricKey in keys }
            .sortedByDescending { abs(it.zScore) }
    }

    /**
     * One sentence per metric, in units. A deviation alert stored before
     * snapshots existed carries the same sentences as its text; an alert of
     * a removed pattern without a snapshot has none.
     */
    fun lines(alert: Anomaly): List<String> {
        val fromSnapshot = signals(alert).map { AlertDetailContent.notificationLine(it) }
        return when {
            fromSnapshot.isNotEmpty() -> fromSnapshot
            alert.patternId == AnomalyDetector.ML_PATTERN_ID && alert.explanation.isNotBlank() -> listOf(alert.explanation)
            else -> emptyList()
        }
    }

    /** What the owner wrote on the alert, as written; null when nothing was written. */
    fun note(alert: Anomaly): String? =
        listOfNotNull(alert.symptoms, alert.notes).filter { it.isNotBlank() }.joinToString("\n").ifEmpty { null }
}
