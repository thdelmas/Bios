package com.bios.app.alerts

import com.bios.app.model.ConditionCategory
import com.bios.app.physiology.PhysiologyState
import com.bios.contracts.MetricType

/**
 * Heart-failure decompensation prodrome pattern — closes CARDIOLOGY_POV.md
 * §2.4 and converges with the Geriatrics/Palliative and Emergency Medicine
 * audits. HeartLogic-lite over the wearable-derivable subset of the FDA-
 * cleared Boston Scientific HeartLogic algorithm (Boehmer 2017 MultiSENSE;
 * Gardner 2018), which predicts HF decompensation 7–14 days ahead in CRT-D
 * patients with ~70% sensitivity at one false alert per patient-year.
 *
 * **Substrate check** (issue #187 verification): HeartLogic composes
 * resting HR + nocturnal respiratory rate + activity + thoracic impedance
 * + heart sounds (S1/S3 intensity). Bios has every input *except*
 * thoracic impedance, which is implantable-only — see
 * [MetricType.RESTING_HEART_RATE], [MetricType.RESPIRATORY_RATE],
 * [MetricType.ACTIVE_MINUTES] / [MetricType.STEPS], and
 * [MetricType.BODY_MASS] (Withings adapter). The wearable-derivable
 * subset is what Cleveland Clinic / Cedars-Sinai / Mayo are actively
 * prototyping for non-CIED HF populations (Conraads 2011 OptiLink;
 * Hindricks 2014 IN-TIME).
 *
 * **Pattern composition** (4 signals, fires on ≥3 active):
 *
 *  1. Resting HR ↑ ≥1.0σ for 7 days — same primitive Mishra et al. 2020
 *     used to detect COVID-19 prodrome, applied to the HF population.
 *  2. Respiratory rate ↑ ≥1.0σ for 5 days — Boehmer 2017's nocturnal-RR
 *     input. Bios doesn't yet separate nocturnal from full-day RR, so the
 *     rule reads the generic [MetricType.RESPIRATORY_RATE] baseline; when
 *     readings are concentrated in sleep windows (typical for wrist PPG
 *     adapters), this approximates the nocturnal signal.
 *  3. Activity ↓ ≥1.0σ for 7 days — declining active-minutes is the
 *     functional-status signal HeartLogic uses.
 *  4. Body weight ↑ — the AHA/HFSA 2017 HF self-care guideline threshold
 *     is **+1.5 kg in 3 days OR +2.3 kg in 7 days**. Bios's pattern
 *     engine evaluates baseline-relative z-scores over a window (no
 *     native delta primitive yet), so this is approximated by a +1.5σ
 *     rise in [MetricType.BODY_MASS] over 72 h against the rolling
 *     baseline — for a typical adult with day-to-day BW SD of ~0.5–1.0
 *     kg this lights up at roughly the AHA/HFSA threshold. A native
 *     delta-style signal-rule shape is a follow-up (#TBD).
 *
 * **Gating** — `requiredStates = setOf(KNOWN_HF)`. The pattern only fires
 * for owners who have affirmatively set [PhysiologyState.KNOWN_HF] in
 * Settings → Physiology state. Firing the HF prodrome on a 32-year-old
 * with a viral illness would light up every signal and be clinically
 * irrelevant noise (CARDIOLOGY_POV.md §2.4 + §3.2). Manifesto guard:
 * Bios never *infers* HF.
 *
 * **Severity** — ADVISORY by default, classifier-driven (no `severityFloor`).
 * The cardiology audit explicitly frames HF decompensation as advisory,
 * not URGENT — 7 days isn't a minute-scale emergency, and the pattern is
 * pull-side useful even without push. See CARDIOLOGY_POV.md §3.2: "the
 * cardiologist gets the early-warning signal they want; the manifesto
 * gets the framing it requires."
 *
 * **Content policy** — all text obeys [AlertContentPolicy]: data
 * statements + professional referral, no second-person judgments, no
 * urgency amplification, no lifestyle prescriptions. See suggestedAction —
 * "Discuss these readings with your HF clinician" rather than directive
 * phrasing.
 */
object HeartFailureDecompensationPattern {

    val all by lazy { listOf(decompensationProdrome) }

    val decompensationProdrome = ConditionPattern(
        id = "hf_decompensation_prodrome",
        title = "Heart-failure decompensation prodrome signals",
        category = ConditionCategory.CARDIOVASCULAR,
        signalRules = listOf(
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.0, 168, 1.0,
                ThresholdSource.LITERATURE,
                "Conraads et al. 2011 (OptiLink HF) + Boehmer 2017 (MultiSENSE / HeartLogic) — sustained resting HR ≥1σ above baseline across a 7-day window is a HF decompensation prodrome signal; same primitive as Mishra 2020's COVID-19 prodrome shape applied to the HF population",
            ),
            SignalRule(
                MetricType.RESPIRATORY_RATE, DeviationDirection.ABOVE, 1.0, 120, 1.0,
                ThresholdSource.LITERATURE,
                "Boehmer et al. 2017 (MultiSENSE / HeartLogic) — sustained nocturnal respiratory-rate elevation across 5 days is one of the highest-weight HeartLogic inputs (pulmonary congestion precursor)",
            ),
            SignalRule(
                MetricType.ACTIVE_MINUTES, DeviationDirection.BELOW, 1.0, 168, 1.0,
                ThresholdSource.LITERATURE,
                "Boehmer et al. 2017 (MultiSENSE / HeartLogic) + Hindricks 2014 (IN-TIME) — sustained activity decline over 7 days corroborates functional-status worsening preceding decompensation",
            ),
            SignalRule(
                MetricType.BODY_MASS, DeviationDirection.ABOVE, 1.5, 72, 1.2,
                ThresholdSource.LITERATURE,
                "AHA/HFSA 2017 HF self-care guideline — body weight rise of ≥1.5 kg in 3 days (or ≥2.3 kg in 7 days) is the canonical HF fluid-retention trigger; approximated by a ≥1.5σ rise over 72h against the rolling baseline until a native delta-style signal-rule shape lands",
            ),
        ),
        minActiveSignals = 3,
        // No severityFloor — classifier output drives severity (ADVISORY when
        // 3+ of 4 signals fire). Cardiology audit §3.2 + Emergency Medicine
        // audit both explicitly frame HF decompensation as ADVISORY, not
        // URGENT: the prodrome is a 7–14 day window, not a minute-scale
        // emergency, and pull-side framing matches the manifesto.
        requiredStates = setOf(PhysiologyState.KNOWN_HF),
        explanation = "Several wearable signals associated with heart-failure decompensation are trending together. Resting heart rate, respiratory rate, and/or body weight have risen above your personal baseline while activity has declined, sustained across several days. In the published HeartLogic literature, this combination has been observed 7–14 days before decompensation events in known-HF populations. This is a data observation gated on your having set the Known heart failure context — it is not a diagnosis, and the pattern would not have fired without that context.",
        suggestedAction = "Discuss these readings with your HF clinician. The resting HR trend, respiratory rate trend, body weight trend, and activity trend from Bios are useful to share alongside any symptoms you have noticed (worsening dyspnoea on exertion, orthopnoea, paroxysmal nocturnal dyspnoea, ankle/leg swelling, fatigue). Self-care plans for HF often include a clinician-specified weight-gain action threshold; refer to the plan your HF team gave you.",
        references = listOf(
            "Boehmer JP et al. (2017) — A Multisensor Algorithm Predicts Heart Failure Events in Patients With Implanted Devices: Results from the MultiSENSE Study (JACC Heart Failure) — the FDA-cleared HeartLogic algorithm reference",
            "Gardner RS et al. (2018) — HeartLogic Multisensor Algorithm Identifies Patients During Periods of Significantly Increased Risk of Heart Failure Events (Circulation: Heart Failure)",
            "Conraads VM et al. (2011) — Sensitivity and positive predictive value of implantable intrathoracic impedance monitoring as a predictor of heart failure hospitalizations: the OptiLink HF rationale (European Heart Journal)",
            "Hindricks G et al. (2014) — Implant-based multiparameter telemonitoring of patients with heart failure (IN-TIME): a randomised controlled trial (The Lancet)",
            "Yancy CW et al. (2017) — 2017 ACC/AHA/HFSA Focused Update of the 2013 ACCF/AHA Guideline for the Management of Heart Failure — body weight self-monitoring threshold (+1.5 kg in 3 days / +2.3 kg in 7 days)",
            "Mishra T et al. (2020) — Pre-symptomatic detection of COVID-19 from smartwatch data (Nature Biomedical Engineering) — resting HR prodrome primitive applied here to the HF population",
        ),
        earlyDetection = "The HeartLogic literature establishes that several wearable-detectable signals shift days to weeks before a clinically apparent heart-failure decompensation event. Resting heart rate drifts upward as the heart compensates for falling stroke volume. Respiratory rate rises — especially during sleep — as pulmonary congestion increases. Daily activity declines as exertional dyspnoea and fatigue worsen. Body weight rises as fluid retention accumulates. Bios watches all four signals over a 5–7 day window and surfaces the pattern when at least three trend together. The pattern only runs for owners who have set the Known heart failure context, so the same physiology in someone without a HF diagnosis (e.g. a viral illness in a healthy adult) does not trigger this alert.",
        prevention = "Heart-failure decompensation risk is reduced by adherence to the medication regimen your HF clinician prescribes (diuretics, beta-blockers, ACE inhibitors / ARBs / ARNIs, MRAs, SGLT2 inhibitors as applicable), sodium restriction per the plan your HF team set (commonly 2–3 g/day), fluid-intake tracking if your clinician has set a limit, daily weight measurements at the same time of day, and prompt attention to early symptoms (worsening dyspnoea, orthopnoea, weight gain, swelling). Treating contributors — atrial fibrillation, sleep apnoea, ischaemia, valvular disease — reduces decompensation frequency. Cardiac rehab participation reduces hospitalisation and mortality (Anderson 2016 Cochrane).",
        healing = "If a decompensation prodrome pattern is firing and symptoms are also developing, contact your HF clinician promptly — the standard outpatient response is often a diuretic-dose adjustment per a pre-arranged action plan, sometimes a clinic visit, sometimes laboratory work (BNP/NT-proBNP, creatinine, electrolytes). For severe symptoms — resting dyspnoea, chest pain, syncope, oxygen saturation below your usual baseline, or any symptom your HF team has classified as red-flag — seek urgent care or contact emergency services. Most early decompensations caught in this window resolve with outpatient management.",
        risks = "Untreated heart-failure decompensation progresses to acute decompensated HF, often requiring hospitalisation. Recurrent hospitalisations are a strong predictor of HF mortality. Early detection of the prodrome — when the wearable signals are shifting but symptoms are still mild — is the window where outpatient diuretic adjustment most often prevents hospitalisation. The wearable signature is not specific to HF; the gating on the Known heart failure context is what makes the pattern clinically useful rather than noisy.",
    )
}
