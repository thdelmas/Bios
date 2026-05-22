package com.bios.app.alerts

import com.bios.app.model.ConditionCategory
import com.bios.contracts.MetricType

/**
 * Autonomic-pattern shift (issue #180, cardiology audit §2.1).
 *
 * Previously lived on [ConditionPatterns] as `atrialFibrillationScreen` —
 * a misleading name for what is actually a personal-baseline z-score over
 * HRV + RHR + SpO2. The cardiology audit flagged the mismatch: the
 * Apple Heart Study (Perez 2019), mAFA-II (Guo 2019), and Fitbit Heart
 * Study (Lubitz 2022) all classify the **PPG-derived RR series itself**
 * as regular or irregularly irregular; an HRV z-score is a different
 * physiological event. The real PPG-derived AFib screen now lives in
 * [AfibRhythmPattern.paroxysmalAfibScreen], gated on the
 * [MetricType.IRREGULAR_RHYTHM_BURDEN] derived metric.
 *
 * This pattern is still useful — autonomic shifts of the kind it detects
 * can accompany infection, dehydration, alcohol withdrawal, thyrotoxicosis,
 * and post-vaccination perturbations. The rename simply makes the framing
 * match the physiology.
 *
 * Lives in its own file (same convention as [SleepApneaPattern] and
 * [HypertensionPatterns]) to keep [ConditionPatterns] under the 500-line
 * cap. All text obeys [AlertContentPolicy].
 */
object AutonomicPatternShiftPattern {

    val all by lazy { listOf(autonomicPatternShift) }

    val autonomicPatternShift = ConditionPattern(
        id = "autonomic_pattern_shift",
        title = "Autonomic pattern shift detected",
        category = ConditionCategory.CARDIOVASCULAR,
        signalRules = listOf(
            SignalRule(
                MetricType.HEART_RATE_VARIABILITY, DeviationDirection.IRREGULAR, 2.0, 12, 1.5,
                ThresholdSource.LITERATURE,
                "Plews et al. (2013) — HRV irregularity above 2σ flags acute autonomic perturbation",
            ),
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.5, 12, 1.0,
                ThresholdSource.LITERATURE,
                "Quer et al. (2021) — resting-HR elevation accompanies the autonomic stress signature",
            ),
            SignalRule(
                MetricType.BLOOD_OXYGEN, DeviationDirection.BELOW, 1.0, 12, 0.6,
                ThresholdSource.ENGINEERING,
            ),
        ),
        minActiveSignals = 2,
        explanation = "Heart-rate variability is showing baseline-relative irregularity alongside a sustained resting-HR elevation. This autonomic pattern is non-specific — infection, dehydration, alcohol withdrawal, thyroid perturbation, and post-vaccination autonomic shifts can all produce it.",
        suggestedAction = "Track how you feel over the next 24–48 hours. If palpitations, chest discomfort, dizziness, or shortness of breath develop, contact a healthcare provider. This pattern is not a rhythm-strip analysis — see the AFib screening surface for the PPG-derived irregular-rhythm burden.",
        references = listOf(
            "Plews DJ et al. (2013) — Training adaptation and HRV in elite endurance athletes",
            "Quer G et al. (2021) — Wearable sensor data and self-reported symptoms",
        ),
        earlyDetection = "Acute autonomic shifts produce a recognisable wearable signature: HRV widens, resting heart rate climbs, and SpO2 may drift mildly downward. The combination is sensitive but non-specific. Bios surfaces this pattern as an observation; the PPG-derived AFib screen (which classifies the RR-interval series itself) is a separate surface.",
        prevention = "Maintain hydration, consistent sleep, and moderate alcohol intake — all stabilise autonomic balance. Treat infections early. Manage chronic stress and avoid sudden training-load jumps. Periodic blood-pressure and resting-HR checks establish a personal baseline that makes shifts easier to interpret.",
        healing = "Rest, hydration, and watching for symptoms over the next 24–48 hours is the first step. If symptoms develop or persist — palpitations, lightheadedness, breathlessness, chest pain — clinical evaluation is the appropriate next step. The pull-side detail view shows the HRV and resting-HR trends.",
        risks = "An isolated autonomic-pattern shift is rarely dangerous on its own — most resolve as the underlying perturbation does. Persistent shifts over weeks warrant clinical evaluation: chronic autonomic imbalance is associated with sustained cardiovascular load, impaired recovery, and reduced exercise tolerance.",
    )
}
