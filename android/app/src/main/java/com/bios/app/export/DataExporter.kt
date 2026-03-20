package com.bios.app.export

import android.content.Context
import com.bios.app.data.BiosDatabase
import com.bios.app.model.MetricType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Exports Bios data to JSON format.
 */
class DataExporter(
    private val context: Context,
    private val db: BiosDatabase
) {
    suspend fun exportToFile(): File {
        val json = buildExportJson()

        val formatter = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)
        val filename = "bios_export_${formatter.format(Date())}.json"

        val file = File(context.cacheDir, filename)
        file.writeText(json.toString(2))
        return file
    }

    private suspend fun buildExportJson(): JSONObject {
        val readingDao = db.metricReadingDao()
        val baselineDao = db.personalBaselineDao()
        val anomalyDao = db.anomalyDao()

        val root = JSONObject()
        root.put("exportedAt", System.currentTimeMillis())
        root.put("version", "1.0")

        // Readings
        val readingsArray = JSONArray()
        for (metricType in MetricType.entries) {
            val readings = readingDao.fetch(metricType.key, 0, Long.MAX_VALUE)
            for (r in readings) {
                readingsArray.put(JSONObject().apply {
                    put("id", r.id)
                    put("metricType", r.metricType)
                    put("value", r.value)
                    put("timestamp", r.timestamp)
                    put("sourceId", r.sourceId)
                    put("confidence", r.confidence)
                })
            }
        }
        root.put("readings", readingsArray)

        // Baselines
        val baselinesArray = JSONArray()
        for (b in baselineDao.fetchAll()) {
            baselinesArray.put(JSONObject().apply {
                put("metricType", b.metricType)
                put("mean", b.mean)
                put("stdDev", b.stdDev)
                put("p5", b.p5)
                put("p95", b.p95)
                put("trend", b.trend)
                put("windowDays", b.windowDays)
            })
        }
        root.put("baselines", baselinesArray)

        // Anomalies
        val anomaliesArray = JSONArray()
        for (a in anomalyDao.fetchRecent(1000)) {
            anomaliesArray.put(JSONObject().apply {
                put("id", a.id)
                put("title", a.title)
                put("severity", a.severity)
                put("detectedAt", a.detectedAt)
                put("combinedScore", a.combinedScore)
                put("acknowledged", a.acknowledged)
            })
        }
        root.put("anomalies", anomaliesArray)

        return root
    }
}
