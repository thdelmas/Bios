package com.bios.app.privacy

import com.bios.app.data.BiosDatabase
import com.bios.app.engine.DetectionAccuracyTracker
import com.bios.app.engine.PatternAccuracy
import com.bios.app.model.UserFeedback
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import kotlin.math.abs
import kotlin.math.ln

/**
 * Aggregates owner feedback into anonymous contributions for the Community tier.
 *
 * Two data streams, both anonymized on-device before transmission:
 *
 * 1. **Pattern accuracy**: How accurate are condition patterns across the user base?
 *    - Per-pattern: total alerts, TP, FP, precision (with DP noise)
 *    - No timestamps, no raw scores, no identifiers
 *    - Tells the team which patterns need threshold tuning
 *
 * 2. **Surface feedback**: How useful are the app's informational surfaces?
 *    - Per-surface: average rating, count (with DP noise)
 *    - No comments transmitted (stored locally only)
 *    - Tells the team which explanations help and which confuse
 *
 * The owner can inspect the exact payload before it leaves the device.
 */
class FeedbackAggregator(private val db: BiosDatabase) {

    private val feedbackDao = db.userFeedbackDao()

    /**
     * Generate an anonymous feedback contribution.
     * Returns null if insufficient data (< 3 feedback entries).
     */
    suspend fun generateContribution(): FeedbackContribution? {
        val accuracyTracker = DetectionAccuracyTracker(db)
        val patternAccuracies = accuracyTracker.computeAccuracy()

        val surfaceFeedback = feedbackDao.fetchRecent(200)
        if (patternAccuracies.sumOf { it.withFeedback } + surfaceFeedback.size < MIN_DATA) {
            return null
        }

        val payload = buildPayload(patternAccuracies, surfaceFeedback)
        return FeedbackContribution(
            payload = payload,
            patternCount = patternAccuracies.size,
            feedbackCount = surfaceFeedback.size,
            sizeBytes = payload.toString().toByteArray().size
        )
    }

    private fun buildPayload(
        accuracies: List<PatternAccuracy>,
        feedback: List<UserFeedback>
    ): JSONObject {
        val random = SecureRandom()
        val root = JSONObject()
        root.put("schema", "bios-feedback-v1")
        root.put("type", "feedback_aggregate")

        // Pattern accuracy (with DP noise)
        val patterns = JSONArray()
        for (acc in accuracies) {
            if (acc.totalAlerts == 0) continue
            patterns.put(JSONObject().apply {
                put("patternId", acc.patternId)
                put("totalAlerts", addIntNoise(acc.totalAlerts, random))
                put("truePositives", addIntNoise(acc.truePositives, random))
                put("falsePositives", addIntNoise(acc.falsePositives, random))
                put("feedbackRate", addDoubleNoise(acc.feedbackRate, random))
                // No: timestamps, raw scores, identifiers
            })
        }
        root.put("patternAccuracy", patterns)

        // Surface feedback (aggregated per surface, with DP noise)
        val bySurface = feedback.groupBy { it.surface }
        val surfaces = JSONArray()
        for ((surface, entries) in bySurface) {
            if (entries.size < 2) continue // Don't contribute singletons
            val avgRating = entries.map { it.rating }.average()
            surfaces.put(JSONObject().apply {
                put("surface", surface)
                put("count", addIntNoise(entries.size, random))
                put("avgRating", addDoubleNoise(avgRating, random))
                // No: comments, item IDs, timestamps
            })
        }
        root.put("surfaceFeedback", surfaces)

        return root
    }

    private fun addIntNoise(value: Int, random: SecureRandom): Int {
        val noise = laplaceSample(random, 1.0 / EPSILON)
        return maxOf(0, (value + noise).toInt())
    }

    private fun addDoubleNoise(value: Double, random: SecureRandom): Double {
        val noise = laplaceSample(random, 0.1 / EPSILON)
        return value + noise
    }

    private fun laplaceSample(random: SecureRandom, scale: Double): Double {
        val u = random.nextDouble() - 0.5
        return -scale * Math.signum(u) * ln(1.0 - 2.0 * abs(u))
    }

    companion object {
        const val MIN_DATA = 3
        const val EPSILON = 1.0
    }
}

data class FeedbackContribution(
    val payload: JSONObject,
    val patternCount: Int,
    val feedbackCount: Int,
    val sizeBytes: Int
)
