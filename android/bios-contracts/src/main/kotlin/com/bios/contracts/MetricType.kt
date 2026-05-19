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
 */
enum class MetricType(val key: String, val unit: MetricUnit, val domain: MetricDomain) {
    // Cardiovascular
    HEART_RATE("heart_rate", MetricUnit.BPM, MetricDomain.CARDIOVASCULAR),
    HEART_RATE_VARIABILITY("heart_rate_variability", MetricUnit.MILLISECONDS, MetricDomain.CARDIOVASCULAR),
    PARASYMPATHETIC_TONE("parasympathetic_tone", MetricUnit.SCORE, MetricDomain.CARDIOVASCULAR),
    STRESS_SCORE("stress_score", MetricUnit.SCORE, MetricDomain.CARDIOVASCULAR),
    LF_HF_RATIO("lf_hf_ratio", MetricUnit.SCORE, MetricDomain.CARDIOVASCULAR),
    HRV_LF_POWER("hrv_lf_power", MetricUnit.MS_SQUARED, MetricDomain.CARDIOVASCULAR),
    HRV_HF_POWER("hrv_hf_power", MetricUnit.MS_SQUARED, MetricDomain.CARDIOVASCULAR),
    VO2_MAX("vo2_max", MetricUnit.ML_PER_KG_MIN, MetricDomain.CARDIOVASCULAR),
    RESTING_HEART_RATE("resting_heart_rate", MetricUnit.BPM, MetricDomain.CARDIOVASCULAR),
    BLOOD_PRESSURE_SYSTOLIC("blood_pressure_systolic", MetricUnit.MMHG, MetricDomain.CARDIOVASCULAR),
    BLOOD_PRESSURE_DIASTOLIC("blood_pressure_diastolic", MetricUnit.MMHG, MetricDomain.CARDIOVASCULAR),
    BLOOD_OXYGEN("blood_oxygen", MetricUnit.PERCENT, MetricDomain.CARDIOVASCULAR),

    // Respiratory
    RESPIRATORY_RATE("respiratory_rate", MetricUnit.BREATHS_PER_MIN, MetricDomain.RESPIRATORY),

    // Temperature
    SKIN_TEMPERATURE("skin_temperature", MetricUnit.CELSIUS, MetricDomain.TEMPERATURE),
    SKIN_TEMPERATURE_DEVIATION("skin_temperature_deviation", MetricUnit.DELTA_CELSIUS, MetricDomain.TEMPERATURE),

    // Sleep
    SLEEP_STAGE("sleep_stage", MetricUnit.CATEGORY, MetricDomain.SLEEP),
    SLEEP_DURATION("sleep_duration", MetricUnit.SECONDS, MetricDomain.SLEEP),
    SLEEP_LATENCY("sleep_latency", MetricUnit.SECONDS, MetricDomain.SLEEP),
    SLEEP_EFFICIENCY("sleep_efficiency", MetricUnit.PERCENT, MetricDomain.SLEEP),
    SLEEP_FRAGMENTATION_INDEX("sleep_fragmentation_index", MetricUnit.COUNT, MetricDomain.SLEEP),
    WAKE_AFTER_SLEEP_ONSET("wake_after_sleep_onset", MetricUnit.SECONDS, MetricDomain.SLEEP),
    SLEEP_SCORE("sleep_score", MetricUnit.SCORE, MetricDomain.SLEEP),
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
    BLOOD_GLUCOSE("blood_glucose", MetricUnit.MG_PER_DL, MetricDomain.METABOLIC),
    // CGM-derived variability keys (#28). Computed over the last 24h of
    // BLOOD_GLUCOSE readings by GlucoseVariability; one value per 24h window.
    GLUCOSE_CV("glucose_cv", MetricUnit.PERCENT, MetricDomain.METABOLIC),
    GLUCOSE_MAGE("glucose_mage", MetricUnit.MG_PER_DL, MetricDomain.METABOLIC),
    GLUCOSE_TIME_IN_RANGE("glucose_time_in_range", MetricUnit.PERCENT, MetricDomain.METABOLIC),
    GLUCOSE_PEAK_24H("glucose_peak_24h", MetricUnit.MG_PER_DL, MetricDomain.METABOLIC),
    BODY_MASS("body_mass", MetricUnit.KILOGRAMS, MetricDomain.METABOLIC),
    BODY_FAT_PCT("body_fat_pct", MetricUnit.PERCENT, MetricDomain.METABOLIC),
    LEAN_MASS("lean_mass", MetricUnit.KILOGRAMS, MetricDomain.METABOLIC),
    BODY_WATER_PCT("body_water_pct", MetricUnit.PERCENT, MetricDomain.METABOLIC),
    BONE_MASS("bone_mass", MetricUnit.KILOGRAMS, MetricDomain.METABOLIC),

    // Recovery
    RECOVERY_SCORE("recovery_score", MetricUnit.SCORE, MetricDomain.RECOVERY),

    // Women's Health
    BASAL_BODY_TEMPERATURE("basal_body_temperature", MetricUnit.CELSIUS, MetricDomain.WOMENS_HEALTH),
    CYCLE_PHASE("cycle_phase", MetricUnit.CATEGORY, MetricDomain.WOMENS_HEALTH),
    MENSTRUATION_ONSET("menstruation_onset", MetricUnit.EVENT, MetricDomain.WOMENS_HEALTH),
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
    HBA1C("hba1c", MetricUnit.PERCENT, MetricDomain.BIOMARKER),
    HSCRP("hscrp", MetricUnit.MG_PER_L, MetricDomain.BIOMARKER),
    TOTAL_CHOLESTEROL("total_cholesterol", MetricUnit.MG_PER_DL, MetricDomain.BIOMARKER),
    LDL_CHOLESTEROL("ldl_cholesterol", MetricUnit.MG_PER_DL, MetricDomain.BIOMARKER),
    HDL_CHOLESTEROL("hdl_cholesterol", MetricUnit.MG_PER_DL, MetricDomain.BIOMARKER),
    TRIGLYCERIDES("triglycerides", MetricUnit.MG_PER_DL, MetricDomain.BIOMARKER),
    APO_B("apo_b", MetricUnit.MG_PER_DL, MetricDomain.BIOMARKER),
    VITAMIN_D_25OH("vitamin_d_25oh", MetricUnit.NG_PER_ML, MetricDomain.BIOMARKER),
    TSH("tsh", MetricUnit.MIU_PER_L, MetricDomain.BIOMARKER),
    FREE_T4("free_t4", MetricUnit.NG_PER_DL, MetricDomain.BIOMARKER),
    FREE_T3("free_t3", MetricUnit.PG_PER_ML, MetricDomain.BIOMARKER),
    HEMOGLOBIN("hemoglobin", MetricUnit.G_PER_DL, MetricDomain.BIOMARKER),
    HEMATOCRIT("hematocrit", MetricUnit.PERCENT, MetricDomain.BIOMARKER),
    WBC("wbc", MetricUnit.GIGA_PER_L, MetricDomain.BIOMARKER),
    RBC("rbc", MetricUnit.TERA_PER_L, MetricDomain.BIOMARKER),
    PLATELETS("platelets", MetricUnit.GIGA_PER_L, MetricDomain.BIOMARKER),

    // Epigenetic age clocks (user-imported from TruDiagnostic / other labs).
    // Slow-rolling: quarterly at best. Treated as biomarkers — the owner sees
    // them alongside HBA1C, ApoB, etc. Bios never derives a composite "age
    // score" from these (manifesto: never evaluate the person).
    EPIGENETIC_AGE_DUNEDIN_PACE("epigenetic_age_dunedin_pace", MetricUnit.SCORE, MetricDomain.BIOMARKER),
    EPIGENETIC_AGE_GRIM("epigenetic_age_grim", MetricUnit.YEARS, MetricDomain.BIOMARKER),
    EPIGENETIC_AGE_PHENO("epigenetic_age_pheno", MetricUnit.YEARS, MetricDomain.BIOMARKER),
    EPIGENETIC_AGE_HORVATH("epigenetic_age_horvath", MetricUnit.YEARS, MetricDomain.BIOMARKER),

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
    PG_PER_ML("pg/mL"),
    MIU_PER_L("mIU/L"),
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
    PPB("ppb")
}

enum class MetricDomain {
    CARDIOVASCULAR, RESPIRATORY, TEMPERATURE, SLEEP,
    ACTIVITY, METABOLIC, RECOVERY, WOMENS_HEALTH,
    MENTAL_HEALTH, INTAKE, SAFETY, ENVIRONMENT, BIOMARKER
}
