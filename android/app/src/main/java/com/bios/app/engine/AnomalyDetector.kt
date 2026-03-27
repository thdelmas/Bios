package com.bios.app.engine

import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.ConditionPattern
import com.bios.app.alerts.DeviationDirection
import com.bios.app.data.BiosDatabase
import com.bios.app.model.*
import com.bios.app.ui.diagnostics.DiagnosticResult
import com.bios.app.ui.diagnostics.SignalStatus
import kotlin.math.abs
import kotlin.math.min

/**
 * Detects anomalies by scoring deviations from personal baselines
 * and cross-correlating multiple signals.
 */
class AnomalyDetector(
    private val db: BiosDatabase,
    private val mlModel: TFLiteAnomalyModel? = null
) {

    private val readingDao = db.metricReadingDao()
    private val baselineDao = db.personalBaselineDao()
    private val anomalyDao = db.anomalyDao()

    suspend fun runDetection(): List<Anomaly> {
        val newAnomalies = mutableListOf<Anomaly>()

        // Pattern-based detection (existing statistical approach)
        for (pattern in ConditionPatterns.all) {
            val anomaly = evaluatePattern(pattern)
            if (anomaly != null) {
                anomalyDao.insert(anomaly)
                newAnomalies.add(anomaly)
            }
        }

        // ML-based holistic anomaly detection
        val mlAnomaly = runMlDetection()
        if (mlAnomaly != null) {
            anomalyDao.insert(mlAnomaly)
            newAnomalies.add(mlAnomaly)
        }

        return newAnomalies
    }

    private suspend fun runMlDetection(): Anomaly? {
        val zScores = computeCurrentZScores()
        if (zScores.isEmpty()) return null

        val features = TFLiteAnomalyModel.buildFeatureVector(zScores)
        val score = mlModel?.score(features)
            ?: TFLiteAnomalyModel.heuristicScore(features)

        if (score < TFLiteAnomalyModel.ANOMALY_THRESHOLD) return null

        // Cool-down: don't re-alert for ML pattern within 24 hours
        val recentAnomalies = anomalyDao.fetchRecent(10)
        val cooldownMillis = 24 * 3600 * 1000L
        val hasCooldown = recentAnomalies.any { anomaly ->
            anomaly.patternId == ML_PATTERN_ID &&
            (System.currentTimeMillis() - anomaly.detectedAt) < cooldownMillis
        }
        if (hasCooldown) return null

        val deviating = zScores.filter { (_, z) -> abs(z) > 1.5 }
        val severity = when {
            score > 0.85 -> AlertTier.ADVISORY
            score > 0.7 -> AlertTier.NOTICE
            else -> AlertTier.OBSERVATION
        }

        val metricTypesJson = "[${deviating.keys.joinToString(",") { "\"$it\"" }}]"
        val scoresJson = "{${deviating.entries.joinToString(",") { "\"${it.key}\":${it.value}" }}}"

        return Anomaly(
            metricTypes = metricTypesJson,
            deviationScores = scoresJson,
            combinedScore = score.toDouble(),
            patternId = ML_PATTERN_ID,
            severity = severity.level,
            title = "Unusual health pattern detected",
            explanation = buildMlExplanation(deviating),
            suggestedAction = "Review your recent health trends and note any symptoms."
        )
    }

    private suspend fun computeCurrentZScores(): Map<String, Double> {
        val metrics = listOf(
            "heart_rate", "heart_rate_variability", "resting_heart_rate",
            "blood_oxygen", "respiratory_rate", "skin_temperature_deviation",
            "sleep_duration", "steps", "active_calories"
        )

        val zScores = mutableMapOf<String, Double>()
        val endMillis = System.currentTimeMillis()
        val startMillis = endMillis - 24L * 3600 * 1000

        for (metric in metrics) {
            val baseline = baselineDao.fetch(metric) ?: continue
            val values = readingDao.fetchValues(metric, startMillis, endMillis)
            if (values.isEmpty()) continue
            zScores[metric] = baseline.zScore(values.average())
        }

        return zScores
    }

    private fun buildMlExplanation(deviations: Map<String, Double>): String {
        if (deviations.isEmpty()) return "Multiple health metrics deviate from your baselines."
        return deviations.entries
            .sortedByDescending { abs(it.value) }
            .take(3)
            .joinToString(" ") { (metric, z) ->
                val dir = if (z > 0) "above" else "below"
                val name = metric.replace("_", " ")
                "Your $name is ${String.format("%.1f", abs(z))} std devs $dir baseline."
            }
    }

    suspend fun scoreAllPatterns(): List<DiagnosticResult> {
        return ConditionPatterns.all.map { pattern -> scorePattern(pattern) }
            .sortedByDescending { it.probability }
    }

    private suspend fun scorePattern(pattern: ConditionPattern): DiagnosticResult {
        val signals = mutableListOf<SignalStatus>()
        var totalWeightedScore = 0.0
        var totalWeight = 0.0
        var activeCount = 0
        var baselinesFound = 0

        for (rule in pattern.signalRules) {
            val baseline = baselineDao.fetch(rule.metricType.key)
            val hasBaseline = baseline != null
            if (hasBaseline) baselinesFound++

            var zScore: Double? = null
            var isActive = false

            if (baseline != null) {
                val values = fetchRecentValues(rule.metricType, rule.minDurationHours)
                if (values.isNotEmpty()) {
                    zScore = baseline.zScore(values.average())
                    isActive = when (rule.direction) {
                        DeviationDirection.ABOVE -> zScore > rule.thresholdSigma
                        DeviationDirection.BELOW -> zScore < -rule.thresholdSigma
                        DeviationDirection.IRREGULAR -> abs(zScore) > rule.thresholdSigma
                    }
                    if (isActive) {
                        activeCount++
                        totalWeightedScore += abs(zScore) * rule.weight
                    }
                }
            }

            totalWeight += rule.weight
            signals.add(SignalStatus(
                metricType = rule.metricType,
                direction = rule.direction,
                thresholdSigma = rule.thresholdSigma,
                weight = rule.weight,
                currentZScore = zScore,
                isActive = isActive,
                hasBaseline = hasBaseline
            ))
        }

        val hasEnoughData = baselinesFound >= pattern.minActiveSignals
        val rawScore = if (totalWeight > 0) totalWeightedScore / totalWeight else 0.0
        val activationRatio = activeCount.toDouble() / pattern.minActiveSignals.toDouble()
        val probability = if (!hasEnoughData || activeCount == 0) 0.0
            else min(1.0, activationRatio * rawScore / (rawScore + 2.0))

        return DiagnosticResult(
            pattern = pattern,
            probability = probability,
            signals = signals,
            activeSignalCount = activeCount,
            hasEnoughData = hasEnoughData
        )
    }

    companion object {
        const val ML_PATTERN_ID = "ml_holistic_anomaly"
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
