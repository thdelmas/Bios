package com.bios.app.alerts

import com.bios.app.model.ConditionCategory
import com.bios.app.physiology.PhysiologyState
import com.bios.contracts.MetricType

/**
 * Anthropometry + body-composition trajectory patterns (#199, audit gap
 * §2.7 PAEDIATRICS_POV.md + Geriatrics sarcopenia + Oncology cachexia).
 *
 * Three patterns share the trajectory primitive — weight / lean-mass /
 * height across time — but read it through different clinical lenses:
 *
 *  - **[failureToThriveScreen]** (paediatric) — weight-for-age percentile
 *    falls ≥2 percentile bands across consecutive measurements over ≥3
 *    months. The canonical primary-paediatric-care surveillance signal.
 *    Gated on `PhysiologyState.PAEDIATRIC` so adult owners never see this
 *    pattern fire. Pattern severity ADVISORY (engine default cap).
 *  - **[sarcopeniaTrajectoryScreen]** (adult / geriatric) — lean body mass
 *    declining + activity declining sustained over ≥6 months. EWGSOP2
 *    anchor (Cruz-Jentoft et al. 2019). Grip strength is the third
 *    canonical EWGSOP2 axis but is not in the Bios substrate today; the
 *    pattern uses LBM + activity as the available proxies.
 *  - **[cachexiaScreen]** (oncology / chronic disease) — sustained weight
 *    loss (>5 % in 6 months, or >2 % with sarcopenia / BMI <20) + activity
 *    drop + ESAS-r appetite / wellbeing decline if available. Fearon 2011
 *    framework. Pattern severity ADVISORY.
 *
 * **Trajectory framing.** Standard [SignalRule] fires on baseline-relative
 * z-score deviations of streaming wearable signals; that shape doesn't
 * carry the *percentile-band crossing over months* semantics these
 * patterns need. The pattern definitions below carry the wearable
 * corroborators (activity decline, RHR drift) on standard SignalRules; the
 * primary trajectory criterion (weight-for-age percentile band drop /
 * LBM trajectory / weight loss percentage) lives in the documentation +
 * the dedicated trajectory evaluator that the
 * [com.bios.app.data.GrowthMeasurementRepo] feeds. The
 * [com.bios.app.engine.AnomalyDetector] evaluates the SignalRule
 * corroborators today; a `TrajectoryEvaluator` that reads
 * [com.bios.app.data.dao.GrowthMeasurementDao] and writes anomalies for
 * these patterns is the natural follow-up that closes the loop without
 * widening the [SignalRule] surface. v1 ships the pattern definitions +
 * trajectory evaluator helpers so the alerts can be wired by a thin
 * follow-up worker.
 *
 * All text obeys [AlertContentPolicy] — data statement + professional
 * referral; no diagnosis ("your child has failure to thrive"); no second-
 * person lifestyle judgments.
 *
 * Severity is ADVISORY across all three — these are surveillance signals,
 * not emergencies. The downstream clinical evaluation is what determines
 * the next step.
 */
object GrowthAndCompositionPatterns {

    val all by lazy {
        listOf(
            failureToThriveScreen,
            sarcopeniaTrajectoryScreen,
            cachexiaScreen,
        )
    }

    /**
     * Paediatric failure-to-thrive surveillance. Fires when weight-for-age
     * percentile falls ≥2 percentile bands across consecutive measurements
     * over ≥3 months — the standard primary-paediatric-care criterion (AAP,
     * NICE NG201).
     *
     * Gated on [PhysiologyState.PAEDIATRIC] via [excludedStates] — applies
     * the pattern's signal rules only for paediatric owners. Adult owners
     * read the sarcopenia / cachexia screens instead.
     *
     * The wearable corroborator here is a paediatric activity drop — both
     * a symptom and a consequence of poor growth. The primary criterion
     * (the percentile-band crossing itself) is consumed from the
     * [com.bios.app.data.dao.GrowthMeasurementDao] trajectory series; the
     * follow-up evaluator turns it into an anomaly.
     */
    val failureToThriveScreen = ConditionPattern(
        id = "failure_to_thrive_screen",
        title = "Weight-for-age percentile is dropping",
        category = ConditionCategory.METABOLIC,
        signalRules = listOf(
            SignalRule(
                metricType = MetricType.STEPS,
                direction = DeviationDirection.BELOW,
                thresholdSigma = 1.0,
                minDurationHours = 24 * 30 * 3,
                weight = 0.8,
                source = ThresholdSource.LITERATURE,
                citation = "AAP / NICE NG201 — sustained activity decline in paediatric owners alongside falling percentile bands corroborates failure-to-thrive surveillance",
            ),
        ),
        minActiveSignals = 1,
        explanation = "Across consecutive growth measurements over the past three months, weight-for-age has crossed two or more percentile bands downward on the WHO growth reference. This is the standard primary-paediatric-care threshold for failure-to-thrive surveillance — a screening signal, not a diagnosis. Catch-up growth after illness, transition to solid foods, or measurement variability between visits can all produce the same trajectory; the clinical evaluation reads the full picture.",
        suggestedAction = "Discuss the growth trajectory with the paediatrician at the next routine visit. The growth-chart percentile plot and individual measurements can be exported and shared. Standard follow-up includes a feeding history, a developmental check, and a focused exam to differentiate normal variability from a pattern that warrants further work-up.",
        references = listOf(
            "WHO Multicentre Growth Reference Study (2006) — WHO child growth standards",
            "Kuczmarski RJ et al. (2002) — CDC growth charts: United States",
            "NICE NG201 (2017) — Faltering growth: recognition and management of faltering growth in children",
            "Cole TJ, Green PJ (1992) — Smoothing reference centile curves: the LMS method",
        ),
        earlyDetection = "Paediatric growth percentiles are the most sensitive single screening axis for caloric, malabsorptive, endocrine, and developmental problems in early childhood. A two-band downward crossing — for example moving from the 50th to the 10th percentile — over three months is the standard surveillance threshold; below the third percentile is a separate, distinct criterion. The growth-chart plot in Bios uses the WHO 0–5y reference for 0–60 months; the curve also surfaces the median (50th) and the 3rd and 97th bands to make trajectory crossings visible. Catch this trajectory early and the paediatrician has the broadest range of work-up and intervention options.",
        prevention = "Routine paediatric growth measurement at the recommended visit schedule is the established preventive surveillance. Breastfeeding through the first year, age-appropriate complementary foods from six months, and consistent meal patterns support normal growth. Acute illness produces transient weight loss that typically recovers within weeks; persistent failure to recover is what the percentile-band screen detects. Recording growth measurements in Bios at every visit creates a longitudinal trajectory that single-visit snapshots miss.",
        healing = "When the screen fires, the standard paediatric work-up reads the full clinical picture: feeding history, dietary intake, gastrointestinal symptoms, developmental milestones, family growth pattern, and any acute or chronic illness. Targeted blood work (CBC, electrolytes, thyroid function, coeliac screen, urinalysis) is common. Most paediatric failure-to-thrive cases resolve with nutritional adjustments — increased caloric density, addressing feeding aversion, treating an identified GI or endocrine problem. A small fraction reflect organic disease that warrants specialty referral. The growth trajectory is the surveillance signal; the paediatrician is the next step.",
        risks = "Untreated paediatric failure-to-thrive carries cumulative consequences — delayed cognitive development, weakened immune defences, longer-term stunting. The first two years of life are when caloric / micronutrient adequacy has the largest neurodevelopmental impact. Most cases are reversible when caught early; the value of the percentile-band screen is precisely that it surfaces the trajectory before the deficit becomes large. Late detection narrows the window for full catch-up growth.",
        excludedStates = PhysiologyState.entries.toSet() - setOf(PhysiologyState.PAEDIATRIC),
    )

    /**
     * Adult / geriatric sarcopenia trajectory screen — EWGSOP2 (Cruz-Jentoft
     * 2019) framework. Fires on sustained lean-body-mass decline alongside
     * an activity drop over a 6-month window. Grip strength is the third
     * canonical EWGSOP2 axis but is not in the Bios substrate today; LBM
     * + activity are the two axes the wearable substrate carries.
     *
     * Excluded for paediatric owners — growing children gain LBM by
     * definition. The pattern reads as an adult-and-up screen.
     */
    val sarcopeniaTrajectoryScreen = ConditionPattern(
        id = "sarcopenia_trajectory_screen",
        title = "Lean body mass and activity are declining together",
        category = ConditionCategory.RECOVERY,
        signalRules = listOf(
            SignalRule(
                metricType = MetricType.LEAN_BODY_MASS_KG,
                direction = DeviationDirection.BELOW,
                thresholdSigma = 1.0,
                minDurationHours = 24 * 30 * 6,
                weight = 1.5,
                source = ThresholdSource.LITERATURE,
                citation = "Cruz-Jentoft AJ et al. (2019) EWGSOP2 — low muscle mass + low muscle strength is the operational definition of sarcopenia",
                required = true,
            ),
            SignalRule(
                metricType = MetricType.ACTIVE_MINUTES,
                direction = DeviationDirection.BELOW,
                thresholdSigma = 1.5,
                minDurationHours = 24 * 30 * 3,
                weight = 1.0,
                source = ThresholdSource.LITERATURE,
                citation = "Cruz-Jentoft 2019 EWGSOP2 — physical-performance decline (SPPB, gait speed) is the third EWGSOP2 axis; sustained activity decline is the wearable analogue",
            ),
            SignalRule(
                metricType = MetricType.STEPS,
                direction = DeviationDirection.BELOW,
                thresholdSigma = 1.0,
                minDurationHours = 24 * 30 * 3,
                weight = 0.8,
                source = ThresholdSource.LITERATURE,
                citation = "Studenski SA et al. (2011) — gait-speed decline tracks sarcopenia onset; daily-step decline is its wearable proxy",
            ),
        ),
        minActiveSignals = 2,
        explanation = "Across the past six months, lean body mass on file has decreased and activity has dropped over a sustained window. This pairing is the operational EWGSOP2 sarcopenia surveillance signature (Cruz-Jentoft 2019). Grip strength — the third EWGSOP2 axis — is not in the Bios substrate; clinical hand-dynamometry is the standard next step. Many things produce the same trajectory: recovery from a major illness or hospitalisation, deliberate weight loss, a course of corticosteroids, low-grade inflammation, thyroid changes.",
        suggestedAction = "Discuss the lean-mass and activity trends with a healthcare provider. The standard work-up adds clinical strength testing (hand-grip dynamometry), gait speed, and the SPPB battery; relevant labs include vitamin D, thyroid function, CBC, and a basic metabolic panel. Resistance training plus adequate protein intake is the established intervention when sarcopenia is confirmed.",
        references = listOf(
            "Cruz-Jentoft AJ et al. (2019) — Sarcopenia: revised European consensus on definition and diagnosis (EWGSOP2). Age and Ageing 48:16–31",
            "Studenski SA et al. (2011) — Gait speed and survival in older adults. JAMA 305:50–58",
            "Beaudart C et al. (2017) — Sarcopenia: review of evidence on prevention and treatment",
        ),
        earlyDetection = "Sarcopenia develops over months to years; the early phase is silent until a fall, a hospitalisation, or a noticed loss of independence makes it visible. The wearable-detectable early signal is the lean-mass trajectory: month-over-month decline against the owner's own history. Activity decline often accompanies it — both as cause and consequence of muscle loss. The EWGSOP2 framework formalised the muscle-mass + strength + performance triad; the pair of axes available on Bios (LBM + activity) is sensitive enough to surface the trajectory while leaving the diagnostic confirmation to clinical strength testing.",
        prevention = "Resistance training is the single most evidence-supported preventive intervention — twice-weekly major-muscle-group resistance work preserves muscle mass and strength across the lifespan. Adequate protein intake (1.0–1.2 g/kg/day for adults, 1.2–1.5 g/kg/day for older adults) is the nutritional anchor. Vitamin D sufficiency supports muscle function. Avoid prolonged immobilisation; the loss rate during bed rest exceeds the regain rate by a wide margin.",
        healing = "Confirmed sarcopenia responds to structured resistance training plus adequate protein — measurable strength gains within weeks, LBM gains over months. Aerobic training adds cardiovascular benefit but does not by itself reverse muscle loss. Address contributing factors: vitamin D deficiency, low protein intake, untreated thyroid or inflammatory disease, and any medications associated with muscle loss (long-term corticosteroids, some statins in a small fraction of owners). A physiotherapist or supervised exercise programme is often the most effective entry point.",
        risks = "Sarcopenia is the strongest single physiological driver of frailty, fall risk, and loss of independence in older adults. Each percentage drop in lean mass increases fall and fracture risk, prolongs recovery from any acute illness, and reduces metabolic reserve. The trajectory is reversible when caught early — months of consistent resistance training restore measurable strength — but the longer the decline accumulates, the harder full recovery becomes.",
        excludedStates = setOf(PhysiologyState.PAEDIATRIC),
    )

    /**
     * Cachexia screen — Fearon 2011 framework. Fires on sustained weight
     * loss (>5 % in 6 months, OR >2 % with sarcopenia / BMI <20) +
     * activity drop. ESAS-r appetite / wellbeing decline (PR #216) is a
     * future corroborator when that surface lands; the pattern definition
     * here uses the available substrate.
     */
    val cachexiaScreen = ConditionPattern(
        id = "cachexia_screen",
        title = "Sustained weight loss alongside activity decline",
        category = ConditionCategory.METABOLIC,
        signalRules = listOf(
            SignalRule(
                metricType = MetricType.BODY_MASS,
                direction = DeviationDirection.BELOW,
                thresholdSigma = 1.0,
                minDurationHours = 24 * 30 * 6,
                weight = 1.5,
                source = ThresholdSource.LITERATURE,
                citation = "Fearon K et al. (2011) Lancet Oncology — cachexia is sustained weight loss >5 % in 6 months, OR >2 % with sarcopenia or BMI <20",
                required = true,
            ),
            SignalRule(
                metricType = MetricType.ACTIVE_MINUTES,
                direction = DeviationDirection.BELOW,
                thresholdSigma = 1.5,
                minDurationHours = 24 * 30 * 3,
                weight = 1.0,
                source = ThresholdSource.LITERATURE,
                citation = "Fearon 2011 — performance status decline accompanies cachexia and tracks its severity",
            ),
            SignalRule(
                metricType = MetricType.RESTING_HEART_RATE,
                direction = DeviationDirection.ABOVE,
                thresholdSigma = 1.0,
                minDurationHours = 24 * 30 * 3,
                weight = 0.6,
                source = ThresholdSource.LITERATURE,
                citation = "Fearon 2011 — resting tachycardia reflects the metabolic-inflammatory state characteristic of cachexia",
            ),
        ),
        minActiveSignals = 2,
        explanation = "Body weight has decreased over a sustained 6-month window alongside an activity drop. This pairing matches the Fearon 2011 cachexia surveillance signature: sustained weight loss greater than 5 % over six months, or greater than 2 % paired with sarcopenia or BMI below 20. Cachexia is the metabolic-inflammatory wasting syndrome seen most often in cancer, advanced heart failure, advanced kidney disease, and severe chronic obstructive pulmonary disease — it differs from simple caloric undernutrition and warrants clinical evaluation.",
        suggestedAction = "Discuss the weight and activity trajectory with a healthcare provider. The work-up reads the full picture: a focused symptom review, the underlying chronic-disease state, appetite and gastrointestinal symptoms, and relevant labs (CBC, CRP, albumin, basic metabolic panel). Established cachexia management combines targeted nutritional support, when feasible structured exercise, and treatment of the underlying driver — early identification widens the intervention window.",
        references = listOf(
            "Fearon K et al. (2011) — Definition and classification of cancer cachexia: an international consensus. Lancet Oncology 12:489–495",
            "Argilés JM et al. (2014) — Cancer cachexia: understanding the molecular basis. Nature Reviews Cancer 14:754–762",
            "Anker SD et al. (1997) — Wasting as independent risk factor for mortality in chronic heart failure. Lancet 349:1050–1053",
        ),
        earlyDetection = "Cachexia differs from intentional weight loss and from simple caloric undernutrition: it carries a metabolic-inflammatory signature — elevated resting heart rate, sustained low-grade inflammation, muscle catabolism that exceeds what caloric intake alone can explain. The Fearon 2011 framework formalised the surveillance criteria around weight loss percentage, sarcopenia, and BMI threshold. The wearable-detectable signature is the convergence of sustained weight decline, activity drop, and a small upward drift in resting heart rate — each non-specific alone, the combination characteristic. Recording weight measurements in Bios across the months of an ongoing illness creates the trajectory that snapshot weights miss.",
        prevention = "Prevention in the established-risk population (cancer, advanced heart failure, advanced kidney disease, severe COPD) centres on routine nutritional screening, early dietetic involvement, and preserving physical activity as long as feasible. Protein intake of at least 1.2 g/kg/day is the nutritional anchor when tolerated. Anti-inflammatory dietary patterns and treating the underlying disease driver remain the most evidence-supported preventive levers.",
        healing = "Confirmed cachexia is multidisciplinary: targeted nutritional support (protein-enriched intake, where indicated branched-chain amino acids or omega-3 supplementation), structured exercise within tolerance (resistance training preserves what nutrition alone cannot), pharmacological options where appropriate (appetite stimulants, anti-inflammatory agents, where evidence supports them), and treatment of the underlying disease. Reversibility depends heavily on the underlying driver — cancer cachexia often partially reverses with effective oncologic treatment; cachexia in advanced organ failure is harder to reverse but quality-of-life interventions remain valuable.",
        risks = "Cachexia is an independent predictor of mortality across cancer, heart failure, kidney disease, and COPD — the wasting itself, separate from the underlying disease, shortens survival. Beyond mortality, it carries fatigue, weakness, increased complication rates from surgery and chemotherapy, and loss of quality of life. The Fearon framework defined cachexia precisely so screening could identify it during the reversible or partially reversible phase rather than at end stage; the trajectory primitive Bios surfaces is shaped for that screening role.",
        excludedStates = setOf(PhysiologyState.PAEDIATRIC),
    )
}
