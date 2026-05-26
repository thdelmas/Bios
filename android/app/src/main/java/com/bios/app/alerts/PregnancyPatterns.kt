package com.bios.app.alerts

import com.bios.app.model.AlertTier
import com.bios.app.model.ConditionCategory
import com.bios.app.physiology.PhysiologyState
import com.bios.contracts.MetricType

/**
 * Pregnancy- and postpartum-specific condition patterns (issue #189,
 * OBGYN_POV §2.1–2.3 with converging support from the Primary Care and
 * Cardiology peripartum-cardiomyopathy lenses).
 *
 * Bios already ingests every input these patterns need
 * ([MetricType.BLOOD_PRESSURE_SYSTOLIC] / [MetricType.BLOOD_PRESSURE_DIASTOLIC]
 * via Withings cuff, Samsung BP, manual entry; [MetricType.RESTING_HEART_RATE]
 * via wearable nightly estimate; [MetricType.STEPS] for activity drop;
 * [MetricType.SLEEP_STAGE] for nocturnal disruption) — but until this PR no
 * pattern composed them under a pregnancy-aware shape. The audit's top
 * finding: pre-eclampsia is the #1 preventable cause of maternal mortality
 * in the developed world (CDC PMSS 2017–2019), it presents quietly as
 * elevated home BP for days-to-weeks before severe-range readings, and the
 * postpartum tail extends six weeks past delivery.
 *
 * ## Three sub-shapes for the preeclampsia screen
 *
 *  1. **[gestationalHypertensionScreen]** — sustained BP rise (median
 *     ≥140 / ≥90 across 3+ readings over a 7-day window) during
 *     PREGNANCY_T2 / T3. Mirrors the median + minimum-readings white-coat
 *     guard from [HypertensionPatterns.hypertensionEmerging] but uses the
 *     ACOG 2020 pregnancy-specific cutoff (≥140/90 — gestational
 *     hypertension threshold) rather than the standard stage-1 cutoff
 *     (≥130/80). Severity classifier output (ADVISORY → NOTICE → URGENT
 *     by activation ratio).
 *  2. **[severeRangePreeclampsiaScreen]** — single-reading severe-range
 *     ≥160/110. ACOG 2020 + Sibai 2003: this is the threshold at which the
 *     risk of stroke, abruption, eclamptic seizure, and acute end-organ
 *     damage becomes acute. `severityFloor = URGENT`. Mirrors the
 *     single-reading shape of [HypertensionPatterns.hypertensiveUrgency]
 *     with the pregnancy-specific cutoff.
 *  3. **[postpartumPreEclampsiaScreen]** — same severe-range cutoff,
 *     gated on POSTPARTUM. Preeclampsia can present de novo in the six
 *     weeks after delivery and accounts for a disproportionate share of
 *     maternal cardiovascular mortality (CDC PMSS 2017–2019).
 *     `severityFloor = URGENT`.
 *
 * ## Postpartum patterns (lighter scope)
 *
 *  4. **[peripartumCardiomyopathyScreen]** — sustained tachycardia + step
 *     drop in POSTPARTUM beyond expected recovery. ESC 2019 / Sliwa 2020
 *     peripartum guidance. Severity ADVISORY — Bios reports the pattern
 *     and prompts evaluation; PPCM diagnosis still requires
 *     echocardiography. Manifesto-clean: instrument-not-coach framing.
 *
 * The VTE pull-side reminder and the postpartum-tuned mental-health
 * variant called out in #189 are explicit follow-up scope — the
 * pull-side reminder lives in a different surface (Read tab / education
 * sheet) and a postpartum-tuned mental-health variant is more than one
 * SignalRule change, so it lands in a separate PR. Perinatal depression
 * is already covered by [ConditionPatterns.mentalHealthCorrelate]
 * remaining active in pregnancy / postpartum states (see brief).
 *
 * ## Why split out from [HypertensionPatterns]
 *
 * The brief specifies `PregnancyPatterns.kt` as a distinct file: pregnancy
 * physiology is not a regional variant of adult hypertension thresholds,
 * it is a different physiologic state with its own cutoffs and its own
 * downstream risks (HELLP, eclampsia, PPCM). Co-locating the four screens
 * keeps the audit-§2 surface readable in one place.
 *
 * ## References (per ACOG 2020, Sibai 2003, Sliwa 2020, CDC PMSS)
 *
 *  - ACOG Practice Bulletin 222 (2020) — Gestational Hypertension and
 *    Preeclampsia. Obstetrics & Gynecology.
 *  - Sibai BM (2003) — Diagnosis and management of gestational
 *    hypertension and preeclampsia. Obstetrics & Gynecology.
 *  - Sliwa K et al. (2020) — Peripartum cardiomyopathy: clinical features,
 *    epidemiology, and outcomes. European Heart Journal / ESC position.
 *  - Centers for Disease Control and Prevention (2017–2019) — Pregnancy
 *    Mortality Surveillance System data on postpartum cardiovascular
 *    mortality.
 *
 * All text obeys [AlertContentPolicy] — data statements plus professional
 * referral, never "you may have preeclampsia". Owner is final.
 */
object PregnancyPatterns {

    val all by lazy {
        listOf(
            gestationalHypertensionScreen,
            severeRangePreeclampsiaScreen,
            postpartumPreEclampsiaScreen,
            peripartumCardiomyopathyScreen,
        )
    }

    /**
     * Pregnancy-state set used by the antepartum screens. T2 + T3 only:
     * gestational hypertension is defined as new-onset HTN after 20 weeks'
     * gestation (ACOG 2020), so T1 BP elevation is more likely chronic
     * hypertension than gestational and is captured by the standard
     * [HypertensionPatterns.hypertensionEmerging] / [HypertensionPatterns.hypertensiveUrgency].
     */
    internal val ANTEPARTUM_T2_T3: Set<PhysiologyState> =
        setOf(PhysiologyState.PREGNANCY_T2, PhysiologyState.PREGNANCY_T3)

    /**
     * The full pregnancy-and-postpartum window in which preeclampsia-class
     * patterns matter. Severe-range BP and postpartum patterns gate on
     * this composite set via the inverse of `excludedStates`.
     */
    internal val PREGNANCY_AND_POSTPARTUM: Set<PhysiologyState> =
        PhysiologyState.PREGNANCY + setOf(PhysiologyState.POSTPARTUM)

    /**
     * All [PhysiologyState]s **except** [ANTEPARTUM_T2_T3]. Used to set
     * `excludedStates` so the screen is *only* active in T2 / T3.
     */
    private val EXCLUDED_OUTSIDE_T2_T3: Set<PhysiologyState> =
        PhysiologyState.entries.toSet() - ANTEPARTUM_T2_T3

    /**
     * All [PhysiologyState]s **except** [PREGNANCY_AND_POSTPARTUM]. Used
     * to keep the severe-range screen active across every pregnancy
     * trimester and the postpartum window.
     */
    private val EXCLUDED_OUTSIDE_PREGNANCY_AND_POSTPARTUM: Set<PhysiologyState> =
        PhysiologyState.entries.toSet() - PREGNANCY_AND_POSTPARTUM

    /**
     * All [PhysiologyState]s **except** [PhysiologyState.POSTPARTUM]. Used
     * by the postpartum-only screens.
     */
    private val EXCLUDED_OUTSIDE_POSTPARTUM: Set<PhysiologyState> =
        PhysiologyState.entries.toSet() - setOf(PhysiologyState.POSTPARTUM)

    /**
     * Gestational hypertension / mild preeclampsia screen. Sustained BP
     * ≥140 systolic OR ≥90 diastolic across the 7-day window with at
     * least 3 readings, gated on T2 / T3 pregnancy. Mirrors the median +
     * minimum-readings shape of [HypertensionPatterns.hypertensionEmerging]
     * with the ACOG 2020 pregnancy cutoff (10 mmHg below the non-pregnant
     * stage-1 threshold).
     *
     * Both BP rules are non-required and `minActiveSignals = 1` so
     * isolated systolic, isolated diastolic, or combined gestational
     * hypertension all surface — pregnancy-onset hypertension often
     * presents as an isolated diastolic rise first.
     */
    val gestationalHypertensionScreen = ConditionPattern(
        id = "gestational_hypertension_screen",
        title = "Blood pressure pattern in pregnancy",
        category = ConditionCategory.CARDIOVASCULAR,
        signalRules = listOf(
            SignalRule(
                MetricType.BLOOD_PRESSURE_SYSTOLIC, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "ACOG Practice Bulletin 222 (2020) — median systolic BP ≥140 mmHg after 20 weeks' gestation defines gestational hypertension; threshold for same-day obstetric evaluation",
                absoluteAbove = 140.0,
                absoluteWindowHours = 168,
                absoluteMinReadings = 3,
            ),
            SignalRule(
                MetricType.BLOOD_PRESSURE_DIASTOLIC, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "ACOG Practice Bulletin 222 (2020) — median diastolic BP ≥90 mmHg after 20 weeks' gestation defines gestational hypertension; pregnancy-specific cutoff is 10 mmHg below the non-pregnant stage-1 threshold",
                absoluteAbove = 90.0,
                absoluteWindowHours = 168,
                absoluteMinReadings = 3,
            ),
        ),
        minActiveSignals = 1,
        explanation = "Blood pressure pattern in pregnancy: the 7-day median of recent home readings is at or above 140/90 (≥3 readings). ACOG Practice Bulletin 222 (2020) defines gestational hypertension as new-onset BP ≥140/90 after 20 weeks' gestation. This is a data observation against a published clinical screen — not a diagnosis of preeclampsia, which additionally requires proteinuria or signs of end-organ involvement that an obstetric evaluation can assess.",
        suggestedAction = "Blood pressure pattern in pregnancy — seek same-day obstetric assessment. Bringing the trend of home readings (timestamps, values, posture if known) to the visit is helpful; preeclampsia is diagnosed by combining BP data with proteinuria, labs, and symptom review.",
        references = listOf(
            "ACOG Practice Bulletin 222 (2020) — Gestational Hypertension and Preeclampsia. Obstetrics & Gynecology",
            "Sibai BM (2003) — Diagnosis and management of gestational hypertension and preeclampsia. Obstetrics & Gynecology",
        ),
        earlyDetection = "Gestational hypertension and preeclampsia present quietly. The earliest detectable signal is a sustained rise in home BP — median ≥140/90 across multiple readings, often days to weeks before any symptom (headache, visual changes, epigastric pain, oedema). The 7-day median + 3-reading floor mirrors the obstetric home-BP convention and is robust to a single white-coat outlier. Pre-eclampsia detected during this window has dramatically better maternal and fetal outcomes than presentation in the severe range.",
        risks = "Untreated gestational hypertension can progress to preeclampsia (15–25 %), severe preeclampsia, HELLP syndrome, eclampsia, placental abruption, and fetal growth restriction. The #1 preventable cause of maternal mortality in the developed world (CDC PMSS 2017–2019). Early obstetric evaluation enables BP control, antenatal surveillance, fetal monitoring, and — when indicated — timely delivery, the only definitive treatment for preeclampsia.",
        // Gate to T2 + T3 only. T1 BP elevation is handled by the standard
        // hypertension patterns (likely chronic hypertension, not
        // gestational). Outside pregnancy, the standard non-pregnant
        // cutoff applies.
        excludedStates = EXCLUDED_OUTSIDE_T2_T3,
    )

    /**
     * Severe-range preeclampsia screen. Single reading ≥160 systolic OR
     * ≥110 diastolic during pregnancy (any trimester) — ACOG 2020 / Sibai
     * 2003. Severe-range BP in pregnancy is the threshold at which
     * stroke, eclamptic seizure, placental abruption, and acute end-organ
     * damage become an acute risk; the clinical protocol is immediate
     * assessment, not the standard "re-check, then act" of non-pregnant
     * hypertensive urgency.
     *
     * Single-reading semantics (no `absoluteWindowHours`) and
     * `severityFloor = URGENT` — mirrors
     * [HypertensionPatterns.hypertensiveUrgency] but at the pregnancy
     * cutoff (≥160/110 vs ≥180/120 non-pregnant). Both BP rules are
     * non-required and `minActiveSignals = 1` so either crossing the
     * threshold fires the pattern (OR semantics).
     *
     * Gated on any pregnancy trimester (T1 / T2 / T3) — even though
     * severe-range BP before 20 weeks is more likely chronic-hypertensive
     * crisis than preeclampsia, the action (immediate assessment) is the
     * same and missing it carries the same stroke / eclampsia risk.
     */
    val severeRangePreeclampsiaScreen = ConditionPattern(
        id = "severe_range_preeclampsia_screen",
        title = "Severe-range blood pressure in pregnancy",
        category = ConditionCategory.CARDIOVASCULAR,
        signalRules = listOf(
            SignalRule(
                MetricType.BLOOD_PRESSURE_SYSTOLIC, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "ACOG Practice Bulletin 222 (2020) — systolic BP ≥160 mmHg in pregnancy is severe-range hypertension; acute treatment threshold (target <160/110) per Sibai 2003",
                absoluteAbove = 160.0,
            ),
            SignalRule(
                MetricType.BLOOD_PRESSURE_DIASTOLIC, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "ACOG Practice Bulletin 222 (2020) — diastolic BP ≥110 mmHg in pregnancy is severe-range hypertension; acute treatment threshold",
                absoluteAbove = 110.0,
            ),
        ),
        minActiveSignals = 1,
        severityFloor = AlertTier.URGENT,
        explanation = "A recent blood pressure reading in pregnancy is at or above the severe-range threshold (≥160 systolic or ≥110 diastolic). ACOG Practice Bulletin 222 (2020) defines this as severe-range hypertension and the threshold at which acute antihypertensive treatment is indicated to reduce the risk of maternal stroke, eclampsia, and placental abruption (Sibai 2003). The non-pregnant 'rest and re-check' protocol does not apply at this magnitude in pregnancy — the action is immediate assessment.",
        suggestedAction = "Blood pressure ≥160/110 in pregnancy — seek immediate medical assessment. If accompanied by severe headache, vision changes, epigastric or right-upper-quadrant pain, sudden swelling of face or hands, reduced fetal movement, or seizure, contact emergency services without delay.",
        references = listOf(
            "ACOG Practice Bulletin 222 (2020) — Gestational Hypertension and Preeclampsia. Obstetrics & Gynecology",
            "Sibai BM (2003) — Diagnosis and management of gestational hypertension and preeclampsia. Obstetrics & Gynecology",
            "Centers for Disease Control and Prevention (2017–2019) — Pregnancy Mortality Surveillance System",
        ),
        earlyDetection = "Severe-range BP in pregnancy can develop within hours from a previously mild gestational-hypertension trajectory. A single confirmed reading at or above 160/110 — regardless of the trend pattern from earlier in the week — crosses the ACOG 2020 acute-treatment threshold. Bios surfaces it as a single-reading signal because the clinical action (immediate obstetric assessment) does not wait for additional readings at this magnitude.",
        risks = "Untreated severe-range hypertension in pregnancy is the leading proximate driver of maternal stroke, eclamptic seizure, placental abruption, HELLP syndrome, pulmonary oedema, and acute kidney injury (CDC PMSS 2017–2019). Definitive treatment of preeclampsia with severe features is delivery; acute antihypertensive treatment (IV labetalol, hydralazine, or nifedipine per ACOG 2020) bridges to delivery and reduces the stroke window.",
        excludedStates = EXCLUDED_OUTSIDE_PREGNANCY_AND_POSTPARTUM,
    )

    /**
     * Postpartum preeclampsia screen. Same severe-range cutoff (≥160/110)
     * gated on the POSTPARTUM state — preeclampsia can present de novo in
     * the six weeks after delivery and accounts for a disproportionate
     * share of postpartum maternal cardiovascular mortality (CDC PMSS
     * 2017–2019). Owners are often discharged from obstetric care during
     * this window and may not recognise the symptoms; the wearable BP
     * substrate matters most exactly here.
     *
     * Single-reading semantics, `severityFloor = URGENT`. Co-exists with
     * [severeRangePreeclampsiaScreen] (which also fires in postpartum) by
     * having a postpartum-specific id, title, explanation, and suggested
     * action — the suggested-action text references the obstetric / ED
     * postpartum pathway (some EDs miss postpartum preeclampsia because
     * the owner is technically no longer pregnant).
     */
    val postpartumPreEclampsiaScreen = ConditionPattern(
        id = "postpartum_pre_eclampsia_screen",
        title = "Severe-range blood pressure postpartum",
        category = ConditionCategory.CARDIOVASCULAR,
        signalRules = listOf(
            SignalRule(
                MetricType.BLOOD_PRESSURE_SYSTOLIC, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "ACOG Practice Bulletin 222 (2020) — postpartum preeclampsia can present de novo up to 6 weeks after delivery; systolic BP ≥160 mmHg is the severe-range / acute-treatment threshold",
                absoluteAbove = 160.0,
            ),
            SignalRule(
                MetricType.BLOOD_PRESSURE_DIASTOLIC, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "ACOG Practice Bulletin 222 (2020) — postpartum preeclampsia: diastolic BP ≥110 mmHg is the severe-range / acute-treatment threshold",
                absoluteAbove = 110.0,
            ),
        ),
        minActiveSignals = 1,
        severityFloor = AlertTier.URGENT,
        explanation = "A recent blood pressure reading after delivery is at or above the severe-range threshold (≥160 systolic or ≥110 diastolic). Preeclampsia can present de novo in the six weeks postpartum and is a leading proximate driver of postpartum maternal cardiovascular mortality (CDC Pregnancy Mortality Surveillance System 2017–2019). Owners discharged from obstetric care may not recognise the presentation — the wearable BP substrate is the screen.",
        suggestedAction = "Blood pressure ≥160/110 in the postpartum window — seek immediate medical assessment. Mention recent delivery to the assessing clinician (some emergency departments under-recognise postpartum preeclampsia because the owner is no longer pregnant). If accompanied by severe headache, vision changes, epigastric or right-upper-quadrant pain, shortness of breath, or seizure, contact emergency services without delay.",
        references = listOf(
            "ACOG Practice Bulletin 222 (2020) — Gestational Hypertension and Preeclampsia. Obstetrics & Gynecology",
            "Sibai BM (2003) — Diagnosis and management of gestational hypertension and preeclampsia. Obstetrics & Gynecology",
            "Centers for Disease Control and Prevention (2017–2019) — Pregnancy Mortality Surveillance System",
        ),
        earlyDetection = "Postpartum preeclampsia most commonly presents between 3 and 6 days after delivery, with the risk window extending to six weeks. A confirmed reading at or above 160/110 crosses the ACOG 2020 acute-treatment threshold and requires same-day obstetric / emergency assessment regardless of how the owner subjectively feels — the silent presentation is the dangerous one.",
        risks = "Postpartum preeclampsia carries the same maternal-stroke, eclamptic-seizure, pulmonary-oedema, and HELLP risks as antepartum severe-range disease, with the additional confounder that obstetric monitoring has typically ended. Early detection from home BP readings — when symptoms are absent or attributed to normal recovery — is the highest-yield intervention point.",
        excludedStates = EXCLUDED_OUTSIDE_POSTPARTUM,
    )

    /**
     * Peripartum cardiomyopathy (PPCM) screen. Sustained tachycardia +
     * activity drop in the POSTPARTUM window beyond the expected
     * postpartum recovery curve. ESC 2019 position statement / Sliwa 2020
     * peripartum guidance: PPCM presents most commonly in the first
     * postpartum month with dyspnoea, exercise intolerance, orthopnoea,
     * and tachycardia; many cases are initially attributed to "normal"
     * postpartum fatigue and diagnosed only after presentation with
     * decompensated heart failure.
     *
     * Pattern is high-sensitivity by design (the audit framing). The
     * specific signals — RHR elevation above personal baseline + step
     * count below baseline + sleep disruption — are the wearable-
     * detectable footprint of a postpartum owner whose cardiac output is
     * dropping. PPCM definitive diagnosis requires echocardiography
     * (LVEF <45 %); Bios reports the pattern and prompts cardiology /
     * obstetric evaluation, never claims PPCM.
     *
     * Severity left at the classifier default (ADVISORY / NOTICE) —
     * `severityFloor = null`. The two preeclampsia URGENT screens above
     * are the immediate-action surface; PPCM is a same-week
     * cardiovascular evaluation question. If the owner additionally
     * crosses the severe-range BP threshold or the tachycardia-critical
     * emergency threshold, those URGENT patterns fire alongside.
     *
     * Gated on POSTPARTUM only. The normal postpartum-recovery RHR
     * elevation is suppressed for the general
     * [com.bios.app.alerts.ConditionPatterns.cardiovascularStress]
     * pattern (which excludes POSTPARTUM) — this PPCM-specific shape
     * trades that broad exclusion for a postpartum-tuned signal that
     * looks for the sustained tachycardia + reduced activity + sleep
     * disruption triad which distinguishes PPCM from baseline
     * postpartum fatigue.
     */
    val peripartumCardiomyopathyScreen = ConditionPattern(
        id = "peripartum_cardiomyopathy_screen",
        title = "Cardiovascular pattern in postpartum window",
        category = ConditionCategory.CARDIOVASCULAR,
        signalRules = listOf(
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.5, 168, 1.2,
                ThresholdSource.LITERATURE,
                "Sliwa K et al. (2020) — sustained tachycardia is the most common wearable-detectable PPCM presenting sign alongside dyspnoea and exercise intolerance",
            ),
            SignalRule(
                MetricType.STEPS, DeviationDirection.BELOW, 1.5, 168, 1.0,
                ThresholdSource.LITERATURE,
                "Sliwa K et al. (2020) — exercise intolerance / reduced daily activity is part of the PPCM presenting triad; postpartum-tuned threshold accommodates the normal recovery activity drop",
            ),
            SignalRule(
                MetricType.SLEEP_STAGE, DeviationDirection.BELOW, 1.0, 168, 0.8,
                ThresholdSource.LITERATURE,
                "Sliwa K et al. (2020) — nocturnal dyspnoea / orthopnoea fragments sleep and is a common PPCM presentation; baseline-relative sleep decline corroborates the cardiovascular pattern",
            ),
        ),
        minActiveSignals = 2,
        explanation = "Cardiovascular pattern in the postpartum window: sustained elevation of resting heart rate above your personal baseline alongside reduced daily activity and disrupted sleep. ESC 2019 / Sliwa 2020 describes this as the wearable-detectable presentation of peripartum cardiomyopathy (PPCM) — a postpartum heart-failure syndrome whose early symptoms are commonly attributed to normal recovery. This is a data observation, not a PPCM diagnosis; definitive diagnosis requires echocardiography.",
        suggestedAction = "Discuss this cardiovascular pattern with an obstetrician or cardiologist. Mention any shortness of breath, difficulty lying flat to sleep, swelling of feet or legs, or chest discomfort — the combination guides whether echocardiography is indicated. If acute shortness of breath, chest pain, or fainting occurs, contact emergency services without delay.",
        references = listOf(
            "Sliwa K et al. (2020) — Peripartum cardiomyopathy: clinical features, epidemiology, and outcomes. European Heart Journal",
            "Bauersachs J et al. (2019) — Pathophysiology, diagnosis and management of peripartum cardiomyopathy. ESC Heart Failure Association position statement",
            "Centers for Disease Control and Prevention (2017–2019) — Pregnancy Mortality Surveillance System",
        ),
        earlyDetection = "PPCM typically presents in the first postpartum month with dyspnoea, exercise intolerance, orthopnoea, and tachycardia. Many cases are diagnosed late because the symptoms overlap with normal postpartum recovery. The wearable substrate distinguishes the trajectory: a sustained RHR elevation that does not return toward the pregnancy / pre-pregnancy baseline, paired with a step count below personal baseline and disrupted sleep, is the high-yield signal for evaluation.",
        risks = "Untreated PPCM can progress to decompensated heart failure, thromboembolic events (cardiac mural thrombus is common in PPCM), arrhythmia, and maternal cardiovascular mortality (CDC PMSS 2017–2019). Outcomes are strongly stage-dependent: detection at the dyspnoea / exercise-intolerance stage is associated with substantially better LV recovery rates than detection after acute decompensation. Cardiology-led treatment (afterload reduction, bromocriptine in selected cases per Bauersachs 2019, anticoagulation when indicated) is well-tolerated and often produces full LV recovery.",
        excludedStates = EXCLUDED_OUTSIDE_POSTPARTUM,
    )
}
