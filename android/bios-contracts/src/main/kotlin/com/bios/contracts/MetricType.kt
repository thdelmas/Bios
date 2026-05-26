package com.bios.contracts

/**
 * Canonical metric keys exposed by the Bios ContentProvider.
 *
 * This is the single source of truth shared between Bios and companion apps
 * (W2F, Smokeless, Virgil, future). Keys here are stable; renames are breaking
 * changes for every consumer.
 *
 * Adding a key here does *not* automatically make it writable by companions —
 * see [com.bios.contracts.BiosHealthContract] for the companion-write surface
 * and Bios's `CompanionContract` for the per-package allowlist.
 *
 * **Forward/backward-compat rules** (see docs/CONSUMER_API.md →
 * "Contracts forward/backward compatibility"):
 *  - Never delete a `key` once it has shipped — annotate with `@Deprecated`
 *    instead so `fromKey()` keeps resolving it.
 *  - Never repurpose a `key` string or change the `unit`/`domain` of an
 *    existing entry — add a new entry.
 *  - Consumers must call `fromKey()` (not `valueOf`) and tolerate `null` for
 *    keys their contracts version doesn't know.
 *
 * [allowsManualEntry] gates the Bios-hosted manual-reading surface. The rule
 * is *"can the owner be the source?"* — reading a device (cuff, pulse-ox,
 * thermometer), being measured by a clinician, observing themselves. Derived
 * keys (HRV power, glucose CV, sleep efficiency) and pure-sensor-only signals
 * (raw accelerometer) stay false. Engine isolation still holds — manual
 * entries flow through SELF_REPORTED and never feed BaselineEngine /
 * AnomalyDetector. See docs/SELF_REPORTED_DATA_HOME.md decision 3.
 */
enum class MetricType(
    val key: String,
    val unit: MetricUnit,
    val domain: MetricDomain,
    val allowsManualEntry: Boolean = false,
) {
    // Cardiovascular
    HEART_RATE("heart_rate", MetricUnit.BPM, MetricDomain.CARDIOVASCULAR, allowsManualEntry = true),
    HEART_RATE_VARIABILITY("heart_rate_variability", MetricUnit.MILLISECONDS, MetricDomain.CARDIOVASCULAR),
    PARASYMPATHETIC_TONE("parasympathetic_tone", MetricUnit.SCORE, MetricDomain.CARDIOVASCULAR),
    STRESS_SCORE("stress_score", MetricUnit.SCORE, MetricDomain.CARDIOVASCULAR),
    LF_HF_RATIO("lf_hf_ratio", MetricUnit.SCORE, MetricDomain.CARDIOVASCULAR),
    HRV_LF_POWER("hrv_lf_power", MetricUnit.MS_SQUARED, MetricDomain.CARDIOVASCULAR),
    HRV_HF_POWER("hrv_hf_power", MetricUnit.MS_SQUARED, MetricDomain.CARDIOVASCULAR),
    VO2_MAX("vo2_max", MetricUnit.ML_PER_KG_MIN, MetricDomain.CARDIOVASCULAR),
    RESTING_HEART_RATE("resting_heart_rate", MetricUnit.BPM, MetricDomain.CARDIOVASCULAR, allowsManualEntry = true),
    BLOOD_PRESSURE_SYSTOLIC("blood_pressure_systolic", MetricUnit.MMHG, MetricDomain.CARDIOVASCULAR, allowsManualEntry = true),
    BLOOD_PRESSURE_DIASTOLIC("blood_pressure_diastolic", MetricUnit.MMHG, MetricDomain.CARDIOVASCULAR, allowsManualEntry = true),
    BLOOD_OXYGEN("blood_oxygen", MetricUnit.PERCENT, MetricDomain.CARDIOVASCULAR, allowsManualEntry = true),
    // PPG-derived AFib screening burden (#180, audit gap §2.1). Percentage of
    // recent valid PPG sessions whose RR-interval series the on-device rhythm
    // classifier scored as irregularly irregular (Poincaré SD1/SD2 + sample
    // entropy + turning-point ratio). Each PPG capture writes a per-session
    // verdict as 0 % (regular) or 100 % (irregularly irregular); the rolling
    // median over a 7-day window is what the AFib screen pattern reads. Apple
    // Heart Study (Perez 2019), mAFA-II (Guo 2019), and Fitbit Heart Study
    // (Lubitz 2022) all run an equivalent classifier over the IBI tachogram.
    // See engine/RhythmClassifier.kt. Manual entry stays false — the value is
    // derived from a PPG session, not something the owner can self-report.
    IRREGULAR_RHYTHM_BURDEN("irregular_rhythm_burden", MetricUnit.PERCENT, MetricDomain.CARDIOVASCULAR),

    // PPG pulse-wave morphology summaries (#181, CARDIOLOGY_POV §2.2 + TCM,
    // Sowa Rigpa, Kampo, Korean, Siddha, Unani, Ayurveda pulse-quality
    // audits). Statistical scalars only — raw waveforms are never persisted.
    // Computed by PpgSignalProcessor.computeWaveformFeatures from the
    // smoothed luminance waveform and accepted peak indices; surfaced for
    // arterial-stiffness reference views (Vlachopoulos 2010; Townsend 2015
    // AHA stiffness statement; ESC 2018) and traditional-medicine pulse-
    // quality readers. No new condition pattern fires off these today —
    // the data is held so pull-side views can read it.
    PPG_PEAK_AMPLITUDE_MEAN("ppg_peak_amplitude_mean", MetricUnit.SCORE, MetricDomain.CARDIOVASCULAR),
    PPG_PEAK_AMPLITUDE_COV("ppg_peak_amplitude_cov", MetricUnit.SCORE, MetricDomain.CARDIOVASCULAR),
    PPG_RISE_TIME_MEAN("ppg_rise_time_mean", MetricUnit.SECONDS, MetricDomain.CARDIOVASCULAR),
    PPG_RISE_TIME_COV("ppg_rise_time_cov", MetricUnit.SCORE, MetricDomain.CARDIOVASCULAR),
    PPG_DECAY_ASYMMETRY_INDEX("ppg_decay_asymmetry_index", MetricUnit.SCORE, MetricDomain.CARDIOVASCULAR),
    PPG_DICHROTIC_NOTCH_POSITION("ppg_dichrotic_notch_position", MetricUnit.SCORE, MetricDomain.CARDIOVASCULAR),

    // Respiratory
    RESPIRATORY_RATE("respiratory_rate", MetricUnit.BREATHS_PER_MIN, MetricDomain.RESPIRATORY, allowsManualEntry = true),
    // Administered supplemental O2 (cannula/mask). Contextualises SpO2:
    // 96 % on room air ≠ 96 % on 4 L/min. Single-point shape; ongoing-therapy
    // log-shape (start/end + rate over time) is deferred — see issue #107.
    OXYGEN_FLOW_RATE("oxygen_flow_rate", MetricUnit.LITERS_PER_MIN, MetricDomain.RESPIRATORY, allowsManualEntry = true),
    // Sleep apnea passthrough (#157, audit gap §2.8). SLEEP_APNEA_EVENT is a
    // discrete event written by vendor adapters that detect apnea episodes
    // (Apple Watch / Samsung Galaxy Watch ship FDA-cleared detection); only
    // timestamp + opaque event-id, no breathing waveform. AHI is the
    // apnea-hypopnea index, the canonical clinical measure (events/hour) —
    // typically vendor-derived nightly or owner-entered from a PSG/HSAT
    // report. AASM severity: <5 normal, 5–15 mild, 15–30 moderate, ≥30 severe.
    SLEEP_APNEA_EVENT("sleep_apnea_event", MetricUnit.EVENT, MetricDomain.RESPIRATORY),
    AHI("ahi", MetricUnit.COUNT, MetricDomain.RESPIRATORY, allowsManualEntry = true),

    // Temperature
    SKIN_TEMPERATURE("skin_temperature", MetricUnit.CELSIUS, MetricDomain.TEMPERATURE, allowsManualEntry = true),
    SKIN_TEMPERATURE_DEVIATION("skin_temperature_deviation", MetricUnit.DELTA_CELSIUS, MetricDomain.TEMPERATURE),

    // Neurological (manual-capture clinical signs).
    // PAIN_SCORE: 0–10 numeric (EVA / VAS / NRS interoperable). Universal
    // triage vital.
    // CONSCIOUSNESS_LEVEL: GCS canonical (3–15 total). GCS encodes AVPU but
    // not vice versa, so GCS covers neuro/ICU/post-trauma/surgery contexts
    // over a lifetime while AVPU-only is ER-triage-only. AVPU entry is a
    // lossless input shortcut (A→15, V→13, P→8, U→3); the original scale +
    // value are preserved in the event_payloads sidecar for provenance.
    PAIN_SCORE("pain_score", MetricUnit.SCORE, MetricDomain.NEUROLOGICAL, allowsManualEntry = true),
    CONSCIOUSNESS_LEVEL("consciousness_level", MetricUnit.SCORE, MetricDomain.NEUROLOGICAL, allowsManualEntry = true),

    // Sleep
    SLEEP_STAGE("sleep_stage", MetricUnit.CATEGORY, MetricDomain.SLEEP),
    SLEEP_DURATION("sleep_duration", MetricUnit.SECONDS, MetricDomain.SLEEP, allowsManualEntry = true),
    SLEEP_LATENCY("sleep_latency", MetricUnit.SECONDS, MetricDomain.SLEEP),
    SLEEP_EFFICIENCY("sleep_efficiency", MetricUnit.PERCENT, MetricDomain.SLEEP),
    SLEEP_FRAGMENTATION_INDEX("sleep_fragmentation_index", MetricUnit.COUNT, MetricDomain.SLEEP),
    WAKE_AFTER_SLEEP_ONSET("wake_after_sleep_onset", MetricUnit.SECONDS, MetricDomain.SLEEP),
    SLEEP_SCORE("sleep_score", MetricUnit.SCORE, MetricDomain.SLEEP),
    // Sleep researchers (Roenneberg, Walker, Phyllis Zee group) consistently
    // rank regularity above absolute duration for long-term outcomes. Sidecar
    // fields (bedtime/wake/midpoint variance + window_days) ride on
    // event_payloads so a future specialty companion can read components
    // rather than just the composite. See engine/SleepRegularityCalculator.
    SLEEP_REGULARITY("sleep_regularity", MetricUnit.SCORE, MetricDomain.SLEEP),
    // Bios-produced from sleep-onset times via cosinor/DLMO math. Universal
    // chronobiology metric — not mood-specific despite W2F being the primary
    // consumer. See docs/ECOSYSTEM_BOUNDARIES.md producer-by-capture-surface
    // rule. (Transition state: W2F currently still writes this until the
    // Bios-side engine ships.)
    CIRCADIAN_PHASE_SHIFT("circadian_phase_shift", MetricUnit.SCORE, MetricDomain.SLEEP),

    // Activity
    STEPS("steps", MetricUnit.COUNT, MetricDomain.ACTIVITY),
    ACTIVE_CALORIES("active_calories", MetricUnit.KCAL, MetricDomain.ACTIVITY),
    ACTIVE_MINUTES("active_minutes", MetricUnit.SECONDS, MetricDomain.ACTIVITY),
    // Composite event: discrete workout/session. value = 1.0 marker,
    // durationSec = session length. Structured fields (modality, start/end
    // utc, avg_hr_bpm, rpe) live in event_payloads keyed by reading id —
    // see docs/DATA_MODEL.md field-key vocabulary.
    EXERCISE_SESSION("exercise_session", MetricUnit.EVENT, MetricDomain.ACTIVITY),

    // Metabolic
    BLOOD_GLUCOSE("blood_glucose", MetricUnit.MG_PER_DL, MetricDomain.METABOLIC, allowsManualEntry = true),
    // CGM-derived variability keys (#28). Computed over the last 24h of
    // BLOOD_GLUCOSE readings by GlucoseVariability; one value per 24h window.
    GLUCOSE_CV("glucose_cv", MetricUnit.PERCENT, MetricDomain.METABOLIC),
    GLUCOSE_MAGE("glucose_mage", MetricUnit.MG_PER_DL, MetricDomain.METABOLIC),
    GLUCOSE_TIME_IN_RANGE("glucose_time_in_range", MetricUnit.PERCENT, MetricDomain.METABOLIC),
    GLUCOSE_PEAK_24H("glucose_peak_24h", MetricUnit.MG_PER_DL, MetricDomain.METABOLIC),
    BODY_MASS("body_mass", MetricUnit.KILOGRAMS, MetricDomain.METABOLIC, allowsManualEntry = true),
    BODY_FAT_PCT("body_fat_pct", MetricUnit.PERCENT, MetricDomain.METABOLIC, allowsManualEntry = true),
    LEAN_MASS("lean_mass", MetricUnit.KILOGRAMS, MetricDomain.METABOLIC, allowsManualEntry = true),
    BODY_WATER_PCT("body_water_pct", MetricUnit.PERCENT, MetricDomain.METABOLIC, allowsManualEntry = true),
    BONE_MASS("bone_mass", MetricUnit.KILOGRAMS, MetricDomain.METABOLIC, allowsManualEntry = true),

    // Recovery
    RECOVERY_SCORE("recovery_score", MetricUnit.SCORE, MetricDomain.RECOVERY),

    // Women's Health
    BASAL_BODY_TEMPERATURE("basal_body_temperature", MetricUnit.CELSIUS, MetricDomain.WOMENS_HEALTH, allowsManualEntry = true),
    CYCLE_PHASE("cycle_phase", MetricUnit.CATEGORY, MetricDomain.WOMENS_HEALTH),
    MENSTRUATION_ONSET("menstruation_onset", MetricUnit.EVENT, MetricDomain.WOMENS_HEALTH, allowsManualEntry = true),
    CYCLE_DAY("cycle_day", MetricUnit.COUNT, MetricDomain.WOMENS_HEALTH),

    // Environment (phone-sensor adapter)
    AMBIENT_LIGHT("ambient_light", MetricUnit.LUX, MetricDomain.ENVIRONMENT),
    AIR_PM25("air_pm25", MetricUnit.UG_PER_M3, MetricDomain.ENVIRONMENT),
    AIR_VOC("air_voc", MetricUnit.PPB, MetricDomain.ENVIRONMENT),
    AIR_CO2("air_co2", MetricUnit.PPM, MetricDomain.ENVIRONMENT),

    // Biomarkers (lab-drawn or imported via FHIR; slow-moving, no streaming).
    // First wave matches the clinical concepts already described in
    // alerts/BiomarkerReference.kt — direct readings displace the wearable
    // proxies when an owner imports labs.
    HBA1C("hba1c", MetricUnit.PERCENT, MetricDomain.BIOMARKER, allowsManualEntry = true),
    HSCRP("hscrp", MetricUnit.MG_PER_L, MetricDomain.BIOMARKER, allowsManualEntry = true),
    TOTAL_CHOLESTEROL("total_cholesterol", MetricUnit.MG_PER_DL, MetricDomain.BIOMARKER, allowsManualEntry = true),
    LDL_CHOLESTEROL("ldl_cholesterol", MetricUnit.MG_PER_DL, MetricDomain.BIOMARKER, allowsManualEntry = true),
    HDL_CHOLESTEROL("hdl_cholesterol", MetricUnit.MG_PER_DL, MetricDomain.BIOMARKER, allowsManualEntry = true),
    TRIGLYCERIDES("triglycerides", MetricUnit.MG_PER_DL, MetricDomain.BIOMARKER, allowsManualEntry = true),
    APO_B("apo_b", MetricUnit.MG_PER_DL, MetricDomain.BIOMARKER, allowsManualEntry = true),
    VITAMIN_D_25OH("vitamin_d_25oh", MetricUnit.NG_PER_ML, MetricDomain.BIOMARKER, allowsManualEntry = true),
    TSH("tsh", MetricUnit.MIU_PER_L, MetricDomain.BIOMARKER, allowsManualEntry = true),
    FREE_T4("free_t4", MetricUnit.NG_PER_DL, MetricDomain.BIOMARKER, allowsManualEntry = true),
    FREE_T3("free_t3", MetricUnit.PG_PER_ML, MetricDomain.BIOMARKER, allowsManualEntry = true),
    HEMOGLOBIN("hemoglobin", MetricUnit.G_PER_DL, MetricDomain.BIOMARKER, allowsManualEntry = true),
    HEMATOCRIT("hematocrit", MetricUnit.PERCENT, MetricDomain.BIOMARKER, allowsManualEntry = true),
    WBC("wbc", MetricUnit.GIGA_PER_L, MetricDomain.BIOMARKER, allowsManualEntry = true),
    RBC("rbc", MetricUnit.TERA_PER_L, MetricDomain.BIOMARKER, allowsManualEntry = true),
    PLATELETS("platelets", MetricUnit.GIGA_PER_L, MetricDomain.BIOMARKER, allowsManualEntry = true),

    // Glycemic-extended (#24). Fasting glucose + insulin enable HOMA-IR, the
    // canonical insulin-resistance index — clinically meaningful before HbA1c
    // shifts. HOMA-IR is stored as the calculated value (Matthews 1985:
    // (fasting insulin µIU/mL × fasting glucose mg/dL) / 405).
    FASTING_GLUCOSE("fasting_glucose", MetricUnit.MG_PER_DL, MetricDomain.BIOMARKER, allowsManualEntry = true),
    FASTING_INSULIN("fasting_insulin", MetricUnit.MICRO_IU_PER_ML, MetricDomain.BIOMARKER, allowsManualEntry = true),
    HOMA_IR("homa_ir", MetricUnit.SCORE, MetricDomain.BIOMARKER, allowsManualEntry = true),

    // Iron status (#24). Ferritin is the most-cited single marker for body
    // iron stores; relevant to fatigue / inflammation patterns.
    FERRITIN("ferritin", MetricUnit.NG_PER_ML, MetricDomain.BIOMARKER, allowsManualEntry = true),

    // Endocrine panel (#24). Sex hormones + adrenal (cortisol) + IGF-1.
    // Reference ranges vary by sex/age — Bios stores raw values, the owner's
    // own provider-supplied range travels with the FHIR import.
    TESTOSTERONE_TOTAL("testosterone_total", MetricUnit.NG_PER_DL, MetricDomain.BIOMARKER, allowsManualEntry = true),
    ESTRADIOL("estradiol", MetricUnit.PG_PER_ML, MetricDomain.BIOMARKER, allowsManualEntry = true),
    CORTISOL("cortisol", MetricUnit.UG_PER_DL, MetricDomain.BIOMARKER, allowsManualEntry = true),
    IGF_1("igf_1", MetricUnit.NG_PER_ML, MetricDomain.BIOMARKER, allowsManualEntry = true),

    // Micronutrients (#24). Common deficiency panel — methylation cofactors
    // (B12/folate) and the electrolyte the standard CMP often omits.
    VITAMIN_B12("vitamin_b12", MetricUnit.PG_PER_ML, MetricDomain.BIOMARKER, allowsManualEntry = true),
    FOLATE("folate", MetricUnit.NG_PER_ML, MetricDomain.BIOMARKER, allowsManualEntry = true),
    MAGNESIUM("magnesium", MetricUnit.MG_PER_DL, MetricDomain.BIOMARKER, allowsManualEntry = true),

    // Renal / hepatic / insulin-resistance panel (#158, audit gap §2.8). The
    // highest-yield silent-disease detectors not yet covered: CKD develops
    // years before symptoms (eGFR is the single most important "preventive"
    // lab for the >50 cohort); NAFLD is high-prevalence and asymptomatic
    // until late stage; HOMA-IR catches insulin resistance before HbA1c
    // shifts. FASTING_INSULIN (#159) + HOMA_IR (#160) above complete the
    // insulin-resistance pathway.
    EGFR("egfr", MetricUnit.ML_PER_MIN_PER_173, MetricDomain.BIOMARKER, allowsManualEntry = true),
    CREATININE("creatinine", MetricUnit.MG_PER_DL, MetricDomain.BIOMARKER, allowsManualEntry = true),
    ALT("alt", MetricUnit.U_PER_L, MetricDomain.BIOMARKER, allowsManualEntry = true),
    AST("ast", MetricUnit.U_PER_L, MetricDomain.BIOMARKER, allowsManualEntry = true),
    GGT("ggt", MetricUnit.U_PER_L, MetricDomain.BIOMARKER, allowsManualEntry = true),

    // Biomarker-panel expansion (#203, audit gap convergence across
    // MEDICAL_PROFESSIONAL_POV §2.8, ONCOLOGY_POV §2.3, CARDIOLOGY_POV §2.11,
    // MODERN_NON_ALLOPATHIC_POV §2.3, GERIATRICS_PALLIATIVE_POV). The
    // expansion fills four gaps the audits identified:
    //  - Hepatic panel completion (ALP / total bilirubin / albumin) — silent
    //    cholestatic disease, hepatocellular injury staging, and synthetic
    //    function. Pairs with the existing ALT/AST/GGT trio.
    //  - Cardiometabolic risk amplifiers — Lp(a) is the single most important
    //    inherited atherogenic factor (EAS/AHA 2022 consensus) and the only
    //    one with a once-in-a-lifetime measurement use-case.
    //  - Functional-medicine / preventive markers — homocysteine,
    //    uric acid, TIBC, reverse T3, TPO antibodies, DHEA-S, SHBG, omega-3
    //    index, leptin, adiponectin. The IFM matrix groups these as
    //    methylation, autoimmune-thyroid, adrenal, and metabolic-hormone
    //    axes. Bios stores raw values; evaluation belongs to the owner.
    //  - Thrombosis / coagulation — D-dimer (VTE workup; cardio-oncology
    //    surveillance adjunct to troponin / NT-proBNP).
    //
    // Cross-references: cardio-oncology troponin / NT-proBNP / absolute
    // neutrophil count are owned by PR #230 (feat/cardio-oncology-
    // surveillance) and intentionally not added here to avoid contract
    // collisions; this PR composes against #230's keys via the
    // BiomarkerConditionPatterns once both ship.
    //
    // Hepatic-panel completion (AASLD 2017 — Practice Guidance on
    // evaluation of abnormal liver chemistries).
    TOTAL_BILIRUBIN_UMOL_PER_L("total_bilirubin_umol_per_l", MetricUnit.UMOL_PER_L, MetricDomain.BIOMARKER, allowsManualEntry = true),
    ALBUMIN_G_PER_L("albumin_g_per_l", MetricUnit.G_PER_L, MetricDomain.BIOMARKER, allowsManualEntry = true),
    ALP_U_PER_L("alp_u_per_l", MetricUnit.U_PER_L, MetricDomain.BIOMARKER, allowsManualEntry = true),

    // Cardiovascular risk amplifiers.
    // Lp(a) — EAS 2022 consensus statement / Reyes-Soffer AHA 2022:
    // measured once in a lifetime; nmol/L is the recommended SI unit
    // (mg/dL is convertible but is not particle-mass-equivalent across
    // assays). Audit gap: primary care + cardiology §2.11.
    LP_A_NMOL_PER_L("lp_a_nmol_per_l", MetricUnit.NMOL_PER_L, MetricDomain.BIOMARKER, allowsManualEntry = true),
    // D-dimer — Wells / PERC adjunct; cardio-oncology adjunct to
    // troponin / NT-proBNP. Reported as ng/mL FEU (fibrinogen equivalent
    // units); the FEU qualifier is universal in clinical practice.
    D_DIMER_NG_PER_ML("d_dimer_ng_per_ml", MetricUnit.NG_PER_ML, MetricDomain.BIOMARKER, allowsManualEntry = true),

    // Functional-medicine / methylation + inflammation panel.
    // Homocysteine — methylation-cycle marker; elevated values associate
    // with cardiovascular risk and B12/folate insufficiency (AHA 2009
    // scientific statement; Smith Refsum 2018).
    HOMOCYSTEINE_UMOL_PER_L("homocysteine_umol_per_l", MetricUnit.UMOL_PER_L, MetricDomain.BIOMARKER, allowsManualEntry = true),
    // Uric acid — metabolic-syndrome marker; gout / kidney-stone risk.
    // SI unit µmol/L; US labs report mg/dL via UnitDisplay.
    URIC_ACID_UMOL_PER_L("uric_acid_umol_per_l", MetricUnit.UMOL_PER_L, MetricDomain.BIOMARKER, allowsManualEntry = true),
    // TIBC — total iron-binding capacity. Pairs with FERRITIN above to
    // disambiguate iron-deficiency from anemia of chronic disease
    // (Williams Hematology 10th ed.).
    TIBC_UMOL_PER_L("tibc_umol_per_l", MetricUnit.UMOL_PER_L, MetricDomain.BIOMARKER, allowsManualEntry = true),

    // Thyroid (functional-medicine extension to TSH/FT4/FT3 above).
    // Reverse T3 — IFM matrix marker for euthyroid sick syndrome and
    // peripheral T4-to-T3 conversion impairment. Not part of the standard
    // ATA workup but used in functional-medicine practice.
    REVERSE_T3_NG_PER_DL("reverse_t3_ng_per_dl", MetricUnit.NG_PER_DL, MetricDomain.BIOMARKER, allowsManualEntry = true),
    // TPO antibody — Hashimoto's autoimmune-thyroid screen (ATA 2014).
    // Assay-specific unit kIU/L is the WHO international-reference
    // standard.
    THYROID_PEROXIDASE_AB_KIU_PER_L("thyroid_peroxidase_ab_kiu_per_l", MetricUnit.KIU_PER_L, MetricDomain.BIOMARKER, allowsManualEntry = true),

    // Endocrine (functional-medicine extension — adrenal + sex-hormone
    // binding capacity).
    // DHEA-S — adrenal-androgen reserve; declines linearly with age and
    // is a functional-medicine adrenal-axis marker (Endocrine Society
    // 2014). SI unit µmol/L; US labs report µg/dL.
    DHEA_S_UMOL_PER_L("dhea_s_umol_per_l", MetricUnit.UMOL_PER_L, MetricDomain.BIOMARKER, allowsManualEntry = true),
    // SHBG — sex-hormone-binding globulin; required to compute free
    // androgen index. PCOS workup, low-testosterone evaluation, and
    // hyperinsulinemia screen (Endocrine Society 2018).
    SHBG_NMOL_PER_L("shbg_nmol_per_l", MetricUnit.NMOL_PER_L, MetricDomain.BIOMARKER, allowsManualEntry = true),

    // Cardiometabolic — leptin, adiponectin, omega-3 index. Each is a
    // functional-medicine matrix marker for metabolic-syndrome staging
    // beyond HOMA-IR / HbA1c.
    OMEGA_3_INDEX_PCT("omega_3_index_pct", MetricUnit.PERCENT, MetricDomain.BIOMARKER, allowsManualEntry = true),
    LEPTIN_NG_PER_ML("leptin_ng_per_ml", MetricUnit.NG_PER_ML, MetricDomain.BIOMARKER, allowsManualEntry = true),
    ADIPONECTIN_MG_PER_L("adiponectin_mg_per_l", MetricUnit.MG_PER_L, MetricDomain.BIOMARKER, allowsManualEntry = true),

    // Epigenetic age clocks (user-imported from TruDiagnostic / other labs).
    // Slow-rolling: quarterly at best. Treated as biomarkers — the owner sees
    // them alongside HBA1C, ApoB, etc. Bios never derives a composite "age
    // score" from these (manifesto: never evaluate the person).
    EPIGENETIC_AGE_DUNEDIN_PACE("epigenetic_age_dunedin_pace", MetricUnit.SCORE, MetricDomain.BIOMARKER, allowsManualEntry = true),
    EPIGENETIC_AGE_GRIM("epigenetic_age_grim", MetricUnit.YEARS, MetricDomain.BIOMARKER, allowsManualEntry = true),
    EPIGENETIC_AGE_PHENO("epigenetic_age_pheno", MetricUnit.YEARS, MetricDomain.BIOMARKER, allowsManualEntry = true),
    EPIGENETIC_AGE_HORVATH("epigenetic_age_horvath", MetricUnit.YEARS, MetricDomain.BIOMARKER, allowsManualEntry = true),

    // Companion signals (injected by W2F via ContentProvider).
    // typing_cadence requires the AccessibilityService capture surface;
    // mood_drift_score is a domain-specific ADA-1/HDA-1 composite. Both
    // pass the producer-by-capture-surface rule.
    TYPING_CADENCE("typing_cadence", MetricUnit.SCORE, MetricDomain.MENTAL_HEALTH),
    MOOD_DRIFT_SCORE("mood_drift_score", MetricUnit.SCORE, MetricDomain.MENTAL_HEALTH),

    // Substance-use events (injected by Smokeless via ContentProvider)
    // Timestamp + opaque event-id only — no dose, brand, or method.
    TOBACCO_USE("tobacco_use", MetricUnit.EVENT, MetricDomain.INTAKE),
    TOBACCO_CRAVING("tobacco_craving", MetricUnit.EVENT, MetricDomain.INTAKE),
    CANNABIS_USE("cannabis_use", MetricUnit.EVENT, MetricDomain.INTAKE),
    CANNABIS_CRAVING("cannabis_craving", MetricUnit.EVENT, MetricDomain.INTAKE),

    // Dosed-intake events (#136). Unlike tobacco_use / cannabis_use, these
    // keys carry the dose in `value` so the pharmacokinetic engine can
    // compute current concentration. Companion-write surfaces (e.g. W2F
    // FuelLog for caffeine) populate the dose; substance identity travels
    // with the metric_type (`caffeine_intake`, `alcohol_intake`) or, for
    // generic medication entries, in event_payloads keyed by reading id.
    // Manifesto-clean: math substrate only — no nudges, no adherence
    // judgments. See engine/ConcentrationCalculator.
    CAFFEINE_INTAKE("caffeine_intake", MetricUnit.MILLIGRAMS, MetricDomain.INTAKE),
    // Stored as grams of pure ethanol (the canonical "standard drink" unit:
    // ~14 g in the US, ~10 g in the EU; the value is always grams of
    // ethanol regardless of regional convention).
    ALCOHOL_INTAKE("alcohol_intake", MetricUnit.GRAMS, MetricDomain.INTAKE),
    // Generic prescribed-medication dose. Specific drug identifier rides
    // in event_payloads (`substance_key`) so a future medications
    // companion can manage its own substance vocabulary without
    // re-allocating MetricType keys per drug.
    MEDICATION_INTAKE("medication_intake", MetricUnit.MILLIGRAMS, MetricDomain.INTAKE),

    // Safety events (injected by Virgil via ContentProvider)
    // Timestamp + opaque event-id only — no GPS, SMS contents, or contact identity.
    FALL_EVENT("fall_event", MetricUnit.EVENT, MetricDomain.SAFETY),
    NEAR_MISS_FALL("near_miss_fall", MetricUnit.EVENT, MetricDomain.SAFETY),
    CHECK_IN_MISS("check_in_miss", MetricUnit.EVENT, MetricDomain.SAFETY),

    // Active-test results (reserved, no companion whitelisted yet).
    // W2F has PVT data today (cognitive_probes); Fil plans SDMT. Reserving
    // the key now commits the shape upfront so two companions don't ship
    // divergent definitions of the same signal. See decision 5 in
    // docs/SELF_REPORTED_DATA_HOME.md.
    REACTION_TIME_MS("reaction_time_ms", MetricUnit.MILLISECONDS, MetricDomain.MENTAL_HEALTH);

    val readableName: String
        get() = key.replace("_", " ").replaceFirstChar { it.uppercase() }

    companion object {
        fun fromKey(key: String): MetricType? = entries.find { it.key == key }
    }
}

enum class MetricUnit(val symbol: String) {
    BPM("bpm"),
    MILLISECONDS("ms"),
    MMHG("mmHg"),
    PERCENT("%"),
    BREATHS_PER_MIN("breaths/min"),
    CELSIUS("°C"),
    DELTA_CELSIUS("Δ°C"),
    CATEGORY(""),
    SECONDS("s"),
    COUNT(""),
    KCAL("kcal"),
    KILOGRAMS("kg"),
    MG_PER_DL("mg/dL"),
    MG_PER_L("mg/L"),
    NG_PER_ML("ng/mL"),
    NG_PER_DL("ng/dL"),
    UG_PER_DL("µg/dL"),
    PG_PER_ML("pg/mL"),
    MIU_PER_L("mIU/L"),
    MICRO_IU_PER_ML("µIU/mL"),
    G_PER_DL("g/dL"),
    GIGA_PER_L("10⁹/L"),
    TERA_PER_L("10¹²/L"),
    SCORE(""),
    EVENT(""),
    LUX("lx"),
    YEARS("yr"),
    MS_SQUARED("ms²"),
    ML_PER_KG_MIN("mL/kg/min"),
    UG_PER_M3("µg/m³"),
    PPM("ppm"),
    PPB("ppb"),
    LITERS_PER_MIN("L/min"),
    MILLIGRAMS("mg"),
    GRAMS("g"),
    /** Enzyme activity per litre — ALT, AST, GGT, etc. */
    U_PER_L("U/L"),
    /** eGFR normalized to body surface area — KDIGO 2024 standard. */
    ML_PER_MIN_PER_173("mL/min/1.73m²"),
    /** SI micromolar — homocysteine, uric acid, TIBC, total bilirubin. */
    UMOL_PER_L("µmol/L"),
    /** SI nanomolar — Lp(a) (EAS 2022), SHBG. */
    NMOL_PER_L("nmol/L"),
    /** Albumin SI — g/L (US uses g/dL via UnitDisplay). */
    G_PER_L("g/L"),
    /** Thyroid antibodies — WHO international-reference assay units. */
    KIU_PER_L("kIU/L")
}

enum class MetricDomain {
    CARDIOVASCULAR, RESPIRATORY, TEMPERATURE, SLEEP,
    ACTIVITY, METABOLIC, RECOVERY, WOMENS_HEALTH,
    MENTAL_HEALTH, NEUROLOGICAL, INTAKE, SAFETY, ENVIRONMENT, BIOMARKER
}
