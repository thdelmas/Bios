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
    // PPG AFib screening burden (#180, audit §2.1). Rolling 7-day median of
    // per-PPG-session irregularly-irregular verdicts (Poincaré SD1/SD2 +
    // sample entropy + turning-point ratio). Mirrors Apple Heart Study
    // (Perez 2019), mAFA-II (Guo 2019), Fitbit Heart Study (Lubitz 2022)
    // classifier shape. See engine/RhythmClassifier.kt.
    IRREGULAR_RHYTHM_BURDEN("irregular_rhythm_burden", MetricUnit.PERCENT, MetricDomain.CARDIOVASCULAR),

    // Single-lead ECG strip presence (#188, audit gap §2.8). The actual
    // waveform is a binary blob in the `ecg_strips` table, not a scalar
    // metric — this key is the presence indicator the pattern engine
    // joins against ("is there an owner-captured ECG for this window?")
    // so the AFib-screen confirmation surface can wire up without
    // re-discovering strips via cross-table joins on every pull.
    // value = 1.0 (boolean true), durationSec = strip length. The
    // companion ContentProvider exposes this key but never the raw
    // waveform — that stays inside the encrypted DB until the owner
    // exports it via FHIR.
    ECG_STRIP_AVAILABLE("ecg_strip_available", MetricUnit.BOOLEAN, MetricDomain.CARDIOVASCULAR),

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

    // PPG-derived augmentation index (Blueprint audit §3.1 #8). Computed in
    // PpgSignalProcessor as (1 − h_diastolic/h_systolic) × 100 across beats
    // with a detectable dichrotic notch; higher = stiffer arterial bed
    // (matches the clinical SphygmoCor AIx direction). Peripheral PPG proxy
    // for trend tracking, not the central-aortic gold standard.
    AUGMENTATION_INDEX_PPG("augmentation_index_ppg", MetricUnit.PERCENT, MetricDomain.CARDIOVASCULAR),

    HR_RECOVERY_1MIN("hr_recovery_1min", MetricUnit.BPM, MetricDomain.CARDIOVASCULAR),
    HR_RECOVERY_2MIN("hr_recovery_2min", MetricUnit.BPM, MetricDomain.CARDIOVASCULAR),

    // PVC-burden estimate from PPG IBI short-long-pair detection (Triage #26).
    ECTOPY_BURDEN_ESTIMATE("ectopy_burden_estimate", MetricUnit.PERCENT, MetricDomain.CARDIOVASCULAR),

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

    // Asthma surveillance (#200, audit gap §2.9 from PAEDIATRICS_POV +
    // MEDICAL_PROFESSIONAL_POV). Owner-measured peak expiratory flow from a
    // standalone peak-flow meter is the canonical paediatric/adult asthma
    // home-monitoring metric (GINA 2024 personal-best framing). FEV1 is the
    // spirometry gold standard typically captured in clinic — owner-entered
    // when an owner has a take-home spirometer or wants to log a clinic
    // reading alongside Bios's wearable signals. Manual entry both — these
    // are owner-measured / clinician-measured values, not wearable-derived.
    PEAK_EXPIRATORY_FLOW_LMIN("peak_expiratory_flow_lmin", MetricUnit.LITERS_PER_MIN, MetricDomain.RESPIRATORY, allowsManualEntry = true),
    FORCED_EXPIRATORY_VOLUME_1_LITERS("forced_expiratory_volume_1_liters", MetricUnit.LITERS, MetricDomain.RESPIRATORY, allowsManualEntry = true),

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

    // Neurology owner-symptom logging (#207, NEUROLOGY_POV §2.6 / §2.5 /
    // §2.15). Owner-input only — manifesto §3.3 prohibits automated voice /
    // face stroke detection. HEADACHE_INTENSITY_NRS is the flat-metric
    // shadow of the structured HeadacheLog / MigraineAttack entities;
    // sidecar fields (aura, triggers, treatment, FAST item) live on the
    // structured row, not in event_payloads.
    HEADACHE_INTENSITY_NRS("headache_intensity_nrs", MetricUnit.SCORE, MetricDomain.NEUROLOGICAL, allowsManualEntry = true),
    MIGRAINE_ATTACK_EVENT("migraine_attack_event", MetricUnit.EVENT, MetricDomain.NEUROLOGICAL, allowsManualEntry = true),
    FAST_STROKE_SUSPECTED("fast_stroke_suspected", MetricUnit.EVENT, MetricDomain.NEUROLOGICAL, allowsManualEntry = true),

    // Neurology URGENT primitives (audit §3.2 #24 / NEUROLOGY_POV §2.1+§2.3).
    // Owner-logged EVENTs with value=1.0 + optional structured sidecar in
    // event_payloads. Thunderclap is separate from HEADACHE_INTENSITY_NRS
    // because the URGENT alert keys on onset velocity (IHS ICHD-3 §6.2.2
    // SAH screen — peak intensity within 60 s) not severity score.
    SEIZURE_EVENT("seizure_event", MetricUnit.EVENT, MetricDomain.NEUROLOGICAL, allowsManualEntry = true),
    THUNDERCLAP_HEADACHE_SUSPECTED("thunderclap_headache_suspected", MetricUnit.EVENT, MetricDomain.NEUROLOGICAL, allowsManualEntry = true),

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

    // Anthropometry + body composition (#199, paediatric + geriatric audits).
    //
    // Paediatric growth tracking requires height + head circumference to plot
    // WHO/CDC growth curves; failure-to-thrive surveillance is the canonical
    // primary-paediatric-care signal. The adult-side trajectory primitives
    // (lean mass + fat mass + BMI) feed the EWGSOP2 sarcopenia and Fearon-2011
    // cachexia screens used by the Geriatrics and Oncology audits.
    //
    // BMI is stored as a derived value alongside its inputs (height + weight)
    // rather than recomputed on every read — body-composition exporters
    // (Withings, manual entry) commonly ship BMI directly, and storing the
    // value lets historical trends survive a future change to the derivation
    // formula. LEAN_BODY_MASS_KG / FAT_MASS_KG ride alongside the existing
    // LEAN_MASS / BODY_FAT_PCT keys: LEAN_MASS is the legacy METABOLIC-domain
    // entry kept for back-compat; LEAN_BODY_MASS_KG is the BODY_COMPOSITION
    // canonical key used by the growth-and-composition surface.
    HEIGHT_CM("height_cm", MetricUnit.CENTIMETERS, MetricDomain.ANTHROPOMETRY, allowsManualEntry = true),
    HEAD_CIRCUMFERENCE_CM("head_circumference_cm", MetricUnit.CENTIMETERS, MetricDomain.ANTHROPOMETRY, allowsManualEntry = true),
    BMI_KG_PER_M2("bmi_kg_per_m2", MetricUnit.KG_PER_M2, MetricDomain.ANTHROPOMETRY, allowsManualEntry = true),
    LEAN_BODY_MASS_KG("lean_body_mass_kg", MetricUnit.KILOGRAMS, MetricDomain.BODY_COMPOSITION, allowsManualEntry = true),
    FAT_MASS_KG("fat_mass_kg", MetricUnit.KILOGRAMS, MetricDomain.BODY_COMPOSITION, allowsManualEntry = true),

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
    // Ambient humidity + temperature (#200, audit gap §2.9). Phone barometers
    // and most BLE ESS sensors emit relative humidity and ambient temperature.
    // Asthma exacerbation triggers correlate with cold-dry and humid-warm
    // conditions (GINA 2024 trigger list); heat-illness screening (#19) reads
    // heat-index = f(temperature, humidity); damp/dry six-pathogens overlay
    // (TCM_POV §2.7) and Ritucharya/six-paruvam patterns (Ayurveda, Siddha,
    // Unani) all key off humidity + temperature. Phone-sensor sourced; not
    // owner-set, so manual entry stays false.
    // Environmental context (#197, audit gaps converge from SOWA_RIGPA_POV
    // §2.1 altitude, INDIGENOUS_AMERICAS_POV Andes/Mesoamerica,
    // OCEANIC_ARCTIC_POV cold + Pacific heat, AFRICAN_TRADITIONAL_POV climate,
    // OTHER_ASIAN_SYSTEMS_POV, MODERN_NON_ALLOPATHIC_POV environmental
    // medicine). Bios's SpO2 / HR / temperature / sleep thresholds were
    // baselined as if the owner lives in a temperate Northern climate at
    // sea level — clinically wrong for owners in the Andes, Tibet, the
    // Sahel, or above 60° latitude. These keys carry owner-set context that
    // modulates absolute clinical thresholds (the personal-baseline path
    // self-corrects; the hard-cutoff path needs explicit context).
    //
    // ELEVATION_M is owner-entered metres above sea level; never derived
    // from GPS without permission (manifesto: no hidden ingestion).
    // AMBIENT_TEMPERATURE_C / AMBIENT_HUMIDITY_PCT can come from phone
    // sensors (if available) or owner annotation. AMBIENT_HUMIDITY_PCT is
    // also slated by issue #200 — if that PR lands first, this declaration
    // will be a no-op duplicate the `fromKey()` resolver still handles.
    // DAYLIGHT_HOURS is computed from owner latitude (RegionConfig) + date
    // by EnvironmentalContextProvider; carries photoperiod for the SAAD
    // (seasonal affective adjustment-disorder) screening pattern.
    ELEVATION_M("elevation_m", MetricUnit.METERS, MetricDomain.ENVIRONMENT, allowsManualEntry = true),
    AMBIENT_TEMPERATURE_C("ambient_temperature_c", MetricUnit.CELSIUS, MetricDomain.ENVIRONMENT, allowsManualEntry = true),
    AMBIENT_HUMIDITY_PCT("ambient_humidity_pct", MetricUnit.PERCENT, MetricDomain.ENVIRONMENT, allowsManualEntry = true),
    DAYLIGHT_HOURS("daylight_hours", MetricUnit.HOURS, MetricDomain.ENVIRONMENT),

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

    // Cardio-oncology biomarker panel (#201, audit gap from ONCOLOGY_POV
    // §2.3-2.5 + CARDIOLOGY_POV §2.3). Anchors the cardiotoxicity /
    // neutropenic-fever surveillance patterns in
    // alerts/CardioOncologyPatterns.kt. NT-proBNP and high-sensitivity
    // troponin are the AHA 2018 / ASCO 2017 cardio-oncology cardiac
    // biomarkers; absolute neutrophil count anchors the IDSA 2010
    // febrile-neutropenia screen.
    TROPONIN_NG_PER_L("troponin_ng_per_l", MetricUnit.NG_PER_L, MetricDomain.BIOMARKER, allowsManualEntry = true),
    NT_PRO_BNP_PG_PER_ML("nt_pro_bnp_pg_per_ml", MetricUnit.PG_PER_ML, MetricDomain.BIOMARKER, allowsManualEntry = true),
    ABSOLUTE_NEUTROPHIL_COUNT("absolute_neutrophil_count", MetricUnit.PER_MICRO_L, MetricDomain.BIOMARKER, allowsManualEntry = true),

    // Wave-1 biomarker expansion (docs/audits/BLUEPRINT_PROTOCOL_AUDIT.md §3.1).
    // Closes the highest-priority Blueprint-vs-Bios coverage gaps: independent
    // ASCVD risk markers, full CMP, full liver panel, CBC indices + 5-cell
    // differential, iron studies, and the reproductive-endocrine panel. All
    // pure-additive enum entries — no engine work, FHIR import via LOINC.

    // Lipoprotein(a) — independent ASCVD risk; ESC 2021 / NLA 2022 report in
    // nmol/L because apo(a) isoform size makes the mg/dL conversion lossy.
    LIPOPROTEIN_A("lipoprotein_a", MetricUnit.NMOL_PER_L, MetricDomain.BIOMARKER, allowsManualEntry = true),

    // Homocysteine — cardiovascular + cognitive risk, B-vitamin sufficiency
    // proxy. Standard longevity panel marker.
    HOMOCYSTEINE("homocysteine", MetricUnit.UMOL_PER_L, MetricDomain.BIOMARKER, allowsManualEntry = true),

    // Comprehensive Metabolic Panel completion — the basic CMP markers Bios
    // was missing. Electrolytes (Na/K/Cl/CO2) use mEq/L by US convention
    // (numerically equal to mmol/L for these monovalent ions plus bicarbonate).
    BUN("bun", MetricUnit.MG_PER_DL, MetricDomain.BIOMARKER, allowsManualEntry = true),
    CALCIUM_SERUM("calcium_serum", MetricUnit.MG_PER_DL, MetricDomain.BIOMARKER, allowsManualEntry = true),
    CARBON_DIOXIDE("carbon_dioxide", MetricUnit.MEQ_PER_L, MetricDomain.BIOMARKER, allowsManualEntry = true),
    CHLORIDE("chloride", MetricUnit.MEQ_PER_L, MetricDomain.BIOMARKER, allowsManualEntry = true),
    PHOSPHATE("phosphate", MetricUnit.MG_PER_DL, MetricDomain.BIOMARKER, allowsManualEntry = true),
    SODIUM("sodium", MetricUnit.MEQ_PER_L, MetricDomain.BIOMARKER, allowsManualEntry = true),
    POTASSIUM("potassium", MetricUnit.MEQ_PER_L, MetricDomain.BIOMARKER, allowsManualEntry = true),
    URIC_ACID("uric_acid", MetricUnit.MG_PER_DL, MetricDomain.BIOMARKER, allowsManualEntry = true),

    // Liver + pancreas panel completion — complements ALT/AST/GGT already
    // shipped. Amylase + lipase ride alongside since labs typically order
    // them on the same liver-function bundle.
    ALBUMIN("albumin", MetricUnit.G_PER_DL, MetricDomain.BIOMARKER, allowsManualEntry = true),
    ALKALINE_PHOSPHATASE("alkaline_phosphatase", MetricUnit.U_PER_L, MetricDomain.BIOMARKER, allowsManualEntry = true),
    BILIRUBIN_TOTAL("bilirubin_total", MetricUnit.MG_PER_DL, MetricDomain.BIOMARKER, allowsManualEntry = true),
    TOTAL_PROTEIN("total_protein", MetricUnit.G_PER_DL, MetricDomain.BIOMARKER, allowsManualEntry = true),
    AMYLASE("amylase", MetricUnit.U_PER_L, MetricDomain.BIOMARKER, allowsManualEntry = true),
    LIPASE("lipase", MetricUnit.U_PER_L, MetricDomain.BIOMARKER, allowsManualEntry = true),

    // CBC indices + leukocyte differential percentages. Absolute differential
    // counts (abs neutrophil already present as ABSOLUTE_NEUTROPHIL_COUNT)
    // can be added later if a clinical pattern requires them.
    MCV("mcv", MetricUnit.FEMTOLITERS, MetricDomain.BIOMARKER, allowsManualEntry = true),
    MCH("mch", MetricUnit.PICOGRAMS, MetricDomain.BIOMARKER, allowsManualEntry = true),
    MCHC("mchc", MetricUnit.G_PER_DL, MetricDomain.BIOMARKER, allowsManualEntry = true),
    RDW("rdw", MetricUnit.PERCENT, MetricDomain.BIOMARKER, allowsManualEntry = true),
    MPV("mpv", MetricUnit.FEMTOLITERS, MetricDomain.BIOMARKER, allowsManualEntry = true),
    NEUTROPHILS_PCT("neutrophils_pct", MetricUnit.PERCENT, MetricDomain.BIOMARKER, allowsManualEntry = true),
    LYMPHOCYTES_PCT("lymphocytes_pct", MetricUnit.PERCENT, MetricDomain.BIOMARKER, allowsManualEntry = true),
    MONOCYTES_PCT("monocytes_pct", MetricUnit.PERCENT, MetricDomain.BIOMARKER, allowsManualEntry = true),
    EOSINOPHILS_PCT("eosinophils_pct", MetricUnit.PERCENT, MetricDomain.BIOMARKER, allowsManualEntry = true),
    BASOPHILS_PCT("basophils_pct", MetricUnit.PERCENT, MetricDomain.BIOMARKER, allowsManualEntry = true),

    // Iron panel — co-ordered with existing FERRITIN. Iron saturation % is a
    // computed lab value most labs report directly; storing the reported
    // value rather than recomputing matches the existing HOMA_IR pattern.
    IRON_SERUM("iron_serum", MetricUnit.UG_PER_DL, MetricDomain.BIOMARKER, allowsManualEntry = true),
    IRON_SATURATION_PCT("iron_saturation_pct", MetricUnit.PERCENT, MetricDomain.BIOMARKER, allowsManualEntry = true),
    TIBC("tibc", MetricUnit.UG_PER_DL, MetricDomain.BIOMARKER, allowsManualEntry = true),

    // Reproductive endocrine panel. Reference ranges are sex-specific and
    // (for women) cycle-phase-specific — provider-supplied range travels
    // with the FHIR import. TESTOSTERONE_TOTAL is already shipped; this
    // adds the free fraction plus the surrounding hypothalamic-pituitary
    // axis.
    FSH("fsh", MetricUnit.MIU_PER_ML, MetricDomain.BIOMARKER, allowsManualEntry = true),
    LH("lh", MetricUnit.MIU_PER_ML, MetricDomain.BIOMARKER, allowsManualEntry = true),
    SHBG("shbg", MetricUnit.NMOL_PER_L, MetricDomain.BIOMARKER, allowsManualEntry = true),
    AMH("amh", MetricUnit.NG_PER_ML, MetricDomain.BIOMARKER, allowsManualEntry = true),
    TESTOSTERONE_FREE("testosterone_free", MetricUnit.PG_PER_ML, MetricDomain.BIOMARKER, allowsManualEntry = true),
    PROLACTIN("prolactin", MetricUnit.NG_PER_ML, MetricDomain.BIOMARKER, allowsManualEntry = true),
    DHEA_SULFATE("dhea_sulfate", MetricUnit.UG_PER_DL, MetricDomain.BIOMARKER, allowsManualEntry = true),

    // Wave-2 biomarker expansion (BLUEPRINT_PROTOCOL_AUDIT.md §3.2) — medium-
    // priority additions: thyroid autoimmunity, prostate screening, imaging-
    // derived risk modifiers, plasma neurology, fat-soluble vitamins, and
    // a cellular-aging clock. Manual entry + FHIR where a stable LOINC exists.
    THYROID_PEROXIDASE_AB("thyroid_peroxidase_ab", MetricUnit.IU_PER_ML, MetricDomain.BIOMARKER, allowsManualEntry = true),
    THYROGLOBULIN_AB("thyroglobulin_ab", MetricUnit.IU_PER_ML, MetricDomain.BIOMARKER, allowsManualEntry = true),
    PSA_TOTAL("psa_total", MetricUnit.NG_PER_ML, MetricDomain.BIOMARKER, allowsManualEntry = true),
    PSA_FREE("psa_free", MetricUnit.NG_PER_ML, MetricDomain.BIOMARKER, allowsManualEntry = true),
    // Telomere length stored as SCORE — every vendor (TeloYears, SpectraCell)
    // ships a proprietary scale; no stable LOINC, manual entry only.
    TELOMERE_LENGTH("telomere_length", MetricUnit.SCORE, MetricDomain.BIOMARKER, allowsManualEntry = true),
    // Agatston CAC score (CT-derived, dimensionless) and DEXA bone-density
    // T-score (WHO osteoporosis: ≤ -2.5). Anatomical site for the T-score
    // rides in event_payloads when the import provides it.
    CORONARY_CALCIUM_SCORE("coronary_calcium_score", MetricUnit.SCORE, MetricDomain.BIOMARKER, allowsManualEntry = true),
    BONE_DENSITY_T_SCORE("bone_density_t_score", MetricUnit.SCORE, MetricDomain.BIOMARKER, allowsManualEntry = true),
    // pTau-217 (Quanterix Simoa / Lilly C2N) — emerging Alzheimer's plasma
    // marker that tracks amyloid PET positivity.
    PTAU_217("ptau_217", MetricUnit.PG_PER_ML, MetricDomain.BIOMARKER, allowsManualEntry = true),
    // Fat-soluble vitamin auxiliaries. Vitamin K2 (MK-7) lacks a stable
    // canonical LOINC so the FHIR path is manual only.
    VITAMIN_K2("vitamin_k2", MetricUnit.NG_PER_ML, MetricDomain.BIOMARKER, allowsManualEntry = true),
    VITAMIN_A_RETINOL("vitamin_a_retinol", MetricUnit.UG_PER_DL, MetricDomain.BIOMARKER, allowsManualEntry = true),
    VITAMIN_E_ALPHA_TOCOPHEROL("vitamin_e_alpha_tocopherol", MetricUnit.MG_PER_L, MetricDomain.BIOMARKER, allowsManualEntry = true),

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
