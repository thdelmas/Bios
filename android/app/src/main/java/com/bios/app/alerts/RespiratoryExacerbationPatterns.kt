package com.bios.app.alerts

import com.bios.app.model.ConditionCategory
import com.bios.contracts.MetricType

/**
 * Chronic-respiratory exacerbation patterns (#161, audit gap §2.8). COPD
 * and asthma both present with multi-day wearable-detectable patterns —
 * SpO2 drift downward, respiratory-rate elevation, sleep-fragmentation
 * rise from nocturnal symptoms, activity-tolerance reduction — that are
 * complementary to (but distinct from) the existing
 * [ConditionPatterns.respiratoryInfection] pattern. Both patterns gate
 * on multi-signal convergence so a one-off SpO2 dip on a hike doesn't
 * fire them; both surface as screening signals, same shape as
 * [AfibRhythmPattern.paroxysmalAfibScreen].
 *
 * **Owner-set chronic-respiratory flag** (#159 PhysiologyState — defer):
 * the audit issue specifies that these patterns should only fire when
 * the owner has set `COPD_DIAGNOSED` / `ASTHMA_DIAGNOSED`. Without that
 * gating, the patterns fire as general screening signals — a non-COPD
 * owner with multi-day SpO2 drift + RR up + activity drop is still in
 * the right population for clinical evaluation. The PhysiologyState
 * gating layers on once #159 ships.
 *
 * Cough_count (planned microphone-adapter signal) is deferred — the
 * adapter doesn't exist yet, so adding it as a SignalRule would create
 * dead weight. Pattern can be extended when the mic surface lands.
 *
 * `smoking_cessation_respiratory_recovery` (positive-direction pattern
 * gated on `TOBACCO_USE` ABSENT for 30+ days + positive trends) is a
 * separate follow-up — builds on the existing `cessation_recovery_pattern`
 * from §7.7 and needs careful tuning to stay on the manifesto side of
 * "information, not praise."
 *
 * All text obeys [AlertContentPolicy].
 */
object RespiratoryExacerbationPatterns {

    val all by lazy {
        listOf(
            copdExacerbationSignature,
            asthmaExacerbationSignature,
        )
    }

    /**
     * COPD exacerbation: multi-day SpO2 baseline-relative drift down
     * + respiratory-rate baseline-relative rise + activity-minutes
     * baseline-relative drop, over a 7-day window. The personal-baseline
     * machinery handles the well-known issue that COPD owners have a
     * lower SpO2 baseline than non-COPD owners (a 92 % baseline drifting
     * to 88 % is the exacerbation signal, not the absolute 88).
     *
     * Differentiated from [ConditionPatterns.respiratoryInfection] by:
     * the *absence* of an acute-infection SpO2 cutoff (this pattern is
     * trend-based, not threshold-based), longer window (168h vs 12-24h),
     * and the activity-tolerance signal (canonical COPD symptom).
     */
    val copdExacerbationSignature = ConditionPattern(
        id = "copd_exacerbation",
        title = "Sustained respiratory pattern shift detected",
        category = ConditionCategory.RESPIRATORY,
        signalRules = listOf(
            SignalRule(
                MetricType.BLOOD_OXYGEN, DeviationDirection.BELOW, 1.0, 168, 1.2,
                ThresholdSource.LITERATURE,
                "GOLD 2024 — sustained SpO2 drift below personal baseline is the canonical wearable signature of COPD exacerbation; personal-baseline framing handles the lower-baseline-than-population characteristic of COPD owners",
            ),
            SignalRule(
                MetricType.RESPIRATORY_RATE, DeviationDirection.ABOVE, 1.0, 168, 1.0,
                ThresholdSource.LITERATURE,
                "Cretikos et al. 2008 — respiratory rate is the most sensitive vital sign for respiratory compromise; sustained elevation over multi-day window distinguishes exacerbation from transient elevation",
            ),
            SignalRule(
                MetricType.ACTIVE_MINUTES, DeviationDirection.BELOW, 1.0, 168, 0.8,
                ThresholdSource.LITERATURE,
                "Garcia-Aymerich et al. 2006 — declining activity tolerance is one of the dominant COPD exacerbation symptoms and corroborates the respiratory signals",
            ),
            SignalRule(
                MetricType.SLEEP_FRAGMENTATION_INDEX, DeviationDirection.ABOVE, 1.0, 168, 0.6,
                ThresholdSource.LITERATURE,
                "Krachman et al. 2014 — nocturnal COPD symptoms (dyspnoea, cough) elevate sleep fragmentation even when total sleep time appears normal",
            ),
        ),
        minActiveSignals = 3,
        explanation = "Several respiratory signals are drifting from your personal baseline over the past week: blood oxygen below baseline, respiratory rate above baseline, and activity below baseline. This combination is consistent with a chronic-respiratory exacerbation pattern in clinical literature. The personal-baseline framing handles the fact that COPD owners often have lower SpO2 baselines than the general population — the *drift*, not the absolute value, is the signal.",
        suggestedAction = "Discuss with a healthcare provider. If you have a rescue-inhaler or supplemental-oxygen prescription and the pattern matches your usual exacerbation prodrome, the standard clinical guidance is to follow that plan. If accompanied by chest pain, severe shortness of breath, confusion, or bluish lips, seek immediate medical attention.",
        references = listOf(
            "Global Initiative for Chronic Obstructive Lung Disease (2024) — GOLD Report",
            "Cretikos MA et al. (2008) — Respiratory rate: the neglected vital sign",
            "Garcia-Aymerich J et al. (2006) — Risk factors of readmission to hospital for a COPD exacerbation",
            "Krachman SL et al. (2014) — Sleep and COPD",
        ),
        earlyDetection = "COPD exacerbations have a prodromal phase of 1–2 weeks during which baseline-relative drift across multiple respiratory signals is detectable before the owner subjectively recognises the exacerbation. SpO2 dropping 2–4 points below personal baseline, respiratory rate creeping up, activity dropping — none alone is alarming, the convergence is. Bios uses a 7-day window and requires 3 of 4 signals to flag the pattern, reducing false positives from short-term illness or environmental factors.",
    )

    /**
     * Asthma exacerbation: multi-day respiratory-rate elevation + sleep
     * fragmentation + activity-tolerance drop. SpO2 desaturation is a
     * late sign in asthma (often normal until acutely severe), so this
     * pattern emphasises the symptom-cluster signals (nocturnal symptoms
     * via sleep fragmentation, declining exercise tolerance, sustained
     * tachypnea) over absolute oxygenation.
     */
    val asthmaExacerbationSignature = ConditionPattern(
        id = "asthma_exacerbation",
        title = "Sustained airway pattern shift detected",
        category = ConditionCategory.RESPIRATORY,
        signalRules = listOf(
            SignalRule(
                MetricType.RESPIRATORY_RATE, DeviationDirection.ABOVE, 1.0, 168, 1.2,
                ThresholdSource.LITERATURE,
                "GINA 2024 — sustained respiratory-rate elevation is a canonical exacerbation marker even in well-controlled asthma; precedes the acute attack",
            ),
            SignalRule(
                MetricType.SLEEP_FRAGMENTATION_INDEX, DeviationDirection.ABOVE, 1.0, 168, 1.0,
                ThresholdSource.LITERATURE,
                "Reddel et al. 2009 — nocturnal asthma symptoms (wheezing, cough, dyspnoea) are a hallmark of poor control and rising exacerbation risk; sleep fragmentation captures the wearable-detectable version",
            ),
            SignalRule(
                MetricType.ACTIVE_MINUTES, DeviationDirection.BELOW, 1.0, 168, 0.8,
                ThresholdSource.LITERATURE,
                "GINA 2024 — declining exercise tolerance is part of the exacerbation prodrome; the symptom commonly precedes the acute attack by 1–3 days",
            ),
            SignalRule(
                MetricType.BLOOD_OXYGEN, DeviationDirection.BELOW, 1.0, 168, 0.6,
                ThresholdSource.LITERATURE,
                "GINA 2024 — SpO2 desaturation is a late sign in asthma but corroborating when present; weighted lower than the symptom-cluster signals",
            ),
        ),
        minActiveSignals = 3,
        explanation = "Several airway signals are drifting from your personal baseline over the past week: respiratory rate above baseline, sleep fragmentation above baseline, and activity below baseline. The combination is consistent with the prodromal phase of an asthma exacerbation in clinical literature. SpO2 desaturation is intentionally not required — it's a late sign in asthma and waiting for it would miss the early window.",
        suggestedAction = "Discuss with a healthcare provider. If you have a controller-medication or rescue-inhaler plan and the pattern matches your usual exacerbation prodrome, the standard clinical guidance is to follow that plan. Acute severe symptoms (audible wheezing, difficulty speaking in full sentences, chest tightness, severe dyspnea) warrant immediate medical attention.",
        references = listOf(
            "Global Initiative for Asthma (2024) — GINA Strategy",
            "Reddel HK et al. (2009) — An official American Thoracic Society / European Respiratory Society statement on asthma control",
            "Cretikos MA et al. (2008) — Respiratory rate: the neglected vital sign",
        ),
        earlyDetection = "Asthma exacerbations have a prodromal phase characterised by symptom-cluster drift before the acute attack: nocturnal symptoms rise (sleep fragmentation up), respiratory rate creeps up, and activity tolerance drops. SpO2 stays normal until late. Bios uses a 7-day window and requires 3 of 4 signals to flag — catching the prodrome window where the owner can act on their asthma plan early, before the acute event.",
    )
}
