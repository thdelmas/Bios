package com.bios.app.alerts

import com.bios.app.model.ConditionCategory
import com.bios.app.physiology.PhysiologyState
import com.bios.contracts.MetricType

/**
 * Owner-declared psychiatry-context patterns (#205, PSYCHIATRY_POV §2.1–§2.7
 * + OBGYN_POV perinatal + EMERGENCY MEDICINE OUD audit convergence).
 *
 * These patterns fire **only** when the owner has explicitly declared the
 * relevant context via `Settings → Physiology state`. Bios never infers
 * bipolar disorder, anxiety, or opioid-use-disorder treatment from sensor
 * data. The physiological signatures here (reduced-sleep + activity rise,
 * sympathetic-spike + HRV collapse, sustained mild bradypnea) overlap too
 * heavily with everyday variation (jet lag, caffeine, exertion, illness)
 * to surface safely without the owner having named the underlying
 * condition first. See [PhysiologyState.KNOWN_BIPOLAR_DISORDER],
 * [PhysiologyState.KNOWN_GAD], [PhysiologyState.KNOWN_OUD_TREATMENT],
 * and `requiredStates` on [ConditionPattern].
 *
 * ## Manifesto guards (extremely strict here)
 *
 * Psychiatry has the highest iatrogenic-anxiety risk of any audit-derived
 * pattern surface. Bios speaks **only** in physiological data statements
 * — never "you may be entering mania," never "you may be depressed,"
 * never a composite "mental health score." All text is alert-policy
 * compliant (no second-person lifestyle judgement, no guilt, no scores,
 * no gamification — see [AlertContentPolicy]).
 *
 * Push-side delivery is **disabled** for these patterns. They surface only
 * on the pull side (the alert log the owner navigates into) so the owner
 * — not Bios — decides when to read them. The manifesto's "instrument,
 * not coach" reaches its strictest form in this file.
 *
 * Suicidality is **explicitly not** auto-detected, per the manifesto and
 * the audit's §2.4 finding. Eating disorders are owner-administered only
 * (audit §2.7). Voice / face / text emotional-state inference is
 * manifesto-banned. No composite "mental health score" exists.
 *
 * ## Patterns
 *
 *  1. [bipolarManiaProdromeScreen] — the audit's highest-impact missing
 *     pattern. Reduced sleep duration sustained ≥3 nights + maintained or
 *     elevated activity + (optional) circadian phase advance. Only fires
 *     under [PhysiologyState.KNOWN_BIPOLAR_DISORDER]. ADVISORY only.
 *     Bauer 2020; Seoul Nat'l Univ digital phenotyping (npj Digital
 *     Medicine 2024).
 *
 *  2. [anxietyPanicScreen] — sudden HR spike (≥20 bpm rise in <10 min)
 *     + HRV drop + (optional) hyperventilation pattern from RR. Only
 *     fires under [PhysiologyState.KNOWN_GAD]. ADVISORY only. Panic on
 *     biomarkers alone is not a medical emergency — the true-anaphylaxis
 *     URGENT path (#221 acute-window) lives elsewhere. Chalmers et al.
 *     2014, Kim et al. 2018.
 *
 *  3. [postpartumMoodScreen] — sleep disruption + activity drop +
 *     circadian flattening over a 14-day window. Only fires under the
 *     [PhysiologyState.POSTPARTUM] state that the perinatal PRs already
 *     wired. ADVISORY only. ACOG 2018 perinatal-mental-health committee
 *     opinion; EPDS framework (Cox et al. 1987).
 *
 *  4. [opioidUseRespiratoryPatternScreen] — sustained mild bradypnea
 *     (RR ≤10) with reduced SpO2 (≤92) over a 30-min window. Only fires
 *     under [PhysiologyState.KNOWN_OUD_TREATMENT]. ADVISORY only — this
 *     is the early-warning trend. The URGENT case (RR ≤8) is already
 *     covered by the NEWS2 / sepsis-screen pathway and the dedicated
 *     `opioid_respiratory_depression_screen` (issues #190 / #221).
 *     Nandakumar et al. 2019 UW SAFE.
 *
 * All text obeys [AlertContentPolicy] — CI-pinned in
 * `PsychiatryPatternsTest`.
 */
object PsychiatryPatterns {

    val all by lazy {
        listOf(
            bipolarManiaProdromeScreen,
            anxietyPanicScreen,
            postpartumMoodScreen,
            opioidUseRespiratoryPatternScreen,
        )
    }

    /**
     * Bipolar mania prodrome (audit §2.1 — highest-impact missing pattern).
     *
     * Seoul National University's 2024 digital-phenotyping study (npj
     * Digital Medicine) found that reduced sleep duration combined with
     * maintained or increased daytime activity is the strongest single
     * predictor of impending manic episodes in bipolar-I cohorts —
     * stronger than mood self-report. Bauer et al. (2020) had earlier
     * established that wearable sleep + actigraphy track manic-episode
     * onset days before clinical recognition. The signal is specific
     * enough in the bipolar population (≥80 % positive predictive value
     * in the Seoul cohort) to surface as a pull-side observation, and
     * unspecific enough in the general population that it must gate on
     * [PhysiologyState.KNOWN_BIPOLAR_DISORDER].
     *
     * Required signals (≥2 of 3): sleep-duration drop sustained ≥3
     * nights, steps elevation, and (optional) circadian phase advance.
     *
     * Severity is ADVISORY — never URGENT. Mania is not a same-shift
     * medical emergency on biomarkers alone; the path to clinical
     * evaluation is the owner's care plan with their psychiatrist,
     * which the alert text references.
     */
    val bipolarManiaProdromeScreen = ConditionPattern(
        id = "bipolar_mania_prodrome_screen",
        title = "Sleep and activity pattern shifted",
        category = ConditionCategory.MENTAL_HEALTH,
        signalRules = listOf(
            // Reduced sleep with maintained / increased activity is the
            // Seoul 2024 signature. ≥1.5h reduction sustained across
            // multiple nights is what the digital-phenotyping paper used
            // as the operational threshold; thresholdSigma 1.0 over a
            // 72-hour window is the closest match against personal
            // baseline that the SignalRule shape allows.
            SignalRule(
                MetricType.SLEEP_DURATION, DeviationDirection.BELOW, 1.0, 72, 1.2,
                ThresholdSource.LITERATURE,
                "Seoul Nat'l Univ (npj Digital Medicine 2024) — reduced sleep duration sustained ≥3 nights is the strongest single sensor predictor of manic-episode onset in bipolar-I cohorts",
            ),
            SignalRule(
                MetricType.STEPS, DeviationDirection.ABOVE, 1.0, 72, 1.2,
                ThresholdSource.LITERATURE,
                "Bauer et al. (2020) — daytime activity elevation alongside reduced sleep is the prodromal signature; healthy sleep reduction normally lowers activity, the inverse pattern is the mania-specific marker",
            ),
            SignalRule(
                MetricType.ACTIVE_MINUTES, DeviationDirection.ABOVE, 1.0, 72, 0.8,
                ThresholdSource.LITERATURE,
                "Bauer et al. (2020) — sustained active-minute elevation corroborates the steps signal across wearable form factors",
            ),
            // Optional circadian corroborator — when the chronobiology
            // engine has phase data, an advance (earlier wake, earlier
            // peak activity) further narrows the signal.
            SignalRule(
                MetricType.CIRCADIAN_PHASE_SHIFT, DeviationDirection.IRREGULAR, 1.0, 168, 0.8,
                ThresholdSource.LITERATURE,
                "Seoul Nat'l Univ (npj Digital Medicine 2024) — circadian phase advance is a corroborating prodromal marker when available",
            ),
        ),
        minActiveSignals = 2,
        explanation = "Sleep duration has dropped from baseline for several consecutive nights while daytime activity has stayed at or above baseline. This combination differs from the usual sleep-reduction-with-activity-drop pattern; in bipolar care plans it is often flagged as worth a check-in.",
        suggestedAction = "Review with the clinician who manages the bipolar care plan if this pattern is relevant. The sleep-duration trend, activity trend, and circadian phase data from Bios can be shared.",
        references = listOf(
            "Bauer M et al. (2020) — Smartphones in mental health: a critical review of background issues",
            "Seoul Nat'l Univ (npj Digital Medicine 2024) — Wearable sleep/circadian features predict mood episodes in bipolar disorder",
        ),
        earlyDetection = "In bipolar-I care, reduced sleep with sustained or rising activity is one of the earliest sensor-detectable markers a manic episode may be approaching — sometimes days before clinical recognition. The pattern is specific enough in this population to be worth surfacing as a data observation, and non-specific enough in the general population that it only activates when the owner has declared the context.",
        prevention = "Mood-stability planning is owned by the owner and their psychiatrist. Bios's role is to report what the sensors show; the clinical plan — sleep hygiene, medication adherence, trigger awareness — belongs to the care relationship.",
        healing = "Recovery from a mood episode is owned by the owner and their care team. Bios continues to report sensor data through the episode and recovery so the owner has objective context for clinical visits.",
        risks = "Untreated manic episodes can have substantial life-impact. The earlier a prodromal pattern is recognised, the more time the care plan has to respond. Bios's contribution is the data; the clinical decision is the owner's and their psychiatrist's.",
        requiredStates = setOf(PhysiologyState.KNOWN_BIPOLAR_DISORDER),
    )

    /**
     * Anxiety / panic episode (audit §2.3).
     *
     * Acute sympathetic arousal produces a recognisable wearable
     * signature: rapid HR rise (≥20 bpm in <10 min), simultaneous HRV
     * collapse, and often hyperventilation (RR ≥20). The pattern is
     * common to panic attacks, acute anxiety episodes, and several
     * non-anxiety states (vasovagal pre-syncope, hypoglycemia,
     * stimulant intake) — which is why it gates on
     * [PhysiologyState.KNOWN_GAD]. Inside that owner-declared context,
     * recurring episodes are clinically useful to track for the owner
     * and their therapist.
     *
     * ADVISORY only. The audit explicitly notes panic is not a medical
     * emergency on biomarkers alone — the true-anaphylaxis acute-window
     * URGENT path (#221) handles anaphylaxis specifically.
     */
    val anxietyPanicScreen = ConditionPattern(
        id = "anxiety_panic_screen",
        title = "Sympathetic arousal pattern detected",
        category = ConditionCategory.MENTAL_HEALTH,
        signalRules = listOf(
            // Rapid HR spike — the defining sympathetic signature.
            // thresholdSigma 2.0 over a 1-hour window approximates the
            // ≥20 bpm rise in <10 min that the literature describes.
            SignalRule(
                MetricType.HEART_RATE, DeviationDirection.ABOVE, 2.0, 1, 1.2,
                ThresholdSource.LITERATURE,
                "Chalmers JA et al. (2014) — acute anxiety / panic episodes produce a rapid HR rise of ≥20 bpm over minutes alongside autonomic withdrawal",
            ),
            SignalRule(
                MetricType.HEART_RATE_VARIABILITY, DeviationDirection.BELOW, 1.5, 1, 1.2,
                ThresholdSource.LITERATURE,
                "Kim HG et al. (2018) — HRV reduction is the autonomic-withdrawal signature accompanying sympathetic spikes in anxiety / panic episodes",
            ),
            // Optional hyperventilation corroborator. RR ≥20 sustained
            // for several minutes is the wearable-detectable shape.
            SignalRule(
                MetricType.RESPIRATORY_RATE, DeviationDirection.ABOVE, 1.5, 1, 0.8,
                ThresholdSource.LITERATURE,
                "Meuret AE & Ritz T (2010) — hyperventilation is a frequent panic-episode component when respiratory rate sensors capture it",
            ),
        ),
        minActiveSignals = 2,
        explanation = "Heart rate rose sharply alongside a drop in heart rate variability — a sympathetic-arousal pattern that commonly accompanies acute anxiety or panic episodes. Several other causes can produce the same shape (caffeine, exertion, vasovagal events); the pattern is reported as data, not as a diagnosis.",
        suggestedAction = "If this pattern is part of an established anxiety care plan, the HR, HRV, and respiratory-rate trace can be shared at the next clinician visit. Recurring episodes may warrant a check-in with a healthcare provider.",
        references = listOf(
            "Chalmers JA et al. (2014) — Anxiety disorders are associated with reduced heart rate variability: a meta-analysis",
            "Kim HG et al. (2018) — Stress and Heart Rate Variability: A Meta-Analysis",
            "Meuret AE, Ritz T (2010) — Hyperventilation in panic disorder and asthma",
        ),
        earlyDetection = "Acute sympathetic spikes are physiologically distinct from gradual baseline shifts. A ≥20 bpm rise in heart rate over minutes, coupled with HRV collapse, is the wearable-detectable shape. In an established anxiety context the recurrence pattern (timing, frequency, trigger correlation) is useful clinical data.",
        prevention = "Anxiety management is owned by the owner and their clinician. Bios's role is to report what the sensors show during an episode; the management plan — therapy, breathing exercises, medication if prescribed — belongs to the care relationship.",
        healing = "Recovery from an acute episode typically follows the autonomic curve back to baseline within minutes to hours. Bios tracks the recovery as data so the owner has objective context for clinical visits.",
        risks = "Untreated anxiety can affect quality of life, sleep, and cardiovascular health over time. The biomarker pattern is not itself dangerous in most cases; the clinical relationship is what addresses the underlying condition.",
        requiredStates = setOf(PhysiologyState.KNOWN_GAD),
    )

    /**
     * Postpartum mood screen (OBGYN_POV perinatal + PSYCHIATRY_POV §2.6).
     *
     * Postpartum depression affects ~10–15 % of postpartum women and
     * remains substantially underdiagnosed. ACOG's 2018 perinatal-
     * mental-health committee opinion recommends screening at multiple
     * postpartum visits using EPDS or equivalent. Wearable sensors can
     * surface the physiological correlates — sleep disruption, activity
     * reduction, circadian flattening — over a 14-day window that
     * predates many in-clinic screens.
     *
     * The pattern fires only under [PhysiologyState.POSTPARTUM] (already
     * wired by PR #189 / #227) — i.e. the owner has declared the
     * postpartum context. Sleep disruption is normative postpartum
     * (audit §2.7 — also wired into [PhysiologyState.POSTPARTUM]
     * exclusions for general sleep-disruption alerts), so this pattern
     * looks specifically for the **convergence** of sleep disruption,
     * activity drop, and circadian flattening that distinguishes
     * postpartum mood symptoms from ordinary postpartum sleep
     * fragmentation.
     *
     * ADVISORY only. No "you may be depressed" language. The text
     * frames as data observation with explicit pathway to the owner's
     * obstetric / mental-health provider and a reference to formal
     * screening instruments (EPDS).
     */
    val postpartumMoodScreen = ConditionPattern(
        id = "postpartum_mood_screen",
        title = "Postpartum sleep, activity, and rhythm pattern shifted",
        category = ConditionCategory.MENTAL_HEALTH,
        signalRules = listOf(
            // Sleep disruption — fragmentation index is the
            // clinically-meaningful axis here. Note: this signal is
            // also normative postpartum, which is why the pattern
            // also requires activity drop and circadian flattening.
            SignalRule(
                MetricType.SLEEP_FRAGMENTATION_INDEX, DeviationDirection.ABOVE, 1.0, 336, 1.0,
                ThresholdSource.LITERATURE,
                "Posmontier B (2008) — sleep fragmentation in postpartum depression exceeds the already-elevated postpartum baseline",
            ),
            SignalRule(
                MetricType.STEPS, DeviationDirection.BELOW, 1.5, 336, 1.0,
                ThresholdSource.LITERATURE,
                "Schuch FB et al. (2018) — activity reduction is both symptom and risk factor for depression; postpartum convergence with sleep disruption is the EPDS-tracked combination",
            ),
            SignalRule(
                MetricType.CIRCADIAN_PHASE_SHIFT, DeviationDirection.IRREGULAR, 1.0, 336, 1.0,
                ThresholdSource.LITERATURE,
                "Wirz-Justice A (2006) — circadian-rhythm flattening accompanies perinatal mood symptoms; distinguishes mood-related disruption from ordinary postpartum sleep fragmentation",
            ),
            SignalRule(
                MetricType.HEART_RATE_VARIABILITY, DeviationDirection.BELOW, 1.0, 336, 0.8,
                ThresholdSource.LITERATURE,
                "Koch C et al. (2019) — HRV depression corroborates mood-symptom physiology",
            ),
        ),
        minActiveSignals = 3,
        explanation = "Over the past two weeks, sleep fragmentation has been higher than baseline alongside reduced activity and a flatter daily rhythm. Postpartum sleep disruption alone is normative; this multi-signal convergence is the combination clinical screens like EPDS look for.",
        suggestedAction = "If this pattern matches recent experience, an obstetric or mental-health provider visit can include a formal perinatal screening (EPDS or equivalent). The sleep, activity, and rhythm data from Bios can be shared at the visit.",
        references = listOf(
            "ACOG Committee Opinion 757 (2018) — Screening for Perinatal Depression",
            "Cox JL et al. (1987) — Edinburgh Postnatal Depression Scale (EPDS)",
            "Posmontier B (2008) — Sleep quality in women with and without postpartum depression",
            "Wirz-Justice A (2006) — Biological rhythm disturbances in mood disorders",
            "Koch C et al. (2019) — Heart rate variability and mental health",
        ),
        earlyDetection = "Postpartum mood symptoms are often masked by the ordinary intensity of newborn-care sleep disruption. The wearable-detectable distinction is the convergence: sleep fragmentation plus activity reduction plus rhythm flattening, sustained across a 14-day window. The EPDS clinical instrument captures the subjective side of the same shift.",
        prevention = "Perinatal-mental-health prevention is owned by the owner and their obstetric / mental-health team. Established protective factors include social support, sleep protection where possible, and proactive screening — areas the care plan addresses.",
        healing = "Recovery from a perinatal mood episode is owned by the owner and their care team. Bios continues to report sensor data through the postpartum window so the owner has objective context for visits.",
        risks = "Untreated postpartum depression has substantial maternal and infant-bonding consequences. Early recognition — clinical screening plus the physiological pattern data Bios surfaces — gives the care plan the most options.",
        requiredStates = setOf(PhysiologyState.POSTPARTUM),
    )

    /**
     * Opioid-use respiratory early-warning pattern (EMERGENCY MEDICINE OUD
     * audit + PSYCHIATRY_POV §2.5).
     *
     * The clear emergency (RR ≤8 sustained, profound bradypnea) is already
     * covered by the NEWS2 / sepsis-screen URGENT pathway in `SepsisScreenPattern`
     * (RR ≤8 scores 3 points → URGENT) and is the explicit target of
     * issues #190 / #221's `opioid_respiratory_depression_screen`.
     *
     * This pattern is the **early-warning trend**: sustained mild
     * bradypnea (RR ≤10) combined with low-end SpO2 (≤92). Nandakumar
     * et al. (2019, UW SAFE) showed contactless sonar can detect this
     * precursor before frank respiratory depression sets in; the
     * wearable equivalent — RR and SpO2 streams — produces a similar
     * signal shape over a 30-minute window.
     *
     * ADVISORY only. The URGENT threshold (RR ≤8) belongs to the
     * existing sepsis / acute opioid-respiratory-depression pathway;
     * this pattern surfaces the slower drift so the owner and their
     * recovery team have the early-warning trace.
     *
     * Required signals (≥2 of 2): RR ≤10 sustained AND SpO2 ≤92
     * sustained. Both absolute thresholds — the wearable values are
     * what the clinical literature uses, not personal-baseline z-scores.
     */
    val opioidUseRespiratoryPatternScreen = ConditionPattern(
        id = "opioid_use_respiratory_pattern_screen",
        title = "Sustained low respiratory rate with low oxygen",
        category = ConditionCategory.RESPIRATORY,
        signalRules = listOf(
            SignalRule(
                MetricType.RESPIRATORY_RATE, DeviationDirection.BELOW, 0.0, 0, 1.2,
                ThresholdSource.LITERATURE,
                "Nandakumar et al. (2019) UW SAFE — RR ≤10 sustained is the early-warning threshold for opioid-induced respiratory depression; profound depression (RR ≤8) is handled by the NEWS2 / sepsis-screen URGENT pathway",
                required = true,
                absoluteBelow = 10.0,
            ),
            SignalRule(
                MetricType.BLOOD_OXYGEN, DeviationDirection.BELOW, 0.0, 0, 1.2,
                ThresholdSource.LITERATURE,
                "Nandakumar et al. (2019) UW SAFE — SpO2 ≤92 sustained alongside low RR is the early-warning combination; SpO2 ≤85 (frank hypoxia) is handled by the EmergencyVitalPatterns URGENT pathway",
                required = true,
                absoluteBelow = 92.0,
            ),
        ),
        minActiveSignals = 2,
        explanation = "Respiratory rate has been at or below 10 breaths per minute with blood oxygen at or below 92 % over a recent window. In the context of opioid-use-disorder treatment, this combination is the early-warning trend the literature flags. Profound depression (RR ≤8 or SpO2 ≤85) would escalate through the urgent pathway.",
        suggestedAction = "Discuss this trace with the prescriber managing the opioid-use-disorder treatment plan. The respiratory-rate and SpO2 timestamps from Bios are useful to share. If accompanied by sedation, confusion, slow speech, blueish lips, or unresponsiveness, contact emergency services immediately.",
        references = listOf(
            "Nandakumar R et al. (2019) — Opioid overdose detection using smartphones (UW SAFE)",
            "Dahan A et al. (2010) — Incidence, reversal, and prevention of opioid-induced respiratory depression",
        ),
        earlyDetection = "Opioid-induced respiratory depression progresses on a slope, not a step. Mild sustained bradypnea (RR 9–10) with low-end SpO2 (90–92) is the wearable-detectable precursor that arrives before the frank-emergency thresholds. Surfacing it as a trend gives the prescriber and the owner objective data to recalibrate before the urgent threshold is reached.",
        prevention = "Opioid-use-disorder treatment safety is owned by the owner and their prescriber. Naloxone access, dose calibration, and respiratory monitoring during titration are areas the care plan addresses.",
        healing = "Recovery and dose stabilisation are owned by the owner and their care team. Bios continues to report respiratory and oxygen data so the owner has objective context for prescriber visits.",
        risks = "Sustained opioid-induced respiratory depression progresses to apnea and hypoxic injury if not addressed. Surfacing the early-warning trend gives the most time for clinical response.",
        requiredStates = setOf(PhysiologyState.KNOWN_OUD_TREATMENT),
    )
}
