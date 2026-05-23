package com.bios.app.engine

import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.ConditionPattern
import com.bios.app.alerts.DeviationDirection
import com.bios.app.config.RegionConfig
import com.bios.app.config.RegionConfigProvider
import com.bios.app.data.BiosDatabase
import com.bios.app.data.MedicationAnnotationRepo
import com.bios.app.data.dao.MetricReadingDao
import com.bios.app.model.*
import com.bios.app.physiology.OwnerCondition
import com.bios.app.physiology.PhysiologyState
import com.bios.contracts.MetricType
import com.bios.app.ui.diagnostics.DiagnosticResult
import com.bios.app.ui.diagnostics.SignalStatus
import kotlin.math.abs
import kotlin.math.min

/**
 * Detects anomalies by scoring deviations from personal baselines and
 * cross-correlating multiple signals. `reproductiveReadingDao` (#142) routes
 * WOMENS_HEALTH metrics to the isolated [com.bios.app.data.ReproductiveDatabase].
 * `medicationRepo` (#154) freezes annotated medications on alert creation.
 * `physiologyState` (#159, CARDIOLOGY_POV §2.4) filters patterns via [appliesIn].
 */
class AnomalyDetector(
    private val db: BiosDatabase,
    private val mlModel: TFLiteAnomalyModel? = null,
    private val latencyTracker: DetectionLatencyTracker? = null,
    private val reproductiveReadingDao: MetricReadingDao? = null,
    private val medicationRepo: MedicationAnnotationRepo? = MedicationAnnotationRepo(db),
    private val physiologyState: PhysiologyState = PhysiologyState.STANDARD,
    /** #196: region gates `requiresTropicalRegion`; ownerConditions gates `requiredOwnerConditions`. */
    private val regionConfig: RegionConfig = RegionConfigProvider.forCurrentLocale(),
    private val ownerConditions: Set<OwnerCondition> = emptySet(),
    /** Cancer-therapy drug class (#201); gates `requiredDrugClasses`. */
    private val drugClass: com.bios.app.physiology.CancerTherapyDrugClass = com.bios.app.physiology.CancerTherapyDrugClass.NONE,
    /** #197: modulates `elevationAdjustedBelow` thresholds when set. */
    private val environmentalContext: com.bios.app.model.EnvironmentalContext? = null,
    /** #190: sub-window acute-event detector. Null disables the acute path. */
    private val acuteWindowDetector: AcuteWindowDetector? = AcuteWindowDetector(db, physiologyState),
) {

    private val readingDao = db.metricReadingDao()
    private val baselineDao = db.personalBaselineDao()
    private val anomalyDao = db.anomalyDao()

    // Acute-window patterns (#190) fire on their own detector (rate-of-change
    // semantics the slow engine cannot express); skip here. appliesIn honors
    // excludedStates + requiredStates (#200) + region (#196) + owner-conditions (#196).
    private fun applicablePatterns(): List<ConditionPattern> {
        val acuteIds = com.bios.app.alerts.AcuteWindowPatterns.all.map { it.id }.toSet()
        return ConditionPatterns.all.filter {
            it.appliesIn(physiologyState, regionConfig, ownerConditions, drugClass) && it.id !in acuteIds
        }
    }

    suspend fun runDetection(): List<Anomaly> {
        val endToEndStart = System.currentTimeMillis()
        val newAnomalies = mutableListOf<Anomaly>()
        val patterns = applicablePatterns()

        // Acute-window detection (#190) — parallel path for sub-window
        // events (anaphylaxis, OUD respiratory depression, DKA, shock).
        val acuteAnomalies = acuteWindowDetector?.runAcuteDetection() ?: emptyList()
        newAnomalies.addAll(acuteAnomalies)

        // Pattern-based detection (existing statistical approach)
        suspend fun evalAll(): List<Anomaly> {
            val results = mutableListOf<Anomaly>()
            for (pattern in patterns) {
                evaluatePattern(pattern)?.let {
                    anomalyDao.insert(it)
                    results.add(it)
                }
            }
            return results
        }
        val patternAnomalies = latencyTracker?.track(PipelineStage.PATTERN_DETECTION) { evalAll() } ?: evalAll()
        newAnomalies.addAll(patternAnomalies)

        // ML-based holistic anomaly detection
        val mlAnomaly = latencyTracker?.track(PipelineStage.ML_INFERENCE) {
            runMlDetection()
        } ?: runMlDetection()
        if (mlAnomaly != null) {
            anomalyDao.insert(mlAnomaly)
            newAnomalies.add(mlAnomaly)
        }

        // Track end-to-end detection latency (notification delivery tracked separately)
        if (newAnomalies.isNotEmpty()) {
            latencyTracker?.record(
                PipelineStage.END_TO_END,
                System.currentTimeMillis() - endToEndStart,
                mapOf("anomaly_count" to newAnomalies.size.toString())
            )
        }

        return newAnomalies
    }

    private suspend fun runMlDetection(): Anomaly? {
        val zScores = computeCurrentZScores()
        if (zScores.isEmpty()) return null

        val features = TFLiteAnomalyModel.buildFeatureVector(zScores)
        val heuristicScore = TFLiteAnomalyModel.heuristicScore(features)
        val mlScore = mlModel?.score(features)
        // A/B comparison signal (issue #1): when the trained model is
        // loaded, record both scores so the pipeline-health surface can
        // report agreement vs disagreement over time. The detection
        // decision still uses the ML score; the heuristic is the safety
        // net + comparison reference.
        if (mlScore != null) {
            AnomalyModelComparison.record(mlScore, heuristicScore)
        }
        val score = mlScore ?: heuristicScore

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
            // SENSOR-only z-scores: docs/SELF_REPORTED_DATA_HOME.md decision 3.
            val values = readingDao.fetchValues(metric, startMillis, endMillis, ReadingKind.SENSOR.name)
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
        return applicablePatterns().map { pattern -> scorePattern(pattern) }
            .sortedByDescending { it.probability }
    }

    private suspend fun scorePattern(pattern: ConditionPattern): DiagnosticResult {
        val signals = mutableListOf<SignalStatus>()
        var totalWeightedScore = 0.0
        var totalWeight = 0.0
        var activeCount = 0
        var baselinesFound = 0

        var requiredMissing = false

        for (rule in pattern.signalRules) {
            var zScore: Double? = null
            var isActive = false
            var hasBaseline = false

            if (rule.isAbsolute) {
                // Absolute clinical threshold — no baseline, no time window.
                // Treat "we have a reading" as having enough data, since labs
                // are dated and a six-month-old hsCRP is still meaningful.
                val (value, active) = evaluateAbsoluteRule(rule)
                hasBaseline = value != null
                if (hasBaseline) baselinesFound++
                isActive = active
                if (isActive) {
                    activeCount++
                    totalWeightedScore += rule.weight
                }
            } else if (rule.direction == DeviationDirection.ABSENT) {
                // No baseline needed — "no events in window" is the signal.
                hasBaseline = true
                baselinesFound++
                val values = fetchRecentValues(rule.metricType, rule.minDurationHours)
                isActive = values.isEmpty()
                if (isActive) {
                    activeCount++
                    totalWeightedScore += rule.weight  // binary signal, full weight
                }
            } else {
                val baseline = baselineDao.fetch(rule.metricType.key)
                hasBaseline = baseline != null
                if (hasBaseline) baselinesFound++

                if (baseline != null) {
                    val values = fetchRecentValues(rule.metricType, rule.minDurationHours)
                    if (values.isNotEmpty()) {
                        zScore = baseline.zScore(values.average())
                        isActive = when (rule.direction) {
                            DeviationDirection.ABOVE -> zScore > rule.thresholdSigma
                            DeviationDirection.BELOW -> zScore < -rule.thresholdSigma
                            DeviationDirection.IRREGULAR -> abs(zScore) > rule.thresholdSigma
                            DeviationDirection.ABSENT -> false  // unreachable, see above
                        }
                        if (isActive) {
                            activeCount++
                            totalWeightedScore += abs(zScore) * rule.weight
                        }
                    }
                }
            }

            if (rule.required && !isActive) requiredMissing = true

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
        val probability = if (!hasEnoughData || activeCount == 0 || requiredMissing) 0.0
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
            if (rule.isAbsolute) {
                val (value, active) = evaluateAbsoluteRule(rule)
                if (rule.required && !active) return null
                totalWeight += rule.weight
                if (active && value != null) {
                    activeDeviations[rule.metricType] = value
                    totalWeightedScore += rule.weight
                }
                continue
            }

            val recentValues = fetchRecentValues(rule.metricType, rule.minDurationHours)
            val (isActive, contributedScore) = when (rule.direction) {
                DeviationDirection.ABSENT -> {
                    val active = recentValues.isEmpty()
                    Pair(active, if (active) rule.weight else 0.0)
                }
                else -> {
                    val baseline = baselineDao.fetch(rule.metricType.key)
                    if (baseline == null || recentValues.isEmpty()) {
                        Pair(false, 0.0)
                    } else {
                        val zScore = baseline.zScore(recentValues.average())
                        val deviating = when (rule.direction) {
                            DeviationDirection.ABOVE -> zScore > rule.thresholdSigma
                            DeviationDirection.BELOW -> zScore < -rule.thresholdSigma
                            DeviationDirection.IRREGULAR -> abs(zScore) > rule.thresholdSigma
                            DeviationDirection.ABSENT -> false  // unreachable, see above
                        }
                        if (deviating) {
                            activeDeviations[rule.metricType] = zScore
                            Pair(true, abs(zScore) * rule.weight)
                        } else {
                            Pair(false, 0.0)
                        }
                    }
                }
            }

            if (rule.required && !isActive) return null
            totalWeightedScore += contributedScore
            totalWeight += rule.weight
            if (isActive && rule.direction == DeviationDirection.ABSENT) {
                // Carry ABSENT rules in the active map so they appear in the
                // anomaly's signals JSON (z=0 sentinel for "no readings").
                activeDeviations[rule.metricType] = 0.0
            }
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

        val classifiedSeverity = classifySeverity(
            activeSignals = activeDeviations.size,
            combinedScore = combinedScore,
            totalRules = pattern.signalRules.size
        )
        // Emergency vital-sign patterns declare a severityFloor (URGENT) so a
        // single hard-cutoff crossing (SpO2 ≤85, glucose ≤54, RHR extreme)
        // isn't presented at the same tier as a 7-day RHR drift. Trend
        // patterns leave severityFloor null and fall through to the
        // classifier output.
        val severity = pattern.severityFloor?.let { floor ->
            if (floor.level > classifiedSeverity.level) floor else classifiedSeverity
        } ?: classifiedSeverity

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

        val baseExplanation = buildExplanation(pattern, activeDeviations)
        // Medication context — read at anomaly-creation time so the stored
        // text reflects what was on record when the pattern fired (§2.5).
        // Null when the owner has no active medications recorded.
        val medsContext = medicationRepo?.formatActiveContext()
        val explanation = if (medsContext != null) "$baseExplanation $medsContext" else baseExplanation

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
        // Per-signal phrasing is z-score-based; rules carried in with z=0 are
        // ABSENT-direction sentinels (no event in window) and don't fit that
        // template, so we leave them to pattern.explanation.
        val parts = deviations.entries
            .filter { it.value != 0.0 }
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
        val dao = daoFor(metricType)
        return dao.fetchValues(
            metricType.key, startMillis, endMillis, readingKindFilterFor(metricType)
        )
    }

    // Window-mode values for the absolute-rule path; row-fetch + duration
    // filter when SignalRule.durationAtLeastSec is set (#268), otherwise
    // the value-only fast path.
    private suspend fun fetchAbsoluteWindowValues(rule: com.bios.app.alerts.SignalRule): List<Double> {
        val minDuration = rule.durationAtLeastSec
            ?: return fetchRecentValues(rule.metricType, rule.absoluteWindowHours)
        val endMillis = System.currentTimeMillis()
        val startMillis = endMillis - rule.absoluteWindowHours.toLong() * 3600 * 1000
        return daoFor(rule.metricType)
            .fetch(rule.metricType.key, startMillis, endMillis)
            .filter { (it.durationSec ?: 0) >= minDuration }
            .map { it.value }
    }

    private fun daoFor(metricType: MetricType): MetricReadingDao =
        if (isReproductiveMetric(metricType) && reproductiveReadingDao != null) {
            reproductiveReadingDao
        } else {
            readingDao
        }

    /**
     * Evaluates an absolute-threshold rule. Default (`absoluteWindowHours = 0`)
     * uses the latest reading — appropriate for sparse, dated labs. Multi-
     * reading mode (`absoluteWindowHours > 0`) fetches all readings in the
     * window, requires `absoluteMinReadings`, then compares the **median**
     * against the cutoff — the home-BP convention (ESH 2023), robust to a
     * single white-coat outlier. Returns `(null, false)` when there isn't
     * enough data to evaluate.
     */
    private suspend fun evaluateAbsoluteRule(
        rule: com.bios.app.alerts.SignalRule
    ): Pair<Double?, Boolean> {
        val representative: Double = if (rule.absoluteWindowHours > 0) {
            val values = fetchAbsoluteWindowValues(rule)
            if (values.size < rule.absoluteMinReadings) return null to false
            values.sorted().let { s ->
                if (s.size % 2 == 0) (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0 else s[s.size / 2]
            }
        } else {
            readingDao.fetchLatest(rule.metricType.key, 1).firstOrNull()?.value
                ?: return null to false
        }
        val above = rule.absoluteAbove?.let { representative >= it } == true
        // Elevation-adjusted "at or below" cutoff (#197). When the rule
        // ships an `elevationAdjustedBelow` map and the owner has declared
        // an environmental context, the band-specific value displaces the
        // base cutoff. Sea-level / null contexts fall through unchanged.
        val resolvedBelow = rule.resolvedAbsoluteBelow(environmentalContext?.elevationBand)
        val below = resolvedBelow?.let { representative <= it } == true
        return representative to (above || below)
    }
}
