package com.bios.app.alerts

import com.bios.app.model.ConditionCategory
import com.bios.contracts.MetricType

/**
 * Wave 6 biomarker condition patterns (#203, audit gap convergence across
 * MEDICAL_PROFESSIONAL_POV §2.8, ONCOLOGY_POV §2.3, CARDIOLOGY_POV §2.11,
 * MODERN_NON_ALLOPATHIC_POV §2.3, GERIATRICS_PALLIATIVE_POV) — focused on
 * sustained-decline screening for the new renal and hepatic panel
 * extensions.
 *
 * The earlier Wave 5 [ckdProgressionSignature] / [nafldSignature] patterns
 * gate on a single lab cross-section. Wave 6 layers a more conservative
 * *trend* check: KDIGO 2024 and AASLD 2021 both require the abnormal
 * finding to persist across multiple measurements before the diagnostic
 * label is appropriate. These two patterns are intentionally narrow —
 * they require a sustained-elevation window and shouldn't false-fire on
 * a one-off draw. ADVISORY tier; no nudges, no judgments. Evaluation
 * belongs to the owner.
 *
 * Both patterns live in their own file to keep [BiomarkerConditionPatterns]
 * under the 500-line cap.
 */
object Wave6BiomarkerPatterns {

    val all by lazy {
        listOf(
            renalFunctionDeclineScreen,
            hepaticInjuryScreen,
        )
    }

    /**
     * eGFR sustained <60 mL/min/1.73m² with corroborating creatinine
     * elevation. KDIGO 2024 §1.1: CKD diagnosis requires the abnormal
     * eGFR to persist at least 3 months *or* be paired with markers of
     * kidney damage (albuminuria, structural abnormalities). This pattern
     * is a *screening* signal — pairs eGFR <60 with creatinine >1.3 mg/dL
     * so a one-off contrast-induced bump or transient AKI doesn't fire.
     *
     * The earlier [Wave5BiomarkerPatterns.ckdProgressionSignature] also
     * gates on eGFR; this pattern differs by requiring the creatinine
     * corroborator (vs. Wave 5's optional corroborators) and a slightly
     * tighter eGFR cutoff. The two patterns can co-fire — that's
     * intentional: independent confirmation across two patterns is
     * useful signal.
     */
    val renalFunctionDeclineScreen = ConditionPattern(
        id = "biomarker_renal_function_decline_screen",
        title = "Sustained eGFR decline with creatinine corroboration",
        category = ConditionCategory.METABOLIC,
        signalRules = listOf(
            SignalRule(
                MetricType.EGFR, DeviationDirection.BELOW, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "KDIGO 2024 §1.1 — eGFR <60 mL/min/1.73m² is the CKD threshold; sustained ≥3 months establishes the diagnosis",
                required = true,
                absoluteBelow = 60.0,
            ),
            SignalRule(
                MetricType.CREATININE, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "KDIGO 2024 — creatinine >1.3 mg/dL (universal male upper bound; female reference is tighter) corroborates eGFR decline and rules out transient haemoconcentration",
                required = true,
                absoluteAbove = 1.3,
            ),
            SignalRule(
                MetricType.URIC_ACID_UMOL_PER_L, DeviationDirection.ABOVE, 0.0, 0, 0.6,
                ThresholdSource.LITERATURE,
                "Johnson RJ 2018 — hyperuricaemia (>420 µmol/L) accompanies CKD progression and corroborates the renal-clearance shortfall",
                absoluteAbove = 420.0,
            ),
        ),
        minActiveSignals = 2,
        explanation = "The most recent eGFR reading sits below 60 mL/min/1.73m² and the most recent creatinine reading sits above 1.3 mg/dL — both KDIGO 2024 thresholds for chronic kidney disease screening. The KDIGO guideline requires sustained values (≥3 months) before a diagnosis is appropriate, so this is a screening signal rather than a diagnosis.",
        suggestedAction = "Discuss the eGFR and creatinine values with a healthcare provider. KDIGO recommends repeating eGFR within 3 months to confirm sustained reduction; urine albumin-to-creatinine ratio is the standard companion test for staging. The lab trends from Bios are useful to share.",
        references = listOf(
            "KDIGO (2024) — Clinical Practice Guideline for the Evaluation and Management of Chronic Kidney Disease",
            "Inker LA et al. (2021) — New creatinine- and cystatin C-based equations to estimate GFR without race (CKD-EPI 2021)",
            "Johnson RJ et al. (2018) — Hyperuricaemia, acute and chronic kidney disease",
        ),
        earlyDetection = "CKD is silent until late stages (G3b or worse). Pairing a low eGFR with an elevated creatinine confirms a genuine clearance shortfall vs. a one-off measurement artefact; the optional urate corroborator strengthens the picture by linking to the metabolic-syndrome milieu that drives most adult CKD.",
    )

    /**
     * ALT or AST sustained >3× upper limit of normal — the AASLD 2021
     * threshold for "marked hepatocellular injury" warranting urgent
     * evaluation. The ULN values used: ALT 33 U/L (male upper) → 3× = 100;
     * AST 35 U/L → 3× = 105. The pattern requires either anchor to fire
     * (both are independent injury markers), with optional corroborators
     * from GGT (cholestatic / alcohol picture), total bilirubin
     * (impaired conjugation / cholestasis), and albumin (synthetic
     * function reserve).
     *
     * Wave 5's [Wave5BiomarkerPatterns.nafldSignature] uses the 50 U/L
     * clinical-action threshold and pairs with metabolic-syndrome
     * corroborators. Wave 6's hepatic-injury screen uses the 3× ULN
     * "marked elevation" threshold from AASLD 2021 — at this tier,
     * the differential opens to drug-induced liver injury, viral hepatitis,
     * autoimmune hepatitis, and ischaemic injury in addition to NAFLD.
     */
    val hepaticInjuryScreen = ConditionPattern(
        id = "biomarker_hepatic_injury_screen",
        title = "Marked hepatic enzyme elevation with corroborating signals",
        category = ConditionCategory.METABOLIC,
        signalRules = listOf(
            SignalRule(
                MetricType.ALT, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "AASLD 2021 — ALT >3× ULN (>100 U/L using the 33 U/L male upper reference) is the marked-hepatocellular-injury threshold warranting prompt evaluation",
                required = true,
                absoluteAbove = 100.0,
            ),
            SignalRule(
                MetricType.AST, DeviationDirection.ABOVE, 0.0, 0, 1.2,
                ThresholdSource.LITERATURE,
                "AASLD 2021 — AST >3× ULN (>105 U/L using the 35 U/L upper reference) corroborates hepatocellular injury; AST/ALT >1 shifts the differential toward alcohol-related or ischaemic injury",
                absoluteAbove = 105.0,
            ),
            SignalRule(
                MetricType.GGT, DeviationDirection.ABOVE, 0.0, 0, 0.8,
                ThresholdSource.LITERATURE,
                "AASLD 2021 — GGT >120 U/L (~2× ULN) corroborates cholestatic or alcohol-related injury patterns",
                absoluteAbove = 120.0,
            ),
            SignalRule(
                MetricType.TOTAL_BILIRUBIN_UMOL_PER_L, DeviationDirection.ABOVE, 0.0, 0, 0.8,
                ThresholdSource.LITERATURE,
                "AASLD 2021 — total bilirubin >35 µmol/L (>2 mg/dL) indicates impaired conjugation / cholestasis and elevates the urgency of the workup",
                absoluteAbove = 35.0,
            ),
            SignalRule(
                MetricType.ALBUMIN_G_PER_L, DeviationDirection.BELOW, 0.0, 0, 0.8,
                ThresholdSource.LITERATURE,
                "AASLD 2021 — albumin <35 g/L during marked transaminase elevation indicates synthetic-function compromise and shifts the workup toward urgent assessment",
                absoluteBelow = 35.0,
            ),
        ),
        // The anchor (ALT) is required, so any single corroborator suffices.
        minActiveSignals = 2,
        explanation = "The most recent ALT reading sits above 3× the upper reference range (>100 U/L) — the AASLD 2021 marked-hepatocellular-injury threshold. At least one corroborating signal has appeared: parallel AST elevation, GGT elevation, bilirubin elevation, or albumin drop. The differential at this tier is broader than for the mild-elevation NAFLD screen and includes drug-induced injury, viral hepatitis, autoimmune hepatitis, and ischaemic injury.",
        suggestedAction = "Discuss the hepatic panel with a healthcare provider promptly. AASLD 2021 recommends evaluation of common reversible causes (medications, alcohol, supplements) and viral / autoimmune workup at this tier. The lab values and trends from Bios are useful to share alongside the original lab report.",
        references = listOf(
            "Kwo PY et al. (AASLD 2017, updated 2021) — ACG Clinical Guideline: Evaluation of Abnormal Liver Chemistries",
            "Pratt DS, Kaplan MM (2000) — Evaluation of abnormal liver-enzyme results in asymptomatic patients",
            "Devarbhavi H et al. (2018) — Drug-induced liver injury: Asia-Pacific Association of Study of Liver consensus",
        ),
        earlyDetection = "Marked hepatocellular injury can be silent until jaundice or fatigue appears. The 3× ULN threshold is the AASLD-recommended trigger for prompt workup — well before fibrosis or synthetic-function failure. Corroborating bilirubin or albumin changes raise the urgency because they indicate the injury has begun to affect downstream liver function.",
    )
}
