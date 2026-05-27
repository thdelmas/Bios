package com.bios.app.alerts

import com.bios.app.model.ConditionCategory
import com.bios.contracts.MetricType

/**
 * Cross-correlation patterns that gate on a directly-measured lab biomarker
 * paired with corroborating wearable-derived signals. Phase 8.6's payoff:
 * the 16 BIOMARKER keys are no longer just data — they actually drive alerts
 * when the lab value crosses a literature-anchored clinical threshold *and*
 * the body's day-to-day signals trend in a compatible direction.
 *
 * Each pattern's biomarker rule uses `absoluteAbove` (the new absolute-
 * threshold SignalRule mode) so the latest lab value is checked against a
 * hard clinical cutoff, ignoring personal-baseline z-scores. The
 * corroborating rules stay baseline-relative, so a high-normal hsCRP on its
 * own won't fire the pattern unless the wearables also drift.
 *
 * All text obeys [AlertContentPolicy] — data statements, never lifestyle
 * judgments. The biomarker rules are `required = true` so wearable drift
 * alone (without a recent elevated lab) never triggers a biomarker
 * pattern (per "silence is a feature").
 */
object BiomarkerConditionPatterns {

    val all by lazy {
        listOf(
            inflammationSignature, prediabetesSignature, dyslipidemiaSignature,
            vitaminDDeficiencySignature, hypothyroidSignature, hyperthyroidSignature,
            anemiaSignature,
        ) + Wave5BiomarkerPatterns.all + Wave6BiomarkerPatterns.all
    }

    /**
     * Elevated hsCRP (≥1.0 mg/L, borderline-or-higher per Ridker AHA/CDC
     * 2003) paired with sustained resting-HR elevation and HRV depression.
     * The vital-sign trends corroborate the lab finding — chronic
     * inflammation drives autonomic shifts the wearable surface can pick up
     * over a 7-day window.
     */
    val inflammationSignature = ConditionPattern(
        id = "biomarker_inflammation_signature",
        title = "Elevated hsCRP with autonomic corroboration",
        category = ConditionCategory.CARDIOVASCULAR,
        signalRules = listOf(
            SignalRule(
                MetricType.HSCRP, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "Ridker AHA/CDC 2003 — hsCRP ≥1.0 mg/L is the borderline-or-higher inflammation tier",
                required = true,
                absoluteAbove = 1.0,
            ),
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.0, 168, 1.0,
                ThresholdSource.LITERATURE,
                "Furman et al. 2019 — chronic inflammation drives resting HR upward over multi-day windows",
            ),
            SignalRule(
                MetricType.HEART_RATE_VARIABILITY, DeviationDirection.BELOW, 1.0, 168, 1.0,
                ThresholdSource.LITERATURE,
                "Furman et al. 2019 — sustained HRV depression accompanies inflammatory states",
            ),
        ),
        minActiveSignals = 2,
        explanation = "The most recent hsCRP reading sits at or above 1.0 mg/L while resting heart rate has trended above baseline and heart rate variability has stayed depressed over the past seven days. Literature links this combination to systemic inflammation; the wearable signals on their own can have many causes, so the direct lab value is what anchors the pattern.",
        suggestedAction = "Discuss the hsCRP value and the multi-day vital-sign trend with a healthcare provider. The hsCRP reading, resting HR trend, and HRV trend from Bios are all useful to share.",
        references = listOf(
            "Ridker PM (2003) — C-reactive protein: a simple test to help predict risk of heart attack and stroke",
            "Furman D et al. (2019) — Chronic inflammation in the etiology of disease across the life span",
        ),
        earlyDetection = "Wearable-only inflammation proxies (resting HR + HRV) are noisy; they respond to fitness, alcohol, sleep, and acute illness as well. Pairing them with a measured hsCRP narrows the signal substantially.",
    )

    /**
     * HbA1c at or above 5.7% (prediabetic threshold per ADA 2024) paired
     * with sustained sleep-efficiency decline and resting-HR elevation.
     * The wearable signals corroborate metabolic dysregulation; on their
     * own they're indirect, so the lab anchors the pattern.
     */
    val prediabetesSignature = ConditionPattern(
        id = "biomarker_prediabetes_signature",
        title = "Elevated HbA1c with metabolic-dysregulation signals",
        category = ConditionCategory.METABOLIC,
        signalRules = listOf(
            SignalRule(
                MetricType.HBA1C, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "ADA 2024 — HbA1c ≥5.7% is the prediabetic threshold",
                required = true,
                absoluteAbove = 5.7,
            ),
            SignalRule(
                MetricType.SLEEP_EFFICIENCY, DeviationDirection.BELOW, 1.0, 168, 1.0,
                ThresholdSource.LITERATURE,
                "Hall et al. 2018 — sleep disruption correlates with glucose dysregulation",
            ),
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.0, 168, 0.8,
                ThresholdSource.LITERATURE,
                "Elevated resting HR is a secondary indicator of metabolic dysfunction",
            ),
        ),
        minActiveSignals = 2,
        explanation = "The most recent HbA1c reading sits at or above 5.7% while sleep efficiency has trended below baseline and resting heart rate has stayed elevated over the past seven days. Literature links this combination to early metabolic dysregulation; the wearable signals are indirect on their own, so the direct lab value anchors the pattern.",
        suggestedAction = "Discuss the HbA1c value and the sleep / resting HR trend with a healthcare provider. The HbA1c reading, sleep efficiency trend, and resting HR trend from Bios are all useful to share.",
        references = listOf(
            "American Diabetes Association (2024) — Standards of Medical Care in Diabetes",
            "Hall H et al. (2018) — Glucotypes reveal new patterns of glucose dysregulation",
        ),
        earlyDetection = "Wearable-only metabolic proxies (sleep, resting HR) are too indirect to be diagnostic. Pairing them with a measured HbA1c narrows the signal to genuine metabolic risk.",
    )

    /**
     * LDL ≥ 160 mg/dL (NCEP ATP III "high" tier) paired with at least one
     * supporting dyslipidemia marker: HDL below 40 mg/dL, triglycerides at
     * or above 200 mg/dL, or ApoB at or above 120 mg/dL (Sniderman 2019 —
     * the atherogenic-particle-count threshold). Multi-marker confirmation
     * is the clinical pattern for dyslipidemia; a single elevated lipid in
     * isolation is too noisy to alert on.
     */
    val dyslipidemiaSignature = ConditionPattern(
        id = "biomarker_dyslipidemia_signature",
        title = "Elevated LDL with corroborating lipid markers",
        category = ConditionCategory.CARDIOVASCULAR,
        signalRules = listOf(
            SignalRule(
                MetricType.LDL_CHOLESTEROL, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "NCEP ATP III — LDL ≥160 mg/dL is the high-risk tier",
                required = true,
                absoluteAbove = 160.0,
            ),
            SignalRule(
                MetricType.HDL_CHOLESTEROL, DeviationDirection.BELOW, 0.0, 0, 1.0,
                ThresholdSource.LITERATURE,
                "NCEP ATP III — HDL <40 mg/dL is independently atherogenic",
                absoluteBelow = 40.0,
            ),
            SignalRule(
                MetricType.TRIGLYCERIDES, DeviationDirection.ABOVE, 0.0, 0, 1.0,
                ThresholdSource.LITERATURE,
                "NCEP ATP III — triglycerides ≥200 mg/dL is the high tier",
                absoluteAbove = 200.0,
            ),
            SignalRule(
                MetricType.APO_B, DeviationDirection.ABOVE, 0.0, 0, 1.2,
                ThresholdSource.LITERATURE,
                "Sniderman et al. 2019 — ApoB ≥120 mg/dL is the atherogenic-particle-count threshold",
                absoluteAbove = 120.0,
            ),
            SignalRule(
                MetricType.LIPOPROTEIN_A, DeviationDirection.ABOVE, 0.0, 0, 0.8,
                ThresholdSource.LITERATURE,
                "ESC 2019 / NLA 2019 — Lp(a) ≥125 nmol/L is the independent ASCVD-risk tier; corroborates the dyslipidemia pattern when elevated",
                absoluteAbove = 125.0,
            ),
        ),
        minActiveSignals = 2,
        explanation = "The most recent LDL reading sits at or above 160 mg/dL while at least one corroborating lipid marker — low HDL, high triglycerides, high ApoB, or elevated Lp(a) — is also out of range. NCEP, ApoB and Lp(a) literature treat this multi-marker combination as the dyslipidemia pattern most predictive of atherosclerotic cardiovascular risk.",
        suggestedAction = "Discuss the lipid panel with a healthcare provider. The LDL, HDL, triglyceride, ApoB and Lp(a) values from Bios are all useful to share alongside the original lab report.",
        references = listOf(
            "NCEP ATP III (2002) — Detection, Evaluation, and Treatment of High Blood Cholesterol in Adults",
            "Sniderman AD et al. (2019) — Apolipoprotein B particles and cardiovascular disease",
            "Mach F et al. (2019) — ESC/EAS Guidelines for the management of dyslipidaemias (Lp(a) §6.4)",
        ),
        earlyDetection = "An isolated LDL elevation can have transient causes (diet, weight cycling). Multi-marker confirmation across the lipid panel — including Lp(a), which is largely genetically determined and adds independent risk — narrows the signal to durable dyslipidemia worth investigating.",
    )

    /**
     * 25-OH vitamin D below 20 ng/mL (Endocrine Society 2011 deficiency
     * threshold) paired with at least one wearable signal consistent with
     * the symptomatic profile: sleep efficiency below baseline (Romano
     * 2019), reduced activity (Roy 2014), or elevated mood-drift score
     * (Anglin 2013) when W2F is connected.
     *
     * Vit D deficiency is silent for months and the wearable corroborators
     * are individually noisy — each has many possible causes. The lab gate
     * narrows the signal to the population where these proxies plausibly
     * track the deficiency itself.
     */
    val vitaminDDeficiencySignature = ConditionPattern(
        id = "biomarker_vitamin_d_deficiency_signature",
        title = "Low vitamin D with corroborating recovery signals",
        category = ConditionCategory.METABOLIC,
        signalRules = listOf(
            SignalRule(
                MetricType.VITAMIN_D_25OH, DeviationDirection.BELOW, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "Endocrine Society 2011 — 25(OH)D <20 ng/mL is the deficiency threshold",
                required = true,
                absoluteBelow = 20.0,
            ),
            SignalRule(
                MetricType.SLEEP_EFFICIENCY, DeviationDirection.BELOW, 1.0, 168, 1.0,
                ThresholdSource.LITERATURE,
                "Romano F et al. 2019 — vitamin D status correlates with sleep regulation",
            ),
            SignalRule(
                MetricType.ACTIVE_MINUTES, DeviationDirection.BELOW, 1.0, 168, 1.0,
                ThresholdSource.LITERATURE,
                "Roy S et al. 2014 — vitamin D deficiency associated with fatigue and reduced activity tolerance",
            ),
            SignalRule(
                MetricType.MOOD_DRIFT_SCORE, DeviationDirection.ABOVE, 1.0, 168, 0.8,
                ThresholdSource.LITERATURE,
                "Anglin RES et al. 2013 — vitamin D status associated with mood; W2F mood-drift score rises during low-D periods",
            ),
        ),
        minActiveSignals = 2,
        explanation = "The most recent 25-OH vitamin D reading sits below 20 ng/mL — the Endocrine Society deficiency threshold — while at least one corroborating recovery signal has trended in the symptomatic direction over the past seven days. The lab anchors the pattern; the wearable signals on their own have many possible causes.",
        suggestedAction = "Discuss the vitamin D value and the recovery-signal trend with a healthcare provider. The 25-OH D reading, sleep efficiency trend, and active-minutes trend from Bios are useful to share.",
        references = listOf(
            "Holick MF et al. (2011) — Evaluation, treatment, and prevention of vitamin D deficiency",
            "Romano F et al. (2019) — Vitamin D and sleep regulation",
            "Roy S et al. (2014) — Correction of low vitamin D improves fatigue in otherwise healthy women",
            "Anglin RES et al. (2013) — Vitamin D deficiency and depression in adults",
        ),
        earlyDetection = "Vitamin D deficiency develops silently over months. Pairing a measured lab value with the wearable signals lets the pattern surface a deficiency that's started affecting sleep / activity / mood before symptoms become explicit.",
    )

    /**
     * TSH ≥ 4.0 mIU/L (AACE/ATA 2012 subclinical-hypothyroidism threshold)
     * paired with at least one of: free T4 below 0.8 ng/dL (overt
     * hypothyroidism), sustained resting-HR bradycardia, or reduced active
     * minutes. Hypothyroidism is ~10× more common than hyperthyroidism in
     * adults and presents with the classic slow-metabolism signature
     * (bradycardia + fatigue) that wearables can corroborate.
     *
     * The mirror condition (hyperthyroidism, TSH below 0.4 with tachycardia
     * and elevated free T3) ships as [hyperthyroidSignature] below.
     */
    val hypothyroidSignature = ConditionPattern(
        id = "biomarker_hypothyroid_signature",
        title = "Elevated TSH with corroborating low-metabolism signals",
        category = ConditionCategory.METABOLIC,
        signalRules = listOf(
            SignalRule(
                MetricType.TSH, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "AACE/ATA 2012 — TSH ≥4.0 mIU/L is the subclinical-hypothyroid threshold",
                required = true,
                absoluteAbove = 4.0,
            ),
            SignalRule(
                MetricType.FREE_T4, DeviationDirection.BELOW, 0.0, 0, 1.2,
                ThresholdSource.LITERATURE,
                "AACE 2012 — free T4 <0.8 ng/dL alongside elevated TSH indicates overt hypothyroidism",
                absoluteBelow = 0.8,
            ),
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.BELOW, 1.0, 168, 1.0,
                ThresholdSource.LITERATURE,
                "Klein & Ojamaa 2001 — hypothyroidism produces sinus bradycardia via reduced sympathetic drive",
            ),
            SignalRule(
                MetricType.ACTIVE_MINUTES, DeviationDirection.BELOW, 1.0, 168, 1.0,
                ThresholdSource.LITERATURE,
                "Surks et al. 2004 — fatigue and reduced exercise tolerance are dominant hypothyroid symptoms",
            ),
        ),
        minActiveSignals = 2,
        explanation = "The most recent TSH reading sits at or above 4.0 mIU/L — the AACE/ATA subclinical-hypothyroidism threshold — while at least one corroborating signal of slowed metabolism has appeared: low free T4, sustained bradycardia, or reduced activity. The lab anchors the pattern; the wearable signals are individually nonspecific.",
        suggestedAction = "Discuss the TSH and free T4 values along with the resting-HR and active-minutes trends with a healthcare provider. A repeat TSH 6–12 weeks later is the standard clinical follow-up for elevated readings.",
        references = listOf(
            "Garber JR et al. (2012) — Clinical practice guidelines for hypothyroidism (AACE/ATA)",
            "Klein I, Ojamaa K (2001) — Thyroid hormone and the cardiovascular system",
            "Surks MI et al. (2004) — Subclinical thyroid disease: scientific review and guidelines",
        ),
        earlyDetection = "Subclinical hypothyroidism is silent for months and the wearable signature (bradycardia + fatigue) overlaps with fitness, illness recovery, and overtraining. Pairing it with a measured TSH narrows the signal to thyroid involvement.",
    )

    /**
     * TSH below 0.4 mIU/L (AACE/ATA 2012 subclinical-hyperthyroidism threshold,
     * Biondi & Cooper 2008) paired with at least one of: free T3 above 4.2
     * pg/mL (the upper end of the adult reference range — T3 is the most
     * sensitive single marker for T3-predominant thyrotoxicosis), sustained
     * resting-HR tachycardia, or elevated active minutes. The mirror of
     * [hypothyroidSignature]: same gating logic on the lab, opposite
     * direction on every corroborator.
     */
    val hyperthyroidSignature = ConditionPattern(
        id = "biomarker_hyperthyroid_signature",
        title = "Suppressed TSH with corroborating high-metabolism signals",
        category = ConditionCategory.METABOLIC,
        signalRules = listOf(
            SignalRule(
                MetricType.TSH, DeviationDirection.BELOW, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "AACE/ATA 2012 — TSH <0.4 mIU/L is the subclinical-hyperthyroid threshold",
                required = true,
                absoluteBelow = 0.4,
            ),
            SignalRule(
                MetricType.FREE_T3, DeviationDirection.ABOVE, 0.0, 0, 1.2,
                ThresholdSource.LITERATURE,
                "Ross DS et al. 2016 — free T3 above the adult reference range (>4.2 pg/mL) indicates thyrotoxicosis, often T3-predominant",
                absoluteAbove = 4.2,
            ),
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.0, 168, 1.0,
                ThresholdSource.LITERATURE,
                "Klein & Ojamaa 2001 — hyperthyroidism produces sinus tachycardia via increased sympathetic drive and direct chronotropic effect",
            ),
            SignalRule(
                MetricType.ACTIVE_MINUTES, DeviationDirection.ABOVE, 1.0, 168, 0.8,
                ThresholdSource.LITERATURE,
                "Biondi & Cooper 2008 — hyperthyroidism presents with restlessness and elevated baseline activity; sustained active-minute increase corroborates the lab",
            ),
        ),
        minActiveSignals = 2,
        explanation = "The most recent TSH reading sits below 0.4 mIU/L — the AACE/ATA subclinical-hyperthyroidism threshold — while at least one corroborating signal of accelerated metabolism has appeared: elevated free T3, sustained tachycardia, or increased baseline activity. The lab anchors the pattern; the wearable signals are individually nonspecific.",
        suggestedAction = "Discuss the TSH and (if available) free T3 values along with the resting-HR and active-minutes trends with a healthcare provider. A repeat TSH 6–12 weeks later is the standard clinical follow-up for a suppressed reading; persistent suppression warrants free T3 / free T4 confirmation.",
        references = listOf(
            "Garber JR et al. (2012) — Clinical practice guidelines for hypothyroidism (AACE/ATA, includes hyperthyroid reference ranges)",
            "Ross DS et al. (2016) — 2016 American Thyroid Association guidelines for hyperthyroidism and other causes of thyrotoxicosis",
            "Biondi B, Cooper DS (2008) — The clinical significance of subclinical thyroid dysfunction",
            "Klein I, Ojamaa K (2001) — Thyroid hormone and the cardiovascular system",
        ),
        earlyDetection = "Subclinical hyperthyroidism is silent for months and the wearable signature (tachycardia + restlessness) overlaps with caffeine, anxiety, stimulant medication, and early infection. Pairing it with a suppressed TSH narrows the signal to thyroid involvement.",
    )

    /**
     * Hemoglobin below 12 g/dL (WHO 2011 universal-female anemia
     * threshold — using the female floor as the conservative universal
     * cutoff so anemia in either sex is captured without sex-stratified
     * config) paired with at least one of: hematocrit < 36%, RBC < 4.0
     * tera/L, sustained resting-HR tachycardia (compensatory cardiac
     * output for reduced oxygen-carrying capacity), or reduced active
     * minutes (fatigue is the dominant anemia symptom).
     *
     * The three CBC corroborators travel together — a true anemia
     * pattern shows multiple CBC-axis decreases, not an isolated
     * hemoglobin drop. Wearable corroborators alone are far too
     * nonspecific (tachycardia + fatigue overlaps with infection,
     * dehydration, deconditioning, stress); the hemoglobin gate is what
     * narrows the signal to anemia specifically.
     */
    val anemiaSignature = ConditionPattern(
        id = "biomarker_anemia_signature",
        title = "Low hemoglobin with corroborating CBC and effort signals",
        category = ConditionCategory.CARDIOVASCULAR,
        signalRules = listOf(
            SignalRule(
                MetricType.HEMOGLOBIN, DeviationDirection.BELOW, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "WHO 2011 — hemoglobin <12 g/dL is the anemia threshold (non-pregnant women); the male threshold is 13 g/dL, so 12 is a conservative universal floor",
                required = true,
                absoluteBelow = 12.0,
            ),
            SignalRule(
                MetricType.HEMATOCRIT, DeviationDirection.BELOW, 0.0, 0, 1.2,
                ThresholdSource.LITERATURE,
                "Williams Hematology 10th ed. — hematocrit <36% corroborates a low hemoglobin reading and rules out spurious dilution artifacts",
                absoluteBelow = 36.0,
            ),
            SignalRule(
                MetricType.RBC, DeviationDirection.BELOW, 0.0, 0, 1.2,
                ThresholdSource.LITERATURE,
                "Williams Hematology 10th ed. — RBC <4.0 tera/L narrows the etiology toward reduced production or chronic loss vs. acute bleed",
                absoluteBelow = 4.0,
            ),
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.0, 168, 1.0,
                ThresholdSource.LITERATURE,
                "Duke & Abelmann 1969 — anemia raises resting heart rate as compensatory cardiac output offsets reduced oxygen-carrying capacity",
            ),
            SignalRule(
                MetricType.ACTIVE_MINUTES, DeviationDirection.BELOW, 1.0, 168, 0.8,
                ThresholdSource.LITERATURE,
                "Patel KV 2008 — fatigue and reduced exercise tolerance are the dominant clinical symptoms of anemia in adults",
            ),
        ),
        minActiveSignals = 2,
        explanation = "The most recent hemoglobin reading sits below 12 g/dL — the WHO anemia threshold — while at least one corroborating signal has appeared: a matching drop in hematocrit or RBC, sustained tachycardia, or reduced activity. The hemoglobin lab anchors the pattern; the wearable signals on their own overlap with too many other causes (infection, deconditioning, stress) to be diagnostic alone.",
        suggestedAction = "Discuss the CBC values along with the resting-HR and active-minutes trends with a healthcare provider. Anemia has many causes (iron deficiency, B12/folate, chronic disease, occult bleed) and the workup is straightforward; a repeat CBC plus ferritin / iron studies / B12 is the standard initial follow-up.",
        references = listOf(
            "WHO (2011) — Haemoglobin concentrations for the diagnosis of anaemia and assessment of severity",
            "Beutler E, Waalen J (2006) — The definition of anemia: what is the lower limit of normal of the blood hemoglobin concentration?",
            "Williams Hematology, 10th ed. — adult CBC reference ranges",
            "Patel KV (2008) — Epidemiology of anemia in older adults",
            "Duke M, Abelmann WH (1969) — The hemodynamic response to chronic anemia",
        ),
        earlyDetection = "Chronic anemia develops slowly and the wearable signature (rising resting HR, declining activity) is nonspecific in isolation. Pairing it with a measured hemoglobin narrows the signal to the population where these proxies plausibly track oxygen-delivery shortfall.",
    )
    // Wave 5 (renal / hepatic / insulin-resistance) lives in
    // Wave5BiomarkerPatterns.kt to keep this file under the 500-line cap.
}
