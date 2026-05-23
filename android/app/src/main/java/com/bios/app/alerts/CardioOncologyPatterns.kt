package com.bios.app.alerts

import com.bios.app.model.AlertTier
import com.bios.app.model.ConditionCategory
import com.bios.app.physiology.CancerTherapyDrugClass
import com.bios.app.physiology.PhysiologyState
import com.bios.contracts.MetricType

/**
 * Cardio-oncology + treatment-toxicity surveillance patterns (#201, audit
 * convergence from ONCOLOGY_POV §2.3-2.5, CARDIOLOGY_POV §2.3, Emergency
 * Medicine, and Geriatrics).
 *
 * Cancer patients on anthracyclines, trastuzumab, TKIs, immune-checkpoint
 * inhibitors (ICIs), or CAR-T cell therapy are at elevated risk for:
 *  - **Cardiotoxicity** (LV dysfunction, myocarditis, CRS)
 *  - **Immune-related adverse events (irAEs)** — pneumonitis, colitis,
 *    hepatitis, thyroiditis, myocarditis, hypophysitis
 *  - **Treatment-emergent toxicities** — neutropenic fever, mucositis,
 *    diarrhoea / electrolyte loss, cachexia
 *
 * Every signal these patterns gate on is already on the bus — the gap was
 * composing them into oncology-aware patterns that fire only when the
 * owner has declared the matching treatment context.
 *
 * ## Gating axes (all patterns)
 *
 *  1. [PhysiologyState] — owner declares treatment state via the Settings
 *     screen. Patterns set `requiredStates` so they stay silent in healthy
 *     / non-treatment populations.
 *  2. [CancerTherapyDrugClass] — owner declares drug class alongside the
 *     state. Most patterns set `requiredDrugClasses` because the toxicity
 *     profile is class-specific (anthracycline cardiotoxicity ≠ ICI
 *     pneumonitis).
 *
 * ## AlertContentPolicy compliance
 *
 * All text is data-statement + professional-referral. Bios reports
 * "vitals suggestive of …" and "seek immediate medical assessment if
 * you are on …" — never "you have neutropenic fever".
 *
 * ## References
 *
 *  - NCCN (2024) — Cardio-Oncology Risk Assessment and Management
 *  - Armenian SH et al. (ASCO 2017) — Prevention and Monitoring of
 *    Cardiac Dysfunction in Survivors of Adult Cancers
 *  - Haanen J et al. (ESMO 2022) — Management of toxicities from
 *    immunotherapy: ESMO Clinical Practice Guideline
 *  - Schmidt-Hieber M et al. (IDSA 2010 update; Freifeld 2011) —
 *    Clinical Practice Guideline for the Use of Antimicrobial Agents in
 *    Neutropenic Patients with Cancer
 *  - Arends J et al. (ESPEN 2017) — ESPEN guidelines on nutrition in
 *    cancer patients
 *  - Bonaca MP et al. (AHA 2018) — Myocarditis in the Setting of
 *    Cancer Therapeutics
 *  - Lyon AR et al. (ESC 2022) — ESC Guidelines on cardio-oncology
 *  - NCCN Guidelines for Hematopoietic Cell Transplantation (CAR-T
 *    cardiotoxicity overlay, 2024)
 */
object CardioOncologyPatterns {

    val all by lazy {
        listOf(
            neutropenicFeverScreen,
            anthracyclineCardiotoxicityScreen,
            trastuzumabCardiotoxicityScreen,
            iciPneumonitisScreen,
            iciThyroiditisScreen,
            iciColitisScreen,
            cancerCachexiaScreen,
        )
    }

    /**
     * Neutropenic fever screen — IDSA 2010 / NCCN 2024 oncology emergency.
     *
     * Febrile neutropenia (ANC < 1500 with fever ≥ 38.3 °C, or sustained
     * ≥ 38.0 °C for one hour) is a true sepsis-class emergency in cancer
     * patients; mortality climbs every hour antibiotics are delayed. The
     * wearable substrate that flags it early: skin-temperature elevation
     * + sustained compensatory tachycardia + (when ANC is in Bios from
     * the biomarker import) ANC below the neutropenia threshold.
     *
     * Gated to [PhysiologyState.ON_ACTIVE_CHEMOTHERAPY]; no drug-class
     * restriction (any cytotoxic chemotherapy can cause neutropenia).
     */
    val neutropenicFeverScreen = ConditionPattern(
        id = "cardio_onc_neutropenic_fever_screen",
        title = "Vitals suggestive of febrile neutropenia",
        category = ConditionCategory.INFECTIOUS,
        signalRules = listOf(
            // ANC < 1500 — the IDSA neutropenia threshold (severe at
            // <500). The lab is `required` so the wearable triad alone
            // never fires this pattern; ANC must have been imported.
            SignalRule(
                MetricType.ABSOLUTE_NEUTROPHIL_COUNT, DeviationDirection.BELOW, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "IDSA 2010 / Freifeld AG 2011 — ANC <1500/µL is the neutropenia threshold; <500/µL is severe neutropenia and the high-risk fever-emergency floor",
                required = true,
                absoluteBelow = 1500.0,
            ),
            // Skin-temperature deviation +0.5 °C over baseline corroborates
            // fever. IDSA fever threshold (oral 38.3 °C single or ≥38.0
            // sustained) maps onto a wearable wrist-skin-temperature rise
            // that Bios already detects in the infection-onset pattern.
            SignalRule(
                MetricType.SKIN_TEMPERATURE_DEVIATION, DeviationDirection.ABOVE, 1.5, 12, 1.5,
                ThresholdSource.LITERATURE,
                "IDSA 2010 — fever (≥38.3 °C single or ≥38.0 sustained) defines febrile neutropenia; wearable skin-temp elevation precedes / corroborates the oral measurement",
            ),
            // Sustained tachycardia (RHR elevation) is the compensatory
            // response and a qSOFA-positive sepsis marker.
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.5, 12, 1.2,
                ThresholdSource.LITERATURE,
                "Klastersky J 2016 — tachycardia is the dominant cardiovascular sign in febrile-neutropenia presentation; compensatory response to systemic infection",
            ),
        ),
        minActiveSignals = 2,
        severityFloor = AlertTier.URGENT,
        explanation = "Recent vital signs show a pattern consistent with febrile neutropenia: a recent absolute neutrophil count below the IDSA neutropenia threshold (1500/µL) alongside wearable skin-temperature elevation and sustained resting-heart-rate elevation. In an owner on active chemotherapy this combination is the IDSA / NCCN oncology emergency — every hour of delayed antimicrobial therapy carries measurable mortality.",
        suggestedAction = "Vitals suggestive of febrile neutropenia — seek immediate medical assessment if you are on chemotherapy. Contact your oncology team's 24-hour line or the nearest emergency department now. Bring the recent ANC value and the temperature / heart-rate trend; the IDSA / NCCN protocol is empiric broad-spectrum antibiotics within one hour of presentation.",
        references = listOf(
            "Freifeld AG et al. (IDSA 2010 / 2011 update) — Clinical Practice Guideline for the Use of Antimicrobial Agents in Neutropenic Patients with Cancer",
            "NCCN (2024) — Prevention and Treatment of Cancer-Related Infections",
            "Klastersky J et al. (2016) — Management of febrile neutropaenia: ESMO Clinical Practice Guidelines",
        ),
        requiredStates = setOf(PhysiologyState.ON_ACTIVE_CHEMOTHERAPY),
    )

    /**
     * Anthracycline cardiotoxicity screen — ASCO 2017 / AHA 2018 / ESC 2022.
     *
     * Anthracyclines (doxorubicin, epirubicin, daunorubicin) cause
     * dose-dependent LV dysfunction. The earliest detectable signal is
     * sustained resting-HR elevation + activity-tolerance drop. NT-proBNP
     * and high-sensitivity troponin are the cardio-oncology cardiac
     * biomarkers (ASCO 2017): both elevated, especially serially, raises
     * the index of suspicion well before LVEF drops on echo.
     *
     * Gated to `ON_ACTIVE_CHEMOTHERAPY + ANTHRACYCLINE`. Severity floor
     * is null (classifier output) for the trend pattern; the severe-HF
     * symptom path (which would warrant URGENT) is owner-reported via
     * symptom flags and not yet wired through this surface.
     */
    val anthracyclineCardiotoxicityScreen = ConditionPattern(
        id = "cardio_onc_anthracycline_cardiotoxicity_screen",
        title = "Cardiovascular trend during anthracycline chemotherapy",
        category = ConditionCategory.CARDIOVASCULAR,
        signalRules = listOf(
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.5, 168, 1.2,
                ThresholdSource.LITERATURE,
                "Armenian SH et al. (ASCO 2017) — sustained resting-HR elevation is one of the earliest non-imaging signals of anthracycline cardiotoxicity",
            ),
            SignalRule(
                MetricType.ACTIVE_MINUTES, DeviationDirection.BELOW, 1.0, 168, 1.0,
                ThresholdSource.LITERATURE,
                "Lyon AR et al. (ESC 2022) — declining exercise tolerance precedes LVEF reduction in anthracycline-treated owners",
            ),
            // NT-proBNP elevation — ASCO 2017 + AHA 2018 cardio-oncology
            // biomarker. Threshold 125 pg/mL is the heart-failure rule-out
            // upper bound (ESC 2021 HF guidelines); cardio-oncology
            // surveillance uses the same cutoff for trend significance.
            SignalRule(
                MetricType.NT_PRO_BNP_PG_PER_ML, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "Armenian SH et al. (ASCO 2017) + ESC 2021 HF — NT-proBNP ≥125 pg/mL is the heart-failure exclusion upper bound and the cardio-oncology trend-significance cutoff",
                absoluteAbove = 125.0,
            ),
            // High-sensitivity troponin elevation — AHA 2018 cardio-oncology
            // biomarker. Threshold 14 ng/L is the upper reference limit of
            // most hs-cTn assays (sex-pooled 99th percentile).
            SignalRule(
                MetricType.TROPONIN_NG_PER_L, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "Curigliano G et al. (AHA 2018) — high-sensitivity troponin ≥14 ng/L (sex-pooled 99th percentile) flags subclinical anthracycline-induced myocardial injury",
                absoluteAbove = 14.0,
            ),
        ),
        minActiveSignals = 2,
        explanation = "Cardiovascular signals during anthracycline chemotherapy are trending in a direction consistent with treatment-emergent cardiotoxicity: sustained resting-heart-rate elevation, reduced activity tolerance, and / or elevated cardiac biomarkers (NT-proBNP, high-sensitivity troponin). Anthracyclines cause dose-dependent LV dysfunction; the ASCO 2017 / AHA 2018 cardio-oncology guidelines recommend biomarker surveillance and earlier echo when these signals appear.",
        suggestedAction = "Discuss the cardiovascular trend with your oncology and cardio-oncology team. The recent NT-proBNP / troponin values and the resting-HR and active-minutes trends from Bios are useful to share. The ASCO 2017 / ESC 2022 protocol is echo + biomarker reassessment; consider this conversation soon if any new symptoms (breathlessness, ankle swelling, chest discomfort) have appeared. If symptoms are severe — chest pain, severe breathlessness, fainting — seek emergency care.",
        references = listOf(
            "Armenian SH et al. (ASCO 2017) — Prevention and Monitoring of Cardiac Dysfunction in Survivors of Adult Cancers",
            "Curigliano G et al. (AHA 2018) — Cardiotoxicity of anticancer treatments",
            "Lyon AR et al. (ESC 2022) — ESC Guidelines on cardio-oncology",
            "NCCN (2024) — Cardio-Oncology Risk Assessment and Management",
        ),
        requiredStates = setOf(PhysiologyState.ON_ACTIVE_CHEMOTHERAPY),
        requiredDrugClasses = setOf(CancerTherapyDrugClass.ANTHRACYCLINE),
    )

    /**
     * Trastuzumab (HER2-targeted) cardiotoxicity screen — ASCO 2017 / ESC
     * 2022. Trastuzumab cardiotoxicity is typically reversible and
     * non-dose-dependent; the surveillance pattern shape mirrors the
     * anthracycline screen but the clinical interpretation and management
     * differ (Lyon ESC 2022).
     */
    val trastuzumabCardiotoxicityScreen = ConditionPattern(
        id = "cardio_onc_trastuzumab_cardiotoxicity_screen",
        title = "Cardiovascular trend during HER2-targeted therapy",
        category = ConditionCategory.CARDIOVASCULAR,
        signalRules = listOf(
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.5, 168, 1.2,
                ThresholdSource.LITERATURE,
                "Armenian SH et al. (ASCO 2017) — resting-HR elevation is an early non-imaging signal of HER2-targeted cardiotoxicity, especially in owners with prior anthracycline exposure",
            ),
            SignalRule(
                MetricType.ACTIVE_MINUTES, DeviationDirection.BELOW, 1.0, 168, 1.0,
                ThresholdSource.LITERATURE,
                "Lyon AR et al. (ESC 2022) — declining exercise tolerance during trastuzumab therapy correlates with subclinical LV dysfunction",
            ),
            SignalRule(
                MetricType.NT_PRO_BNP_PG_PER_ML, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "Armenian SH et al. (ASCO 2017) — NT-proBNP elevation is part of the cardio-oncology biomarker surveillance panel for HER2-targeted therapy",
                absoluteAbove = 125.0,
            ),
            SignalRule(
                MetricType.TROPONIN_NG_PER_L, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "Curigliano G et al. (AHA 2018) — high-sensitivity troponin elevation during trastuzumab therapy raises the index of suspicion for cardiotoxicity even when LVEF remains in range",
                absoluteAbove = 14.0,
            ),
        ),
        minActiveSignals = 2,
        explanation = "Cardiovascular signals during HER2-targeted therapy (trastuzumab, pertuzumab, T-DM1, T-DXd) are trending in a direction consistent with treatment-related cardiotoxicity: sustained resting-heart-rate elevation, reduced activity tolerance, and / or elevated cardiac biomarkers. HER2 cardiotoxicity is typically reversible when caught early; the ASCO 2017 / ESC 2022 protocol is periodic LVEF + biomarker surveillance, with earlier reassessment when these signals appear.",
        suggestedAction = "Discuss the cardiovascular trend with your oncology and cardio-oncology team. The NT-proBNP / troponin values and the resting-HR and active-minutes trends from Bios are useful to share. The standard follow-up is echo + biomarker reassessment, often without interrupting therapy when caught early. If symptoms are severe — chest pain, severe breathlessness, fainting — seek emergency care.",
        references = listOf(
            "Armenian SH et al. (ASCO 2017) — Prevention and Monitoring of Cardiac Dysfunction in Survivors of Adult Cancers",
            "Lyon AR et al. (ESC 2022) — ESC Guidelines on cardio-oncology",
            "Curigliano G et al. (AHA 2018) — Cardiotoxicity of anticancer treatments",
        ),
        requiredStates = setOf(PhysiologyState.ON_ACTIVE_CHEMOTHERAPY),
        requiredDrugClasses = setOf(CancerTherapyDrugClass.TRASTUZUMAB),
    )

    /**
     * ICI pneumonitis screen — ESMO 2022. Immune-checkpoint inhibitor
     * pneumonitis is the leading ICI-AE mortality driver; declining SpO2
     * + tachycardia + activity drop in an ICI owner is the early
     * wearable-detectable signal.
     */
    val iciPneumonitisScreen = ConditionPattern(
        id = "cardio_onc_ici_pneumonitis_screen",
        title = "Vitals suggestive of ICI-related pneumonitis",
        category = ConditionCategory.RESPIRATORY,
        signalRules = listOf(
            SignalRule(
                MetricType.BLOOD_OXYGEN, DeviationDirection.BELOW, 1.5, 24, 1.5,
                ThresholdSource.LITERATURE,
                "Haanen J et al. (ESMO 2022) — declining SpO2 from baseline is the earliest wearable-detectable sign of ICI-related pneumonitis (grade-2+ pneumonitis is the irAE mortality driver)",
            ),
            SignalRule(
                MetricType.RESPIRATORY_RATE, DeviationDirection.ABOVE, 1.5, 24, 1.2,
                ThresholdSource.LITERATURE,
                "Naidoo J et al. (2017) — tachypnoea precedes radiographic pneumonitis findings in ICI-treated cohorts",
            ),
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.5, 24, 1.0,
                ThresholdSource.LITERATURE,
                "Haanen J et al. (ESMO 2022) — compensatory tachycardia accompanies hypoxia in ICI pneumonitis",
            ),
            SignalRule(
                MetricType.ACTIVE_MINUTES, DeviationDirection.BELOW, 1.0, 168, 0.8,
                ThresholdSource.LITERATURE,
                "Naidoo J et al. (2017) — declining exercise tolerance is an early symptom of ICI pneumonitis",
            ),
        ),
        minActiveSignals = 2,
        severityFloor = AlertTier.URGENT,
        explanation = "Recent vital signs show a pattern consistent with immune-checkpoint-inhibitor (ICI)-related pneumonitis: declining SpO2 from baseline, elevated respiratory rate, sustained tachycardia, and / or reduced activity tolerance. ICI pneumonitis is the leading mortality driver among ICI immune-related adverse events; the ESMO 2022 protocol is early steroid initiation, and early presentation maps to better outcomes.",
        suggestedAction = "Vitals suggestive of ICI-related pneumonitis — seek immediate medical assessment if you are on a PD-1 / PD-L1 inhibitor. Contact your oncology team's 24-hour line or the nearest emergency department now. Bring the SpO2 / respiratory-rate / heart-rate trend; the ESMO 2022 protocol for grade-2+ pneumonitis is steroid initiation and ICI hold.",
        references = listOf(
            "Haanen J et al. (ESMO 2022) — Management of toxicities from immunotherapy: ESMO Clinical Practice Guideline",
            "Naidoo J et al. (2017) — Pneumonitis in patients treated with anti-programmed death-1/programmed death ligand 1 therapy",
            "Schneider BJ et al. (ASCO 2021) — Management of irAEs in Patients Treated With ICPi",
        ),
        requiredStates = setOf(PhysiologyState.ON_IMMUNE_CHECKPOINT_INHIBITOR),
        requiredDrugClasses = setOf(CancerTherapyDrugClass.ICI_PD1_PDL1),
    )

    /**
     * ICI thyroiditis screen — ESMO 2022. Leverages the existing
     * [BiomarkerConditionPatterns.hyperthyroidSignature] /
     * [BiomarkerConditionPatterns.hypothyroidSignature] biomarker
     * substrate but with heightened sensitivity and explicit ICI
     * framing. Thyroiditis is one of the most common irAEs (10-15%
     * cumulative incidence for combination ICI per Haanen 2022).
     *
     * v1 ships the hyperthyroid (thyrotoxic) shape — the transient
     * thyrotoxic phase usually precedes the longer hypothyroid phase
     * and is the more wearable-detectable signal (tachycardia +
     * restlessness vs. bradycardia + fatigue, which overlaps with
     * generic cancer fatigue).
     */
    val iciThyroiditisScreen = ConditionPattern(
        id = "cardio_onc_ici_thyroiditis_screen",
        title = "Thyroid-axis signals during ICI therapy",
        category = ConditionCategory.METABOLIC,
        signalRules = listOf(
            // TSH suppression — same threshold as hyperthyroidSignature
            // (AACE/ATA 2012, Biondi & Cooper 2008). Required = true so
            // the wearable triad alone never fires this pattern.
            SignalRule(
                MetricType.TSH, DeviationDirection.BELOW, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "AACE/ATA 2012 — TSH <0.4 mIU/L is the subclinical-hyperthyroid threshold; ICI-thyroiditis typically begins with a transient thyrotoxic phase before progressing to hypothyroidism (Haanen ESMO 2022)",
                required = true,
                absoluteBelow = 0.4,
            ),
            SignalRule(
                MetricType.FREE_T3, DeviationDirection.ABOVE, 0.0, 0, 1.2,
                ThresholdSource.LITERATURE,
                "Haanen J et al. (ESMO 2022) — elevated free T3 in ICI-thyroiditis confirms the thyrotoxic phase; thyroid antibody testing and endocrinology referral are the standard follow-up",
                absoluteAbove = 4.2,
            ),
            // Lower the sensitivity vs. generic hyperthyroidSignature
            // (1.0 sigma vs the 1.0 sigma already there) — same threshold
            // but with ICI-state gating, false-positive risk is far lower.
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.0, 168, 1.0,
                ThresholdSource.LITERATURE,
                "Klein & Ojamaa 2001 — thyrotoxicosis produces sinus tachycardia via increased sympathetic drive; corroborates ICI-thyroiditis in the gated population",
            ),
        ),
        minActiveSignals = 2,
        explanation = "Thyroid-axis signals during ICI therapy are trending in a direction consistent with immune-related thyroiditis: TSH below the subclinical-hyperthyroid threshold, with elevated free T3 and / or sustained tachycardia. ICI thyroiditis is one of the most common immune-related adverse events; it typically presents with a transient thyrotoxic phase before progressing to hypothyroidism. The ESMO 2022 protocol is thyroid-antibody testing and endocrinology referral.",
        suggestedAction = "Discuss the thyroid panel with your oncology team. The TSH, free T3, and resting-HR trend from Bios are useful to share. The ESMO 2022 / ASCO 2021 standard follow-up is thyroid-antibody testing, free T4 confirmation, and endocrinology referral; most ICI thyroiditis is managed without interrupting ICI therapy.",
        references = listOf(
            "Haanen J et al. (ESMO 2022) — Management of toxicities from immunotherapy: ESMO Clinical Practice Guideline",
            "Schneider BJ et al. (ASCO 2021) — Management of irAEs in Patients Treated With ICPi",
            "Garber JR et al. (AACE/ATA 2012) — Clinical practice guidelines for hypothyroidism (includes hyperthyroid reference ranges)",
        ),
        requiredStates = setOf(PhysiologyState.ON_IMMUNE_CHECKPOINT_INHIBITOR),
        requiredDrugClasses = setOf(CancerTherapyDrugClass.ICI_PD1_PDL1),
    )

    /**
     * ICI colitis screen — ESMO 2022 / ASCO 2021. ICI-related colitis
     * presents with persistent diarrhoea + dehydration physiology
     * (tachycardia, BP variability). Owner-reported diarrhoea symptom
     * via ESAS-r (Edmonton Symptom Assessment, separately ingested) is
     * the canonical confirmation; the wearable pattern alone is too
     * non-specific outside the ICI context.
     *
     * v1 ships the wearable-only shape (sustained tachycardia in an ICI
     * owner). When the ESAS-r diarrhoea symptom flag lands (PR #216
     * referenced in the brief), the symptom should become a required
     * corroborator. ADVISORY severity — ICI colitis at grade 1-2 is not
     * an emergency, but grade 3+ (≥7 stools/day, blood, severe pain) is,
     * and the suggestedAction phrasing captures both branches.
     */
    val iciColitisScreen = ConditionPattern(
        id = "cardio_onc_ici_colitis_screen",
        title = "Dehydration-pattern signals during ICI therapy",
        category = ConditionCategory.CARDIOVASCULAR,
        signalRules = listOf(
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.5, 48, 1.5,
                ThresholdSource.LITERATURE,
                "Haanen J et al. (ESMO 2022) — sustained tachycardia in an ICI owner is the wearable signature of fluid-volume loss from ICI-related colitis",
            ),
            SignalRule(
                MetricType.HEART_RATE_VARIABILITY, DeviationDirection.BELOW, 1.5, 48, 1.0,
                ThresholdSource.LITERATURE,
                "Brames MJ et al. (2017) — autonomic stress (HRV depression) accompanies dehydration physiology",
            ),
            // Weight drop as a secondary corroborator — diarrhoea +
            // poor PO intake produces measurable weight loss in 48-72h.
            SignalRule(
                MetricType.BODY_MASS, DeviationDirection.BELOW, 1.0, 168, 0.8,
                ThresholdSource.LITERATURE,
                "Haanen J et al. (ESMO 2022) — measurable body-mass loss accompanies grade-2+ ICI colitis via diarrhoea + reduced PO intake",
            ),
        ),
        minActiveSignals = 2,
        explanation = "Cardiovascular and weight signals during ICI therapy are trending in a direction consistent with dehydration physiology, the wearable signature of ICI-related colitis: sustained tachycardia, HRV depression, and / or recent body-mass loss. ICI colitis is one of the more common immune-related adverse events; the ESMO 2022 protocol is early steroid initiation for grade 2+ presentations.",
        suggestedAction = "If diarrhoea (≥4 stools/day above baseline) or abdominal pain has appeared, contact your oncology team within 24 hours. The ESMO 2022 protocol for grade 2+ ICI colitis is stool work-up + steroid initiation. If diarrhoea is severe (≥7 stools/day, blood, severe abdominal pain, fever), seek emergency care — grade-3+ ICI colitis is an oncology emergency.",
        references = listOf(
            "Haanen J et al. (ESMO 2022) — Management of toxicities from immunotherapy: ESMO Clinical Practice Guideline",
            "Schneider BJ et al. (ASCO 2021) — Management of irAEs in Patients Treated With ICPi",
            "Brahmer JR et al. (2018) — Management of Immune-Related Adverse Events",
        ),
        requiredStates = setOf(PhysiologyState.ON_IMMUNE_CHECKPOINT_INHIBITOR),
        requiredDrugClasses = setOf(CancerTherapyDrugClass.ICI_PD1_PDL1),
    )

    /**
     * Cancer cachexia screen — ESPEN 2017. Sustained weight-loss
     * trajectory + activity-tolerance drop is the wearable-detectable
     * cachexia signature; ESPEN defines cachexia as >5% body-weight
     * loss in 6 months or >2% in BMI <20 owners. v1 ships the
     * wearable-only shape; ESAS-r appetite-score elevation lands as a
     * corroborator when the symptom companion is wired.
     *
     * Gated to all cancer-treatment states (no drug-class restriction —
     * cachexia is treatment-class agnostic).
     */
    val cancerCachexiaScreen = ConditionPattern(
        id = "cardio_onc_cancer_cachexia_screen",
        title = "Cachexia-trajectory signals during cancer treatment",
        category = ConditionCategory.METABOLIC,
        signalRules = listOf(
            SignalRule(
                MetricType.BODY_MASS, DeviationDirection.BELOW, 1.5, 720, 1.5,
                ThresholdSource.LITERATURE,
                "Arends J et al. (ESPEN 2017) — sustained body-mass decline (>5% over 6 months, or >2% with BMI <20) defines cancer cachexia; the 30-day window detects the trajectory earlier",
            ),
            SignalRule(
                MetricType.ACTIVE_MINUTES, DeviationDirection.BELOW, 1.0, 336, 1.0,
                ThresholdSource.LITERATURE,
                "Fearon K et al. (2011) — declining functional status (reduced activity tolerance, sarcopenia) is the second pillar of the cancer-cachexia diagnostic framework",
            ),
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.0, 336, 0.8,
                ThresholdSource.LITERATURE,
                "Argilés JM et al. (2014) — cachexia produces compensatory tachycardia via reduced lean mass and chronic inflammatory cytokine load",
            ),
        ),
        minActiveSignals = 2,
        explanation = "Body-mass and activity-tolerance signals during cancer treatment are trending in a direction consistent with cancer cachexia (ESPEN 2017 framework): sustained body-mass decline, reduced activity, and / or compensatory resting-heart-rate elevation. Cachexia is multifactorial — treatment side effects, tumour biology, and reduced PO intake all contribute — and early nutritional intervention has the strongest evidence base.",
        suggestedAction = "Discuss the weight and activity trend with your oncology team and ask about a nutrition consult. The ESPEN 2017 protocol is early dietitian involvement, screening for reversible contributors (mucositis, nausea, mood, pain), and considering pharmacological appetite support when indicated. The body-mass and active-minutes trends from Bios are useful to share alongside any ESAS-r appetite / nausea reports.",
        references = listOf(
            "Arends J et al. (ESPEN 2017) — ESPEN guidelines on nutrition in cancer patients",
            "Fearon K et al. (2011) — Definition and classification of cancer cachexia: an international consensus",
            "Argilés JM et al. (2014) — Cancer cachexia: understanding the molecular basis",
        ),
        // No drug-class restriction — cachexia is treatment-class
        // agnostic. Gates only on a cancer-treatment state.
        requiredStates = PhysiologyState.CANCER_TREATMENT,
    )
}
