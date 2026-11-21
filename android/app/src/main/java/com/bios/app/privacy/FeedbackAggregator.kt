package com.bios.app.privacy

import com.bios.app.data.BiosDatabase
import com.bios.app.model.UserFeedback
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import kotlin.math.abs
import kotlin.math.ln

/**
 * Aggregates owner feedback into anonymous contributions for the Community tier.
 *
 * One data stream, anonymized on-device before transmission:
 *
 * **Surface feedback**: How useful are the app's informational surfaces?
 *  - Per-surface: average rating, count (with DP noise)
 *  - No comments transmitted (stored locally only)
 *
 * The per-pattern accuracy stream was removed with the condition patterns;
 * the `patternAccuracy` key stays in the payload as an empty array.
 *
 * The owner can inspect the exact payload before it leaves the device.
 */
class FeedbackAggregator(db: BiosDatabase) {

    private val feedbackDao = db.userFeedbackDao()

    /**
     * Generate an anonymous feedback contribution.
     * Returns null if insufficient data (< 3 feedback entries).
     */
    suspend fun generateContribution(): FeedbackContribution? {
        val surfaceFeedback = feedbackDao.fetchRecent(200)
        if (surfaceFeedback.size < MIN_DATA) return null

        val payload = buildPayload(surfaceFeedback)
        return FeedbackContribution(
            payload = payload,
            patternCount = 0,
            feedbackCount = surfaceFeedback.size,
            sizeBytes = payload.toString().toByteArray().size
        )
    }

    private fun buildPayload(feedback: List<UserFeedback>): JSONObject {
        val random = SecureRandom()
        val root = JSONObject()
        root.put("schema", "bios-feedback-v1")
        root.put("type", "feedback_aggregate")

        root.put("patternAccuracy", JSONArray())

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
