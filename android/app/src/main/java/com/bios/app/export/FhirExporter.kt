package com.bios.app.export

import android.content.Context
import com.bios.app.data.BiosDatabase
import com.bios.app.model.MetricType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Exports Bios health data as a FHIR R4 Bundle.
 *
 * Mapping:
 *   MetricReading → Observation resource
 *   DataSource    → Device resource
 *   Anomaly       → DetectedIssue resource
 *   PersonalBaseline → Observation (with interpretation = "baseline")
 *
 * The export is encrypted by default using [EncryptedExporter]'s passphrase scheme.
 * The owner can share with their doctor — no cloud intermediary.
 *
 * FHIR R4 spec: https://hl7.org/fhir/R4/
 */
class FhirExporter(
    private val context: Context,
    private val db: BiosDatabase
) {
    private val readingDao = db.metricReadingDao()
    private val sourceDao = db.dataSourceDao()
    private val anomalyDao = db.anomalyDao()
    private val baselineDao = db.personalBaselineDao()

    /**
     * Export all Bios data as a FHIR R4 Bundle (JSON).
     * Returns the file path. The file is plaintext — encrypt with [EncryptedExporter] before sharing.
     */
    suspend fun exportToFhirBundle(): File {
        val bundle = buildBundle()
        val filename = "bios_fhir_${timestamp()}.json"
        val file = File(context.cacheDir, filename)
        file.writeText(bundle.toString(2))
        return file
    }

    private suspend fun buildBundle(): JSONObject {
        val entries = JSONArray()

        // Device resources (data sources)
        for (source in sourceDao.getAll()) {
            entries.put(bundleEntry(buildDeviceResource(source)))
        }

        // Observation resources (readings — last 30 days for manageable size)
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
        for (metricType in MetricType.entries) {
            val readings = readingDao.fetch(metricType.key, thirtyDaysAgo, Long.MAX_VALUE)
            for (reading in readings.take(500)) { // Cap per metric to avoid huge bundles
                entries.put(bundleEntry(buildObservationResource(reading, metricType)))
            }
        }

        // Baseline Observations
        for (baseline in baselineDao.fetchAll()) {
            entries.put(bundleEntry(buildBaselineObservation(baseline)))
        }

        // DetectedIssue resources (anomalies)
        for (anomaly in anomalyDao.fetchAll()) {
            entries.put(bundleEntry(buildDetectedIssueResource(anomaly)))
        }

        return JSONObject().apply {
            put("resourceType", "Bundle")
            put("type", "collection")
            put("timestamp", formatInstant(Instant.now()))
            put("meta", JSONObject().apply {
                put("profile", JSONArray().put("https://bios.health/fhir/export-bundle"))
            })
            put("identifier", JSONObject().apply {
                put("system", "https://bios.health/export")
                put("value", UUID.randomUUID().toString())
            })
            put("entry", entries)
        }
    }

    private fun buildDeviceResource(source: com.bios.app.model.DataSource): JSONObject {
        return JSONObject().apply {
            put("resourceType", "Device")
            put("id", source.id)
            put("status", "active")
            put("deviceName", JSONArray().put(JSONObject().apply {
                put("name", source.deviceName ?: "Unknown")
                put("type", "user-friendly-name")
            }))
            put("type", JSONObject().apply {
                put("text", source.sourceType)
            })
            if (source.deviceModel != null) {
                put("modelNumber", source.deviceModel)
            }
        }
    }

    private fun buildObservationResource(
        reading: com.bios.app.model.MetricReading,
        metricType: MetricType
    ): JSONObject {
        return JSONObject().apply {
            put("resourceType", "Observation")
            put("id", reading.id)
            put("status", "final")
            put("code", JSONObject().apply {
                put("coding", JSONArray().put(JSONObject().apply {
                    put("system", "https://bios.health/metrics")
                    put("code", metricType.key)
                    put("display", metricType.readableName)
                }))
                // Map to LOINC where possible
                loincCode(metricType)?.let { (code, display) ->
                    getJSONArray("coding").put(JSONObject().apply {
                        put("system", "http://loinc.org")
                        put("code", code)
                        put("display", display)
                    })
                }
            })
            put("effectiveDateTime", formatEpochMillis(reading.timestamp))
            put("valueQuantity", JSONObject().apply {
                put("value", reading.value)
                put("unit", metricType.unit.symbol)
                put("system", "http://unitsofmeasure.org")
                put("code", ucumCode(metricType))
            })
            put("device", JSONObject().apply {
                put("reference", "Device/${reading.sourceId}")
            })
        }
    }

    private fun buildBaselineObservation(
        baseline: com.bios.app.model.PersonalBaseline
    ): JSONObject {
        return JSONObject().apply {
            put("resourceType", "Observation")
            put("id", baseline.id)
            put("status", "final")
            put("code", JSONObject().apply {
                put("coding", JSONArray().put(JSONObject().apply {
                    put("system", "https://bios.health/baselines")
                    put("code", baseline.metricType)
                    put("display", "Personal baseline: ${baseline.metricType}")
                }))
            })
            put("effectiveDateTime", formatEpochMillis(baseline.computedAt))
            put("valueQuantity", JSONObject().apply {
                put("value", baseline.mean)
            })
            put("referenceRange", JSONArray().put(JSONObject().apply {
                put("low", JSONObject().apply { put("value", baseline.p5) })
                put("high", JSONObject().apply { put("value", baseline.p95) })
                put("text", "Personal baseline p5-p95 (${baseline.windowDays}-day window)")
            }))
            put("interpretation", JSONArray().put(JSONObject().apply {
                put("coding", JSONArray().put(JSONObject().apply {
                    put("system", "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation")
                    put("code", "N")
                    put("display", "Normal (personal baseline)")
                }))
            }))
        }
    }

    private fun buildDetectedIssueResource(
        anomaly: com.bios.app.model.Anomaly
    ): JSONObject {
        return JSONObject().apply {
            put("resourceType", "DetectedIssue")
            put("id", anomaly.id)
            put("status", if (anomaly.acknowledged) "final" else "preliminary")
            put("severity", when (anomaly.severity) {
                3 -> "high"
                2 -> "moderate"
                1 -> "low"
                else -> "low"
            })
            put("code", JSONObject().apply {
                put("text", anomaly.title)
            })
            put("detail", anomaly.explanation)
            put("identifiedDateTime", formatEpochMillis(anomaly.detectedAt))
            if (anomaly.patternId != null) {
                put("extension", JSONArray().put(JSONObject().apply {
                    put("url", "https://bios.health/fhir/pattern-id")
                    put("valueString", anomaly.patternId)
                }))
            }
        }
    }

    private fun bundleEntry(resource: JSONObject): JSONObject {
        return JSONObject().apply {
            put("fullUrl", "urn:uuid:${resource.getString("id")}")
            put("resource", resource)
        }
    }

    private fun loincCode(metricType: MetricType): Pair<String, String>? {
        return when (metricType) {
            MetricType.HEART_RATE -> "8867-4" to "Heart rate"
            MetricType.HEART_RATE_VARIABILITY -> "80404-7" to "R-R interval.standard deviation"
            MetricType.RESTING_HEART_RATE -> "40443-4" to "Heart rate - resting"
            MetricType.BLOOD_OXYGEN -> "2708-6" to "Oxygen saturation in Arterial blood"
            MetricType.RESPIRATORY_RATE -> "9279-1" to "Respiratory rate"
            MetricType.BLOOD_PRESSURE_SYSTOLIC -> "8480-6" to "Systolic blood pressure"
            MetricType.BLOOD_PRESSURE_DIASTOLIC -> "8462-4" to "Diastolic blood pressure"
            MetricType.BLOOD_GLUCOSE -> "2345-7" to "Glucose [Mass/volume] in Serum or Plasma"
            MetricType.STEPS -> "55423-8" to "Number of steps in unspecified time Pedometer"
            MetricType.SLEEP_DURATION -> "93832-4" to "Sleep duration"
            MetricType.SKIN_TEMPERATURE -> "8310-5" to "Body temperature"
            MetricType.BASAL_BODY_TEMPERATURE -> "8332-9" to "Oral temperature"
            else -> null
        }
    }

    private fun ucumCode(metricType: MetricType): String {
        return when (metricType.unit) {
            com.bios.app.model.MetricUnit.BPM -> "/min"
            com.bios.app.model.MetricUnit.MILLISECONDS -> "ms"
            com.bios.app.model.MetricUnit.PERCENT -> "%"
            com.bios.app.model.MetricUnit.MMHG -> "mm[Hg]"
            com.bios.app.model.MetricUnit.BREATHS_PER_MIN -> "/min"
            com.bios.app.model.MetricUnit.CELSIUS -> "Cel"
            com.bios.app.model.MetricUnit.DELTA_CELSIUS -> "Cel"
            com.bios.app.model.MetricUnit.SECONDS -> "s"
            com.bios.app.model.MetricUnit.COUNT -> "{count}"
            com.bios.app.model.MetricUnit.KCAL -> "kcal"
            com.bios.app.model.MetricUnit.MG_PER_DL -> "mg/dL"
            com.bios.app.model.MetricUnit.SCORE -> "{score}"
            com.bios.app.model.MetricUnit.CATEGORY -> "{category}"
        }
    }

    private fun formatInstant(instant: Instant): String =
        instant.atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    private fun formatEpochMillis(millis: Long): String =
        formatInstant(Instant.ofEpochMilli(millis))

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
}
