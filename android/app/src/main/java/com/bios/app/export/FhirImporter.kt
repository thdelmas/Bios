package com.bios.app.export

import android.content.Context
import android.net.Uri
import com.bios.app.data.BiomarkerEntryRepo
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
            repo.add(reading.metricType, reading.value, reading.timestamp)
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
        val observations = when (resourceType) {
            "Bundle" -> extractBundleObservations(root)
            "Observation" -> listOf(root)
            else -> return FhirImportSummary.fileError(
                "Unsupported resourceType \"$resourceType\" — expected Bundle or Observation"
            )
        }

        val accepted = mutableListOf<AcceptedReading>()
        val skipped = mutableListOf<SkippedObservation>()
        for (obs in observations) {
            when (val outcome = parseObservation(obs)) {
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

    private fun parseObservation(obs: JSONObject): ParseOutcome {
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

        return ParseOutcome.Accepted(AcceptedReading(metric, value, timestamp))
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
    val timestamp: Long
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
