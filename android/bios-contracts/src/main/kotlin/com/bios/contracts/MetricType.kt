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
 */
enum class MetricType(val key: String, val unit: MetricUnit, val domain: MetricDomain) {
    // Cardiovascular
    HEART_RATE("heart_rate", MetricUnit.BPM, MetricDomain.CARDIOVASCULAR),
    HEART_RATE_VARIABILITY("heart_rate_variability", MetricUnit.MILLISECONDS, MetricDomain.CARDIOVASCULAR),
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

    // Activity
    STEPS("steps", MetricUnit.COUNT, MetricDomain.ACTIVITY),
    ACTIVE_CALORIES("active_calories", MetricUnit.KCAL, MetricDomain.ACTIVITY),
    ACTIVE_MINUTES("active_minutes", MetricUnit.SECONDS, MetricDomain.ACTIVITY),

    // Metabolic
    BLOOD_GLUCOSE("blood_glucose", MetricUnit.MG_PER_DL, MetricDomain.METABOLIC),

    // Recovery
    RECOVERY_SCORE("recovery_score", MetricUnit.SCORE, MetricDomain.RECOVERY),

    // Women's Health
    BASAL_BODY_TEMPERATURE("basal_body_temperature", MetricUnit.CELSIUS, MetricDomain.WOMENS_HEALTH),

    // Companion signals (injected by W2F via ContentProvider)
    TYPING_CADENCE("typing_cadence", MetricUnit.SCORE, MetricDomain.MENTAL_HEALTH),
    CIRCADIAN_PHASE_SHIFT("circadian_phase_shift", MetricUnit.SCORE, MetricDomain.MENTAL_HEALTH),
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
    CHECK_IN_MISS("check_in_miss", MetricUnit.EVENT, MetricDomain.SAFETY);

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
    MG_PER_DL("mg/dL"),
    SCORE(""),
    EVENT("")
}

enum class MetricDomain {
    CARDIOVASCULAR, RESPIRATORY, TEMPERATURE, SLEEP,
    ACTIVITY, METABOLIC, RECOVERY, WOMENS_HEALTH,
    MENTAL_HEALTH, INTAKE, SAFETY
}
