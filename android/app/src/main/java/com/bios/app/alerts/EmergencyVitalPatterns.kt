package com.bios.app.alerts

import com.bios.app.model.AlertTier
import com.bios.app.model.ConditionCategory
import com.bios.app.model.ElevationBand
import com.bios.app.physiology.PhysiologyState
import com.bios.contracts.MetricType

/**
 * Hard-cutoff vital-sign patterns that fire on a single latest reading
 * crossing a literature-anchored clinical threshold. Distinct from the
 * baseline-relative trend patterns in [ConditionPatterns] — those describe
 * a *deviation from this individual's normal*; these describe a *value that
 * is dangerous regardless of personal baseline*. Closes audit gap §2.1:
 * `AlertTier.URGENT` was previously unreachable in code.
 *
 * Each pattern uses a single `required = true` absolute-threshold rule
 * (the same mechanism the biomarker patterns built in Phase 8.6), and
 * declares [ConditionPattern.severityFloor] = `URGENT` so a single
 * crossing escalates above the classifier's `ADVISORY` cap.
 *
 * Scope discipline (per the audit issue): v1 ships single-reading cutoffs
 * only. `RESTING_HEART_RATE` and `BLOOD_OXYGEN` from consumer wearables
 * are already vendor-smoothed (RHR is a nightly estimate, SpO2 is an
 * averaged spot reading), so a single-reading trigger is clinically
 * meaningful. A sustained-window variant ("≥5 min below 90 %") is a
 * possible follow-up if the single-reading cutoffs prove noisy in practice.
 *
 * Hypertensive urgency (BP ≥180/120) lives in its own pattern in issue #153
 * because it needs a second-reading re-check path to avoid white-coat
 * false positives — different shape from the unambiguous-value cutoffs
 * here.
 *
 * Medication-aware suppression (a beta-blocker user's RHR of 38 is not
 * the same clinical event as an opioid-induced bradycardia at 38) is
 * tracked in issue #154 and will gate these patterns when the medication
 * annotation surface ships.
 *
 * All text obeys [AlertContentPolicy] — data statements plus professional-
 * referral language. No second-person judgment.
 */
object EmergencyVitalPatterns {

    val all by lazy {
        listOf(
            spo2Critical,
            hypoglycemiaCritical,
            tachycardiaCritical,
            bradycardiaCritical,
        ) + PaediatricEmergencyVitalPatterns.all
    }

    /**
     * SpO2 ≤85 % at sea level; elevation-adjusted by [ElevationBand] per
     * West JB (2010) *High Altitude Medicine and Physiology* and the
     * SOWA_RIGPA audit §2.1 (#197). At 4000 m an acclimatised herder may
     * baseline at 84–86 % without pathology; firing URGENT on those
     * readings would be clinically incorrect for the Sowa Rigpa catchment
     * (Tibet, Bhutan, Mongolia, Nepal, Andean Peru/Bolivia/Ecuador). The
     * adjusted floors below preserve a "this is true hypoxia regardless of
     * acclimatisation" semantics at every band.
     *
     * Band-specific cutoffs (West 2010; Subudhi 2014 — military mountain
     * medicine; Beall 2014 — Andean/Tibetan high-altitude adaptation):
     *  - SEA_LEVEL       — ≤85 % (unchanged baseline)
     *  - MODERATE        — ≤83 % (mild downward shift)
     *  - HIGH            — ≤82 %
     *  - VERY_HIGH       — ≤80 %
     *  - EXTREME (>3500) — ≤78 % (Beall 2014 Tibetan natives baseline ~88 %)
     */
    val spo2Critical = ConditionPattern(
        id = "emergency_spo2_critical",
        title = "Critically low blood oxygen reading",
        category = ConditionCategory.RESPIRATORY,
        signalRules = listOf(
            SignalRule(
                MetricType.BLOOD_OXYGEN, DeviationDirection.BELOW, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "WHO 2011 pulse oximetry guidance + West JB 2010 — SpO2 cutoff is elevation-adjusted: ≤85 % at sea level, ≤80 % at very-high altitude, ≤78 % above 3500 m (acclimatised resident baselines)",
                required = true,
                absoluteBelow = 85.0,
                elevationAdjustedBelow = mapOf(
                    ElevationBand.SEA_LEVEL to 85.0,
                    ElevationBand.MODERATE to 83.0,
                    ElevationBand.HIGH to 82.0,
                    ElevationBand.VERY_HIGH to 80.0,
                    ElevationBand.EXTREME to 78.0,
                ),
            ),
        ),
        minActiveSignals = 1,
        severityFloor = AlertTier.URGENT,
        explanation = "The most recent blood-oxygen reading is at or below the elevation-adjusted clinical floor for the owner's recorded altitude. At sea level the floor is 85 %; above 3500 m it is 78 %, reflecting normal acclimatised resident baselines. Motion artifact, cold fingers, or nail polish can produce false low readings — re-check on a warm, clean finger.",
        suggestedAction = "Re-check the reading on a warm clean finger without nail polish. If confirmed at or below the adjusted floor, or accompanied by shortness of breath, chest pain, confusion, bluish lips, or fingertips, seek immediate medical attention or contact emergency services.",
        references = listOf(
            "WHO (2011) — Pulse Oximetry Training Manual",
            "West JB (2010) — High Altitude Medicine and Physiology, 5th ed.",
            "Beall CM (2014) — Adaptation to high altitude: phenotypes and genotypes",
            "ATS/ACCP — Pulmonary rehabilitation: joint statement",
        ),
        earlyDetection = "A single SpO2 reading at or below the elevation-adjusted cutoff is the standard ED triage threshold for severe hypoxia. Wearable optical sensors can produce false-low readings under motion, cold, or poor perfusion — but a confirmed reading at this level warrants the same response as a clinical pulse-oximetry reading. The cutoff drops with elevation because acclimatised residents at altitude maintain steady-state oxygen delivery at lower saturations than sea-level residents.",
    )

    /**
     * Blood glucose ≤54 mg/dL. ADA "Level 2" hypoglycemia — the clinically
     * significant threshold below which immediate correction is indicated.
     * Common in insulin-treated diabetes; far less common but possible
     * in non-diabetic owners (reactive hypoglycemia, insulinoma, post-
     * bariatric, alcohol-related).
     */
    val hypoglycemiaCritical = ConditionPattern(
        id = "emergency_hypoglycemia_critical",
        title = "Critically low blood glucose reading",
        category = ConditionCategory.METABOLIC,
        signalRules = listOf(
            SignalRule(
                MetricType.BLOOD_GLUCOSE, DeviationDirection.BELOW, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "ADA 2024 — blood glucose ≤54 mg/dL (3.0 mmol/L) is Level 2 hypoglycemia, the clinically significant threshold requiring immediate correction",
                required = true,
                absoluteBelow = 54.0,
            ),
        ),
        minActiveSignals = 1,
        severityFloor = AlertTier.URGENT,
        explanation = "The most recent blood-glucose reading is at or below 54 mg/dL (3.0 mmol/L). ADA classifies this as Level 2 hypoglycemia, the threshold for clinically significant low glucose requiring immediate correction.",
        suggestedAction = "Consume 15–20 g of fast-acting carbohydrate (glucose tablets, juice, or sugar) and re-check in 15 minutes. Repeat if still below 70 mg/dL. If unable to swallow, confused, or unresponsive, glucagon or emergency services are needed.",
        references = listOf(
            "American Diabetes Association (2024) — Standards of Medical Care in Diabetes, Section 6: Glycemic Targets",
            "International Hypoglycaemia Study Group (2017) — Glucose concentrations of less than 3.0 mmol/L",
        ),
        earlyDetection = "Level 2 hypoglycemia (≤54 mg/dL) is the threshold at which cognitive impairment, autonomic symptoms, and risk of progression to severe hypoglycemia rise sharply. CGM-equipped owners on insulin should respond as they would to a clinical low; non-diabetic owners with a confirmed reading at this level should investigate the cause with a clinician.",
    )

    /**
     * Resting heart rate ≥130 bpm. A nightly-estimated RHR at this level is
     * far outside normal adult variation and is the threshold at which
     * AFib with rapid ventricular response, SVT, thyrotoxicosis, severe
     * dehydration, and sepsis become differential considerations. Note:
     * RESTING_HEART_RATE is a vendor-derived nightly estimate, not a
     * continuous reading — a single reading at this level represents a
     * sustained-rest value, not transient exertion.
     */
    val tachycardiaCritical = ConditionPattern(
        id = "emergency_tachycardia_critical",
        title = "Critically elevated resting heart rate",
        category = ConditionCategory.CARDIOVASCULAR,
        signalRules = listOf(
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "AHA/ACC — resting heart rate ≥130 bpm in adults at rest is the threshold for evaluating tachyarrhythmia (AFib RVR, SVT), thyrotoxicosis, sepsis, or severe volume depletion",
                required = true,
                absoluteAbove = 130.0,
            ),
        ),
        minActiveSignals = 1,
        severityFloor = AlertTier.URGENT,
        explanation = "The most recent resting heart rate is at or above 130 bpm. This is outside normal adult variation and is the standard clinical threshold for evaluating tachyarrhythmia (such as atrial fibrillation with rapid ventricular response or supraventricular tachycardia), thyrotoxicosis, severe dehydration, infection, or other systemic causes.",
        suggestedAction = "If accompanied by chest pain, shortness of breath, dizziness, fainting, or palpitations, seek immediate medical attention. Without acute symptoms, re-check at rest after several minutes; a sustained reading at this level warrants prompt evaluation by a healthcare provider.",
        references = listOf(
            "January CT et al. (2014) — AHA/ACC/HRS guideline for management of atrial fibrillation",
            "Page RL et al. (2015) — ACC/AHA/HRS guideline for the management of adult patients with supraventricular tachycardia",
        ),
        earlyDetection = "Wearable resting-HR estimates are nightly averages, not transient readings — a single estimate at or above 130 bpm reflects sustained tachycardia at rest, not exertion. AFib with rapid ventricular response, SVT, hyperthyroidism, infection, and severe dehydration all present this way. Clinical confirmation (ECG, labs) is the next step.",
        // Adult cutoff (≥130) is wrong for children — a healthy toddler's
        // resting HR routinely sits at 120. PALS-anchored paediatric
        // patterns ship in PaediatricEmergencyVitalPatterns with band-
        // specific ceilings (NEONATE ≥220, INFANT ≥200, TODDLER ≥180,
        // PRESCHOOL ≥160, SCHOOL ≥140, ADOLESCENT ≥135). Issue #198.
        excludedStates = PhysiologyState.PAEDIATRIC_ALL,
    )

    /**
     * Resting heart rate ≤35 bpm without medication context. The threshold
     * below which symptomatic bradycardia, third-degree heart block, sick
     * sinus syndrome, or severe hypothyroidism become differential
     * considerations in an adult without known beta-blocker / CCB therapy.
     * Note: highly fit endurance athletes can have true resting HR in the
     * 30s — the PhysiologyState gating in issue #159 will exempt the
     * `ATHLETE_HIGH_FITNESS` state from this pattern; the medication
     * annotation surface in issue #154 will suppress it for documented
     * rate-control therapy.
     */
    val bradycardiaCritical = ConditionPattern(
        id = "emergency_bradycardia_critical",
        title = "Critically low resting heart rate",
        category = ConditionCategory.CARDIOVASCULAR,
        signalRules = listOf(
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.BELOW, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "AHA/ACC — sustained resting heart rate ≤35 bpm in adults (outside athletic conditioning or rate-control therapy) is the threshold for evaluating symptomatic bradycardia, heart block, or severe hypothyroidism",
                required = true,
                absoluteBelow = 35.0,
            ),
        ),
        minActiveSignals = 1,
        severityFloor = AlertTier.URGENT,
        explanation = "The most recent resting heart rate is at or below 35 bpm. This is below the threshold at which symptomatic bradycardia, conduction-system disease (such as high-grade AV block or sick sinus syndrome), or severe hypothyroidism are clinical considerations. Highly trained endurance athletes can have true resting heart rates in this range without pathology; documented rate-control medication (beta-blockers, certain calcium channel blockers) can also explain this reading.",
        suggestedAction = "If accompanied by dizziness, fainting, fatigue, shortness of breath, or chest pain, seek immediate medical attention. Without acute symptoms, an ECG and clinical evaluation are the standard next step to distinguish athletic conditioning from a conduction problem.",
        references = listOf(
            "Kusumoto FM et al. (2018) — ACC/AHA/HRS guideline on the evaluation and management of patients with bradycardia and cardiac conduction delay",
            "Klein I, Ojamaa K (2001) — Thyroid hormone and the cardiovascular system",
        ),
        earlyDetection = "A wearable resting-HR estimate at or below 35 bpm is an unusual finding in adults outside elite endurance conditioning. Conduction-system disease often presents with this kind of asymptomatic-or-mildly-symptomatic bradycardia before progressing to symptomatic episodes; ECG is the definitive next step.",
        // Adult floor (≤35) stays silent on real bradycardia in children
        // — a neonate at 80 bpm is in bradycardia; the adult cutoff
        // never triggers. PALS-anchored paediatric floors ship in
        // PaediatricEmergencyVitalPatterns (NEONATE ≤80, INFANT ≤60,
        // TODDLER ≤50, PRESCHOOL ≤45, SCHOOL ≤40, ADOLESCENT ≤40).
        // Issue #198.
        excludedStates = PhysiologyState.PAEDIATRIC_ALL,
    )
}
