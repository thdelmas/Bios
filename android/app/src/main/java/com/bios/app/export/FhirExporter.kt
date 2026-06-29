package com.bios.app.export

import android.content.Context
import com.bios.app.data.BiomarkerContext
import com.bios.app.data.BiomarkerEntryRepo
import com.bios.app.data.BiosDatabase
import com.bios.app.model.Anomaly
import com.bios.app.model.DataSource
import com.bios.app.model.MetricReading
import com.bios.app.model.Specimen
import com.bios.contracts.MetricDomain
import com.bios.contracts.MetricType
import com.bios.app.model.PersonalBaseline
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Exports Bios health data as a FHIR R4 Bundle.
 *
 * Mapping:
 *   MetricReading → Observation resource
 *   DataSource    → Device resource
 *   Anomaly       → DetectedIssue resource
 *   PersonalBaseline → Observation (with interpretation = "baseline")
 *
 * The bundle is produced as plaintext so a clinician can read it directly. The
 * owner can opt into password protection at export time, which wraps the file
 * in a standard AES-256 zip ([EncryptedZipExporter]) any tool can open with the
 * passphrase — no cloud intermediary either way.
 *
 * FHIR R4 spec: https://hl7.org/fhir/R4/
 *
 * Resource-building helpers are top-level `internal` functions so the unit
 * test can validate each resource type against the FHIR R4 JSON Schema
 * without needing a Context or Room database.
 */
class FhirExporter(
    private val context: Context,
    private val db: BiosDatabase
) {
    private val readingDao = db.metricReadingDao()
    private val sourceDao = db.dataSourceDao()
    private val anomalyDao = db.anomalyDao()
    private val baselineDao = db.personalBaselineDao()
    private val payloadDao = db.eventPayloadFieldDao()
    private val fastStrokeDao = db.fastStrokeEventDao()

    companion object {
        /**
         * The MetricTypes the default FHIR export enumerates: everything except
         * the WOMENS_HEALTH (reproductive) domain.
         *
         * Reproductive-domain readings live in the isolated ReproductiveDatabase
         * (separate SQLCipher key, independent wipe, priority destruction on a
         * duress PIN). The main-DB-backed exporter must never read them — the
         * rows aren't here, and including them in a default bundle would collapse
         * the isolation that the separate DB exists to provide. A future
         * per-export "include reproductive data" opt-in would query
         * ReproductiveDatabase explicitly; today's default is a hard skip.
         *
         * Shared with the test boundary so a new reproductive key can't be added
         * without this exclusion (or a conscious opt-in) being noticed.
         */
        internal fun defaultExportMetricTypes(): List<MetricType> =
            MetricType.entries.filter { it.domain != MetricDomain.WOMENS_HEALTH }
    }

    /**
     * Export all Bios data as a FHIR R4 Bundle (JSON).
     * Returns the file path. The file is plaintext; the export UI can wrap it in
     * a password-protected AES-256 zip ([EncryptedZipExporter]) on request.
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

        for (source in sourceDao.getAll()) {
            entries.put(bundleEntry(buildDeviceResource(source)))
        }

        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
        // WOMENS_HEALTH (reproductive) keys are excluded — see
        // [defaultExportMetricTypes] for why the main-DB exporter must never
        // touch the isolated ReproductiveDatabase.
        for (metricType in defaultExportMetricTypes()) {
            val isBiomarker = metricType.domain == MetricDomain.BIOMARKER
            val readings = readingDao.fetch(metricType.key, thirtyDaysAgo, Long.MAX_VALUE)
            for (reading in readings.take(500)) {
                // Biomarker readings carry optional provenance (lab name,
                // fasting status, specimen type) in the event-payload sidecar.
                // The owner-recall note in MetricReading.note is intentionally
                // *not* exported — it's owner-recall only, never shared by
                // default. See issue #105.
                val context = if (isBiomarker) {
                    val rows = payloadDao.fetchForReading(reading.id)
                    BiomarkerEntryRepo.rowsToContext(rows, note = null)
                } else null
                entries.put(bundleEntry(buildObservationResource(reading, metricType, context)))
            }
        }

        for (baseline in baselineDao.fetchAll()) {
            entries.put(bundleEntry(buildBaselineObservation(baseline)))
        }

        for (anomaly in anomalyDao.fetchAll()) {
            entries.put(bundleEntry(buildDetectedIssueResource(anomaly)))
        }

        // Owner-recorded FAST stroke-recognition events (#207). Only positive
        // screens are emitted as flag observations — the negative checks stay
        // in the local diary but don't add noise to the clinician bundle.
        for (event in fastStrokeDao.fetchAll()) {
            if (event.isPositive) {
                entries.put(bundleEntry(buildFastStrokeFlagResource(event)))
            }
        }

        return buildBundleResource(entries)
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
}

// --- Pure FHIR R4 resource builders ---------------------------------------
// These are top-level `internal` so tests can exercise them without a Context.

internal fun buildBundleResource(entries: JSONArray): JSONObject = JSONObject().apply {
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

internal fun bundleEntry(resource: JSONObject): JSONObject = JSONObject().apply {
    put("fullUrl", "urn:uuid:${resource.getString("id")}")
    put("resource", resource)
}

internal fun buildDeviceResource(source: DataSource): JSONObject = JSONObject().apply {
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

internal fun buildObservationResource(
    reading: MetricReading,
    metricType: MetricType,
    context: BiomarkerContext? = null,
): JSONObject = JSONObject().apply {
    put("resourceType", "Observation")
    put("id", reading.id)
    put("status", "final")
    put("code", JSONObject().apply {
        put("coding", JSONArray().put(JSONObject().apply {
            put("system", "https://bios.health/metrics")
            put("code", metricType.key)
            put("display", metricType.readableName)
        }))
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
        // FHIR R4 rejects empty-string `unit`; omit for annotation-only units
        // (MetricUnit.SCORE/COUNT/CATEGORY) and let the UCUM `code` carry it.
        if (metricType.unit.symbol.isNotEmpty()) {
            put("unit", metricType.unit.symbol)
        }
        put("system", "http://unitsofmeasure.org")
        put("code", ucumCode(metricType))
    })
    put("device", JSONObject().apply {
        put("reference", "Device/${reading.sourceId}")
    })

    // Biomarker provenance: emit when context is non-null and any of the
    // shareable fields is populated. Owner-recall note is intentionally not
    // emitted (BiomarkerContext.note is engine-irrelevant and stays local).
    // Source URI is also not emitted — it's a local content://, useless to
    // a remote reader and trades nothing for a privacy risk.
    if (context != null) {
        context.labName?.takeIf { it.isNotBlank() }?.let { lab ->
            put("performer", JSONArray().put(JSONObject().apply {
                put("display", lab)
            }))
        }
        context.fasting?.let { fasting ->
            // LOINC 49541-6 "Fasting status - Reported" on a component, with
            // the boolean as valueBoolean — the simplest FHIR-R4 encoding that
            // round-trips cleanly with Bios's Boolean? storage. Clinical
            // systems that emit valueCodeableConcept with LA33-6 / LA32-8 are
            // also parsed by FhirImporter.
            val components = optJSONArray("component") ?: JSONArray().also { put("component", it) }
            components.put(JSONObject().apply {
                put("code", JSONObject().apply {
                    put("coding", JSONArray().put(JSONObject().apply {
                        put("system", "http://loinc.org")
                        put("code", "49541-6")
                        put("display", "Fasting status - Reported")
                    }))
                })
                put("valueBoolean", fasting)
            })
        }
        context.specimen?.let { specimen ->
            val containedId = "specimen-${reading.id}"
            val contained = optJSONArray("contained") ?: JSONArray().also { put("contained", it) }
            contained.put(buildSpecimenResource(containedId, specimen))
            put("specimen", JSONObject().apply {
                put("reference", "#$containedId")
            })
        }
    }
}

/**
 * Builds a contained Specimen resource that names a [Specimen] enum value
 * using SNOMED CT concept codes — what clinical systems index against.
 * `OTHER` falls back to text-only so the receiving system at least gets the
 * Bios label rather than nothing.
 */
internal fun buildSpecimenResource(id: String, specimen: Specimen): JSONObject = JSONObject().apply {
    put("resourceType", "Specimen")
    put("id", id)
    put("status", "available")
    put("type", JSONObject().apply {
        when (specimen) {
            Specimen.SERUM -> {
                put("coding", JSONArray().put(JSONObject().apply {
                    put("system", "http://snomed.info/sct")
                    put("code", "119364003")
                    put("display", "Serum specimen")
                }))
                put("text", specimen.readable)
            }
            Specimen.PLASMA -> {
                put("coding", JSONArray().put(JSONObject().apply {
                    put("system", "http://snomed.info/sct")
                    put("code", "119361006")
                    put("display", "Plasma specimen")
                }))
                put("text", specimen.readable)
            }
            Specimen.WHOLE_BLOOD -> {
                put("coding", JSONArray().put(JSONObject().apply {
                    put("system", "http://snomed.info/sct")
                    put("code", "258580003")
                    put("display", "Whole blood specimen")
                }))
                put("text", specimen.readable)
            }
            Specimen.OTHER -> put("text", specimen.readable)
        }
    })
}

internal fun buildBaselineObservation(baseline: PersonalBaseline): JSONObject = JSONObject().apply {
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

internal fun buildDetectedIssueResource(anomaly: Anomaly): JSONObject = JSONObject().apply {
    put("resourceType", "DetectedIssue")
    put("id", anomaly.id)
    put("status", if (anomaly.acknowledged) "final" else "preliminary")
    put("severity", when (anomaly.severity) {
        3 -> "high"
        2 -> "moderate"
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

internal fun formatInstant(instant: Instant): String = instant.atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
internal fun formatEpochMillis(millis: Long): String = formatInstant(Instant.ofEpochMilli(millis))
