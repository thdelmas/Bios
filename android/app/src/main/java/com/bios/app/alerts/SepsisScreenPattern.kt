package com.bios.app.alerts

import com.bios.app.model.AlertTier
import com.bios.app.model.ConditionCategory
import com.bios.contracts.MetricType

/**
 * Sepsis screening pattern (issue #182, EMERGENCY_CRITICAL_CARE_POV §2.2 +
 * SURGICAL_POV §2.5 + Primary Care / Geriatrics / Oncology convergence).
 *
 * NEWS2 (Royal College of Physicians 2017) and qSOFA (Singer 2016, JAMA)
 * are the validated multi-signal early-warning composites the NHS and the
 * Surviving Sepsis Campaign (Evans 2021) use to trigger MET / RRT activation
 * and pre-hospital sepsis escalation. Every input is already on the bus:
 * [MetricType.RESPIRATORY_RATE], [MetricType.BLOOD_OXYGEN],
 * [MetricType.OXYGEN_FLOW_RATE], [MetricType.BLOOD_PRESSURE_SYSTOLIC],
 * [MetricType.RESTING_HEART_RATE] (NEWS2 uses HR; the wearable's nightly RHR
 * estimate is the closest available proxy), [MetricType.SKIN_TEMPERATURE]
 * and [MetricType.CONSCIOUSNESS_LEVEL] (Glasgow Coma Scale total 3–15;
 * NEWS2's "AVPU not A" maps to GCS <15).
 *
 * The audit's recommendation: ship a `sepsis_screen` pattern that computes
 * NEWS2 from the available inputs on a 6-hour rolling window, escalates
 * URGENT at ≥5 (the NHS Sepsis Six trigger), and is suppressible via
 * PhysiologyState gating for owners with a chronic-illness flag that
 * produces a permanently-elevated baseline NEWS2 (e.g. a stable COPD owner
 * on home O2 will score ≥2 for SpO2 alone).
 *
 * ## Two layers in this file
 *
 *  1. **[Sepsis2NewsCalculator]** — pure-Kotlin scoring math for NEWS2 and
 *     qSOFA over a list of readings. Testable in isolation; the calculator
 *     is what an in-app diagnostics surface or a clinician-export view
 *     would call. Encodes the full NEWS2 / qSOFA component-by-component
 *     scoring table from the Royal College of Physicians 2017 release.
 *
 *  2. **[sepsisScreen]** — a registered [ConditionPattern] that fires
 *     through the existing [com.bios.app.engine.AnomalyDetector] pipeline.
 *     The pattern engine today counts active SignalRules rather than
 *     summing per-component scores, so the SignalRules below encode the
 *     **3-point ("red") component thresholds** from NEWS2 — any single
 *     one of which is enough to lift NEWS2 to ≥3 on its own. This is the
 *     conservative wearable-detectable approximation: a 3-point red
 *     component plus any qSOFA corroborator firing crosses the NEWS2 ≥5
 *     URGENT boundary that the Sepsis Six protocol uses.
 *     `severityFloor = URGENT` ensures escalation when the pattern fires.
 *
 * The pattern stays high-sensitivity (NEWS2's design intent — "rather
 * over-call than miss the one event that mattered" is the audit's framing
 * of the EM lens). False positives are mitigated by the manifesto-clean
 * data-statement framing in the alert text — Bios reports that wearable
 * vital signs meet the NHS screening trigger, never claims sepsis.
 *
 * ## Suppressibility
 *
 * Owners with chronic COPD on home oxygen will score ≥2 on SpO2 with
 * NEWS2 Scale 2 (the alternative scoring scale for hypoxic-respiratory-
 * failure owners) and an additional +2 for any supplemental O2 — which
 * makes baseline NEWS2 ≥4 for a stable owner. A future
 * [com.bios.app.physiology.PhysiologyState] entry (e.g. `COPD_HOME_O2`)
 * should land in [ConditionPattern.excludedStates] for this pattern. v1
 * leaves the set empty; see the TODO below.
 *
 * ## References (per the audit)
 *
 *  - Royal College of Physicians (2017) — National Early Warning Score
 *    (NEWS) 2: Standardising the assessment of acute-illness severity in
 *    the NHS.
 *  - Singer M et al. (2016) — The Third International Consensus
 *    Definitions for Sepsis and Septic Shock (Sepsis-3). JAMA.
 *  - Seymour CW et al. (2016) — Assessment of Clinical Criteria for
 *    Sepsis (qSOFA derivation cohort). JAMA.
 *  - Evans L et al. (2021) — Surviving Sepsis Campaign: International
 *    Guidelines for Management of Sepsis and Septic Shock. Critical Care
 *    Medicine.
 *  - Kumar A et al. (2006) — Duration of hypotension before initiation of
 *    effective antimicrobial therapy is the critical determinant of
 *    survival in human septic shock. Critical Care Medicine.
 *
 * All text obeys [AlertContentPolicy].
 */
object SepsisScreenPattern {

    val all by lazy { listOf(sepsisScreen) }

    /**
     * Rolling window the pure-math calculator uses when picking the most
     * recent per-metric reading. 6 hours matches the audit recommendation
     * and the NHS NEWS2 "score on each observation set, escalate within
     * the hour" cadence — long enough to ingest a wearable nightly RHR
     * estimate alongside a manually-entered cuff BP, short enough that
     * stale readings don't carry the score.
     */
    const val ROLLING_WINDOW_HOURS: Int = 6

    /**
     * NEWS2 ADVISORY trigger. Score 1–4 in NEWS2 is the "low risk" band
     * with min 12-hourly reassessment; ≥3 in any single parameter raises
     * the response to "low–medium" requiring ward-based review. Bios uses
     * ≥3 as the ADVISORY threshold to surface the early signal.
     */
    const val NEWS2_ADVISORY_THRESHOLD: Int = 3

    /**
     * NEWS2 URGENT trigger. Score ≥5 is the NHS "medium risk" band that
     * triggers urgent review by a clinician with critical-care competencies
     * — the Sepsis Six protocol activation threshold.
     */
    const val NEWS2_URGENT_THRESHOLD: Int = 5

    /**
     * qSOFA URGENT trigger. Score ≥2 (out of 3) is the Surviving Sepsis
     * Campaign 2021 pre-hospital sepsis screen threshold.
     */
    const val QSOFA_URGENT_THRESHOLD: Int = 2

    /**
     * Wearable-detectable sepsis screen.
     *
     * SignalRules encode the **3-point red component thresholds** from
     * NEWS2 — each rule represents a component bad enough that, on its
     * own, it lifts NEWS2 to ≥3 (ADVISORY). `minActiveSignals = 1` is
     * deliberate: a single red component is the NHS-defined "low–medium
     * risk" trigger requiring ward-based response. `severityFloor =
     * URGENT` then escalates when the pattern fires alongside other
     * abnormal vitals (the NEWS2 ≥5 / qSOFA ≥2 envelope).
     *
     * The component-by-component summed score (NEWS2 / qSOFA totals) is
     * computed by [Sepsis2NewsCalculator] and exposed to callers that
     * want the actual numeric score — the SignalRule shape is the gate
     * the existing AnomalyDetector knows how to evaluate.
     *
     * No `required = true` on any rule — sepsis often presents with
     * sparse vitals (e.g. RR + HR + temp without a cuff BP). The qSOFA
     * fallback in the calculator covers the BP-missing case.
     *
     * TODO(physiology-state): when a `COPD_HOME_O2` or chronic-illness
     * baseline-elevation state lands in [com.bios.app.physiology.PhysiologyState],
     * add it to `excludedStates` here so stable owners don't accumulate
     * false-positive sepsis alerts from their baseline hypoxia. The audit
     * §2.2 recommendation explicitly calls this out.
     */
    val sepsisScreen = ConditionPattern(
        id = "sepsis_screen",
        title = "Vital signs meet sepsis-screening trigger",
        category = ConditionCategory.INFECTIOUS,
        signalRules = listOf(
            // RR ≤8 OR ≥25 — NEWS2 3-point band (Royal College of
            // Physicians 2017). Also qSOFA-positive at ≥22.
            SignalRule(
                MetricType.RESPIRATORY_RATE, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "Royal College of Physicians (2017) NEWS2 — respiratory rate ≥25/min scores 3 points; also qSOFA-positive at ≥22",
                absoluteAbove = 25.0,
                absoluteWindowHours = ROLLING_WINDOW_HOURS,
                absoluteMinReadings = 1,
            ),
            SignalRule(
                MetricType.RESPIRATORY_RATE, DeviationDirection.BELOW, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "Royal College of Physicians (2017) NEWS2 — respiratory rate ≤8/min scores 3 points (severe bradypnoea, opioid toxicity, exhaustion-phase sepsis)",
                absoluteBelow = 8.0,
                absoluteWindowHours = ROLLING_WINDOW_HOURS,
                absoluteMinReadings = 1,
            ),
            // SpO2 ≤91 % on room air (NEWS2 Scale 1) — 3-point band.
            // Scale 2 (chronic hypoxic respiratory failure) uses a
            // different cutoff; the PhysiologyState TODO above handles
            // that population by exclusion rather than by mid-pattern
            // branching, since Bios cannot reliably infer Scale-2
            // eligibility from readings alone.
            SignalRule(
                MetricType.BLOOD_OXYGEN, DeviationDirection.BELOW, 0.0, 0, 1.2,
                ThresholdSource.LITERATURE,
                "Royal College of Physicians (2017) NEWS2 — SpO2 ≤91 % on room air scores 3 points (Scale 1; Scale 2 applies for documented chronic hypoxic respiratory failure)",
                absoluteBelow = 91.0,
                absoluteWindowHours = ROLLING_WINDOW_HOURS,
                absoluteMinReadings = 1,
            ),
            // SBP ≤90 OR ≥220 — NEWS2 3-point band. ≤100 is the qSOFA
            // hypotension threshold (covered by Sepsis2NewsCalculator).
            SignalRule(
                MetricType.BLOOD_PRESSURE_SYSTOLIC, DeviationDirection.BELOW, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "Royal College of Physicians (2017) NEWS2 — systolic BP ≤90 mmHg scores 3 points (also qSOFA-positive at ≤100); marker of distributive shock",
                absoluteBelow = 90.0,
                absoluteWindowHours = ROLLING_WINDOW_HOURS,
                absoluteMinReadings = 1,
            ),
            SignalRule(
                MetricType.BLOOD_PRESSURE_SYSTOLIC, DeviationDirection.ABOVE, 0.0, 0, 1.0,
                ThresholdSource.LITERATURE,
                "Royal College of Physicians (2017) NEWS2 — systolic BP ≥220 mmHg scores 3 points",
                absoluteAbove = 220.0,
                absoluteWindowHours = ROLLING_WINDOW_HOURS,
                absoluteMinReadings = 1,
            ),
            // HR ≤40 OR ≥131 — NEWS2 3-point band. RESTING_HEART_RATE is
            // the wearable proxy (nightly vendor estimate). The existing
            // [EmergencyVitalPatterns.tachycardiaCritical] /
            // [EmergencyVitalPatterns.bradycardiaCritical] patterns share
            // the same thresholds; this pattern composes them with the
            // other NEWS2 components for the sepsis-specific screen.
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 0.0, 0, 1.2,
                ThresholdSource.LITERATURE,
                "Royal College of Physicians (2017) NEWS2 — heart rate ≥131/min scores 3 points; tachycardia is a hallmark of the sepsis compensatory response",
                absoluteAbove = 131.0,
                absoluteWindowHours = ROLLING_WINDOW_HOURS,
                absoluteMinReadings = 1,
            ),
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.BELOW, 0.0, 0, 1.0,
                ThresholdSource.LITERATURE,
                "Royal College of Physicians (2017) NEWS2 — heart rate ≤40/min scores 3 points (terminal bradycardia in severe sepsis or unrelated conduction disease)",
                absoluteBelow = 40.0,
                absoluteWindowHours = ROLLING_WINDOW_HOURS,
                absoluteMinReadings = 1,
            ),
            // Temperature ≤35.0 °C — NEWS2 3-point band. Hyperthermia
            // (≥39.1) is only a 2-point component in NEWS2 (no 3-point
            // hyperthermia band), so it doesn't appear as a single-rule
            // ADVISORY gate — but it still contributes to the summed
            // score via the calculator.
            SignalRule(
                MetricType.SKIN_TEMPERATURE, DeviationDirection.BELOW, 0.0, 0, 1.2,
                ThresholdSource.LITERATURE,
                "Royal College of Physicians (2017) NEWS2 — temperature ≤35.0 °C scores 3 points (hypothermia is a late and ominous sign in sepsis)",
                absoluteBelow = 35.0,
                absoluteWindowHours = ROLLING_WINDOW_HOURS,
                absoluteMinReadings = 1,
            ),
            // CONSCIOUSNESS_LEVEL — GCS canonical (3–15 total). NEWS2
            // scores 3 points for "AVPU not Alert", which maps to GCS
            // <15. This is also the qSOFA altered-mentation component.
            SignalRule(
                MetricType.CONSCIOUSNESS_LEVEL, DeviationDirection.BELOW, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "Royal College of Physicians (2017) NEWS2 — any reduction in consciousness (GCS <15, AVPU not Alert) scores 3 points; also a qSOFA component (Singer 2016, JAMA)",
                absoluteBelow = 15.0,
                absoluteWindowHours = ROLLING_WINDOW_HOURS,
                absoluteMinReadings = 1,
            ),
        ),
        minActiveSignals = 1,
        severityFloor = AlertTier.URGENT,
        explanation = "Vital signs in the last 6 hours meet the NHS NEWS2 / Sepsis Six screening trigger. NEWS2 (Royal College of Physicians 2017) and qSOFA (Singer 2016, JAMA) are the multi-signal early-warning composites the Surviving Sepsis Campaign uses to flag possible sepsis. This is a data observation against a published clinical screen — not a sepsis diagnosis. Many non-sepsis conditions can produce the same vital-sign pattern (medication effect, dehydration, anxiety, transient illness).",
        suggestedAction = "Vital signs meet the NHS Sepsis Six trigger criteria — seek immediate medical assessment. Bring a recent vital-signs summary (the readings that triggered this alert) for the assessing clinician. If accompanied by fever, rigors, new confusion, severe shortness of breath, or symptoms of infection (cough, urinary symptoms, wound, indwelling line), contact emergency services without delay.",
        references = listOf(
            "Royal College of Physicians (2017) — National Early Warning Score (NEWS) 2: Standardising the assessment of acute-illness severity in the NHS",
            "Singer M et al. (2016) — The Third International Consensus Definitions for Sepsis and Septic Shock (Sepsis-3). JAMA",
            "Seymour CW et al. (2016) — Assessment of Clinical Criteria for Sepsis (qSOFA derivation cohort). JAMA",
            "Evans L et al. (2021) — Surviving Sepsis Campaign: International Guidelines for Management of Sepsis and Septic Shock. Critical Care Medicine",
            "Kumar A et al. (2006) — Duration of hypotension before initiation of effective antimicrobial therapy is the critical determinant of survival in human septic shock. Critical Care Medicine",
        ),
        earlyDetection = "Sepsis often presents with a 24–48 hour window of pre-presentation vital-sign deterioration. NEWS2 captures that envelope: tachypnoea (RR ≥22 is qSOFA-positive, ≥25 is NEWS2 red), tachycardia, fever or paradoxical hypothermia, hypotension, and altered mentation cluster well before culture or imaging confirms the source. The wearable substrate — continuous RHR, nightly SpO2, manually-entered cuff BP and temperature, and the manual-clinical-reading surface for GCS / AVPU — covers every NEWS2 input. The Surviving Sepsis Campaign 2021 guidelines anchor every hour of antibiotic delay in septic shock to measurable mortality increase (Kumar 2006), which is why the screen is high-sensitivity by design.",
        risks = "Untreated sepsis progresses through SIRS → sepsis → severe sepsis → septic shock with mortality climbing at each stage. The populations this screen matters most for: post-surgical owners, immunocompromised owners (chemotherapy, transplant, biologic-DMARD), chronically catheterised owners (indwelling urinary catheter, CVC, PICC, peritoneal dialysis catheter), residents of nursing facilities, and elderly owners whose subjective symptom reports often understate the severity. The Surviving Sepsis Campaign 2021 hour-1 bundle (lactate, cultures, broad-spectrum antibiotics, fluid resuscitation, vasopressors) is the standard early-management protocol — earlier presentation maps to better outcomes.",
        // TODO(physiology-state): the audit explicitly calls for
        // chronic-COPD-on-home-O2 suppression because Scale 2 NEWS2
        // owners baseline at ≥2 on SpO2 alone. PhysiologyState today
        // (PREGNANCY_*, POSTPARTUM, ATHLETE_HIGH_FITNESS, FRAILTY_FLAG,
        // PAEDIATRIC) has no `COPD_HOME_O2` or `CHRONIC_HYPOXIC_RESP_FAILURE`
        // entry. When that lands, add it to excludedStates here. The
        // PAEDIATRIC state is already excluded because NEWS2's component
        // thresholds are validated only for adults — paediatric early-
        // warning scores (PEWS) use age-banded thresholds that don't
        // map onto NEWS2 component bands.
        // NEWS2's component thresholds are validated only for adults —
        // paediatric early-warning scores (PEWS) use age-banded thresholds
        // that don't map onto NEWS2 components. Suppress for every
        // paediatric band, not just the coarse PAEDIATRIC parent (#198).
        excludedStates = com.bios.app.physiology.PhysiologyState.PAEDIATRIC_ALL,
    )
}

/**
 * Pure-Kotlin NEWS2 + qSOFA scoring over time-stamped readings.
 *
 * Encodes the full NEWS2 component-by-component scoring table from the
 * Royal College of Physicians 2017 release and the qSOFA three-component
 * screen from Singer 2016 (JAMA). Both functions operate on a list of
 * [Reading]s and return the integer composite — exactly what a
 * clinician-export view or diagnostics surface would show alongside the
 * raw component breakdown.
 *
 * The functions are intentionally side-effect-free: they don't reach into
 * the database, they don't compute baselines, they take raw values in
 * canonical units and return an integer score. This shape makes the
 * scoring math directly unit-testable.
 *
 * Component selection within the rolling window picks the **most-recent
 * reading per metric**, matching NHS clinical practice (the most recent
 * observation set drives the score) and the audit recommendation.
 *
 * Notes:
 *  - SpO2 uses NEWS2 **Scale 1** (room air). Scale 2 (chronic hypoxic
 *    respiratory failure) is handled by suppressing the pattern entirely
 *    for owners in the future `COPD_HOME_O2` PhysiologyState — see the
 *    TODO on [SepsisScreenPattern.sepsisScreen].
 *  - OXYGEN_FLOW_RATE is treated as the "on supplemental oxygen" flag for
 *    the +2 air/O2 component (any reading >0 in the window counts as
 *    "on O2"). Zero or absent = on room air.
 *  - CONSCIOUSNESS_LEVEL uses the GCS total (3–15). NEWS2 scores 3 for
 *    "AVPU not A" which corresponds to GCS <15. qSOFA uses the same
 *    altered-mentation criterion.
 *  - SKIN_TEMPERATURE is the closest available wearable / manual proxy
 *    for core temperature. Wearable wrist-skin temperature is offset
 *    from core; for the purposes of the NEWS2 band the calculator uses
 *    the raw recorded value (the owner / clinician is expected to enter
 *    a core measurement when one is available via the manual-entry
 *    surface; vendor adapters writing wrist-skin temperature will tend
 *    to under-trigger rather than over-trigger).
 */
object Sepsis2NewsCalculator {

    /**
     * Minimal time-stamped numeric reading shape. Decoupled from
     * [com.bios.app.model.MetricReading] so the calculator is testable
     * without database / SQLCipher fixtures.
     */
    data class Reading(val metricType: MetricType, val value: Double, val timestampMillis: Long)

    /**
     * Composite NEWS2 score over [readings] filtered to the
     * [windowHours]-hour window ending at [nowMillis]. Picks the most
     * recent value per metric within the window; returns 0 when no
     * scorable readings are present.
     */
    fun computeNews2(
        readings: List<Reading>,
        nowMillis: Long = System.currentTimeMillis(),
        windowHours: Int = SepsisScreenPattern.ROLLING_WINDOW_HOURS,
    ): Int {
        val latest = latestPerMetric(readings, nowMillis, windowHours)
        var score = 0
        latest[MetricType.RESPIRATORY_RATE]?.let { score += news2RespiratoryRate(it) }
        latest[MetricType.BLOOD_OXYGEN]?.let { score += news2Spo2Scale1(it) }
        val onO2 = latest[MetricType.OXYGEN_FLOW_RATE]?.let { it > 0.0 } == true
        if (onO2) score += 2
        latest[MetricType.SKIN_TEMPERATURE]?.let { score += news2Temperature(it) }
        latest[MetricType.BLOOD_PRESSURE_SYSTOLIC]?.let { score += news2SystolicBp(it) }
        latest[MetricType.RESTING_HEART_RATE]?.let { score += news2HeartRate(it) }
        latest[MetricType.CONSCIOUSNESS_LEVEL]?.let { score += news2Consciousness(it) }
        return score
    }

    /**
     * qSOFA composite over the same window. Returns 0–3. Used as the
     * NEWS2 fallback when wearable inputs are sparse: NEWS2 needs RR /
     * SpO2 / BP / HR / temp / consciousness to be meaningful; qSOFA only
     * needs RR + SBP + consciousness (Singer 2016, JAMA).
     */
    fun computeQsofa(
        readings: List<Reading>,
        nowMillis: Long = System.currentTimeMillis(),
        windowHours: Int = SepsisScreenPattern.ROLLING_WINDOW_HOURS,
    ): Int {
        val latest = latestPerMetric(readings, nowMillis, windowHours)
        var score = 0
        if ((latest[MetricType.RESPIRATORY_RATE] ?: 0.0) >= 22.0) score++
        latest[MetricType.BLOOD_PRESSURE_SYSTOLIC]?.let { if (it <= 100.0) score++ }
        latest[MetricType.CONSCIOUSNESS_LEVEL]?.let { if (it < 15.0) score++ }
        return score
    }

    /**
     * Resolves the URGENT / ADVISORY decision combining NEWS2 + qSOFA
     * (qSOFA covers the BP-or-RR-missing sparse-vitals case the audit
     * called out explicitly). Returns `null` when neither composite
     * crosses its threshold.
     */
    fun classify(
        readings: List<Reading>,
        nowMillis: Long = System.currentTimeMillis(),
        windowHours: Int = SepsisScreenPattern.ROLLING_WINDOW_HOURS,
    ): AlertTier? {
        val news2 = computeNews2(readings, nowMillis, windowHours)
        val qsofa = computeQsofa(readings, nowMillis, windowHours)
        return when {
            news2 >= SepsisScreenPattern.NEWS2_URGENT_THRESHOLD -> AlertTier.URGENT
            qsofa >= SepsisScreenPattern.QSOFA_URGENT_THRESHOLD -> AlertTier.URGENT
            news2 >= SepsisScreenPattern.NEWS2_ADVISORY_THRESHOLD -> AlertTier.ADVISORY
            else -> null
        }
    }

    // -- NEWS2 component scoring (Royal College of Physicians 2017) --

    internal fun news2RespiratoryRate(rr: Double): Int = when {
        rr <= 8.0 -> 3
        rr <= 11.0 -> 1
        rr <= 20.0 -> 0
        rr <= 24.0 -> 2
        else -> 3 // ≥25
    }

    /** NEWS2 Scale 1 — room-air SpO2. */
    internal fun news2Spo2Scale1(spo2: Double): Int = when {
        spo2 <= 91.0 -> 3
        spo2 <= 93.0 -> 2
        spo2 <= 95.0 -> 1
        else -> 0
    }

    internal fun news2Temperature(tempC: Double): Int = when {
        tempC <= 35.0 -> 3
        tempC <= 36.0 -> 1
        tempC <= 38.0 -> 0
        tempC <= 39.0 -> 1
        else -> 2 // ≥39.1
    }

    internal fun news2SystolicBp(sbp: Double): Int = when {
        sbp <= 90.0 -> 3
        sbp <= 100.0 -> 2
        sbp <= 110.0 -> 1
        sbp <= 219.0 -> 0
        else -> 3 // ≥220
    }

    internal fun news2HeartRate(hr: Double): Int = when {
        hr <= 40.0 -> 3
        hr <= 50.0 -> 1
        hr <= 90.0 -> 0
        hr <= 110.0 -> 1
        hr <= 130.0 -> 2
        else -> 3 // ≥131
    }

    /**
     * NEWS2 "AVPU not A" → 3 points. CONSCIOUSNESS_LEVEL is GCS-canonical
     * 3–15 with AVPU lossless encoded as A→15 / V→13 / P→8 / U→3 per
     * [MetricType.CONSCIOUSNESS_LEVEL]. Anything < 15 means not fully
     * alert and scores 3.
     */
    internal fun news2Consciousness(gcs: Double): Int = if (gcs < 15.0) 3 else 0

    /**
     * Most-recent reading per metric within the window. Readings outside
     * the window are dropped; metrics absent from the input are absent
     * from the result map (so callers can branch on availability).
     */
    private fun latestPerMetric(
        readings: List<Reading>,
        nowMillis: Long,
        windowHours: Int,
    ): Map<MetricType, Double> {
        val cutoff = nowMillis - windowHours.toLong() * 3600L * 1000L
        return readings.asSequence()
            .filter { it.timestampMillis in cutoff..nowMillis }
            .groupBy { it.metricType }
            .mapValues { (_, readings) -> readings.maxBy { it.timestampMillis }.value }
    }
}
