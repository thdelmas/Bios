package com.bios.app.export

import com.bios.app.alerts.AlertDetailContent
import com.bios.app.alerts.AlertMeasurements
import com.bios.app.model.Anomaly
import org.json.JSONArray
import org.json.JSONObject

/**
 * How a stored alert leaves in a file: when it was raised, its tier, the
 * metrics involved with their measured value and the owner's usual range,
 * whether it was acknowledged, and the owner's note. No title, no text and
 * no identifier of the rule that raised it.
 */
internal object AlertExport {

    const val CSV_HEADER = "id,detectedAt,tier,metrics,measured,acknowledged,acknowledgedAt,note"

    fun json(alert: Anomaly): JSONObject = JSONObject().apply {
        put("id", alert.id)
        put("detectedAt", alert.detectedAt)
        put("tier", alert.severity)
        put("metrics", JSONArray(AlertMeasurements.metricKeys(alert)))
        put("measured", JSONArray().also { array ->
            AlertMeasurements.signals(alert).forEach { s ->
                array.put(JSONObject().apply {
                    put("metric", s.metricKey)
                    put("value", s.value)
                    put("unit", AlertDetailContent.unit(s.metricKey))
                    put("usualLow", s.usualLow)
                    put("usualHigh", s.usualHigh)
                })
            }
        })
        put("acknowledged", alert.acknowledged)
        put("acknowledgedAt", alert.acknowledgedAt ?: JSONObject.NULL)
        put("note", AlertMeasurements.note(alert) ?: JSONObject.NULL)
    }

    /** "resting_heart_rate=66.0 (53.0 to 60.0)" per metric, separated by "; ". */
    fun csvRow(alert: Anomaly, escape: (String) -> String): String {
        val measured = AlertMeasurements.signals(alert).joinToString("; ") {
            "${it.metricKey}=${it.value} (${it.usualLow} to ${it.usualHigh})"
        }
        return listOf(
            escape(alert.id),
            alert.detectedAt.toString(),
            alert.severity.toString(),
            escape(AlertMeasurements.metricKeys(alert).joinToString("; ")),
            escape(measured),
            alert.acknowledged.toString(),
            alert.acknowledgedAt?.toString().orEmpty(),
            escape(AlertMeasurements.note(alert).orEmpty()),
        ).joinToString(",")
    }
}
