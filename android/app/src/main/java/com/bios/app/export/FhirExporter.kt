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
import com.bios.contracts.MetricUnit
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
 * The export is encrypted by default using [EncryptedExporter]'s passphrase scheme.
 * The owner can share with their doctor — no cloud intermediary.
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

        for (source in sourceDao.getAll()) {
            entries.put(bundleEntry(buildDeviceResource(source)))
        }

        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
        for (metricType in MetricType.entries) {
            // Reproductive-domain readings live in the isolated ReproductiveDatabase
            // (separate encryption key, independent wipe, priority destruction on
            // duress PIN). The main-DB-backed exporter must never read them, both
            // because the rows aren't here and — critically — because including
            // them in a default FHIR bundle would collapse the isolation the
            // separate DB exists to provide. A future per-export "include
            // reproductive data" opt-in would query ReproductiveDatabase
            // explicitly; today's default is hard-skip.
            if (metricType.domain == MetricDomain.WOMENS_HEALTH) continue
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

internal fun loincCode(metricType: MetricType): Pair<String, String>? = when (metricType) {
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
    MetricType.BODY_MASS -> "29463-7" to "Body weight"
    MetricType.BODY_FAT_PCT -> "41982-0" to "Percentage of body fat Measured"
    MetricType.LEAN_MASS -> "91557-9" to "Body lean mass Measured by Bioelectrical impedance analysis"
    MetricType.VO2_MAX -> "97090-9" to "Maximum oxygen consumption per unit time per unit body mass"
    MetricType.HBA1C -> "4548-4" to "Hemoglobin A1c/Hemoglobin.total in Blood"
    MetricType.HSCRP -> "30522-7" to "C reactive protein.high sensitivity [Mass/volume] in Serum or Plasma"
    MetricType.TOTAL_CHOLESTEROL -> "2093-3" to "Cholesterol [Mass/volume] in Serum or Plasma"
    MetricType.LDL_CHOLESTEROL -> "2089-1" to "Cholesterol in LDL [Mass/volume] in Serum or Plasma"
    MetricType.HDL_CHOLESTEROL -> "2085-9" to "Cholesterol in HDL [Mass/volume] in Serum or Plasma"
    MetricType.TRIGLYCERIDES -> "2571-8" to "Triglyceride [Mass/volume] in Serum or Plasma"
    MetricType.APO_B -> "1884-6" to "Apolipoprotein B [Mass/volume] in Serum or Plasma"
    MetricType.VITAMIN_D_25OH -> "14635-7" to "25-hydroxyvitamin D2+D3 [Mass/volume] in Serum or Plasma"
    MetricType.TSH -> "3016-3" to "Thyrotropin [Units/volume] in Serum or Plasma"
    MetricType.FREE_T4 -> "3024-7" to "Thyroxine (T4) free [Mass/volume] in Serum or Plasma"
    MetricType.FREE_T3 -> "3051-0" to "Triiodothyronine (T3) free [Mass/volume] in Serum or Plasma"
    MetricType.HEMOGLOBIN -> "718-7" to "Hemoglobin [Mass/volume] in Blood"
    MetricType.HEMATOCRIT -> "4544-3" to "Hematocrit [Volume Fraction] of Blood by Automated count"
    MetricType.WBC -> "6690-2" to "Leukocytes [#/volume] in Blood by Automated count"
    MetricType.RBC -> "789-8" to "Erythrocytes [#/volume] in Blood by Automated count"
    MetricType.PLATELETS -> "777-3" to "Platelets [#/volume] in Blood by Automated count"
    // #24 — glycemic-extended, iron, endocrine, micronutrient.
    // LOINC codes sourced from loinc.org's canonical lab-test catalog; each
    // pairs the code with its official long-form display name so the FHIR
    // round-trip is greppable in a Bundle.
    MetricType.FASTING_GLUCOSE -> "1558-6" to "Fasting glucose [Mass/volume] in Serum or Plasma"
    MetricType.FASTING_INSULIN -> "20448-7" to "Insulin [Units/volume] in Serum or Plasma --fasting"
    MetricType.HOMA_IR -> "81576-7" to "Homeostatic model assessment Insulin resistance Calculated"
    MetricType.FERRITIN -> "2276-4" to "Ferritin [Mass/volume] in Serum or Plasma"
    MetricType.TESTOSTERONE_TOTAL -> "2986-8" to "Testosterone [Mass/volume] in Serum or Plasma"
    MetricType.ESTRADIOL -> "2243-4" to "Estradiol (E2) [Mass/volume] in Serum or Plasma"
    MetricType.CORTISOL -> "2143-6" to "Cortisol [Mass/volume] in Serum or Plasma"
    MetricType.IGF_1 -> "2484-4" to "Insulin-like growth factor-I [Mass/volume] in Serum or Plasma"
    MetricType.VITAMIN_B12 -> "2132-9" to "Cobalamins [Mass/volume] in Serum or Plasma"
    MetricType.FOLATE -> "2284-8" to "Folate [Mass/volume] in Serum or Plasma"
    MetricType.MAGNESIUM -> "2601-3" to "Magnesium [Mass/volume] in Serum or Plasma"
    // #158 — renal / hepatic. eGFR pairs with creatinine (creatinine is the
    // raw input; eGFR is the body-surface-normalised derived value most labs
    // report alongside it). ALT / AST / GGT are the core hepatic panel.
    MetricType.EGFR -> "62238-1" to "Glomerular filtration rate/1.73 sq M.predicted by Creatinine-based formula (CKD-EPI 2021)"
    MetricType.CREATININE -> "2160-0" to "Creatinine [Mass/volume] in Serum or Plasma"
    MetricType.ALT -> "1742-6" to "Alanine aminotransferase [Enzymatic activity/volume] in Serum or Plasma"
    MetricType.AST -> "1920-8" to "Aspartate aminotransferase [Enzymatic activity/volume] in Serum or Plasma"
    MetricType.GGT -> "2324-2" to "Gamma glutamyl transferase [Enzymatic activity/volume] in Serum or Plasma"
    // #157 — apnea-hypopnea index. Universal sleep-medicine measure;
    // 90562-0 is the standard PSG-derived LOINC ("Sleep apnea hypopnea
    // index"). Wearable-derived passthrough uses the same code.
    MetricType.AHI -> "90562-0" to "Sleep apnea hypopnea index"
    else -> null
}

internal fun ucumCode(metricType: MetricType): String = when (metricType.unit) {
    MetricUnit.BPM -> "/min"
    MetricUnit.MILLISECONDS -> "ms"
    MetricUnit.PERCENT -> "%"
    MetricUnit.MMHG -> "mm[Hg]"
    MetricUnit.BREATHS_PER_MIN -> "/min"
    MetricUnit.CELSIUS -> "Cel"
    MetricUnit.DELTA_CELSIUS -> "Cel"
    MetricUnit.SECONDS -> "s"
    MetricUnit.COUNT -> "{count}"
    MetricUnit.KCAL -> "kcal"
    MetricUnit.KILOGRAMS -> "kg"
    MetricUnit.MG_PER_DL -> "mg/dL"
    MetricUnit.MG_PER_L -> "mg/L"
    MetricUnit.NG_PER_ML -> "ng/mL"
    MetricUnit.NG_PER_DL -> "ng/dL"
    MetricUnit.UG_PER_DL -> "ug/dL"
    MetricUnit.PG_PER_ML -> "pg/mL"
    MetricUnit.MIU_PER_L -> "m[IU]/L"
    MetricUnit.MICRO_IU_PER_ML -> "u[IU]/mL"
    MetricUnit.G_PER_DL -> "g/dL"
    MetricUnit.GIGA_PER_L -> "10*9/L"
    MetricUnit.TERA_PER_L -> "10*12/L"
    MetricUnit.SCORE -> "{score}"
    MetricUnit.CATEGORY -> "{category}"
    MetricUnit.EVENT -> "{event}"
    MetricUnit.LUX -> "lx"
    MetricUnit.YEARS -> "a"
    MetricUnit.MS_SQUARED -> "ms2"
    MetricUnit.ML_PER_KG_MIN -> "mL/(kg.min)"
    MetricUnit.UG_PER_M3 -> "ug/m3"
    MetricUnit.PPM -> "[ppm]"
    MetricUnit.PPB -> "[ppb]"
    MetricUnit.LITERS_PER_MIN -> "L/min"
    MetricUnit.LITERS -> "L"
    MetricUnit.MILLIGRAMS -> "mg"
    MetricUnit.GRAMS -> "g"
    MetricUnit.U_PER_L -> "U/L"
    MetricUnit.ML_PER_MIN_PER_173 -> "mL/min/{1.73_m2}"
    MetricUnit.CENTIMETERS -> "cm"
    MetricUnit.KG_PER_M2 -> "kg/m2"
}

internal fun formatInstant(instant: Instant): String =
    instant.atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

internal fun formatEpochMillis(millis: Long): String =
    formatInstant(Instant.ofEpochMilli(millis))
