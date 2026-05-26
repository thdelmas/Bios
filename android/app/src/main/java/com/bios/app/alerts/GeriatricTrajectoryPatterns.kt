package com.bios.app.alerts

import com.bios.app.model.ConditionCategory
import com.bios.app.physiology.PhysiologyState
import com.bios.contracts.MetricType

/**
 * Geriatric trajectory advisories (issue #208).
 *
 * Three audits converge on pull-side trajectory surfaces:
 *  - GERIATRICS_PALLIATIVE_POV §2.3 — fall risk trajectory.
 *  - GERIATRICS_PALLIATIVE_POV §2.4 — cognitive trajectory.
 *  - GERIATRICS_PALLIATIVE_POV §2.5 + Emergency Medicine post-discharge —
 *    delirium risk (post-discharge window + sleep-wake disruption).
 *  - Neurology MCI — passive smartphone data as MCI prodromal signal.
 *
 * All three patterns share four design constraints:
 *
 *  1. **ADVISORY only.** No URGENT. No `severityFloor`. Severity caps at
 *     ADVISORY by classifier output.
 *  2. **Pull-side only.** `pullSideOnly = true` — the anomaly persists
 *     so the owner can find it on the trajectory view, but
 *     [AlertManager.sendNotification] never raises a system
 *     notification. Bios stays silent until the owner navigates in.
 *  3. **Owner opt-in.** `requiredStates = setOf(PhysiologyState.FRAILTY_FLAG)`
 *     gates all three: the owner has to explicitly declare the
 *     frailty / age-≥75 context before the patterns run. Manifesto
 *     guard: Bios never auto-classifies an owner as frail.
 *  4. **Data-statement framing.** No "your memory is declining," no
 *     "fall risk increased," no "you may have MCI." Strict
 *     [AlertContentPolicy] banlist (extended for this PR) keeps the
 *     copy clinical-observation-only.
 *
 * ## References
 *
 *  - Hausdorff JM (2007) — *Gait variability: methods, modeling and
 *    meaning*. Journal of NeuroEngineering and Rehabilitation.
 *  - Verghese J et al. (2009) — *Quantitative gait dysfunction and
 *    risk of cognitive decline and dementia*. J Gerontol A Biol Sci.
 *  - CDC STEADI — Stopping Elderly Accidents, Deaths & Injuries
 *    initiative (2017). Multifactorial fall-risk assessment.
 *  - Tinetti ME (1986) — *Performance-oriented assessment of mobility
 *    problems in elderly patients* (Tinetti POMA framework).
 *  - Inouye SK (1990) — *Clarifying confusion: the Confusion
 *    Assessment Method (CAM)*. Ann Intern Med.
 *  - Inouye SK et al. (2014) — *The Confusion Assessment Method:
 *    expanded usage and CAM-S severity*. Ann Intern Med.
 *  - Bellelli G et al. (2014) — *4AT, a new instrument for rapid
 *    delirium screening*. Age and Ageing.
 *  - Madan A et al. (Cogito study, 2012) — passive smartphone data
 *    as behavioural-state biomarker substrate.
 *  - Apple Cognition Score framework (Apple Heart & Movement Study,
 *    AlzNet 2024) — passive-smartphone composite for cognitive
 *    trajectory monitoring.
 *
 * All text obeys [AlertContentPolicy].
 */
object GeriatricTrajectoryPatterns {

    val all by lazy {
        listOf(
            fallRiskTrajectoryAdvisory,
            deliriumRiskScreen,
            cognitiveTrajectoryAdvisory,
        )
    }

    /**
     * Fall-risk trajectory (GERIATRICS_PALLIATIVE_POV §2.3 + STEADI +
     * Tinetti POMA). Composite signal: rising gait-time variability
     * (Hausdorff 2007 / Verghese 2009 anchor: CoV ≥ ~3 %) + the owner's
     * declared frailty context. Polypharmacy (the
     * [com.bios.app.data.MedicationAnnotationRepo] active-medications
     * count) is appended to the alert explanation by the existing
     * medication-context enhancer at anomaly creation time, so the
     * pattern itself doesn't need a SignalRule for it.
     */
    val fallRiskTrajectoryAdvisory = ConditionPattern(
        id = "fall_risk_trajectory_advisory",
        title = "Gait-variability trend higher than baseline",
        category = ConditionCategory.SAFETY,
        signalRules = listOf(
            // Baseline-relative gait-variability rise — 1.5σ over a 14-day
            // rolling window is the conservative trajectory signal. The
            // CDC STEADI / Tinetti POMA cutoffs use absolute CoV ≥ ~3 %;
            // this rule uses the owner's personal trajectory rather than
            // a population cutoff so the pattern reads a *change* rather
            // than declaring the owner above some external threshold.
            SignalRule(
                MetricType.GAIT_VARIABILITY_COV, DeviationDirection.ABOVE, 1.5, 336, 1.5,
                ThresholdSource.LITERATURE,
                "Hausdorff JM (2007); Verghese J et al. (2009) — stride-time CoV rise above baseline tracks gait dysfunction and fall risk",
            ),
            // Steps-per-day decline corroborates: deconditioning + fall
            // risk are coupled (CDC STEADI). 1.0σ over 7 days keeps the
            // bar low enough that a real shift contributes.
            SignalRule(
                MetricType.STEPS, DeviationDirection.BELOW, 1.0, 168, 0.8,
                ThresholdSource.LITERATURE,
                "CDC STEADI (2017) — sustained activity decline is part of the multifactorial fall-risk assessment",
            ),
            // Resting HR drift adds a deconditioning corroborator (Booth
            // 2012) — same logic used in cardiorespiratory_deconditioning.
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.0, 336, 0.5,
                ThresholdSource.LITERATURE,
                "Booth FW et al. (2012) — resting-HR drift over weeks accompanies deconditioning, a contributor to fall risk",
            ),
        ),
        minActiveSignals = 1,
        explanation = "Gait-variability trended higher this fortnight compared with the earlier baseline window. Higher stride-time variability is one of the multifactorial signals the CDC STEADI and Tinetti POMA frameworks include in older-adult fall-risk assessment, alongside medication review, balance testing, and clinical history. This is a data observation against a published clinical framework — not a fall-risk classification.",
        suggestedAction = "Review the trajectory at your next routine clinical visit. The CDC STEADI fall-risk algorithm pairs this kind of trend with medication review, balance / strength assessment, and home-safety review — a discussion best had with your healthcare provider.",
        references = listOf(
            "Hausdorff JM (2007) — Gait variability: methods, modeling and meaning. J NeuroEng Rehabil",
            "Verghese J et al. (2009) — Quantitative gait dysfunction and risk of cognitive decline and dementia",
            "CDC STEADI (2017) — Stopping Elderly Accidents, Deaths & Injuries: clinical-algorithm-based fall-risk assessment",
            "Tinetti ME (1986) — Performance-oriented assessment of mobility problems in elderly patients (POMA)",
        ),
        earlyDetection = "Stride-time coefficient of variation is a quantitative gait-timing signal: when the time between consecutive steps becomes more variable, the resulting trajectory is one of the substrate measurements the geriatric literature uses in fall-risk research. Bios surfaces the trend; clinical fall-risk assessment combines this kind of data with medication review, balance testing, and history.",
        prevention = "The CDC STEADI evidence-based components for older-adult fall reduction include medication review (especially psychotropics and antihypertensives), strength and balance work (tai-chi has the strongest evidence base), vision check, vitamin-D status, and home-safety review. The Tinetti POMA framework and the STEADI clinical algorithm operationalise these into a structured review with a primary-care or geriatric clinician.",
        healing = "When gait variability has risen, the standard clinical response is a structured fall-risk assessment with a clinician: medication reconciliation, balance / strength evaluation (Timed Up and Go, Tinetti POMA), vision, vitamin D, and home-safety review. Physical therapy referral and tai-chi or similar balance training have evidence supporting trajectory reversal.",
        risks = "The geriatric literature documents falls as a leading cause of injury, hospitalisation, and loss of independence in older adults. Fall-risk assessment is the standard clinical response — the trajectory surface is intended as a substrate the owner reviews with their clinician, not a stand-alone risk score.",
        // Owner has to declare FRAILTY_FLAG / age ≥75 context first.
        requiredStates = setOf(PhysiologyState.FRAILTY_FLAG),
        pullSideOnly = true,
    )

    /**
     * Delirium-risk screen (GERIATRICS_PALLIATIVE_POV §2.5 + Emergency
     * Medicine post-discharge delirium audit). Sleep-wake disruption +
     * recent ED / hospital discharge state + age ≥75. CAM-S (Inouye
     * 1990 / 2014) and 4AT (Bellelli 2014) anchor the screening
     * framework.
     *
     * The "recent discharge" gate isn't directly representable as a
     * SignalRule today; the pattern fires on sleep-wake disruption +
     * frailty-flag state, and the
     * [com.bios.app.alerts.GeriatricTrajectoryStore.isRecentlyDischarged]
     * helper is intended for an additional pull-side filter applied by
     * the trajectory view (so the advisory is only surfaced as the
     * "post-discharge variant" when both conditions are met). The
     * baseline pattern still fires on the physiological signal alone —
     * sleep-wake disruption in a frailty-flagged owner is itself a
     * delirium-risk substrate per the audit.
     */
    val deliriumRiskScreen = ConditionPattern(
        id = "delirium_risk_screen",
        title = "Sleep-wake pattern shift in geriatric context",
        category = ConditionCategory.SLEEP,
        signalRules = listOf(
            // Sleep fragmentation is the canonical wearable signal for
            // sleep-wake disruption — the proximal trigger the CAM-S /
            // 4AT delirium screening literature attaches to.
            SignalRule(
                MetricType.SLEEP_FRAGMENTATION_INDEX, DeviationDirection.ABOVE, 1.0, 72, 1.2,
                ThresholdSource.LITERATURE,
                "Inouye SK (1990) / Bellelli G (2014) — sleep-wake disruption is a CAM-S / 4AT framework input for delirium-risk screening",
            ),
            // Circadian phase shift adds an independent corroborator.
            SignalRule(
                MetricType.CIRCADIAN_PHASE_SHIFT, DeviationDirection.IRREGULAR, 1.0, 168, 1.0,
                ThresholdSource.LITERATURE,
                "Bellelli G et al. (2014) — circadian-rhythm disruption corroborates the sleep-wake component of the 4AT framework",
            ),
            // Wake after sleep onset is the third sleep-architecture signal.
            SignalRule(
                MetricType.WAKE_AFTER_SLEEP_ONSET, DeviationDirection.ABOVE, 1.0, 72, 0.8,
                ThresholdSource.LITERATURE,
                "Inouye SK (1990) — fragmented nocturnal sleep and increased nocturnal wakefulness are documented delirium-risk substrate",
            ),
        ),
        minActiveSignals = 2,
        explanation = "Sleep-wake architecture has shifted from baseline over the last few days. The CAM-S (Inouye 1990) and 4AT (Bellelli 2014) delirium-screening frameworks include sleep-wake disruption as one of several inputs that older-adult clinicians and emergency-medicine teams review, especially in the 30-day post-discharge window. This is a data observation against published clinical screens — not a delirium classification.",
        suggestedAction = "If new confusion, disorientation, hallucinations, or sudden mental-status change accompanies this sleep-wake shift, contact a healthcare provider promptly — acute delirium is a medical condition that benefits from rapid evaluation. Otherwise, mention the trend at the next routine clinical visit alongside any other context (recent illness, medication changes, hospitalisation).",
        references = listOf(
            "Inouye SK (1990) — Clarifying confusion: the Confusion Assessment Method (CAM). Ann Intern Med",
            "Inouye SK et al. (2014) — The Confusion Assessment Method: expanded usage and CAM-S severity",
            "Bellelli G et al. (2014) — 4AT, a new instrument for rapid delirium screening. Age and Ageing",
        ),
        earlyDetection = "Sleep-wake disruption frequently precedes or accompanies acute delirium in older adults — especially in the 30 days after a hospital or emergency-department discharge. The CAM-S and 4AT frameworks operationalise this into clinical screening tools. The wearable substrate (fragmentation, circadian phase, wake after sleep onset) covers part of the input; clinical screening adds the mental-status assessment piece.",
        prevention = "Standard inpatient delirium-prevention bundles (HELP: Hospital Elder Life Program, Inouye 1999) include orientation, mobility, sleep-environment management, hydration, and medication review. Outpatient analogues — consistent sleep schedule, daytime light exposure, hydration, careful medication review especially for sedating agents and anticholinergics — apply in the post-discharge window.",
        healing = "If new confusion, disorientation, or hallucinations develop, prompt medical evaluation is the standard clinical response. Outside acute presentation, sleep-schedule consolidation, daytime activity and light exposure, hydration tracking, and a structured medication review with a clinician (especially in the 30-day post-discharge window) are the evidence-supported components.",
        risks = "Delirium in older adults is associated with longer hospital stays, increased mortality, and accelerated cognitive decline when missed or untreated. Early clinical recognition — for which the wearable sleep-wake signal can be one input among several — is the standard care pathway.",
        requiredStates = setOf(PhysiologyState.FRAILTY_FLAG),
        pullSideOnly = true,
    )

    /**
     * Cognitive trajectory (GERIATRICS_PALLIATIVE_POV §2.4 + Neurology
     * MCI). Passive-smartphone composite — typing-speed decline + gait-
     * variability rise + circadian fragmentation + activity-pattern
     * simplification, over a 30-day rolling window.
     *
     * **Manifesto-critical**: this pattern surfaces a data trajectory,
     * never a label. The framing is exclusively "these passive-
     * smartphone signals shifted over the last month" — there is no
     * "you may have MCI", no "cognition declining", no "memory loss"
     * language anywhere in the explanation, suggested action, or
     * surrounding pull-side view. The [AlertContentPolicy] banlist is
     * extended in this PR to enforce the constraint.
     */
    val cognitiveTrajectoryAdvisory = ConditionPattern(
        id = "cognitive_trajectory_advisory",
        title = "Passive-smartphone activity pattern shifted over the last month",
        category = ConditionCategory.MENTAL_HEALTH,
        signalRules = listOf(
            // Typing-speed decline. v1: opt-in via TypingSpeedReader stub;
            // the rule reads MetricType.TYPING_SPEED_CHAR_PER_MIN when
            // the owner has enabled cognitive tracking. Gracefully no-ops
            // when no baseline exists.
            SignalRule(
                MetricType.TYPING_SPEED_CHAR_PER_MIN, DeviationDirection.BELOW, 1.0, 720, 1.2,
                ThresholdSource.LITERATURE,
                "Madan A et al. (Cogito study, 2012); Apple AlzNet (2024) — passive keyboard cadence is one substrate input for cognitive trajectory monitoring",
            ),
            // Gait variability rise is the second arm of the composite
            // (Verghese 2009: gait dysfunction tracks incident MCI /
            // dementia in community-dwelling older adults).
            SignalRule(
                MetricType.GAIT_VARIABILITY_COV, DeviationDirection.ABOVE, 1.0, 720, 1.0,
                ThresholdSource.LITERATURE,
                "Verghese J et al. (2009) — gait-variability rise tracks incident cognitive decline in community-dwelling older adults",
            ),
            // Circadian fragmentation — sleep regularity drop is the
            // third arm. Wirz-Justice 2006 anchored on mood, but the
            // same wearable substrate carries the cognitive-trajectory
            // signal (Lim 2013, Annals of Neurology — sleep fragmentation
            // and incident AD risk).
            SignalRule(
                MetricType.SLEEP_REGULARITY, DeviationDirection.BELOW, 1.0, 720, 1.0,
                ThresholdSource.LITERATURE,
                "Lim ASP et al. (2013) — sleep regularity and risk of incident cognitive decline in older adults",
            ),
            // Activity-pattern simplification — sustained steps decline.
            SignalRule(
                MetricType.STEPS, DeviationDirection.BELOW, 1.0, 720, 0.8,
                ThresholdSource.LITERATURE,
                "Buchman AS et al. (2012) — daily activity and incident cognitive impairment in older adults",
            ),
        ),
        minActiveSignals = 3,
        explanation = "Several passive-smartphone signals — typing cadence, gait timing, sleep-pattern regularity, and daily activity — have shifted over the past month relative to the earlier baseline window. The Cogito study and the Apple Cognition Score framework treat this kind of multi-signal passive-smartphone composite as a substrate for cognitive trajectory monitoring research. This is a data observation about your own signals — not a clinical assessment of cognitive function.",
        suggestedAction = "Mention the trend at your next routine clinical visit. The framework around this kind of passive-smartphone data is research-grade; clinical evaluation (history, structured testing) is what translates a data trajectory into clinical context.",
        references = listOf(
            "Madan A et al. (2012) — Sensing the 'health state' of a community (Cogito study)",
            "Verghese J et al. (2009) — Quantitative gait dysfunction and risk of cognitive decline and dementia",
            "Lim ASP et al. (2013) — Sleep fragmentation and risk of incident Alzheimer disease and cognitive decline. Ann Neurol",
            "Buchman AS et al. (2012) — Total daily physical activity and the risk of AD and cognitive decline in older adults. Neurology",
            "Apple Heart & Movement Study / AlzNet (2024) — passive-smartphone cognitive trajectory framework",
        ),
        earlyDetection = "Passive-smartphone data — keyboard cadence, gait timing, sleep-pattern regularity, and daily activity — is the substrate the research literature uses for long-horizon trajectory monitoring. The aggregate is a research-grade trend; clinical evaluation is what attaches meaning to the trend.",
        prevention = "The trajectory literature points to consistent sleep, regular physical activity, social engagement, and ongoing intellectual engagement as the components with the strongest evidence base for older-adult brain-health maintenance. These are the same components a routine clinical brain-health review covers.",
        healing = "When a trajectory shift is observed, the clinical pathway is structured review with a primary-care or geriatric clinician — history, medication review, vascular and metabolic risk-factor management, and where indicated, structured testing. The passive-smartphone data is one input among several, not a stand-alone evaluation.",
        risks = "Trajectory monitoring is a research-grade substrate; conclusions about its individual-level meaning require clinical evaluation. The trajectory view is intended as something the owner reviews with a clinician — not a stand-alone score.",
        requiredStates = setOf(PhysiologyState.FRAILTY_FLAG),
        pullSideOnly = true,
    )
}
