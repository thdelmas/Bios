package com.bios.app.alerts

import com.bios.app.model.ConditionCategory
import com.bios.contracts.MetricType

/**
 * Wave 5 biomarker condition patterns (#158, audit gap §2.8) — renal,
 * hepatic, and insulin-resistance domains. Same absolute-threshold
 * mechanism as the wave 1–4 patterns in [BiomarkerConditionPatterns];
 * lives in its own file to keep that file under the 500-line cap.
 *
 * All three patterns are silent-disease detectors. eGFR catches CKD years
 * before symptoms; ALT/AST/GGT catch NAFLD before it progresses to
 * fibrosis; HOMA-IR catches insulin resistance before HbA1c shifts. Each
 * gates on a lab anchor and pairs it with wearable / metabolic
 * corroborators that on their own are too nonspecific to alert on.
 */
object Wave5BiomarkerPatterns {

    val all by lazy {
        listOf(
            ckdProgressionSignature,
            nafldSignature,
            insulinResistanceSignature,
        )
    }

    /**
     * eGFR <60 mL/min/1.73m² (KDIGO 2024 G3a or worse — the threshold at
     * which CKD is diagnosed when sustained ≥3 months). Paired with at
     * least one of: creatinine above the universal upper bound (>1.3
     * mg/dL — sex-stratified ranges are a future enhancement), or sustained
     * resting-HR drift (cardiorenal axis — early CKD often presents with
     * volume / autonomic shifts). Hemoglobin <12 g/dL completes the
     * "anemia of CKD" presentation but is not required so isolated GFR
     * decline still fires.
     *
     * eGFR is the single most important "preventive" lab for the >50
     * cohort after lipids and HbA1c, but Bios had no condition pattern
     * for it before this wave.
     */
    val ckdProgressionSignature = ConditionPattern(
        id = "biomarker_ckd_progression_signature",
        title = "Reduced eGFR with corroborating renal signals",
        category = ConditionCategory.METABOLIC,
        signalRules = listOf(
            SignalRule(
                MetricType.EGFR, DeviationDirection.BELOW, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "KDIGO 2024 — eGFR <60 mL/min/1.73m² sustained is the CKD diagnostic threshold (G3a or worse)",
                required = true,
                absoluteBelow = 60.0,
            ),
            SignalRule(
                MetricType.CREATININE, DeviationDirection.ABOVE, 0.0, 0, 1.2,
                ThresholdSource.LITERATURE,
                "Williams Hematology — creatinine >1.3 mg/dL is the conservative universal upper bound (male reference range; female reference is tighter)",
                absoluteAbove = 1.3,
            ),
            SignalRule(
                MetricType.HEMOGLOBIN, DeviationDirection.BELOW, 0.0, 0, 1.0,
                ThresholdSource.LITERATURE,
                "KDIGO 2024 — anemia of CKD develops as kidney function declines (reduced erythropoietin); hemoglobin <12 g/dL is the WHO anemia floor",
                absoluteBelow = 12.0,
            ),
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.0, 168, 0.8,
                ThresholdSource.LITERATURE,
                "Drawz & Rahman 2015 — cardiorenal axis: sustained RHR drift corroborates declining kidney function via autonomic and volume changes",
            ),
        ),
        minActiveSignals = 2,
        explanation = "The most recent eGFR reading sits below 60 mL/min/1.73m² — the KDIGO threshold at which chronic kidney disease is diagnosed when sustained for at least 3 months — while at least one corroborating signal has appeared: elevated creatinine, low hemoglobin (anemia of CKD), or sustained resting-HR elevation. The lab anchors the pattern; the wearable signals on their own are nonspecific.",
        suggestedAction = "Discuss the eGFR and creatinine values with a healthcare provider. KDIGO recommends repeating eGFR within 3 months to confirm sustained reduction; urine albumin-to-creatinine ratio is the standard companion test for staging. The lab and wearable trends from Bios are useful to share.",
        references = listOf(
            "KDIGO (2024) — Clinical Practice Guideline for the Evaluation and Management of Chronic Kidney Disease",
            "Drawz PE, Rahman M (2015) — Chronic kidney disease (Ann Intern Med In the Clinic)",
            "Inker LA et al. (2021) — New creatinine- and cystatin C-based equations to estimate GFR without race (CKD-EPI 2021)",
        ),
        earlyDetection = "CKD develops silently over years. eGFR is the canonical marker — by the time symptoms appear (fatigue, edema, nocturia), the owner is often at stage G3b or worse. Pairing a low eGFR with corroborating creatinine elevation or cardiorenal-axis signals (resting HR drift, anemia of CKD) raises confidence the lab finding reflects genuine kidney decline, not transient volume status or measurement artefact.",
    )

    /**
     * NAFLD signature: elevated ALT/AST with sustained metabolic
     * corroborators. ALT alone has the highest sensitivity for NAFLD;
     * pairing with AST and GGT improves specificity. Required: ALT
     * ≥50 U/L (the universal clinical-action threshold). At least one of:
     * AST ≥50 (parallel hepatocellular injury), GGT ≥60 (cholestatic /
     * alcohol-adjacent picture), or sustained metabolic backdrop (HbA1c
     * elevated, sleep efficiency declining — the metabolic-syndrome
     * milieu most NAFLD presents in).
     *
     * NAFLD is high-prevalence (~25 % of US adults) and silent until late
     * stage; the lab signal is the screening path.
     */
    val nafldSignature = ConditionPattern(
        id = "biomarker_nafld_signature",
        title = "Elevated hepatic enzymes with metabolic corroboration",
        category = ConditionCategory.METABOLIC,
        signalRules = listOf(
            SignalRule(
                MetricType.ALT, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "AASLD 2017 — ALT ≥50 U/L is the standard clinical-action threshold across most labs (true upper-limit is sex-stratified: 25 ♀ / 33 ♂; 50 captures both)",
                required = true,
                absoluteAbove = 50.0,
            ),
            SignalRule(
                MetricType.AST, DeviationDirection.ABOVE, 0.0, 0, 1.2,
                ThresholdSource.LITERATURE,
                "AASLD 2017 — AST ≥50 U/L corroborates hepatocellular injury; AST/ALT ratio >1 shifts the differential toward alcohol-related disease",
                absoluteAbove = 50.0,
            ),
            SignalRule(
                MetricType.GGT, DeviationDirection.ABOVE, 0.0, 0, 1.0,
                ThresholdSource.LITERATURE,
                "AASLD 2017 — GGT ≥60 U/L adds a cholestatic / alcohol-sensitive corroborator (rises with alcohol use, NAFLD, drug-induced cholestasis)",
                absoluteAbove = 60.0,
            ),
            SignalRule(
                MetricType.HBA1C, DeviationDirection.ABOVE, 0.0, 0, 0.8,
                ThresholdSource.LITERATURE,
                "Younossi et al. 2018 — NAFLD has bidirectional association with type 2 diabetes; HbA1c ≥5.7 % is the prediabetic threshold and the most common metabolic backdrop for NAFLD",
                absoluteAbove = 5.7,
            ),
            SignalRule(
                MetricType.SLEEP_EFFICIENCY, DeviationDirection.BELOW, 1.0, 168, 0.6,
                ThresholdSource.LITERATURE,
                "Marin-Alejandre et al. 2020 — sleep disruption is part of the metabolic-syndrome milieu most NAFLD develops in; not specific to NAFLD but corroborates the metabolic backdrop",
            ),
        ),
        minActiveSignals = 2,
        explanation = "The most recent ALT reading sits at or above 50 U/L — the standard clinical-action threshold — while at least one corroborating signal has appeared: AST elevation, GGT elevation, an elevated HbA1c metabolic backdrop, or declining sleep efficiency. The ALT anchors the pattern; isolated mild AST/GGT elevations have many causes.",
        suggestedAction = "Discuss the hepatic panel with a healthcare provider. Confirming the AST/ALT ratio, repeating in 4–8 weeks, and evaluating common reversible causes (alcohol, medications, metabolic syndrome) is the standard workup. The lab values and metabolic trends from Bios are useful to share.",
        references = listOf(
            "Chalasani N et al. (2018) — AASLD Practice Guideline for the diagnosis and management of NAFLD",
            "Younossi ZM et al. (2018) — Global epidemiology of NAFLD",
            "Marin-Alejandre BA et al. (2020) — Sleep quality and NAFLD",
        ),
        earlyDetection = "NAFLD is the most common chronic liver condition in adults and is silent until late stage. ALT elevation is the highest-yield single screening signal; the AST/GGT/HbA1c corroborators distinguish NAFLD from acute injury, alcohol-related disease, or drug-induced patterns. Lifestyle intervention at the asymptomatic stage is the highest-leverage path.",
    )

    /**
     * HOMA-IR ≥2.5 (Matthews 1985 — the canonical insulin-resistance index;
     * Tam et al. 2012 — 2.5 is the conservative population threshold)
     * paired with at least one of: elevated glucose variability (the
     * earliest CGM-visible insulin-resistance signal), HbA1c above the
     * prediabetic threshold, or sustained RHR elevation. HOMA-IR catches
     * insulin resistance years before HbA1c shifts; pairing it with the
     * wearable backdrop reduces the noise floor.
     */
    val insulinResistanceSignature = ConditionPattern(
        id = "biomarker_insulin_resistance_signature",
        title = "Elevated HOMA-IR with metabolic-load corroboration",
        category = ConditionCategory.METABOLIC,
        signalRules = listOf(
            SignalRule(
                MetricType.HOMA_IR, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "Matthews 1985 + Tam et al. 2012 — HOMA-IR ≥2.5 is the conservative population threshold for insulin resistance (some cohorts use 2.6; 2.5 captures clinically meaningful resistance)",
                required = true,
                absoluteAbove = 2.5,
            ),
            SignalRule(
                MetricType.GLUCOSE_CV, DeviationDirection.ABOVE, 1.0, 168, 1.0,
                ThresholdSource.LITERATURE,
                "Battelino et al. 2019 — elevated CGM glucose CV is an earlier marker than mean glucose in pre-diabetic transition; corroborates HOMA-IR",
            ),
            SignalRule(
                MetricType.HBA1C, DeviationDirection.ABOVE, 0.0, 0, 1.0,
                ThresholdSource.LITERATURE,
                "ADA 2024 — HbA1c ≥5.7 % is the prediabetic threshold; pairing with HOMA-IR captures the resistance → glycemic-shift progression",
                absoluteAbove = 5.7,
            ),
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.0, 168, 0.6,
                ThresholdSource.LITERATURE,
                "Aune et al. 2017 — elevated resting HR associated with insulin resistance / metabolic syndrome",
            ),
        ),
        minActiveSignals = 2,
        explanation = "The most recent HOMA-IR sits at or above 2.5 — the conservative population threshold for insulin resistance — while at least one corroborating signal has appeared: glucose-variability elevation, HbA1c at or above the prediabetic threshold, or sustained resting-HR drift. HOMA-IR catches the resistance phase years before HbA1c shifts; pairing it with the metabolic backdrop reduces the noise floor.",
        suggestedAction = "Discuss the HOMA-IR (or its inputs — fasting glucose and fasting insulin) and the metabolic trends with a healthcare provider. Lifestyle modification at the insulin-resistance stage — before glycemic thresholds shift — is the highest-leverage path; the lab values and CGM/wearable trends from Bios are useful to share.",
        references = listOf(
            "Matthews DR et al. (1985) — Homeostasis model assessment: insulin resistance and beta-cell function",
            "Tam CS et al. (2012) — Defining insulin resistance from hyperinsulinemic-euglycemic clamps",
            "Battelino T et al. (2019) — Clinical targets for continuous glucose monitoring data interpretation",
        ),
        earlyDetection = "Insulin resistance is the silent first phase of the type-2-diabetes pathway. HbA1c stays normal for years while HOMA-IR climbs and post-meal glucose variability widens. The HOMA-IR + CGM combination is the highest-leverage early-detection surface for metabolic disease — months to years before the clinical thresholds shift.",
    )
}
