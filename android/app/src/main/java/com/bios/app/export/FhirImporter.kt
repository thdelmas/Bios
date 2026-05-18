package com.bios.app.export

import android.content.Context
import android.net.Uri
import com.bios.app.data.BiomarkerContext
import com.bios.app.data.BiomarkerEntryRepo
import com.bios.app.model.Specimen
import com.bios.contracts.MetricDomain
import com.bios.contracts.MetricType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant

/**
 * Reads a FHIR R4 JSON file (Bundle or single Observation) and routes
 * recognised lab values into [BiomarkerEntryRepo].
 *
 * Lives next to [FhirExporter] and shares its LOINC table — the reverse map
 * is built by scanning [MetricType.entries] through [loincCode], so the
 * export and import sides never drift.
 *
 * Skip-and-report semantics: a malformed Observation never aborts the
 * import. Each rejection is captured in [FhirImportSummary.skipped] with a
 * human-readable reason, so the owner sees what didn't make it through.
 */
object FhirImporter {

    /** LOINC code -> MetricType, derived from the export-side table. */
    val loincToMetricType: Map<String, MetricType> by lazy {
        buildMap {
            for (type in MetricType.entries) {
                val pair = loincCode(type) ?: continue
                put(pair.first, type)
            }
        }
    }

    /**
     * Read a FHIR JSON file from [uri], parse it, and write every accepted
     * biomarker reading via [repo]. Returns a summary of what was accepted,
     * skipped, or unparseable.
     */
    suspend fun importFromUri(
        context: Context,
        uri: Uri,
        repo: BiomarkerEntryRepo
    ): FhirImportSummary = withContext(Dispatchers.IO) {
        val text = readUriToString(context, uri)
            ?: return@withContext FhirImportSummary.fileError("Could not read selected file")

        val summary = parse(text)
        for (reading in summary.accepted) {
            repo.add(
                metricType = reading.metricType,
                value = reading.value,
                timestamp = reading.timestamp,
                context = BiomarkerContext(
                    labName = reading.labName,
                    fasting = reading.fasting,
                    specimen = reading.specimen,
                )
            )
        }
        summary
    }

    /** Pure parse over a FHIR JSON string. No I/O, no DB. */
    fun parse(json: String): FhirImportSummary {
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            return FhirImportSummary.fileError("Not valid JSON: ${e.message}")
        }

        val resourceType = root.optString("resourceType")
        val (observations, bundleSpecimens) = when (resourceType) {
            "Bundle" -> extractBundleObservations(root) to extractBundleSpecimens(root)
            "Observation" -> listOf(root) to emptyMap()
            else -> return FhirImportSummary.fileError(
                "Unsupported resourceType \"$resourceType\" — expected Bundle or Observation"
            )
        }

        val accepted = mutableListOf<AcceptedReading>()
        val skipped = mutableListOf<SkippedObservation>()
        for (obs in observations) {
            when (val outcome = parseObservation(obs, bundleSpecimens)) {
                is ParseOutcome.Accepted -> accepted += outcome.reading
                is ParseOutcome.Skipped -> skipped += outcome.skipped
            }
        }
        return FhirImportSummary(accepted = accepted, skipped = skipped, fileError = null)
    }

    private fun extractBundleObservations(bundle: JSONObject): List<JSONObject> {
        val entries = bundle.optJSONArray("entry") ?: return emptyList()
        val out = mutableListOf<JSONObject>()
        for (i in 0 until entries.length()) {
            val resource = entries.optJSONObject(i)?.optJSONObject("resource") ?: continue
            if (resource.optString("resourceType") == "Observation") out += resource
        }
        return out
    }

    /**
     * Index any `Specimen` resources in the Bundle by id so an Observation's
     * `specimen.reference = "Specimen/<id>"` can resolve back to the type
     * coding. Contained specimens on the Observation itself are handled
     * inline in [parseObservation] and do not need to live here.
     */
    private fun extractBundleSpecimens(bundle: JSONObject): Map<String, JSONObject> {
        val entries = bundle.optJSONArray("entry") ?: return emptyMap()
        val out = mutableMapOf<String, JSONObject>()
        for (i in 0 until entries.length()) {
            val resource = entries.optJSONObject(i)?.optJSONObject("resource") ?: continue
            if (resource.optString("resourceType") != "Specimen") continue
            val id = resource.optString("id").takeIf { it.isNotBlank() } ?: continue
            out[id] = resource
        }
        return out
    }

    private fun parseObservation(
        obs: JSONObject,
        bundleSpecimens: Map<String, JSONObject>,
    ): ParseOutcome {
        val loinc = findLoincCode(obs.optJSONObject("code")?.optJSONArray("coding"))
            ?: return ParseOutcome.Skipped(
                SkippedObservation("No LOINC coding on Observation", loincCode = null)
            )

        val metric = loincToMetricType[loinc]
            ?: return ParseOutcome.Skipped(
                SkippedObservation("LOINC $loinc not mapped to a Bios MetricType", loincCode = loinc)
            )

        if (metric.domain != MetricDomain.BIOMARKER) {
            return ParseOutcome.Skipped(
                SkippedObservation(
                    "${metric.key} is not a biomarker — only biomarkers import through this surface",
                    loincCode = loinc
                )
            )
        }

        val valueQuantity = obs.optJSONObject("valueQuantity")
            ?: return ParseOutcome.Skipped(
                SkippedObservation("Observation has no valueQuantity", loincCode = loinc)
            )
        if (!valueQuantity.has("value")) {
            return ParseOutcome.Skipped(
                SkippedObservation("valueQuantity missing numeric value", loincCode = loinc)
            )
        }
        val value = valueQuantity.optDouble("value", Double.NaN)
        if (value.isNaN() || value <= 0.0) {
            return ParseOutcome.Skipped(
                SkippedObservation("Non-positive or non-numeric value", loincCode = loinc)
            )
        }

        val timestamp = readEffectiveDateTime(obs)
            ?: return ParseOutcome.Skipped(
                SkippedObservation(
                    "Missing or unparseable effectiveDateTime / issued", loincCode = loinc
                )
            )

        val labName = readPerformerDisplay(obs)
        val fasting = readFastingComponent(obs)
        val specimen = readSpecimen(obs, bundleSpecimens)
        return ParseOutcome.Accepted(
            AcceptedReading(metric, value, timestamp, labName, fasting, specimen)
        )
    }

    /**
     * Reads a fasting-status component on the Observation. LOINC `49541-6`
     * "Fasting status - Reported" is what clinical systems emit. We accept
     * three encodings of the answer:
     *   - `valueBoolean` (FHIR-simple, what Bios's own exporter writes)
     *   - `valueCodeableConcept.coding[].code` matching LOINC answer list
     *     `LL1815-1`: `LA33-6` = Yes / `LA32-8` = No (clinical-systems form)
     *   - `valueCodeableConcept.text` matching "yes" / "no" (text fallback)
     * Returns `null` (i.e., "unknown") when the component is absent or
     * encodes an unrecognised answer. Doesn't trip on `LA46-7` (Don't know).
     */
    private fun readFastingComponent(obs: JSONObject): Boolean? {
        val components = obs.optJSONArray("component") ?: return null
        for (i in 0 until components.length()) {
            val component = components.optJSONObject(i) ?: continue
            val coding = component.optJSONObject("code")?.optJSONArray("coding") ?: continue
            if (findLoincCode(coding) != "49541-6") continue
            if (component.has("valueBoolean")) {
                return component.optBoolean("valueBoolean")
            }
            val cc = component.optJSONObject("valueCodeableConcept")
            cc?.optJSONArray("coding")?.let { codings ->
                for (j in 0 until codings.length()) {
                    val c = codings.optJSONObject(j)?.optString("code")
                    when (c) {
                        "LA33-6" -> return true
                        "LA32-8" -> return false
                    }
                }
            }
            cc?.optString("text")?.lowercase()?.let { text ->
                when {
                    text.startsWith("yes") || text == "fasting" -> return true
                    text.startsWith("no") || text == "non-fasting" -> return false
                }
            }
            return null
        }
        return null
    }

    /**
     * Resolves the Observation's `specimen.reference` to a [Specimen] enum.
     * Tries, in order: `contained` resources on the Observation itself, then
     * the Bundle-level Specimen index. Returns `null` when the reference is
     * absent, the target can't be found, or its type doesn't match a Bios
     * specimen enum.
     */
    private fun readSpecimen(
        obs: JSONObject,
        bundleSpecimens: Map<String, JSONObject>,
    ): Specimen? {
        val reference = obs.optJSONObject("specimen")?.optString("reference") ?: return null
        if (reference.isBlank()) return null

        val specimenResource = if (reference.startsWith("#")) {
            findContainedSpecimen(obs, reference.removePrefix("#"))
        } else {
            val id = reference.substringAfter("Specimen/").substringAfter("/")
            bundleSpecimens[id]
        } ?: return null

        return mapSpecimenType(specimenResource.optJSONObject("type"))
    }

    private fun findContainedSpecimen(obs: JSONObject, id: String): JSONObject? {
        val contained = obs.optJSONArray("contained") ?: return null
        for (i in 0 until contained.length()) {
            val r = contained.optJSONObject(i) ?: continue
            if (r.optString("resourceType") == "Specimen" && r.optString("id") == id) return r
        }
        return null
    }

    /**
     * Maps a FHIR `Specimen.type` CodeableConcept to our enum. Prefers SCT
     * coding (the standard); falls back to free-text matching so labs that
     * skip the codings — common in EU public-system exports — still resolve.
     */
    private fun mapSpecimenType(type: JSONObject?): Specimen? {
        if (type == null) return null
        type.optJSONArray("coding")?.let { codings ->
            for (i in 0 until codings.length()) {
                val c = codings.optJSONObject(i) ?: continue
                val system = c.optString("system")
                val code = c.optString("code")
                if (system == "http://snomed.info/sct") {
                    when (code) {
                        "119364003" -> return Specimen.SERUM
                        "119361006" -> return Specimen.PLASMA
                        "258580003" -> return Specimen.WHOLE_BLOOD
                    }
                }
            }
        }
        val text = type.optString("text").lowercase()
        return when {
            "serum" in text -> Specimen.SERUM
            "plasma" in text -> Specimen.PLASMA
            "whole blood" in text -> Specimen.WHOLE_BLOOD
            text.isNotBlank() -> Specimen.OTHER
            else -> null
        }
    }

    private fun readPerformerDisplay(obs: JSONObject): String? {
        val performers = obs.optJSONArray("performer") ?: return null
        for (i in 0 until performers.length()) {
            val entry = performers.optJSONObject(i) ?: continue
            val display = entry.optString("display")
            if (display.isNotBlank()) return display
        }
        return null
    }

    private fun findLoincCode(coding: JSONArray?): String? {
        if (coding == null) return null
        for (i in 0 until coding.length()) {
            val entry = coding.optJSONObject(i) ?: continue
            if (entry.optString("system") == "http://loinc.org") {
                val code = entry.optString("code")
                if (code.isNotBlank()) return code
            }
        }
        return null
    }

    private fun readEffectiveDateTime(obs: JSONObject): Long? {
        val candidates = listOf(
            obs.optString("effectiveDateTime"),
            obs.optString("issued"),
            obs.optJSONObject("effectivePeriod")?.optString("start").orEmpty()
        )
        for (s in candidates) {
            if (s.isBlank()) continue
            try {
                return Instant.parse(s).toEpochMilli()
            } catch (_: Exception) {
                // try next candidate
            }
        }
        return null
    }

    private fun readUriToString(context: Context, uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
    } catch (_: Exception) {
        null
    }

    private sealed class ParseOutcome {
        data class Accepted(val reading: AcceptedReading) : ParseOutcome()
        data class Skipped(val skipped: SkippedObservation) : ParseOutcome()
    }
}

data class AcceptedReading(
    val metricType: MetricType,
    val value: Double,
    val timestamp: Long,
    val labName: String? = null,
    val fasting: Boolean? = null,
    val specimen: Specimen? = null,
)

data class SkippedObservation(
    val reason: String,
    val loincCode: String? = null
)

data class FhirImportSummary(
    val accepted: List<AcceptedReading>,
    val skipped: List<SkippedObservation>,
    val fileError: String?
) {
    val acceptedCount: Int get() = accepted.size
    val skippedCount: Int get() = skipped.size
    val isFileError: Boolean get() = fileError != null

    companion object {
        fun fileError(message: String) = FhirImportSummary(
            accepted = emptyList(),
            skipped = emptyList(),
            fileError = message
        )
    }
}
