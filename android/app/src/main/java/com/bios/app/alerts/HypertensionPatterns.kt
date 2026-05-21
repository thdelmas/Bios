package com.bios.app.alerts

import com.bios.app.model.AlertTier
import com.bios.app.model.ConditionCategory
import com.bios.contracts.MetricType

/**
 * Blood-pressure-anchored condition patterns. Closes audit gap §2.4: BP is
 * already ingested (Withings cuff, Samsung BP, manual entry) and the
 * [MetricType.BLOOD_PRESSURE_SYSTOLIC] / [MetricType.BLOOD_PRESSURE_DIASTOLIC]
 * keys are in the schema, but no condition pattern was gated on BP. Hyper-
 * tension is the single highest-impact modifiable preventive target in every
 * regional guideline; this file adds the two patterns that close that gap:
 *
 *  - [hypertensionEmerging] — sustained multi-day elevation pattern. Median
 *    of recent readings against an absolute cutoff, paired with a wearable
 *    corroborator. White-coat-robust via the median + minimum-readings gate
 *    that landed on [SignalRule] in this PR.
 *  - [hypertensiveUrgency] — single reading at hypertensive-crisis level
 *    (≥180/120). Promotes to [AlertTier.URGENT] via the `severityFloor`
 *    mechanism that landed in [EmergencyVitalPatterns] (issue #152).
 *
 * **Threshold sourcing.** Stage-1 hypertension differs by jurisdiction
 * (ACC-AHA 2017: 130/80; NICE / ESC / JSH / Hypertension Canada: 140/90).
 * The audit issue calls for region-aware thresholds, but the pattern
 * registry today is a single static `object`-scope list — restructuring
 * `ConditionPatterns.all` to be region-aware is broader than this PR. v1
 * uses the **most conservative universal floor** (130/80, ACC-AHA 2017):
 * owners in 140/90 jurisdictions see the pattern fire ~10 mmHg earlier
 * than their local guideline, which is clinically defensible (catches
 * more potential hypertension; suggested-action remains "discuss with a
 * provider" — the provider applies the local guideline). Region-aware
 * thresholds tracked as a follow-up to issue #153.
 *
 * The **hypertensive-crisis** threshold (180/120) is universal across
 * ACC-AHA, NICE, ESC, JSH, Hypertension Canada, AACE, and AHA crisis
 * protocols, so no region dependency.
 *
 * All text obeys [AlertContentPolicy] — data statements plus professional
 * referral.
 */
object HypertensionPatterns {

    val all by lazy {
        listOf(
            hypertensionEmerging,
            hypertensiveUrgency,
        )
    }

    /**
     * Sustained elevated BP across a 7-day window, median ≥130 systolic *or*
     * ≥80 diastolic (covers isolated systolic hypertension common in older
     * adults), with at least 3 readings in the window and a wearable RHR
     * corroborator. The 3-reading floor + median check is the standard
     * clinical guard against single white-coat false positives.
     *
     * Both BP rules are non-required so isolated systolic, isolated diastolic,
     * and combined hypertension all surface; one BP rule + RHR corroborator,
     * or both BP rules together, fires the pattern.
     */
    val hypertensionEmerging = ConditionPattern(
        id = "hypertension_emerging",
        title = "Sustained elevated blood pressure readings",
        category = ConditionCategory.CARDIOVASCULAR,
        signalRules = listOf(
            SignalRule(
                MetricType.BLOOD_PRESSURE_SYSTOLIC, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "ACC/AHA 2017 — median systolic BP ≥130 mmHg across multiple home readings is the stage-1 hypertension threshold (NICE/ESC/JSH use 140; 130 is the conservative universal floor)",
                absoluteAbove = 130.0,
                absoluteWindowHours = 168,
                absoluteMinReadings = 3,
            ),
            SignalRule(
                MetricType.BLOOD_PRESSURE_DIASTOLIC, DeviationDirection.ABOVE, 0.0, 0, 1.2,
                ThresholdSource.LITERATURE,
                "ACC/AHA 2017 — median diastolic BP ≥80 mmHg across multiple home readings is the stage-1 hypertension threshold (NICE/ESC/JSH use 90; 80 is the conservative universal floor)",
                absoluteAbove = 80.0,
                absoluteWindowHours = 168,
                absoluteMinReadings = 3,
            ),
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.0, 168, 0.8,
                ThresholdSource.LITERATURE,
                "Aune et al. 2017 — sustained resting-HR elevation alongside elevated BP corroborates cardiovascular load and metabolic-syndrome risk",
            ),
        ),
        minActiveSignals = 2,
        explanation = "The median of recent blood pressure readings sits at or above stage-1 hypertension thresholds (≥130 systolic or ≥80 diastolic per ACC/AHA 2017; regional guidelines such as NICE / ESC / JSH use 140/90). The median across multiple home readings is more reliable than any single cuff measurement, which can be affected by white-coat effect, posture, or recent activity.",
        suggestedAction = "Discuss these readings with a healthcare provider. Home BP monitoring over 7 days (morning and evening) is the standard clinical follow-up, and the trend data from Bios is useful to share. The provider applies the regional guideline cutoff for diagnosis.",
        references = listOf(
            "Whelton PK et al. (2017) — ACC/AHA Guideline for the Prevention, Detection, Evaluation, and Management of High Blood Pressure in Adults",
            "Williams B et al. (2018) — ESC/ESH Guidelines for the management of arterial hypertension",
            "Aune D et al. (2017) — Resting heart rate and the risk of cardiovascular disease, total cancer, and all-cause mortality",
        ),
        earlyDetection = "Hypertension is silent for years; the elevated readings precede any symptom by decades on average. The earliest detectable signal is sustained median BP above the stage-1 threshold, especially when paired with an upward drift in resting heart rate. Bios uses a 7-day window and the median (not the average) so a single white-coat reading doesn't pull the signal — three or more readings spread over the window with a median above the cutoff is the gate.",
        prevention = "Modifiable risk factors: sodium intake (especially processed foods), alcohol consumption, body weight, sleep quality, untreated sleep apnea, sedentary lifestyle, chronic stress, and tobacco use. Each lowered by a meaningful amount lowers BP. Regular moderate aerobic activity (150 min/week) and the DASH dietary pattern have the largest evidence base. Home BP monitoring is itself a protective intervention — awareness alone modestly improves outcomes.",
        healing = "Stage-1 hypertension is typically addressed with lifestyle modification first (3–6 month trial) unless 10-year cardiovascular risk is elevated, in which case a clinician may add pharmacotherapy earlier. The lifestyle changes that work: dietary sodium reduction to <2300 mg/day, the DASH eating pattern, weight loss if BMI elevated, regular aerobic exercise, alcohol moderation, treating sleep apnea, smoking cessation. Pharmacotherapy when needed is well-tolerated and effective — most owners reach target on a single agent. Regular home BP monitoring helps confirm response and detect medication effects.",
        risks = "Untreated hypertension is the single strongest modifiable risk factor for stroke, heart failure, atrial fibrillation, kidney disease, and aortic disease. Each 10 mmHg systolic reduction lowers cardiovascular event risk by approximately 20 %. Hypertension is silent until end-organ damage manifests, which is why detection during the asymptomatic phase matters disproportionately — it is the highest-yield single intervention preventive medicine offers.",
    )

    /**
     * Single-reading hypertensive crisis: systolic ≥180 *or* diastolic ≥120.
     * Universal across all major guidelines (ACC-AHA, NICE, ESC, JSH,
     * Hypertension Canada, AHA crisis protocols) — no region dependency.
     *
     * The "re-check within 5 minutes and seek care if confirmed" guidance is
     * the explicit clinical protocol; surfaced in suggestedAction. Both rules
     * are non-required and `minActiveSignals = 1` so either crossing the
     * threshold fires the pattern (OR semantics).
     *
     * Lives in a single-reading shape (no `absoluteWindowHours`) because at
     * this magnitude the reading itself is the indication to re-check and
     * potentially seek care; waiting for additional readings would delay the
     * actionable signal.
     */
    val hypertensiveUrgency = ConditionPattern(
        id = "hypertensive_urgency",
        title = "Critically elevated blood pressure reading",
        category = ConditionCategory.CARDIOVASCULAR,
        signalRules = listOf(
            SignalRule(
                MetricType.BLOOD_PRESSURE_SYSTOLIC, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "ACC/AHA 2017 + AHA crisis protocols — systolic BP ≥180 mmHg is the hypertensive-crisis threshold (universal across ACC-AHA, NICE, ESC, JSH guidelines)",
                absoluteAbove = 180.0,
            ),
            SignalRule(
                MetricType.BLOOD_PRESSURE_DIASTOLIC, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "ACC/AHA 2017 + AHA crisis protocols — diastolic BP ≥120 mmHg is the hypertensive-crisis threshold (universal across guidelines)",
                absoluteAbove = 120.0,
            ),
        ),
        minActiveSignals = 1,
        severityFloor = AlertTier.URGENT,
        explanation = "A recent blood pressure reading is at or above the hypertensive-crisis threshold (≥180 systolic or ≥120 diastolic). This is the universal cutoff across ACC/AHA, NICE, ESC, JSH, and Hypertension Canada guidelines. Single cuff readings at this level can occur from recent exertion, stress, or measurement technique — the clinical protocol is to re-check after a few minutes of rest before treating it as confirmed.",
        suggestedAction = "Rest for 5 minutes, then re-check on the opposite arm with the cuff at heart level. If the reading is confirmed at or above 180/120, or accompanied by chest pain, shortness of breath, severe headache, vision changes, or weakness, seek immediate medical attention or contact emergency services. Without acute symptoms but with a confirmed reading at this level, urgent (same-day) clinical evaluation is the standard.",
        references = listOf(
            "Whelton PK et al. (2017) — ACC/AHA Guideline (hypertensive urgency/emergency thresholds)",
            "Williams B et al. (2018) — ESC/ESH Guidelines (hypertensive crisis definitions)",
            "van den Born B-JH et al. (2019) — ESC position paper on hypertensive emergencies",
        ),
        earlyDetection = "Hypertensive crisis is the threshold at which end-organ damage (stroke, MI, aortic dissection, acute kidney injury, hypertensive encephalopathy) becomes a near-term risk. A single confirmed reading at or above 180/120 — regardless of personal baseline — is the clinical action threshold. White-coat effect can occasionally produce a single reading at this level even in normotensive owners, which is why the protocol is re-check first, then act.",
    )
}
