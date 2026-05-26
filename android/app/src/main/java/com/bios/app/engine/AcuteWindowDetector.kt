package com.bios.app.engine

import com.bios.app.alerts.AcuteWindowPatterns
import com.bios.app.alerts.ConditionPattern
import com.bios.app.data.BiosDatabase
import com.bios.app.model.AlertTier
import com.bios.app.model.Anomaly
import com.bios.app.physiology.PhysiologyState
import com.bios.contracts.MetricType

/**
 * Sub-window acute-event detector (#190, EMERGENCY_CRITICAL_CARE_POV §2.3
 * / §2.4 / §2.9 / §2.10, PSYCHIATRY §2.5 OUD, PAEDIATRICS §2.11 DKA).
 *
 * The existing [AnomalyDetector] uses ≥12-hour windows. Adequate for trend
 * patterns; blind to events that develop in minutes:
 *
 *  - Anaphylaxis: 5-minute multi-system event
 *  - Opioid / sedative respiratory depression: minutes-to-hours
 *  - DKA: hours-to-day
 *  - Hypotensive shock: minutes
 *
 * The audit recommends a `vitalsAccelerationDetector` engine for these
 * sub-window events. This class is that engine. It:
 *
 *  - Operates on a tight rolling window (default 10 min via
 *    [AcuteWindowPatterns.DEFAULT_WINDOW_MINUTES]).
 *  - Adds rate-of-change semantics the slow engine cannot express (ΔHR
 *    for anaphylaxis; MAP computed from concurrent SBP + DBP for shock;
 *    consecutive-reading gates for RR-and-SpO2).
 *  - Reuses [ConditionPattern] metadata from [AcuteWindowPatterns] so the
 *    alert text passes the same [com.bios.app.alerts.AlertContentPolicy]
 *    CI gate as the slow-engine patterns.
 *  - Routes [Anomaly] output through the same alerts pipeline.
 *
 * Cadence: invoked by [AnomalyDetector.runDetection] inline so a high-
 * frequency caller gets both the trend and acute paths fired together.
 * Cheap: no baseline lookups, just window-filtered reads against
 * literature thresholds. No state between invocations.
 *
 * The pure-function pattern evaluators live in the companion object so
 * they are testable without a SQLCipher / Room fixture — same shape
 * [com.bios.app.alerts.Sepsis2NewsCalculator] established for sepsis
 * screening.
 *
 * References: Sampson 2006 (anaphylaxis), Nandakumar 2019 SAFE (opioid),
 * Wolfsdorf 2022 ISPAD (paediatric DKA), Singer 2016 Sepsis-3 + Cecconi
 * 2014 ESICM (shock).
 */
class AcuteWindowDetector(
    private val db: BiosDatabase,
    private val physiologyState: PhysiologyState = PhysiologyState.STANDARD,
    private val windowMinutes: Int = AcuteWindowPatterns.DEFAULT_WINDOW_MINUTES,
) {

    private val readingDao = db.metricReadingDao()
    private val anomalyDao = db.anomalyDao()

    /**
     * Evaluate the four acute-window patterns against the current
     * window. Writes any newly-fired [Anomaly] rows via the anomaly DAO
     * and returns them.
     */
    suspend fun runAcuteDetection(
        nowMillis: Long = System.currentTimeMillis(),
    ): List<Anomaly> {
        val patterns = AcuteWindowPatterns.all.filter {
            physiologyState !in it.excludedStates
        }
        if (patterns.isEmpty()) return emptyList()

        val readings = fetchWindowReadings(nowMillis)
        val newAnomalies = mutableListOf<Anomaly>()

        for (pattern in patterns) {
            // Cool-down: 1 hour (tighter than the standard engine's 24-hour
            // window — URGENT acute events can recur on the same hour but
            // a duplicate inside an hour adds noise without information).
            val cooldownMillis = 60L * 60L * 1000L
            val recent = anomalyDao.fetchRecent(20)
            val hasCooldown = recent.any { a ->
                a.patternId == pattern.id &&
                    (nowMillis - a.detectedAt) < cooldownMillis
            }
            if (hasCooldown) continue

            val match = evaluatePattern(pattern, readings, nowMillis) ?: continue
            anomalyDao.insert(match)
            newAnomalies.add(match)
        }

        return newAnomalies
    }

    /**
     * Fetch the readings each acute pattern needs. HR / SpO2 / RR / BP
     * span [windowMinutes] (the 10-minute default). Glucose uses a
     * 24-hour window to tolerate sparse CGM and capture the
     * sustained-elevation envelope (not a single post-prandial spike).
     */
    internal suspend fun fetchWindowReadings(nowMillis: Long): List<Reading> {
        val acute: List<MetricType> = listOf(
            MetricType.HEART_RATE,
            MetricType.BLOOD_OXYGEN,
            MetricType.RESPIRATORY_RATE,
            MetricType.BLOOD_PRESSURE_SYSTOLIC,
            MetricType.BLOOD_PRESSURE_DIASTOLIC,
        )
        val acuteStart = nowMillis - windowMinutes.toLong() * 60_000L
        val out = mutableListOf<Reading>()
        for (metric in acute) {
            val rows = readingDao.fetch(metric.key, acuteStart, nowMillis)
            for (row in rows) {
                out += Reading(metric, row.value, row.timestamp)
            }
        }
        val glucoseStart = nowMillis - 24L * 3600L * 1000L
        val glucoseRows = readingDao.fetch(
            MetricType.BLOOD_GLUCOSE.key, glucoseStart, nowMillis,
        )
        for (row in glucoseRows) {
            out += Reading(MetricType.BLOOD_GLUCOSE, row.value, row.timestamp)
        }
        return out
    }

    /**
     * Single time-stamped numeric reading. Decoupled from
     * [com.bios.app.model.MetricReading] so the rate-of-change math is
     * unit-testable without Room / SQLCipher fixtures.
     */
    data class Reading(
        val metricType: MetricType,
        val value: Double,
        val timestampMillis: Long,
    )

    companion object {

        /**
         * Pattern dispatch — pure function over the four acute patterns.
         * Returns `null` when the pattern doesn't match or doesn't apply.
         */
        fun evaluatePattern(
            pattern: ConditionPattern,
            readings: List<Reading>,
            nowMillis: Long,
        ): Anomaly? = when (pattern.id) {
            AcuteWindowPatterns.anaphylaxisScreen.id ->
                evaluateAnaphylaxis(pattern, readings, nowMillis)
            AcuteWindowPatterns.opioidRespiratoryDepressionScreen.id ->
                evaluateOpioidRespiratoryDepression(pattern, readings, nowMillis)
            AcuteWindowPatterns.dkaScreen.id ->
                evaluateDka(pattern, readings, nowMillis)
            AcuteWindowPatterns.hypotensiveShockScreen.id ->
                evaluateHypotensiveShock(pattern, readings, nowMillis)
            else -> null
        }

        /**
         * Anaphylaxis: HR rising ≥30 bpm within the rolling window AND
         * SpO2 ≤92 % across at least two readings. Rate-of-change captured
         * by comparing the latest HR reading against the earliest in the
         * window; sustained-drop captured by counting SpO2 readings at or
         * below the threshold.
         *
         * Anti-false-positive: requires ≥3 HR readings in the window
         * (single spike + SpO2 dip from motion artefact would otherwise
         * fire). The SpO2 two-reading floor is the same anti-artefact
         * gate the slow engine applies.
         */
        fun evaluateAnaphylaxis(
            pattern: ConditionPattern,
            readings: List<Reading>,
            nowMillis: Long,
        ): Anomaly? {
            val hr = readings.filter { it.metricType == MetricType.HEART_RATE }
                .sortedBy { it.timestampMillis }
            if (hr.size < 3) return null
            val hrDelta = hr.last().value - hr.first().value
            if (hrDelta < 30.0) return null

            val spo2Low = readings.count {
                it.metricType == MetricType.BLOOD_OXYGEN && it.value <= 92.0
            }
            if (spo2Low < 2) return null

            return buildAnomaly(
                pattern = pattern,
                metrics = listOf(MetricType.HEART_RATE, MetricType.BLOOD_OXYGEN),
                scoresJson = "{\"heart_rate_delta_bpm\":$hrDelta,\"spo2_low_readings\":$spo2Low}",
            )
        }

        /**
         * Opioid / sedative respiratory depression: RR ≤8 sustained across
         * at least two readings AND SpO2 ≤88 % across at least two
         * readings. The SpO2 lag of 2–5 min after RR drop is captured by
         * requiring sustained low values rather than a single coincident
         * pair.
         *
         * Audit framing: NOT a "you're using drugs" detector — also fires
         * on sedative overdose, severe sleep apnea event, COPD
         * exacerbation, post-anaesthesia residual narcosis.
         */
        fun evaluateOpioidRespiratoryDepression(
            pattern: ConditionPattern,
            readings: List<Reading>,
            nowMillis: Long,
        ): Anomaly? {
            val rr = readings.filter { it.metricType == MetricType.RESPIRATORY_RATE }
            if (rr.size < 2) return null
            val rrLow = rr.count { it.value <= 8.0 }
            if (rrLow < 2) return null

            val spo2 = readings.filter { it.metricType == MetricType.BLOOD_OXYGEN }
            val spo2Low = spo2.count { it.value <= 88.0 }
            if (spo2Low < 2) return null

            return buildAnomaly(
                pattern = pattern,
                metrics = listOf(MetricType.RESPIRATORY_RATE, MetricType.BLOOD_OXYGEN),
                scoresJson = "{\"rr_below_8_readings\":$rrLow,\"spo2_below_88_readings\":$spo2Low}",
            )
        }

        /**
         * DKA: sustained glucose ≥250 mg/dL across at least three readings
         * AND persistent tachycardia ≥100 bpm across at least two readings.
         * Multi-reading floor distinguishes from transient post-prandial
         * spikes; HR corroborator suppresses non-diabetic stress
         * hyperglycaemia.
         *
         * Paediatric ISPAD 2022 cutoff is ≥200 mg/dL — v1 ships the adult
         * threshold and excludes [PhysiologyState.PAEDIATRIC] at the
         * pattern level (already applied at the [runAcuteDetection]
         * filter step).
         */
        fun evaluateDka(
            pattern: ConditionPattern,
            readings: List<Reading>,
            nowMillis: Long,
        ): Anomaly? {
            val glucoseHigh = readings.count {
                it.metricType == MetricType.BLOOD_GLUCOSE && it.value >= 250.0
            }
            if (glucoseHigh < 3) return null

            val hrHigh = readings.count {
                it.metricType == MetricType.HEART_RATE && it.value >= 100.0
            }
            if (hrHigh < 2) return null

            return buildAnomaly(
                pattern = pattern,
                metrics = listOf(MetricType.BLOOD_GLUCOSE, MetricType.HEART_RATE),
                scoresJson = "{\"glucose_above_250_readings\":$glucoseHigh,\"hr_above_100_readings\":$hrHigh}",
            )
        }

        /**
         * Hypotensive shock: MAP <65 mmHg (computed from concurrent SBP +
         * DBP) OR SBP ≤90 mmHg, AND compensatory HR >100 bpm.
         * MAP formula: DBP + (SBP − DBP) / 3 — standard textbook
         * approximation, Sepsis-3 / ESICM vasopressor cutoff.
         *
         * Specificity gate: requires HR >100 alongside the BP criterion
         * so a lone low-SBP reading (orthostatic drop, white-coat-
         * equivalent rebound, athletic conditioning) without compensatory
         * tachycardia does not fire.
         */
        fun evaluateHypotensiveShock(
            pattern: ConditionPattern,
            readings: List<Reading>,
            nowMillis: Long,
        ): Anomaly? {
            val sbpReadings = readings.filter {
                it.metricType == MetricType.BLOOD_PRESSURE_SYSTOLIC
            }
            val dbpReadings = readings.filter {
                it.metricType == MetricType.BLOOD_PRESSURE_DIASTOLIC
            }
            if (sbpReadings.isEmpty()) return null

            val lowSbp = sbpReadings.any { it.value <= 90.0 }
            val lowMap = computeAnyLowMap(sbpReadings, dbpReadings, mapThreshold = 65.0)
            if (!lowSbp && !lowMap) return null

            val hrHigh = readings.count {
                it.metricType == MetricType.HEART_RATE && it.value > 100.0
            }
            if (hrHigh < 1) return null

            return buildAnomaly(
                pattern = pattern,
                metrics = listOf(
                    MetricType.BLOOD_PRESSURE_SYSTOLIC,
                    MetricType.HEART_RATE,
                ),
                scoresJson = "{\"sbp_below_90\":$lowSbp,\"map_below_65\":$lowMap,\"hr_above_100_readings\":$hrHigh}",
            )
        }

        /**
         * True when any concurrent SBP + DBP pair (within 60 s) produces
         * a computed MAP below the threshold. Cuff adapter writes SBP and
         * DBP from the same measurement event so the timestamps are
         * typically identical or within a few seconds.
         */
        fun computeAnyLowMap(
            sbpReadings: List<Reading>,
            dbpReadings: List<Reading>,
            mapThreshold: Double,
        ): Boolean {
            if (sbpReadings.isEmpty() || dbpReadings.isEmpty()) return false
            for (sbp in sbpReadings) {
                val dbp = dbpReadings.minByOrNull {
                    kotlin.math.abs(it.timestampMillis - sbp.timestampMillis)
                } ?: continue
                if (kotlin.math.abs(dbp.timestampMillis - sbp.timestampMillis) > 60_000L) {
                    continue
                }
                val map = dbp.value + (sbp.value - dbp.value) / 3.0
                if (map < mapThreshold) return true
            }
            return false
        }

        /**
         * Build the [Anomaly] row from pattern metadata so the same
         * content-policy-compliant text used by the slow engine ships
         * here.
         */
        private fun buildAnomaly(
            pattern: ConditionPattern,
            metrics: List<MetricType>,
            scoresJson: String,
        ): Anomaly {
            val metricTypesJson =
                "[${metrics.joinToString(",") { "\"${it.key}\"" }}]"
            val severity = pattern.severityFloor ?: AlertTier.URGENT
            return Anomaly(
                metricTypes = metricTypesJson,
                deviationScores = scoresJson,
                combinedScore = 1.0,
                patternId = pattern.id,
                severity = severity.level,
                title = pattern.title,
                explanation = pattern.explanation,
                suggestedAction = pattern.suggestedAction,
            )
        }
    }
}
