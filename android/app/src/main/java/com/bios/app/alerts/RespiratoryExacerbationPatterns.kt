package com.bios.app.alerts

import com.bios.app.model.AlertTier
import com.bios.app.model.ConditionCategory
import com.bios.app.physiology.PhysiologyState
import com.bios.contracts.MetricType

/**
 * Chronic-respiratory exacerbation patterns (#161, #200, audit gap §2.8 /
 * §2.9). COPD and asthma both present with multi-day wearable-detectable
 * patterns — SpO2 drift downward, respiratory-rate elevation, sleep-
 * fragmentation rise from nocturnal symptoms, activity-tolerance reduction
 * — that are complementary to (but distinct from) the existing
 * [ConditionPatterns.respiratoryInfection] pattern. Both patterns gate
 * on multi-signal convergence so a one-off SpO2 dip on a hike doesn't
 * fire them; both surface as screening signals, same shape as
 * [AfibRhythmPattern.paroxysmalAfibScreen].
 *
 * Two pattern families ship here:
 *
 *  - **Trend-based signatures** ([copdExacerbationSignature],
 *    [asthmaExacerbationSignature]) — 7-day baseline-relative drift across
 *    3+ signals. Population-wide screens, no PhysiologyState gating, no
 *    severity floor (engine default tier). They catch the prodromal
 *    window where the owner can act before the acute event.
 *  - **Acute screens** ([acuteCopdExacerbationScreen],
 *    [acuteAsthmaExacerbationScreen]) — short-window (≤24 h) absolute-
 *    threshold + corroborator. Gated on [PhysiologyState.KNOWN_COPD] /
 *    [PhysiologyState.KNOWN_ASTHMA] (#200 added these states) so they
 *    don't fire for healthy owners. URGENT severity floor when SpO2
 *    drops below the literature cutoff (GINA 2024: ≤90 % in asthma
 *    exacerbation; GOLD 2024: ≤88 % in COPD exacerbation).
 *
 * **Environmental trigger correlation.** Asthma exacerbations correlate
 * with cold-dry and humid-warm conditions (GINA 2024 trigger list);
 * the acute asthma screen includes ambient-humidity and ambient-
 * temperature corroborator rules. When the environmental context is
 * absent (no phone-sensor / ESS BLE peripheral paired), those rules
 * gracefully don't activate and the pattern still fires from the
 * respiratory signals alone (corroborators have weight, never `required`).
 *
 * **OSA cross-reference.** Untreated obstructive sleep apnea is a known
 * upstream factor for both atrial fibrillation
 * ([AfibRhythmPattern.paroxysmalAfibScreen]) and chronic cardiovascular
 * strain ([ConditionPatterns.cardiovascularStress]) — see the references
 * field on [SleepApneaPattern.sleepApneaScreen] for the AHA / Somers
 * 2008 evidence base. The cross-link is intentionally documented here
 * so respiratory-side readers find the cardiology connection.
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
            acuteCopdExacerbationScreen,
            acuteAsthmaExacerbationScreen,
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

    /**
     * Acute asthma exacerbation screen (#200, audit gap §2.9 from
     * PAEDIATRICS_POV + MEDICAL_PROFESSIONAL_POV). Short-window
     * (24 h) screen that fires for owners with declared
     * [PhysiologyState.KNOWN_ASTHMA] when an acute SpO2 drop is
     * accompanied by sustained tachycardia, an activity collapse, and
     * (when peak-flow data is available) a peak-expiratory-flow decline.
     *
     * Severity floor [AlertTier.URGENT] — GINA 2024 lists SpO2 ≤90 % in
     * an asthma exacerbation as the threshold for moderate-to-severe
     * exacerbation requiring urgent medical evaluation. The absolute
     * cutoff lives on the SpO2 rule (`absoluteBelow = 90.0`) so this
     * pattern fires from a single SpO2 reading without waiting for a
     * baseline-relative deviation — when an owner with known asthma
     * sees 89 % on the oximeter, that's an acute event, not a trend.
     *
     * Distinct from [asthmaExacerbationSignature] (the prodromal 7-day
     * symptom-cluster pattern) by: short window, KNOWN_ASTHMA gating,
     * URGENT severity floor, absolute SpO2 cutoff, environmental
     * trigger correlation. Both patterns can fire concurrently — the
     * trend signature catches the rising-risk window before the
     * acute event; the acute screen catches the event itself.
     *
     * Environmental rules ([MetricType.AMBIENT_HUMIDITY_PCT] /
     * [MetricType.AMBIENT_TEMPERATURE_C]) are corroborators (`weight`
     * only, never `required`) — they add context when an ESS-class BLE
     * sensor or phone-barometer humidity reading is paired, and
     * gracefully don't activate when no environmental context is on
     * the bus. GINA 2024 trigger list calls out cold-dry and humid-
     * warm conditions as common exacerbation triggers.
     */
    val acuteAsthmaExacerbationScreen = ConditionPattern(
        id = "asthma_exacerbation_screen",
        title = "Acute asthma exacerbation pattern detected",
        category = ConditionCategory.RESPIRATORY,
        signalRules = listOf(
            SignalRule(
                MetricType.BLOOD_OXYGEN, DeviationDirection.BELOW, 0.0, 0, 2.0,
                ThresholdSource.LITERATURE,
                "GINA 2024 — SpO2 ≤90 % in an asthma exacerbation indicates moderate-to-severe acute attack requiring urgent medical evaluation",
                required = true,
                absoluteBelow = 90.0,
            ),
            SignalRule(
                MetricType.HEART_RATE, DeviationDirection.ABOVE, 1.5, 1, 1.0,
                ThresholdSource.LITERATURE,
                "GINA 2024 — sustained tachycardia (HR rise from rest) is a canonical acute-exacerbation sign; reflects sympathetic activation and beta-agonist response",
            ),
            SignalRule(
                MetricType.ACTIVE_MINUTES, DeviationDirection.BELOW, 1.0, 24, 0.8,
                ThresholdSource.LITERATURE,
                "GINA 2024 — activity collapse over the past 24 h corroborates acute respiratory limitation",
            ),
            SignalRule(
                MetricType.PEAK_EXPIRATORY_FLOW_LMIN, DeviationDirection.BELOW, 1.5, 24, 1.2,
                ThresholdSource.LITERATURE,
                "GINA 2024 — PEF below personal-best (typically <80 % of best) is the canonical home-monitoring sign of asthma exacerbation; weighted high when present",
            ),
            SignalRule(
                MetricType.AMBIENT_HUMIDITY_PCT, DeviationDirection.IRREGULAR, 1.5, 24, 0.4,
                ThresholdSource.LITERATURE,
                "GINA 2024 trigger list — humidity excursions (very dry <30 % or very humid >70 %) commonly precede exacerbations; corroborator only, gracefully absent when no environmental sensor paired",
            ),
            SignalRule(
                MetricType.AMBIENT_TEMPERATURE_C, DeviationDirection.IRREGULAR, 1.5, 24, 0.4,
                ThresholdSource.LITERATURE,
                "GINA 2024 trigger list — cold-air exposure is a common exacerbation trigger; corroborator only, gracefully absent without environmental sensor",
            ),
        ),
        minActiveSignals = 2,
        severityFloor = AlertTier.URGENT,
        explanation = "The most recent blood-oxygen reading is at or below 90 % alongside an acute respiratory pattern: tachycardia rising from resting baseline, activity dropping, and (when available) peak expiratory flow below personal best. Where ambient humidity or temperature data are on the bus, an environmental excursion in the past 24 h is included as exacerbation-trigger context. GINA 2024 lists SpO2 ≤90 % in an asthma exacerbation as the threshold for moderate-to-severe acute attack.",
        suggestedAction = "Vitals consistent with asthma exacerbation — consider rescue inhaler per your written asthma action plan and seek medical assessment if not improving. SpO2 ≤90 % with respiratory symptoms — seek immediate medical assessment.",
        references = listOf(
            "Global Initiative for Asthma (2024) — GINA Strategy for Asthma Management and Prevention",
            "Reddel HK et al. (2009) — ATS/ERS statement on asthma control and exacerbations",
            "Cretikos MA et al. (2008) — Respiratory rate: the neglected vital sign",
        ),
        earlyDetection = "GINA's home-monitoring framework anchors on peak expiratory flow versus the owner's personal best, with SpO2 ≤90 %, tachycardia, and activity collapse as the wearable-detectable signs of acute exacerbation. The acute screen requires KNOWN_ASTHMA so non-asthmatic owners don't see false fires from a strenuous hike at altitude.",
        requiredStates = setOf(PhysiologyState.KNOWN_ASTHMA, PhysiologyState.PAEDIATRIC),
    )

    /**
     * Acute COPD exacerbation screen (#200, audit gap §2.8). Short-window
     * (24 h) screen that fires for owners with declared
     * [PhysiologyState.KNOWN_COPD] when an acute SpO2 drop is accompanied
     * by respiratory-rate rise and activity collapse.
     *
     * Severity floor [AlertTier.URGENT] — GOLD 2024 calls out SpO2 ≤88 %
     * in a COPD patient as the threshold for moderate-to-severe acute
     * exacerbation requiring urgent medical evaluation (the lower
     * threshold versus asthma reflects COPD owners' typically lower
     * baseline SpO2). The absolute cutoff lives on the SpO2 rule
     * (`absoluteBelow = 88.0`).
     *
     * Distinct from [copdExacerbationSignature] (the prodromal 7-day
     * trend pattern) by: short window, KNOWN_COPD gating, URGENT
     * severity floor, absolute SpO2 cutoff. Both can fire concurrently.
     */
    val acuteCopdExacerbationScreen = ConditionPattern(
        id = "copd_exacerbation_screen",
        title = "Acute COPD exacerbation pattern detected",
        category = ConditionCategory.RESPIRATORY,
        signalRules = listOf(
            SignalRule(
                MetricType.BLOOD_OXYGEN, DeviationDirection.BELOW, 0.0, 0, 2.0,
                ThresholdSource.LITERATURE,
                "GOLD 2024 — SpO2 ≤88 % in a COPD patient indicates moderate-to-severe acute exacerbation requiring urgent medical evaluation; threshold accounts for typically lower COPD baseline",
                required = true,
                absoluteBelow = 88.0,
            ),
            SignalRule(
                MetricType.RESPIRATORY_RATE, DeviationDirection.ABOVE, 1.5, 24, 1.2,
                ThresholdSource.LITERATURE,
                "GOLD 2024 — respiratory-rate elevation above personal baseline is a canonical acute-exacerbation marker; Cretikos 2008 establishes RR as the most sensitive vital sign for respiratory compromise",
            ),
            SignalRule(
                MetricType.ACTIVE_MINUTES, DeviationDirection.BELOW, 1.0, 24, 0.8,
                ThresholdSource.LITERATURE,
                "GOLD 2024 — declining exercise tolerance and activity collapse corroborate acute respiratory limitation",
            ),
            SignalRule(
                MetricType.HEART_RATE, DeviationDirection.ABOVE, 1.5, 1, 0.6,
                ThresholdSource.LITERATURE,
                "GOLD 2024 — sustained tachycardia from rest reflects sympathetic activation and hypoxemia compensation in acute exacerbation",
            ),
        ),
        minActiveSignals = 2,
        severityFloor = AlertTier.URGENT,
        explanation = "The most recent blood-oxygen reading is at or below 88 % alongside an acute respiratory pattern: respiratory rate above baseline, activity dropping, and tachycardia. GOLD 2024 lists SpO2 ≤88 % in a COPD patient as the threshold for moderate-to-severe acute exacerbation. The 88 % threshold accounts for the typically lower COPD baseline relative to the general population.",
        suggestedAction = "Vitals consistent with COPD exacerbation — consider rescue medication per your written COPD action plan and seek medical assessment if not improving. SpO2 ≤88 % with respiratory symptoms — seek immediate medical assessment.",
        references = listOf(
            "Global Initiative for Chronic Obstructive Lung Disease (2024) — GOLD Report",
            "Cretikos MA et al. (2008) — Respiratory rate: the neglected vital sign",
            "Garcia-Aymerich J et al. (2006) — Risk factors of readmission to hospital for a COPD exacerbation",
        ),
        earlyDetection = "GOLD 2024 anchors home monitoring on SpO2 plus respiratory rate and activity tolerance — the acute screen requires KNOWN_COPD so the lower SpO2 cutoff doesn't fire for non-COPD owners at altitude or with transient illness.",
        requiredStates = setOf(PhysiologyState.KNOWN_COPD),
    )
}
