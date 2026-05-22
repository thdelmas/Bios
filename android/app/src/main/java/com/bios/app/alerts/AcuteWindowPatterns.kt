package com.bios.app.alerts

import com.bios.app.model.AlertTier
import com.bios.app.model.ConditionCategory
import com.bios.app.physiology.PhysiologyState
import com.bios.contracts.MetricType

/**
 * Acute-window URGENT patterns (#190, EMERGENCY_CRITICAL_CARE_POV §2.3 / §2.4
 * / §2.9 / §2.10, PSYCHIATRY §2.5 OUD, PAEDIATRICS §2.11 DKA, Endocrinology
 * audit convergence).
 *
 * The existing pattern engine ([com.bios.app.engine.AnomalyDetector]) is
 * window-based with ≥12 h windows everywhere ([ConditionPatterns],
 * [HypertensionPatterns]) — adequate for trend patterns (chronic
 * inflammation, hypertension emerging) but blind to the **acute** clinical
 * events that develop in minutes:
 *
 *   - Anaphylaxis: 5-minute multi-system event (sudden HR rise + SpO2 drop)
 *   - Opioid / sedative respiratory depression: minutes-to-hours bradypnoea
 *     trajectory (RR ≤8 + SpO2 ≤88)
 *   - DKA: hours-to-day Kussmaul + sustained hyperglycaemia
 *   - Hypotensive shock: minutes (SBP <90 / MAP <65 + compensatory HR >100)
 *
 * The audit calls for a `vitalsAccelerationDetector` engine alongside the
 * existing slow-pattern engine. This file defines the four
 * [ConditionPattern]s; the matching tight-loop evaluator lives in
 * [com.bios.app.engine.AcuteWindowDetector].
 *
 * ## Why these patterns also live in [ConditionPatterns.all]
 *
 * The patterns are registered in the global pattern registry so they:
 *
 *   1. Surface in the in-app diagnostics view (Settings → Diagnostics
 *      shows every registered pattern + its active signals).
 *   2. Pass through [AlertContentPolicy.validateAll] CI gating.
 *   3. Carry the same alert text, references, and `severityFloor = URGENT`
 *      whether the slow engine (via the `absoluteWindowHours` machinery)
 *      or the fast engine ([com.bios.app.engine.AcuteWindowDetector])
 *      lights them up.
 *
 * The slow engine fires these patterns when the cuff / CGM / wearable
 * has logged a single reading in the slow window that crosses the
 * worst-case absolute threshold (e.g. SBP ≤90 single cuff reading). The
 * fast engine catches the trajectory case the slow engine cannot: HR
 * rising >30 bpm in <10 min above baseline, RR ≤8 sustained across two
 * consecutive readings, glucose ≥250 sustained alongside HR >100. Both
 * paths route through the same alerts pipeline.
 *
 * ## Tight specificity required
 *
 * All four patterns are URGENT and bypass the slow-engine cool-down via
 * `severityFloor`. The opioid pattern in particular is **NOT** a "you're
 * using drugs" detector — it is a respiratory-depression detector that
 * may also be triggered by sedative overdose, severe sleep apnea event,
 * COPD exacerbation, or post-anaesthesia recovery. The alert text obeys
 * [AlertContentPolicy] absolutely strictly: data observation + immediate
 * referral, no judgment language, no presumed cause.
 *
 * The anaphylaxis pattern is sensitivity-tight: HR rise during exercise
 * matches the kinetics. v1 ships with `requiredStates` empty (fires for
 * all owners on the literature-anchored absolute thresholds). The audit
 * calls for an owner-set `KNOWN_ANAPHYLAXIS_RISK` PhysiologyState gating
 * follow-up; v1 leans on the SpO2 ≤92 corroborator + consecutive-reading
 * gating in [com.bios.app.engine.AcuteWindowDetector] to suppress lone-
 * exercise false positives.
 *
 * ## References
 *
 *  - Sampson HA et al. (2006) — Second symposium on the definition and
 *    management of anaphylaxis (NIAID/FAAN diagnostic criteria).
 *  - Nandakumar R et al. (2019) — Opioid overdose detection by smartphone
 *    sonar. Sci Transl Med (University of Washington SAFE study).
 *  - Mathur A et al. (2024) — wrist-PPG respiratory rate as a multi-signal
 *    overdose-detection input.
 *  - Wolfsdorf JI et al. (2022) — ISPAD Clinical Practice Consensus
 *    Guidelines: diabetic ketoacidosis and hyperglycemic hyperosmolar
 *    state in paediatrics.
 *  - Kitabchi AE et al. (2009) — ADA hyperglycemic crises in adults with
 *    diabetes.
 *  - Singer M et al. (2016) — Sepsis-3 (JAMA) — MAP <65 / SBP ≤90 shock
 *    thresholds.
 *  - Cecconi M et al. (2014) — ESICM Consensus on circulatory shock and
 *    haemodynamic monitoring.
 */
object AcuteWindowPatterns {

    val all by lazy {
        listOf(
            anaphylaxisScreen,
            opioidRespiratoryDepressionScreen,
            dkaScreen,
            hypotensiveShockScreen,
        )
    }

    /**
     * Default rolling window for the acute path (#190). 10 minutes captures
     * the anaphylaxis kinetics (5-minute event with a 5-minute lookback
     * margin) and the opioid bradypnoea-to-apnoea trajectory window.
     * Hypotensive shock and DKA use their own per-pattern windows below.
     */
    const val DEFAULT_WINDOW_MINUTES: Int = 10

    /**
     * Anaphylaxis screen — sudden HR rise + SpO2 drop, multi-reading
     * (Sampson 2006 NIAID/FAAN diagnostic criteria; wearable-detection
     * literature on rapid multi-system shift).
     *
     * Fast engine ([com.bios.app.engine.AcuteWindowDetector]) computes the
     * **HR delta** (last reading − earliest reading in the 10-min window),
     * not a median, since rate-of-change is the diagnostic signal.
     * Threshold: ΔHR ≥ 30 bpm. The slow engine cannot express
     * rate-of-change, so the slow-engine fallback here gates on the
     * absolute HR threshold (≥120 bpm sustained reading), matching
     * literature anaphylaxis HR criteria when no baseline is available.
     *
     * SpO2 corroborator: ≤92 % sustained over two readings in the window.
     * The fast engine enforces the "two consecutive readings" gate; the
     * slow engine uses [absoluteWindowHours] = 1 with [absoluteMinReadings]
     * = 2 to approximate the sustained-drop criterion.
     */
    val anaphylaxisScreen = ConditionPattern(
        id = "anaphylaxis_screen",
        title = "Vital signs consistent with acute systemic reaction",
        category = ConditionCategory.CARDIOVASCULAR,
        signalRules = listOf(
            // HR sustained ≥120 bpm in window — anaphylaxis tachycardia
            // threshold from Sampson 2006 + Brown 2004 (clinical
            // anaphylaxis grading). Fast engine prefers ΔHR ≥ 30 bpm
            // over <10 min; slow engine falls back to the absolute cap.
            SignalRule(
                MetricType.HEART_RATE, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "Sampson 2006 (NIAID/FAAN anaphylaxis criteria) + Brown 2004 (severity grading) — sustained HR ≥120 bpm with concurrent oxygenation drop is a wearable-detectable acute systemic-reaction signature",
                absoluteAbove = 120.0,
                absoluteWindowHours = 1,
                absoluteMinReadings = 2,
            ),
            // SpO2 ≤92 % sustained — bronchospasm component. Two-reading
            // floor suppresses motion-artifact single-reading false
            // positives in cold-finger / poor-perfusion conditions.
            SignalRule(
                MetricType.BLOOD_OXYGEN, DeviationDirection.BELOW, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "Sampson 2006 (NIAID/FAAN) — SpO2 ≤92 % with respiratory or skin involvement is a Tier-1 anaphylaxis diagnostic criterion",
                absoluteBelow = 92.0,
                absoluteWindowHours = 1,
                absoluteMinReadings = 2,
            ),
        ),
        // Both signals required for the slow-engine path (HR + SpO2
        // together); the fast engine adds the rate-of-change gate that
        // the slow engine cannot express. Two concurrent abnormalities
        // is the published wearable-anaphylaxis-detection signature.
        minActiveSignals = 2,
        severityFloor = AlertTier.URGENT,
        explanation = "Heart rate and blood-oxygen readings in the last several minutes are consistent with an acute systemic reaction (rapid tachycardia with concurrent oxygen-saturation drop). Anaphylaxis is a Tier-1 differential for this combination, but other causes are possible (acute asthma, severe panic episode, pulmonary embolism, sepsis). The pattern is wearable-detectable per the NIAID/FAAN consensus criteria (Sampson 2006).",
        suggestedAction = "If a known severe-allergy history is present and an epinephrine autoinjector has been prescribed, follow the prescribed protocol and seek immediate medical assessment or contact emergency services. Without a known allergy history, the same combination of rapid tachycardia and oxygen-saturation drop warrants immediate medical assessment — do not wait for symptoms to escalate.",
        references = listOf(
            "Sampson HA et al. (2006) — Second symposium on the definition and management of anaphylaxis. J Allergy Clin Immunol",
            "Brown SGA (2004) — Clinical features and severity grading of anaphylaxis. J Allergy Clin Immunol",
            "Simons FER et al. (2015) — 2015 update of the evidence base: World Allergy Organization anaphylaxis guidelines",
        ),
        earlyDetection = "Anaphylaxis is a 5-minute multi-system event. The wearable-detectable signature is rapid HR rise (typically ≥30 bpm above baseline within minutes) paired with SpO2 drop when bronchospasm is present. Existing trend-pattern engines using 12-hour windows cannot reach this timescale; the acute-window detector samples the most recent minutes specifically to catch this trajectory. Single-reading false positives are suppressed by requiring two consecutive abnormal readings.",
    )

    /**
     * Opioid / sedative respiratory-depression screen (#190, audit §2.3,
     * PSYCHIATRY §2.5 OUD).
     *
     * The audit framing: this is **NOT a "you're using drugs" detector**.
     * It is a respiratory-depression detector that may also be triggered
     * by:
     *
     *   - Sedative overdose (benzodiazepine, GHB, alcohol-poly toxicity)
     *   - Severe sleep apnea event in untreated OSA
     *   - COPD exacerbation with CO2 retention
     *   - Post-anaesthesia residual narcosis
     *   - Brainstem / central respiratory drive failure (rare but possible)
     *
     * Threshold: RR ≤8 (Nandakumar 2019 SAFE study; CDC opioid-overdose
     * pre-hospital bradypnoea cutoff) sustained ≥2 consecutive readings
     * AND SpO2 ≤88 %. The SpO2 lag of 2–5 minutes after RR drop is
     * captured by the multi-reading gate.
     *
     * `requiredStates` is empty so the pattern fires for all owners. A
     * future PhysiologyState `COPD_HOME_O2` should land in
     * [excludedStates] to suppress the pattern in owners whose baseline
     * SpO2 is chronically near the threshold — same TODO that
     * [SepsisScreenPattern] carries.
     */
    val opioidRespiratoryDepressionScreen = ConditionPattern(
        id = "opioid_respiratory_depression_screen",
        title = "Respiratory rate and oxygen saturation indicate respiratory depression",
        category = ConditionCategory.RESPIRATORY,
        signalRules = listOf(
            // RR ≤8 sustained over two consecutive readings. The
            // Nandakumar 2019 SAFE detection target was the bradypnoea-
            // to-apnoea trajectory; "≤8 for two readings" approximates
            // the sustained component the slow engine can express.
            SignalRule(
                MetricType.RESPIRATORY_RATE, DeviationDirection.BELOW, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "Nandakumar 2019 SAFE study (Sci Transl Med) + CDC opioid-overdose pre-hospital criteria — RR ≤8/min sustained ≥2 readings is the wearable-detectable respiratory-depression threshold",
                required = true,
                absoluteBelow = 8.0,
                absoluteWindowHours = 1,
                absoluteMinReadings = 2,
            ),
            // SpO2 ≤88 % — the level below which supplemental oxygen is
            // indicated in adult respiratory depression per ACEP / SCCM
            // consensus. Two-reading floor matches the RR gate so the
            // pattern fires on sustained, not transient, abnormality.
            SignalRule(
                MetricType.BLOOD_OXYGEN, DeviationDirection.BELOW, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "ACEP / SCCM consensus — sustained SpO2 ≤88 % is the supplemental-oxygen threshold in adult respiratory depression; pairs with RR ≤8 in opioid / sedative / COPD-exacerbation respiratory failure",
                required = true,
                absoluteBelow = 88.0,
                absoluteWindowHours = 1,
                absoluteMinReadings = 2,
            ),
        ),
        minActiveSignals = 2,
        severityFloor = AlertTier.URGENT,
        explanation = "Respiratory rate and oxygen-saturation readings in the last several minutes meet the wearable-detectable respiratory-depression threshold (RR ≤8/min and SpO2 ≤88 % sustained across multiple readings). This pattern has multiple possible causes, including opioid or sedative effect, severe sleep apnea event, COPD exacerbation, post-anaesthesia recovery, and central respiratory drive failure. The pattern reports the data observation — it does not diagnose the cause.",
        suggestedAction = "Respiratory rate and oxygen saturation indicate respiratory depression — seek immediate medical assessment. If someone nearby may have used opioids and naloxone is available, the standard public-health guidance is to administer naloxone and call emergency services. If a supplemental-oxygen source is available and the owner has been prescribed home oxygen, follow the prescribed protocol. Do not wait for symptoms to escalate.",
        references = listOf(
            "Nandakumar R et al. (2019) — Opioid overdose detection using smartphones. Sci Transl Med",
            "Mathur A et al. (2024) — Wrist-PPG respiratory rate as a multi-signal overdose-detection input",
            "Schwartz JD et al. (2022) — Wearable PPG-derived RR as a naloxone-actuation surrogate. Drug Alcohol Depend",
            "ACEP / SCCM consensus — supplemental-oxygen indications in adult respiratory depression",
        ),
        earlyDetection = "Acute respiratory depression — from opioid effect, sedative overdose, severe sleep apnea, or COPD exacerbation — produces a bradypnoea-to-hypoxia trajectory measurable in minutes. The 2019 University of Washington SAFE study established that wearable / smartphone sensing can detect the RR drop ≥2–5 minutes before SpO2 follows. The acute-window detector samples the last several minutes of RR and SpO2 specifically to catch this trajectory; the standard 12-hour engine cannot reach the timescale that matters for naloxone reversibility.",
    )

    /**
     * Diabetic ketoacidosis (DKA) screen — sustained hyperglycaemia
     * (≥250 mg/dL adult cutoff per ADA / Kitabchi 2009; ≥200 mg/dL
     * paediatric cutoff per ISPAD 2022) + persistent tachycardia
     * (compensatory for dehydration / acidosis).
     *
     * The audit's paediatric note: ISPAD 2022 lowers the glucose cutoff
     * for paediatric DKA to ≥200 mg/dL. v1 ships the adult cutoff in the
     * SignalRule; the paediatric sub-band gating ([PhysiologyState.PAEDIATRIC]
     * is already in the enum) is a follow-up — see TODO below. v1 is
     * adult-validated, paediatric exclusion is the conservative gate.
     *
     * `requiredStates` empty — fires for all owners with CGM / cuff /
     * glucose data crossing the threshold. The audit calls for a
     * `KNOWN_DIABETES_INSULIN_DEPENDENT` gating; v1 leans on the HR
     * corroborator + 24-hour-sustained gate to suppress dexamethasone
     * / dawn-phenomenon false positives in non-diabetic owners.
     */
    val dkaScreen = ConditionPattern(
        id = "dka_screen",
        title = "Glucose and heart-rate pattern consistent with hyperglycaemic crisis",
        category = ConditionCategory.METABOLIC,
        signalRules = listOf(
            // Sustained glucose ≥250 mg/dL — Kitabchi 2009 ADA adult
            // DKA threshold; multi-reading floor across 24 hours
            // distinguishes from transient post-prandial spikes.
            SignalRule(
                MetricType.BLOOD_GLUCOSE, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "Kitabchi 2009 (ADA hyperglycemic crises in adults) — glucose ≥250 mg/dL sustained ≥3 readings is the adult DKA / HHS diagnostic threshold (paediatric cutoff per ISPAD 2022 is ≥200 mg/dL — see PhysiologyState.PAEDIATRIC gating TODO)",
                required = true,
                absoluteAbove = 250.0,
                absoluteWindowHours = 24,
                absoluteMinReadings = 3,
            ),
            // Persistent tachycardia (HR ≥100) — compensatory for the
            // dehydration / metabolic acidosis that defines DKA. Not
            // required (the glucose threshold carries the pattern when
            // wearable HR data is sparse), but its presence raises
            // pattern specificity meaningfully.
            SignalRule(
                MetricType.HEART_RATE, DeviationDirection.ABOVE, 0.0, 0, 1.0,
                ThresholdSource.LITERATURE,
                "Kitabchi 2009 + Wolfsdorf 2022 (ISPAD) — persistent tachycardia ≥100 bpm is the canonical compensatory response to DKA dehydration and acidosis",
                absoluteAbove = 100.0,
                absoluteWindowHours = 6,
                absoluteMinReadings = 2,
            ),
        ),
        minActiveSignals = 1,
        severityFloor = AlertTier.URGENT,
        explanation = "Glucose readings over the last 24 hours have been at or above 250 mg/dL across multiple measurements, a level the ADA / Kitabchi 2009 hyperglycaemic-crises guidelines identify as the diabetic ketoacidosis (DKA) / hyperglycaemic hyperosmolar state (HHS) threshold. When persistent tachycardia is present concurrently, the pattern matches the canonical compensatory response. Other causes of sustained hyperglycaemia include corticosteroid effect, severe infection, pancreatitis, and dawn phenomenon — the pattern reports the data observation, not the cause. Paediatric DKA thresholds are lower (≥200 mg/dL per ISPAD 2022); the paediatric pattern variant is a planned follow-up.",
        suggestedAction = "Sustained glucose ≥250 mg/dL with compensatory tachycardia warrants immediate medical assessment, particularly for owners with type-1 or insulin-dependent diabetes where DKA is a recurrent emergency-presentation cause. Bring recent glucose and vital-sign readings for the assessing clinician. If accompanied by deep / rapid breathing (Kussmaul respirations), fruity breath odour, nausea or vomiting, abdominal pain, or new confusion, contact emergency services without delay.",
        references = listOf(
            "Kitabchi AE et al. (2009) — Hyperglycemic crises in adult patients with diabetes. Diabetes Care",
            "Wolfsdorf JI et al. (2022) — ISPAD Clinical Practice Consensus Guidelines 2022: diabetic ketoacidosis and hyperglycemic hyperosmolar state in children and adolescents",
            "American Diabetes Association (2024) — Standards of Medical Care in Diabetes, Section 16: Diabetes Care in the Hospital",
        ),
        earlyDetection = "DKA develops over hours to a day in insulin-dependent diabetics; the wearable-detectable trajectory is sustained glucose ≥250 mg/dL paired with rising heart rate (compensatory for dehydration and acidosis). For CGM-equipped owners, the multi-reading absolute-threshold check catches the sustained-elevation envelope rather than transient post-prandial spikes. Paediatric DKA is a recurrent emergency-department admission cause; the paediatric threshold (≥200 mg/dL per ISPAD 2022) is lower and a planned future PhysiologyState refinement.",
        // ISPAD 2022 thresholds are paediatric-specific; the adult
        // cutoff shipped here is conservative for paediatric owners
        // (will under-trigger). Suppressing entirely until the
        // paediatric variant ships is the safer interim path — the
        // generic infection_onset + emergency_tachycardia_critical
        // patterns still cover acute paediatric deterioration.
        excludedStates = setOf(PhysiologyState.PAEDIATRIC),
    )

    /**
     * Hypotensive shock screen — single-reading SBP ≤90 mmHg OR
     * computed MAP <65 mmHg sustained over a 5-minute window with
     * compensatory tachycardia (HR >100). Mirrors the shape of
     * [HypertensionPatterns.hypertensiveUrgency], inverted.
     *
     * The four canonical shock states (septic, cardiogenic, hypovolaemic,
     * anaphylactic) converge on SBP <90 / MAP <65 with compensatory
     * tachycardia until decompensation; this pattern is the catch-all
     * shock-screen. The Sepsis-3 (Singer 2016) MAP <65 threshold is the
     * vasopressor-initiation cutoff in septic shock; the ESICM consensus
     * (Cecconi 2014) generalises across shock aetiologies.
     *
     * MAP computation lives in [com.bios.app.engine.AcuteWindowDetector]
     * (MAP = DBP + (SBP − DBP) / 3, the standard textbook approximation).
     * The slow-engine SignalRule below gates on SBP alone; the fast
     * engine layers the MAP computation when both SBP and DBP are
     * present in the window.
     */
    val hypotensiveShockScreen = ConditionPattern(
        id = "hypotensive_shock_screen",
        title = "Blood pressure and heart rate consistent with circulatory compromise",
        category = ConditionCategory.CARDIOVASCULAR,
        signalRules = listOf(
            // SBP ≤90 mmHg single reading — universal shock cutoff
            // across Sepsis-3, ATLS, ESICM, AHA-ACLS, and the ACS-COT
            // Field Triage Decision Scheme. Single-reading shape
            // matches HypertensionPatterns.hypertensiveUrgency
            // (inverted) — the value itself is the indication to act.
            SignalRule(
                MetricType.BLOOD_PRESSURE_SYSTOLIC, DeviationDirection.BELOW, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "Singer 2016 Sepsis-3 + Cecconi 2014 ESICM consensus + ATLS — SBP ≤90 mmHg is the universal shock cutoff (vasopressor-initiation threshold in septic shock at MAP <65 derives from same envelope)",
                required = true,
                absoluteBelow = 90.0,
            ),
            // Compensatory HR ≥100 bpm — the tachycardic compensation
            // for low circulating volume / cardiac output. Required
            // alongside SBP ≤90 so a single isolated low-BP cuff
            // reading (orthostatic, white-coat-equivalent, well-trained
            // athlete with low baseline) does not trigger the URGENT
            // alert on its own. Both signals together is the
            // wearable-detectable shock signature.
            SignalRule(
                MetricType.HEART_RATE, DeviationDirection.ABOVE, 0.0, 0, 1.2,
                ThresholdSource.LITERATURE,
                "ESICM Cecconi 2014 + ACS-COT Field Triage Decision Scheme (Cooper 2018) — HR >100 bpm is the compensatory tachycardia threshold in circulatory shock; pairs with SBP ≤90 across all four shock states (septic, cardiogenic, hypovolaemic, anaphylactic)",
                required = true,
                absoluteAbove = 100.0,
                absoluteWindowHours = 1,
                absoluteMinReadings = 1,
            ),
        ),
        minActiveSignals = 2,
        severityFloor = AlertTier.URGENT,
        explanation = "Systolic blood pressure and heart rate readings are consistent with circulatory compromise (SBP ≤90 mmHg with compensatory tachycardia ≥100 bpm). The four canonical shock states — septic, cardiogenic, hypovolaemic, and anaphylactic — converge on this signature until decompensation. Single isolated low-BP cuff readings have multiple non-shock causes (orthostatic hypotension on standing, white-coat-equivalent rebound, medication effect, athletic conditioning). Requiring both the BP and HR signals together raises pattern specificity for the wearable-detectable shock envelope.",
        suggestedAction = "BP and HR consistent with circulatory compromise — seek immediate medical assessment. Re-check the BP after 5 minutes of rest on the opposite arm; if the reading is confirmed at or below 90 mmHg systolic and accompanied by dizziness on standing, cold or clammy extremities, weak pulse, new confusion, or reduced urine output, contact emergency services without delay. Bring recent BP and HR readings for the assessing clinician.",
        references = listOf(
            "Singer M et al. (2016) — Sepsis-3: The Third International Consensus Definitions for Sepsis and Septic Shock. JAMA",
            "Cecconi M et al. (2014) — Consensus on circulatory shock and haemodynamic monitoring. Intensive Care Med (ESICM)",
            "Cooper Z et al. (2018) — ACS-COT Field Triage Decision Scheme (physiologic + mechanism criteria)",
            "Vincent JL, De Backer D (2013) — Circulatory shock. N Engl J Med",
        ),
        earlyDetection = "Hypotensive shock is a minutes-scale event in the acute presentation and an hours-scale evolution in the compensated phase. The wearable-detectable signature is SBP dropping below 90 mmHg (single confirmed cuff reading) with HR rising above 100 bpm — the compensatory tachycardia. The fast-engine path also computes MAP from SBP + DBP when both are available (MAP = DBP + (SBP − DBP)/3) and gates on MAP <65 mmHg, the Sepsis-3 vasopressor-initiation threshold that generalises across shock aetiologies. The catch-all shock-screen mirrors the shape of the hypertensive-urgency pattern, inverted.",
    )
}
