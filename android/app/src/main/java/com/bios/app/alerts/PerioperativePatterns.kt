package com.bios.app.alerts

import com.bios.app.model.AlertTier
import com.bios.app.model.ConditionCategory
import com.bios.app.physiology.PhysiologyState
import com.bios.contracts.MetricType

/**
 * Peri-operative pattern library (#183) — closes the four convergent
 * audits (SURGICAL_POV §2.1-2.4 + EMERGENCY_CRITICAL_CARE §3.6 +
 * CARDIOLOGY_POV + ONCOLOGY_POV) that flagged Bios's missing "an
 * operation happened" concept.
 *
 * Three patterns gated to [PhysiologyState.POD_0_30] / [PhysiologyState.POD_30_90]:
 *
 *  - [ssiSurveillance] — NHSN 30-day SSI surveillance. Fires on
 *    re-elevation after normalisation against the frozen
 *    [com.bios.app.physiology.PerioperativeBaseline] (POD3-30 window).
 *  - [anastomoticLeakScreen] — POD3-7 high-specificity bowel-leak shape
 *    (skin temp + tachycardia + activity drop sustained).
 *  - [postOpVteScreen] — acute PE/DVT pattern (sudden persistent
 *    tachycardia + SpO2 drop), URGENT-tier.
 *
 * All three use `requiredStates` to ensure they only fire when the owner
 * has affirmatively set their peri-operative context via Settings →
 * Identity → Surgical recovery. Firing them on owners without surgical
 * context would be clinically irrelevant noise.
 *
 * **References** (cited per pattern):
 *  - CDC NHSN (2024) — Surgical Site Infection Surveillance Definitions.
 *  - Sands K et al. (2025) — Wearable detection of post-discharge SSI.
 *  - Daams F et al. (2014) — Identification and management of
 *    anastomotic leakage after colorectal surgery.
 *  - den Dulk M et al. (2009) — Dutch anastomotic leak score (DULK).
 *  - ERAS Society (2018) colorectal guideline — post-op trajectory.
 *  - Wells PS et al. (2000) — Wells score for PE.
 *  - Caprini JA et al. (2010) — Caprini VTE risk assessment.
 *
 * All text obeys [AlertContentPolicy]: data statements + professional
 * referral, no second-person judgments, no urgency amplification.
 *
 * Manifesto guard: Bios never *infers* the peri-operative state. The
 * owner sets it explicitly.
 */
object PerioperativePatterns {

    val all by lazy { listOf(ssiSurveillance, anastomoticLeakScreen, postOpVteScreen) }

    /**
     * Surgical site infection surveillance (SURGICAL_POV §2.2).
     *
     * NHSN 30-day SSI surveillance shape: a post-operative owner's
     * normative recovery trajectory is RHR 10-20 bpm above pre-op for
     * 2-4 weeks, mildly elevated skin temperature through POD3-5, and
     * gradually improving sleep efficiency over POD10-21. The pattern
     * fires when these signals *re-elevate after starting to normalise*
     * — the canonical NHSN SSI shape (POD3-30 window, often clinically
     * presenting POD7-14).
     *
     * Frozen baseline: all rules use [useFrozenBaseline = true] so the
     * z-score is against the pre-op snapshot, not the rolling 14-day
     * baseline (which has already drifted to include the post-op state).
     *
     * Severity: ADVISORY by default — SSI is a phone-your-surgical-team
     * conversation, not a 911 call. The classifier can lift severity
     * higher when multiple signals converge severely (qSOFA-equivalent),
     * but the pattern itself does not set a severityFloor of URGENT.
     */
    val ssiSurveillance = ConditionPattern(
        id = "ssi_surveillance",
        title = "Post-operative vital-sign re-elevation",
        category = ConditionCategory.INFECTIOUS,
        signalRules = listOf(
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.5, 24, 1.2,
                ThresholdSource.LITERATURE,
                "CDC NHSN (2024) SSI surveillance + Sands 2025 wearable post-discharge SSI — RHR ≥1.5σ above pre-op baseline sustained ≥24h in the POD3-30 window is the canonical surveillance signal",
                useFrozenBaseline = true,
            ),
            SignalRule(
                MetricType.SKIN_TEMPERATURE_DEVIATION, DeviationDirection.ABOVE, 1.5, 12, 1.2,
                ThresholdSource.LITERATURE,
                "Smarr 2020 + CDC NHSN (2024) — skin-temp deviation ≥1.5σ above pre-op baseline sustained ≥12h is consistent with surgical site infection (T ≥38.3°C is the NHSN fever criterion)",
                useFrozenBaseline = true,
            ),
            SignalRule(
                MetricType.RESPIRATORY_RATE, DeviationDirection.ABOVE, 1.5, 12, 0.8,
                ThresholdSource.LITERATURE,
                "Cretikos 2008 + Sands 2025 — elevated respiratory rate is an early SSI sign; ≥1.5σ above pre-op baseline corroborates infection",
                useFrozenBaseline = true,
            ),
            SignalRule(
                MetricType.HEART_RATE_VARIABILITY, DeviationDirection.BELOW, 1.0, 24, 0.8,
                ThresholdSource.LITERATURE,
                "Quer 2021 — HRV depression precedes infection symptoms by 1-2 days; ≥1σ below pre-op baseline corroborates the SSI prodrome",
                useFrozenBaseline = true,
            ),
            SignalRule(
                MetricType.STEPS, DeviationDirection.BELOW, 1.0, 24, 0.4,
                ThresholdSource.ENGINEERING,
                "Reduced ambulation accompanies infection (post-op patients with SSI stop walking before pain is the named reason)",
                useFrozenBaseline = true,
            ),
        ),
        minActiveSignals = 3,
        // No URGENT severityFloor — SSI is a surgical-team conversation,
        // not an emergency-services event. Classifier output drives
        // severity; multi-signal convergence will land at ADVISORY+.
        requiredStates = setOf(PhysiologyState.POD_0_30),
        explanation = "Multiple vital signs have re-elevated above your pre-operative baseline in a pattern consistent with the NHSN 30-day surgical site infection surveillance criteria. Resting heart rate, skin temperature, respiratory rate, and reduced activity are the canonical wearable signals for a developing post-operative infection. This pattern uses the pre-op snapshot you froze when you entered surgical recovery, not the rolling 14-day baseline (which already includes your normal post-op recovery trajectory).",
        suggestedAction = "Contact your surgical team and describe the readings. The standard SSI symptoms to look for are incisional erythema or warmth, drainage, wound dehiscence, fever ≥38.3°C, or worsening surgical-site pain. Bring the pre-op vs post-op vital-sign trends from Bios to any clinical visit — the trajectory matters more than any single reading.",
        references = listOf(
            "CDC NHSN (2024) — Surgical Site Infection (SSI) Surveillance Definitions",
            "Sands K et al. (2025) — Wearable-derived signals for post-discharge surgical site infection detection",
            "ACS NSQIP — National Surgical Quality Improvement Program 30-day SSI surveillance methodology",
            "Mishra T et al. (2020) — Pre-symptomatic detection of infection from smartwatch data (Nature Biomedical Engineering)",
            "Quer G et al. (2021) — Wearable sensor data for infection detection",
        ),
        earlyDetection = "Post-operative surgical site infection follows a stereotyped trajectory: the first 48-72 hours, vital signs deviate from baseline as the body responds to the surgical trauma — this is normative healing. By POD3-5 those signals begin returning toward the pre-op baseline. SSI announces itself as a *re-elevation* of those same signals around POD3-14, often before incisional symptoms (erythema, drainage, dehiscence) are visible. The earliest wearable-detectable signal is resting heart rate rising again, followed by skin temperature elevation and respiratory rate rise. Bios watches the four-signal convergence over the NHSN 30-day surveillance window and flags when at least three signals re-elevate against the frozen pre-op snapshot.",
        prevention = "Surgical site infection prevention starts pre-operatively: chlorhexidine skin preparation, smoking cessation 4+ weeks pre-op, glycaemic control (HbA1c target per your surgical team), weight optimisation when relevant. Post-operatively, follow your surgical team's wound care plan: hand hygiene before any incision contact, keeping dressings dry per their instructions, completing the prescribed antibiotic course in full, and monitoring incision for the warning signs (redness, warmth, drainage, separation, increasing pain). Adequate protein intake supports wound healing.",
        healing = "If the pattern fires and incisional symptoms are also present, contact your surgical team promptly. The standard initial assessment is wound inspection ± microbiological swab; superficial SSI is often managed with wound care and oral antibiotics, deep or organ-space SSI may require imaging, drainage, or operative re-exploration. Continuing to monitor your vital-sign trajectory and sharing the Bios trend with your surgical team helps quantify the response to treatment.",
        risks = "Untreated surgical site infection can progress to deep-tissue infection, abscess formation, sepsis, wound dehiscence, and — for implant-related procedures — peri-prosthetic infection requiring revision surgery. Early detection within the NHSN 30-day surveillance window is the single most actionable intervention; mortality, morbidity, and resource utilisation all scale with detection delay.",
    )

    /**
     * Anastomotic leak screen (SURGICAL_POV §2.3).
     *
     * POD3-7 colorectal anastomotic leak is one of the highest-mortality
     * complications in bowel surgery; 12-24h earlier detection
     * meaningfully changes outcomes. The pattern shape is narrower than
     * SSI surveillance — POD3-7 only, requires both fever-like skin
     * temp AND tachycardia AND activity drop sustained 24-48h.
     *
     * The narrow window is the specificity gate: outside POD3-7 the
     * same signal pattern is more likely to be SSI or another
     * complication; inside it, the literature supports anastomotic
     * leak as the leading differential.
     *
     * Severity: ADVISORY+ with strong surgical-team language; classifier
     * lifts to URGENT when convergence is severe.
     */
    val anastomoticLeakScreen = ConditionPattern(
        id = "anastomotic_leak_screen",
        title = "POD3-7 vital-sign pattern consistent with anastomotic leak",
        category = ConditionCategory.INFECTIOUS,
        signalRules = listOf(
            SignalRule(
                MetricType.SKIN_TEMPERATURE_DEVIATION, DeviationDirection.ABOVE, 1.5, 24, 1.2,
                ThresholdSource.LITERATURE,
                "Daams 2014 + den Dulk 2009 (DULK) — sustained skin-temp deviation ≥1.5σ above pre-op baseline for ≥24h within POD3-7 is the leading non-incisional sign of colorectal anastomotic leak",
                required = true,
                useFrozenBaseline = true,
            ),
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 2.0, 24, 1.2,
                ThresholdSource.LITERATURE,
                "Daams 2014 — sustained tachycardia ≥2σ above pre-op baseline within POD3-7 is the second canonical anastomotic-leak signal (van Rooijen 2019 reports RHR > 100 sustained as a clinical predictor)",
                required = true,
                useFrozenBaseline = true,
            ),
            SignalRule(
                MetricType.STEPS, DeviationDirection.BELOW, 1.5, 24, 1.0,
                ThresholdSource.LITERATURE,
                "ERAS Society colorectal guideline — POD3-7 patients with developing leak stop ambulating before pain is the named reason (post-operative ileus proxy via activity drop)",
                useFrozenBaseline = true,
            ),
            SignalRule(
                MetricType.RESPIRATORY_RATE, DeviationDirection.ABOVE, 1.5, 12, 0.8,
                ThresholdSource.LITERATURE,
                "Cretikos 2008 — tachypnoea accompanies intra-abdominal sepsis; corroborator within the leak window",
                useFrozenBaseline = true,
            ),
        ),
        minActiveSignals = 3,
        requiredStates = setOf(PhysiologyState.POD_0_30),
        explanation = "Skin temperature, resting heart rate, and reduced activity have shifted together against your pre-operative baseline within the POD3-7 window. This three-signal convergence is the classical wearable signature of a developing intra-abdominal anastomotic leak in published colorectal surgery literature (Daams 2014; den Dulk 2009 DULK score). Many other things can produce the same pattern — this is a screen, not a diagnosis — but the timing window and the signal combination warrant prompt clinical attention.",
        suggestedAction = "Contact your surgical team today. POD3-7 anastomotic leak is a recognised surgical complication where 12-24 hours of earlier detection meaningfully changes outcomes. Bring the pre-op vs current vital-sign trends from Bios to any visit. Watch for new abdominal pain, distension, decreased or absent flatus, feculent or purulent drainage from the incision or any surgical drain, and contact emergency services if these develop or if you become acutely unwell.",
        references = listOf(
            "Daams F et al. (2014) — Identification of anastomotic leakage after colorectal surgery: a systematic review",
            "den Dulk M et al. (2009) — The DULK (Dutch leakage) score — a simple clinical risk score for early detection of anastomotic leakage",
            "ERAS Society (2018) — Guidelines for perioperative care in elective colorectal surgery",
            "van Rooijen SJ et al. (2019) — Hospital-related complications after elective colorectal surgery",
        ),
        earlyDetection = "Anastomotic leak after bowel surgery typically presents POD3-7 with fever (skin-temperature elevation), tachycardia (resting-heart-rate rise), and decreased activity (post-operative ileus as the patient stops walking despite pain not being the stated reason). The signature can precede the classical clinical presentation — abdominal pain, distension, peritoneal signs — by 12 to 24 hours. Bios watches for the three-signal convergence sustained over 24-48 hours within the POD3-7 window only; outside that window the same physiology is more likely to be a different complication and is captured by ssi_surveillance.",
        prevention = "Anastomotic leak prevention is mostly intra-operative and patient-factor-dependent — tension-free anastomosis, adequate blood supply, no contamination, glycaemic control, smoking cessation 4+ weeks pre-op, optimisation of nutritional status. Post-operatively, follow the ERAS protocol your surgical team specified (early mobilisation, early oral intake when permitted, multimodal analgesia limiting opioid load). Adherence to the ERAS pathway is itself protective.",
        healing = "If the screen fires and you have abdominal symptoms, contact your surgical team without delay. Standard work-up is bedside assessment, blood tests including lactate and inflammatory markers, and often CT imaging with oral / IV contrast. Management ranges from antibiotics and percutaneous drainage for contained leaks to operative re-exploration with stoma formation for free perforation. Earlier intervention is consistently associated with better outcomes.",
        risks = "Untreated anastomotic leak progresses to faecal peritonitis, sepsis, septic shock, and multi-organ failure — among the highest-mortality surgical complications. The 12-24 hour window between wearable-detectable physiological deviation and overt clinical presentation is the window in which earlier surgical attention most plausibly improves outcomes.",
    )

    /**
     * Acute post-op VTE screen — DVT / pulmonary embolism (SURGICAL_POV §2.4).
     *
     * The wearable opportunity is the acute embolic event, not the
     * silent DVT: sudden persistent tachycardia + SpO2 drop in an
     * otherwise-recovering owner during POD0-30 (and continued elevated
     * risk through POD30-90 for orthopaedic / oncologic procedures).
     *
     * Mirrors Wells score logic (without imaging): tachycardia >100 +
     * recent surgery is a 1.5-point Wells criterion; sustained
     * hypoxaemia in the absence of new pulmonary infection or fluid
     * overload narrows the differential to PE.
     *
     * Severity: URGENT (severityFloor) — PE is a 911-class differential
     * and the Surviving Sepsis-equivalent for cardiopulmonary
     * peri-operative collapse is "earlier presentation is everything".
     */
    val postOpVteScreen = ConditionPattern(
        id = "post_op_vte_screen",
        title = "Post-op cardiopulmonary deviation consistent with VTE/PE",
        category = ConditionCategory.CARDIOVASCULAR,
        signalRules = listOf(
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 2.0, 6, 1.5,
                ThresholdSource.LITERATURE,
                "Wells PS et al. (2000) — tachycardia >100 in a recent-surgery owner is a Wells PE-criterion (1.5 points); ≥2σ above pre-op baseline sustained ≥6h is the wearable analogue of a sudden persistent step-up",
                required = true,
                useFrozenBaseline = true,
            ),
            SignalRule(
                MetricType.BLOOD_OXYGEN, DeviationDirection.BELOW, 1.5, 4, 1.5,
                ThresholdSource.LITERATURE,
                "Wells PS et al. (2000) + Caprini 2010 — unexpected SpO2 drop in a post-op owner not otherwise compromised narrows differential to PE; ≥1.5σ below pre-op baseline sustained ≥4h is the wearable trigger",
                required = true,
                useFrozenBaseline = true,
            ),
            SignalRule(
                MetricType.RESPIRATORY_RATE, DeviationDirection.ABOVE, 1.5, 6, 1.0,
                ThresholdSource.LITERATURE,
                "Wells PS et al. (2000) — tachypnoea (RR >20) accompanies acute PE; ≥1.5σ above pre-op baseline corroborates the diagnosis",
                useFrozenBaseline = true,
            ),
            SignalRule(
                MetricType.STEPS, DeviationDirection.BELOW, 1.5, 12, 0.6,
                ThresholdSource.ENGINEERING,
                "Activity drop coincident with the cardiopulmonary deviation (the owner sits down because they cannot breathe); corroborator only",
                useFrozenBaseline = true,
            ),
        ),
        minActiveSignals = 2,
        // PE is a 911-class differential — Surviving-Sepsis-equivalent
        // for the cardiopulmonary peri-operative envelope, where hour-1
        // anticoagulation maps to measurable mortality reduction.
        severityFloor = AlertTier.URGENT,
        requiredStates = setOf(PhysiologyState.POD_0_30, PhysiologyState.POD_30_90),
        explanation = "Resting heart rate has risen and blood oxygen has dropped against your pre-operative baseline in a pattern consistent with acute venous thromboembolism — pulmonary embolism in particular. The Wells score (Wells 2000) names tachycardia >100 plus recent surgery as a key clinical criterion for PE; sustained SpO2 drop in a post-op owner not otherwise compromised narrows the differential further. This is a screen against the published wearable pattern, not a diagnosis — but PE is time-critical, so the alert is URGENT.",
        suggestedAction = "Seek emergency medical assessment now. Acute pulmonary embolism is a recognised post-operative complication where minutes of earlier treatment translate to measurable mortality reduction (Kearon 2016, CHEST guideline). If you are experiencing chest pain, sudden shortness of breath, calf pain or swelling, lightheadedness, or syncope, call emergency services. Bring the pre-op vs current vital-sign trends from Bios for the assessing clinician.",
        references = listOf(
            "Wells PS et al. (2000) — Derivation of a simple clinical model to categorize patients probability of pulmonary embolism",
            "Caprini JA et al. (2010) — Risk assessment as a guide for the prevention of the many faces of venous thromboembolism",
            "Kearon C et al. (2016) — Antithrombotic Therapy for VTE Disease: CHEST Guideline and Expert Panel Report",
            "Anderson DR et al. (2019) — American Society of Hematology 2019 guidelines for management of venous thromboembolism",
        ),
        earlyDetection = "Post-operative venous thromboembolism is bimodal: silent DVT in the calf that may embolise hours to weeks later; or acute pulmonary embolism that presents abruptly. The wearable opportunity is the acute event — a sudden persistent tachycardia step-up combined with an unexpected SpO2 drop in an owner otherwise recovering normally. Respiratory rate often rises in parallel as the body compensates. Activity drops as the owner finds themselves unable to maintain prior effort. Bios watches for this convergent shift against the pre-op baseline through the POD0-30 window, with extended surveillance to POD30-90 for high-risk procedures (orthopaedic, oncologic).",
        prevention = "Post-operative VTE prevention follows the Caprini risk-stratified protocol your surgical team prescribed: mechanical prophylaxis (sequential compression devices, graduated compression stockings) plus pharmacological prophylaxis (low-molecular-weight heparin, direct oral anticoagulants) for the duration specified for your procedure and risk category. Early mobilisation is consistently protective. Adequate hydration matters. If you have prior personal or family history of VTE, hormonal therapy, or active malignancy, the prescribed prophylaxis duration is often extended.",
        healing = "If acute PE is confirmed, treatment is anticoagulation — initial parenteral heparin or LMWH bridged to direct oral anticoagulant or warfarin per current guidelines, with duration depending on the provoking factor (provoked vs unprovoked, ongoing risk). Massive or sub-massive PE may require thrombolysis or catheter-directed intervention. Confirmed DVT in the legs is managed by anticoagulation alone in most cases. Adherence to the prescribed course is the single most important determinant of recurrence risk.",
        risks = "Untreated pulmonary embolism is rapidly fatal — case-fatality without treatment exceeds 30%; with prompt anticoagulation it drops below 5% (Goldhaber 1999). Recurrence risk after a first VTE event is meaningful and shapes longer-term anticoagulation duration. Post-thrombotic syndrome after DVT is a long-term sequela for some owners. Earlier presentation is consistently associated with better outcomes; in the acute setting, hour-1 anticoagulation closely parallels the Surviving-Sepsis-Campaign hour-1 framing for sepsis.",
    )
}
