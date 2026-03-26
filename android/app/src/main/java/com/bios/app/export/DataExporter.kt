package com.bios.app.export

import android.content.Context
import com.bios.app.data.BiosDatabase
import com.bios.app.model.MetricType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exports Bios data in open formats.
 *
 * Supported formats:
 * - JSON: Single file with full schema and all data tables
 * - CSV ZIP: One CSV per table (readings, baselines, anomalies, aggregates, sources)
 *
 * The user owns their data — exports are complete and unredacted.
 */
class DataExporter(
    private val context: Context,
    private val db: BiosDatabase
) {
    private val readingDao = db.metricReadingDao()
    private val baselineDao = db.personalBaselineDao()
    private val anomalyDao = db.anomalyDao()
    private val aggregateDao = db.computedAggregateDao()
    private val sourceDao = db.dataSourceDao()
    private val healthEventDao = db.healthEventDao()
    private val actionItemDao = db.actionItemDao()

    // MARK: - JSON export

    suspend fun exportToFile(): File {
        val json = buildExportJson()
        val filename = "bios_export_${timestamp()}.json"
        val file = File(context.cacheDir, filename)
        file.writeText(json.toString(2))
        return file
    }

    private suspend fun buildExportJson(): JSONObject {
        val root = JSONObject()
        root.put("schema", SCHEMA_ID)
        root.put("schemaVersion", SCHEMA_VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("exportFormat", "bios-open-health-v1")

        root.put("dataSources", buildSourcesJson())
        root.put("readings", buildReadingsJson())
        root.put("baselines", buildBaselinesJson())
        root.put("aggregates", buildAggregatesJson())
        root.put("anomalies", buildAnomaliesJson())
        root.put("healthEvents", buildHealthEventsJson())
        root.put("actionItems", buildActionItemsJson())

        return root
    }

    private suspend fun buildSourcesJson(): JSONArray {
        val array = JSONArray()
        for (s in sourceDao.getAll()) {
            array.put(JSONObject().apply {
                put("id", s.id)
                put("sourceType", s.sourceType)
                put("deviceName", s.deviceName)
                put("deviceModel", s.deviceModel)
                put("sensorType", s.sensorType)
                put("connectedAt", s.connectedAt)
                put("lastSyncAt", s.lastSyncAt)
            })
        }
        return array
    }

    private suspend fun buildReadingsJson(): JSONArray {
        val array = JSONArray()
        for (metricType in MetricType.entries) {
            val readings = readingDao.fetch(metricType.key, 0, Long.MAX_VALUE)
            for (r in readings) {
                array.put(JSONObject().apply {
                    put("id", r.id)
                    put("metricType", r.metricType)
                    put("value", r.value)
                    put("timestamp", r.timestamp)
                    put("durationSec", r.durationSec)
                    put("sourceId", r.sourceId)
                    put("confidence", r.confidence)
                    put("isPrimary", r.isPrimary)
                    put("createdAt", r.createdAt)
                })
            }
        }
        return array
    }

    private suspend fun buildBaselinesJson(): JSONArray {
        val array = JSONArray()
        for (b in baselineDao.fetchAll()) {
            array.put(JSONObject().apply {
                put("id", b.id)
                put("metricType", b.metricType)
                put("context", b.context)
                put("windowDays", b.windowDays)
                put("computedAt", b.computedAt)
                put("mean", b.mean)
                put("stdDev", b.stdDev)
                put("p5", b.p5)
                put("p95", b.p95)
                put("trend", b.trend)
                put("trendSlope", b.trendSlope)
            })
        }
        return array
    }

    private suspend fun buildAggregatesJson(): JSONArray {
        val array = JSONArray()
        for (a in aggregateDao.fetchAll()) {
            array.put(JSONObject().apply {
                put("id", a.id)
                put("metricType", a.metricType)
                put("period", a.period)
                put("periodStart", a.periodStart)
                put("mean", a.mean)
                put("median", a.median)
                put("min", a.min)
                put("max", a.max)
                put("stdDev", a.stdDev)
                put("sampleCount", a.sampleCount)
            })
        }
        return array
    }

    private suspend fun buildAnomaliesJson(): JSONArray {
        val array = JSONArray()
        for (a in anomalyDao.fetchAll()) {
            array.put(JSONObject().apply {
                put("id", a.id)
                put("detectedAt", a.detectedAt)
                put("metricTypes", a.metricTypes)
                put("deviationScores", a.deviationScores)
                put("combinedScore", a.combinedScore)
                put("patternId", a.patternId)
                put("severity", a.severity)
                put("title", a.title)
                put("explanation", a.explanation)
                put("suggestedAction", a.suggestedAction)
                put("acknowledged", a.acknowledged)
                put("acknowledgedAt", a.acknowledgedAt)
                put("feltSick", a.feltSick)
                put("visitedDoctor", a.visitedDoctor)
                put("diagnosis", a.diagnosis)
                put("symptoms", a.symptoms)
                put("notes", a.notes)
                put("outcomeAccurate", a.outcomeAccurate)
            })
        }
        return array
    }

    private suspend fun buildHealthEventsJson(): JSONArray {
        val array = JSONArray()
        for (e in healthEventDao.fetchAll()) {
            array.put(JSONObject().apply {
                put("id", e.id)
                put("type", e.type)
                put("status", e.status)
                put("title", e.title)
                put("description", e.description)
                put("createdAt", e.createdAt)
                put("updatedAt", e.updatedAt)
                put("anomalyId", e.anomalyId)
                put("parentEventId", e.parentEventId)
            })
        }
        return array
    }

    private suspend fun buildActionItemsJson(): JSONArray {
        val array = JSONArray()
        for (e in healthEventDao.fetchAll()) {
            for (a in actionItemDao.fetchByEventId(e.id)) {
                array.put(JSONObject().apply {
                    put("id", a.id)
                    put("healthEventId", a.healthEventId)
                    put("description", a.description)
                    put("dueAt", a.dueAt)
                    put("completed", a.completed)
                    put("completedAt", a.completedAt)
                    put("createdAt", a.createdAt)
                })
            }
        }
        return array
    }

    // MARK: - CSV ZIP export

    suspend fun exportToCsvZip(): File {
        val filename = "bios_export_${timestamp()}.zip"
        val file = File(context.cacheDir, filename)

        ZipOutputStream(FileOutputStream(file)).use { zip ->
            writeCsvEntry(zip, "readings.csv", readingsCsvRows())
            writeCsvEntry(zip, "baselines.csv", baselinesCsvRows())
            writeCsvEntry(zip, "anomalies.csv", anomaliesCsvRows())
            writeCsvEntry(zip, "aggregates.csv", aggregatesCsvRows())
            writeCsvEntry(zip, "data_sources.csv", sourcesCsvRows())
            writeCsvEntry(zip, "health_events.csv", healthEventsCsvRows())
            writeCsvEntry(zip, "action_items.csv", actionItemsCsvRows())
        }

        return file
    }

    private suspend fun readingsCsvRows(): List<String> {
        val header = "id,metricType,value,timestamp,durationSec,sourceId,confidence,isPrimary,createdAt"
        val rows = mutableListOf(header)
        for (mt in MetricType.entries) {
            for (r in readingDao.fetch(mt.key, 0, Long.MAX_VALUE)) {
                rows += "${csvEscape(r.id)},${r.metricType},${r.value},${r.timestamp}," +
                    "${r.durationSec ?: ""},${csvEscape(r.sourceId)},${r.confidence}," +
                    "${r.isPrimary},${r.createdAt}"
            }
        }
        return rows
    }

    private suspend fun baselinesCsvRows(): List<String> {
        val header = "id,metricType,context,windowDays,computedAt,mean,stdDev,p5,p95,trend,trendSlope"
        val rows = mutableListOf(header)
        for (b in baselineDao.fetchAll()) {
            rows += "${csvEscape(b.id)},${b.metricType},${b.context},${b.windowDays}," +
                "${b.computedAt},${b.mean},${b.stdDev},${b.p5},${b.p95},${b.trend},${b.trendSlope}"
        }
        return rows
    }

    private suspend fun anomaliesCsvRows(): List<String> {
        val header = "id,detectedAt,severity,title,combinedScore,patternId," +
            "acknowledged,feltSick,visitedDoctor,diagnosis,outcomeAccurate"
        val rows = mutableListOf(header)
        for (a in anomalyDao.fetchAll()) {
            rows += "${csvEscape(a.id)},${a.detectedAt},${a.severity}," +
                "${csvEscape(a.title)},${a.combinedScore},${csvEscape(a.patternId ?: "")}," +
                "${a.acknowledged},${a.feltSick ?: ""},${a.visitedDoctor ?: ""}," +
                "${csvEscape(a.diagnosis ?: "")},${a.outcomeAccurate ?: ""}"
        }
        return rows
    }

    private suspend fun aggregatesCsvRows(): List<String> {
        val header = "id,metricType,period,periodStart,mean,median,min,max,stdDev,sampleCount"
        val rows = mutableListOf(header)
        for (a in aggregateDao.fetchAll()) {
            rows += "${csvEscape(a.id)},${a.metricType},${a.period},${a.periodStart}," +
                "${a.mean},${a.median},${a.min},${a.max},${a.stdDev},${a.sampleCount}"
        }
        return rows
    }

    private suspend fun sourcesCsvRows(): List<String> {
        val header = "id,sourceType,deviceName,deviceModel,sensorType,connectedAt,lastSyncAt"
        val rows = mutableListOf(header)
        for (s in sourceDao.getAll()) {
            rows += "${csvEscape(s.id)},${s.sourceType},${csvEscape(s.deviceName ?: "")}," +
                "${csvEscape(s.deviceModel ?: "")},${s.sensorType},${s.connectedAt},${s.lastSyncAt}"
        }
        return rows
    }

    private suspend fun healthEventsCsvRows(): List<String> {
        val header = "id,type,status,title,description,createdAt,updatedAt,anomalyId,parentEventId"
        val rows = mutableListOf(header)
        for (e in healthEventDao.fetchAll()) {
            rows += "${csvEscape(e.id)},${e.type},${e.status},${csvEscape(e.title)}," +
                "${csvEscape(e.description ?: "")},${e.createdAt},${e.updatedAt}," +
                "${csvEscape(e.anomalyId ?: "")},${csvEscape(e.parentEventId ?: "")}"
        }
        return rows
    }

    private suspend fun actionItemsCsvRows(): List<String> {
        val header = "id,healthEventId,description,dueAt,completed,completedAt,createdAt"
        val rows = mutableListOf(header)
        for (e in healthEventDao.fetchAll()) {
            for (a in actionItemDao.fetchByEventId(e.id)) {
                rows += "${csvEscape(a.id)},${csvEscape(a.healthEventId)}," +
                    "${csvEscape(a.description)},${a.dueAt ?: ""},${a.completed}," +
                    "${a.completedAt ?: ""},${a.createdAt}"
            }
        }
        return rows
    }

    private fun writeCsvEntry(zip: ZipOutputStream, name: String, rows: List<String>) {
        zip.putNextEntry(ZipEntry(name))
        val writer = OutputStreamWriter(zip, Charsets.UTF_8)
        for (row in rows) {
            writer.write(row)
            writer.write("\n")
        }
        writer.flush()
        zip.closeEntry()
    }

    // MARK: - Helpers

    private fun csvEscape(value: String): String {
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            return "\"${value.replace("\"", "\"\"")}\""
        }
        return value
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())

    companion object {
        const val SCHEMA_ID = "bios-open-health"
        const val SCHEMA_VERSION = "1.2"
    }
}
