package com.bios.app.engine

import com.bios.app.data.BiosDatabase
import com.bios.app.model.Anomaly

/**
 * Tracks detection accuracy per condition pattern using owner feedback.
 *
 * Answers: "How accurate are the 12 condition patterns across this owner's data?"
 *
 * Metrics per pattern:
 * - True positive: owner confirmed illness (feltSick = true)
 * - False positive: owner said wasn't sick (feltSick = false)
 * - Confirmed accurate: owner said alert was correct (outcomeAccurate = true)
 * - Confirmed inaccurate: owner said alert was wrong (outcomeAccurate = false)
 * - No feedback: alert acknowledged but no outcome data
 *
 * This data is:
 * - Displayed locally in diagnostics (so the owner sees pattern reliability)
 * - Fed into FederatedTrainer (so the model improves)
 * - Anonymously aggregated in Community tier (so the population model improves)
 */
class DetectionAccuracyTracker(private val db: BiosDatabase) {

    private val anomalyDao = db.anomalyDao()

    /**
     * Compute accuracy stats for all patterns that have feedback data.
     */
    suspend fun computeAccuracy(): List<PatternAccuracy> {
        val allAnomalies = anomalyDao.fetchAll()
        val byPattern = allAnomalies.groupBy { it.patternId ?: "unknown" }

        return byPattern.map { (patternId, anomalies) ->
            computePatternAccuracy(patternId, anomalies)
        }.sortedByDescending { it.totalAlerts }
    }

    /**
     * Get accuracy for a specific pattern.
     */
    suspend fun computeForPattern(patternId: String): PatternAccuracy {
        val anomalies = anomalyDao.fetchAll().filter { it.patternId == patternId }
        return computePatternAccuracy(patternId, anomalies)
    }

    private fun computePatternAccuracy(
        patternId: String,
        anomalies: List<Anomaly>
    ): PatternAccuracy {
        val withFeedback = anomalies.filter { it.feedbackAt != null }
        val withOutcome = withFeedback.filter { it.feltSick != null }

        val truePositives = withOutcome.count { it.feltSick == true }
        val falsePositives = withOutcome.count { it.feltSick == false }
        val confirmedAccurate = withFeedback.count { it.outcomeAccurate == true }
        val confirmedInaccurate = withFeedback.count { it.outcomeAccurate == false }
        val noFeedback = anomalies.count { it.feedbackAt == null }

        val precision = if (truePositives + falsePositives > 0) {
            truePositives.toDouble() / (truePositives + falsePositives)
        } else null

        val ownerConfidence = if (confirmedAccurate + confirmedInaccurate > 0) {
            confirmedAccurate.toDouble() / (confirmedAccurate + confirmedInaccurate)
        } else null

        return PatternAccuracy(
            patternId = patternId,
            totalAlerts = anomalies.size,
            withFeedback = withFeedback.size,
            noFeedback = noFeedback,
            truePositives = truePositives,
            falsePositives = falsePositives,
            confirmedAccurate = confirmedAccurate,
            confirmedInaccurate = confirmedInaccurate,
            precision = precision,
            ownerConfidence = ownerConfidence
        )
    }
}

/**
 * Accuracy statistics for a single condition pattern.
 * Surfaced in diagnostics and anonymously aggregated for Community tier.
 */
data class PatternAccuracy(
    val patternId: String,
    val totalAlerts: Int,
    val withFeedback: Int,
    val noFeedback: Int,
    val truePositives: Int,
    val falsePositives: Int,
    val confirmedAccurate: Int,
    val confirmedInaccurate: Int,
    val precision: Double?,          // TP / (TP + FP), null if insufficient data
    val ownerConfidence: Double?     // % of alerts owner rated as accurate
) {
    val feedbackRate: Double
        get() = if (totalAlerts > 0) withFeedback.toDouble() / totalAlerts else 0.0

    val hasSufficientData: Boolean
        get() = truePositives + falsePositives >= 3
}
