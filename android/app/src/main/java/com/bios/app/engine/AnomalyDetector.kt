package com.bios.app.engine

import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.ConditionPattern
import com.bios.app.alerts.DeviationDirection
import com.bios.app.data.BiosDatabase
import com.bios.app.model.*
import kotlin.math.abs

/**
 * Detects anomalies by scoring deviations from personal baselines
 * and cross-correlating multiple signals.
 */
class AnomalyDetector(private val db: BiosDatabase) {

    private val readingDao = db.metricReadingDao()
    private val baselineDao = db.personalBaselineDao()
    private val anomalyDao = db.anomalyDao()

    suspend fun runDetection(): List<Anomaly> {
        val newAnomalies = mutableListOf<Anomaly>()

        for (pattern in ConditionPatterns.all) {
            val anomaly = evaluatePattern(pattern)
            if (anomaly != null) {
                anomalyDao.insert(anomaly)
                newAnomalies.add(anomaly)
            }
        }

        return newAnomalies
    }

    private suspend fun evaluatePattern(pattern: ConditionPattern): Anomaly? {
        val activeDeviations = mutableMapOf<MetricType, Double>()
        var totalWeightedScore = 0.0
        var totalWeight = 0.0

        for (rule in pattern.signalRules) {
            val baseline = baselineDao.fetch(rule.metricType.key) ?: continue

            val recentValues = fetchRecentValues(rule.metricType, rule.minDurationHours)
            if (recentValues.isEmpty()) continue

            val recentMean = recentValues.average()
            val zScore = baseline.zScore(recentMean)

            val isDeviating = when (rule.direction) {
                DeviationDirection.ABOVE -> zScore > rule.thresholdSigma
                DeviationDirection.BELOW -> zScore < -rule.thresholdSigma
                DeviationDirection.IRREGULAR -> abs(zScore) > rule.thresholdSigma
            }

            if (isDeviating) {
                activeDeviations[rule.metricType] = zScore
                totalWeightedScore += abs(zScore) * rule.weight
            }

            totalWeight += rule.weight
        }

        if (activeDeviations.size < pattern.minActiveSignals) return null

        val combinedScore = if (totalWeight > 0) totalWeightedScore / totalWeight else 0.0

        // Cool-down: don't re-alert for the same pattern within 24 hours
        val recentAnomalies = anomalyDao.fetchRecent(10)
        val cooldownMillis = 24 * 3600 * 1000L
        val hasCooldown = recentAnomalies.any { anomaly ->
            anomaly.patternId == pattern.id &&
            (System.currentTimeMillis() - anomaly.detectedAt) < cooldownMillis
        }
        if (hasCooldown) return null

        val severity = classifySeverity(
            activeSignals = activeDeviations.size,
            combinedScore = combinedScore,
            totalRules = pattern.signalRules.size
        )

        val deviationScoresJson = buildString {
            append("{")
            append(activeDeviations.entries.joinToString(",") { (k, v) ->
                "\"${k.key}\":$v"
            })
            append("}")
        }

        val metricTypesJson = buildString {
            append("[")
            append(activeDeviations.keys.joinToString(",") { "\"${it.key}\"" })
            append("]")
        }

        val explanation = buildExplanation(pattern, activeDeviations)

        return Anomaly(
            metricTypes = metricTypesJson,
            deviationScores = deviationScoresJson,
            combinedScore = combinedScore,
            patternId = pattern.id,
            severity = severity.level,
            title = pattern.title,
            explanation = explanation,
            suggestedAction = pattern.suggestedAction
        )
    }

    private fun classifySeverity(
        activeSignals: Int,
        combinedScore: Double,
        totalRules: Int
    ): AlertTier {
        val signalRatio = activeSignals.toDouble() / totalRules.toDouble()

        return when {
            combinedScore > 3.0 || signalRatio > 0.8 -> AlertTier.ADVISORY
            combinedScore > 2.0 || signalRatio > 0.5 -> AlertTier.NOTICE
            else -> AlertTier.OBSERVATION
        }
    }

    private fun buildExplanation(
        pattern: ConditionPattern,
        deviations: Map<MetricType, Double>
    ): String {
        val parts = deviations.entries
            .sortedByDescending { abs(it.value) }
            .map { (metric, zScore) ->
                val direction = if (zScore > 0) "above" else "below"
                val sigmas = String.format("%.1f", abs(zScore))
                "Your ${metric.readableName.lowercase()} is $sigmas standard deviations $direction your personal baseline."
            }
            .toMutableList()

        parts.add(pattern.explanation)
        return parts.joinToString(" ")
    }

    private suspend fun fetchRecentValues(metricType: MetricType, hours: Int): List<Double> {
        val endMillis = System.currentTimeMillis()
        val startMillis = endMillis - hours.toLong() * 3600 * 1000
        return readingDao.fetchValues(metricType.key, startMillis, endMillis)
    }
}
