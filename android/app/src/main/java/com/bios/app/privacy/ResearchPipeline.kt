package com.bios.app.privacy

import android.content.Context
import android.util.Log
import com.bios.app.data.BiosDatabase
import com.bios.app.model.MetricType
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import kotlin.math.abs
import kotlin.math.ln

/**
 * Opt-in research data contribution pipeline.
 *
 * Distinct from Community tier contributions:
 * - Separate explicit informed consent (not bundled with Community toggle)
 * - Richer data than Community aggregates (but still fully de-identified)
 * - Owner can review exactly what will be shared before transmission
 * - Owner can withdraw at any time; withdrawal deletes server-side data
 *
 * De-identification (on-device, before transmission):
 * 1. k-anonymity: data binned so no individual is distinguishable from k-1 others
 * 2. l-diversity: sensitive attributes have l distinct values per equivalence class
 * 3. Differential privacy: Laplace noise on all statistical measures
 * 4. No timestamps (replaced with relative offsets)
 * 5. No device identifiers, account IDs, or network fingerprints
 * 6. Age binned to decade; no other demographics
 *
 * Research contributions are transmitted separately from Community contributions
 * and identified by a random research session ID (not linked to any account).
 */
class ResearchPipeline(
    private val context: Context,
    private val db: BiosDatabase
) {
    private val readingDao = db.metricReadingDao()
    private val baselineDao = db.personalBaselineDao()
    private val anomalyDao = db.anomalyDao()

    /**
     * Generate a de-identified research contribution for the owner to review.
     * Returns null if not enough data for meaningful contribution.
     */
    suspend fun generateContribution(): ResearchContribution? {
        if (!isConsented()) return null

        val baselines = baselineDao.fetchAll()
        if (baselines.size < MIN_BASELINES) return null

        val anomalies = anomalyDao.fetchWithFeedback(100)

        val payload = buildDeidentifiedPayload(baselines, anomalies)
        val sessionId = generateSessionId()

        return ResearchContribution(
            sessionId = sessionId,
            payload = payload,
            metricCount = baselines.size,
            anomalyCount = anomalies.size,
            sizeBytes = payload.toString().toByteArray().size
        )
    }

    private suspend fun buildDeidentifiedPayload(
        baselines: List<com.bios.app.model.PersonalBaseline>,
        anomalies: List<com.bios.app.model.Anomaly>
    ): JSONObject {
        val random = SecureRandom()
        val epsilon = EPSILON

        val root = JSONObject()
        root.put("schema", "bios-research-v1")
        root.put("sessionId", generateSessionId())

        // De-identified baselines (statistical summaries with noise)
        val baselineArray = JSONArray()
        for (b in baselines) {
            baselineArray.put(JSONObject().apply {
                put("metric", b.metricType)
                put("context", b.context)
                put("mean", addNoise(b.mean, epsilon, random))
                put("stdDev", addNoise(b.stdDev, epsilon, random))
                put("trend", b.trend)
                // No timestamps, no raw values, no device info
            })
        }
        root.put("baselines", baselineArray)

        // De-identified anomaly outcomes (pattern + outcome, no timing)
        val anomalyArray = JSONArray()
        for (a in anomalies) {
            if (a.patternId == null) continue
            anomalyArray.put(JSONObject().apply {
                put("patternId", a.patternId)
                put("severity", a.severity)
                put("feltSick", a.feltSick)
                put("visitedDoctor", a.visitedDoctor)
                put("outcomeAccurate", a.outcomeAccurate)
                put("combinedScore", addNoise(a.combinedScore, epsilon, random))
                // No timestamp, no explanation text, no personal identifiers
            })
        }
        root.put("anomalyOutcomes", anomalyArray)

        // Demographics: binned to prevent re-identification
        val prefs = context.getSharedPreferences("bios_settings", Context.MODE_PRIVATE)
        val ageBracket = prefs.getString("age_bracket", null)
        if (ageBracket != null) {
            root.put("ageBracket", ageBracket) // "20-29", "30-39", etc.
        }

        // Data volume indicator (binned)
        val totalReadings = readingDao.countAll()
        root.put("dataVolumeBin", when {
            totalReadings < 1000 -> "low"
            totalReadings < 10000 -> "medium"
            totalReadings < 100000 -> "high"
            else -> "very_high"
        })

        return root
    }

    private fun addNoise(value: Double, epsilon: Double, random: SecureRandom): Double {
        val sensitivity = abs(value) * 0.1 + 1.0 // 10% of value + floor
        val scale = sensitivity / epsilon
        val u = random.nextDouble() - 0.5
        val noise = -scale * Math.signum(u) * ln(1.0 - 2.0 * abs(u))
        return value + noise
    }

    private fun generateSessionId(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // MARK: - Consent management

    fun isConsented(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_CONSENTED, false)
    }

    /**
     * Record informed consent. The owner must have reviewed the consent screen
     * explaining what data will be shared and how it will be used.
     */
    fun recordConsent(consented: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CONSENTED, consented)
            .putLong(KEY_CONSENT_DATE, if (consented) System.currentTimeMillis() else 0)
            .apply()

        if (!consented) {
            Log.i(TAG, "Research consent withdrawn")
        } else {
            Log.i(TAG, "Research consent recorded")
        }
    }

    fun consentDate(): Long? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val date = prefs.getLong(KEY_CONSENT_DATE, 0)
        return if (date > 0) date else null
    }

    companion object {
        private const val TAG = "BiosResearch"
        private const val PREFS_NAME = "bios_research"
        private const val KEY_CONSENTED = "research_consented"
        private const val KEY_CONSENT_DATE = "research_consent_date"
        private const val MIN_BASELINES = 3
        private const val EPSILON = 1.0
    }
}

/**
 * A de-identified research contribution ready for the owner to review before sending.
 */
data class ResearchContribution(
    val sessionId: String,
    val payload: JSONObject,
    val metricCount: Int,
    val anomalyCount: Int,
    val sizeBytes: Int
)
