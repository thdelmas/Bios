package com.bios.app.alerts

import org.json.JSONArray
import org.json.JSONObject

/**
 * The watched signals as they stood when an alert fired, stored on the alert
 * row (`anomalies.signalSnapshot`, v37) so the detail screen shows what the
 * detector saw rather than what the window reads after later syncs. Source
 * names are not known at detection and are left empty.
 */
object SignalSnapshot {

    fun encode(signals: Collection<MeasuredSignal>): String {
        val array = JSONArray()
        signals.forEach { s ->
            array.put(
                JSONObject()
                    .put(KEY_METRIC, s.metricKey)
                    .put(KEY_VALUE, s.value)
                    .put(KEY_LOW, s.usualLow)
                    .put(KEY_HIGH, s.usualHigh)
                    .put(KEY_Z, s.zScore)
                    .put(KEY_COUNT, s.readingCount),
            )
        }
        return array.toString()
    }

    /** Empty for null, blank or unreadable input: the caller falls back to a recomputation. */
    fun decode(json: String?): List<MeasuredSignal> {
        if (json.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val metric = o.optString(KEY_METRIC).ifEmpty { return@mapNotNull null }
            MeasuredSignal(
                metricKey = metric,
                value = o.optDouble(KEY_VALUE),
                usualLow = o.optDouble(KEY_LOW),
                usualHigh = o.optDouble(KEY_HIGH),
                zScore = o.optDouble(KEY_Z, 0.0),
                readingCount = o.optInt(KEY_COUNT, 0),
            )
        }.filter { !it.value.isNaN() }
    }

    private const val KEY_METRIC = "metric"
    private const val KEY_VALUE = "value"
    private const val KEY_LOW = "low"
    private const val KEY_HIGH = "high"
    private const val KEY_Z = "z"
    private const val KEY_COUNT = "n"
}
