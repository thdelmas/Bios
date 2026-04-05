package com.bios.app.model

// MARK: - Metric Types

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
    BASAL_BODY_TEMPERATURE("basal_body_temperature", MetricUnit.CELSIUS, MetricDomain.WOMENS_HEALTH);

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
    SCORE("")
}

enum class MetricDomain {
    CARDIOVASCULAR, RESPIRATORY, TEMPERATURE, SLEEP,
    ACTIVITY, METABOLIC, RECOVERY, WOMENS_HEALTH
}

// MARK: - Data Source

enum class SourceType(val key: String) {
    HEALTH_CONNECT("health_connect"),
    GADGETBRIDGE("gadgetbridge"),
    DIRECT_SENSOR("direct_sensor"),
    OURA_API("oura_api"),
    WHOOP_API("whoop_api"),
    GARMIN_API("garmin_api"),
    WITHINGS_API("withings_api"),
    DEXCOM_API("dexcom_api"),
    POLAR_API("polar_api"),
    PHONE_SENSOR("phone_sensor")
}

enum class SensorType {
    OPTICAL_HR, ECG, ACCELEROMETER, THERMISTOR,
    PPG_CAMERA, CGM, PULSE_OXIMETER, BAROMETER,
    MICROPHONE, DERIVED
}

enum class ConfidenceTier(val level: Int) {
    VENDOR_DERIVED(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    CLINICAL(4);

    companion object {
        fun fromLevel(level: Int): ConfidenceTier =
            entries.find { it.level == level } ?: LOW
    }
}

// MARK: - Sleep

enum class SleepStage(val value: Int) {
    AWAKE(0), LIGHT(1), DEEP(2), REM(3)
}

// MARK: - Aggregation

enum class AggregatePeriod { HOUR, DAY, WEEK, MONTH }

// MARK: - Baselines

enum class BaselineContext { RESTING, ACTIVE, SLEEPING, ALL }

enum class TrendDirection { RISING, STABLE, FALLING }

// MARK: - Alerts

enum class AlertTier(val level: Int, val label: String) {
    OBSERVATION(0, "Observation"),
    NOTICE(1, "Notice"),
    ADVISORY(2, "Advisory"),
    URGENT(3, "Urgent");

    companion object {
        fun fromLevel(level: Int): AlertTier =
            entries.find { it.level == level } ?: OBSERVATION
    }
}

enum class ConditionCategory {
    CARDIOVASCULAR, RESPIRATORY, METABOLIC, SLEEP,
    MENTAL_HEALTH, INFECTIOUS, WOMENS_HEALTH, RECOVERY
}

// MARK: - Privacy

enum class PrivacyTier {
    PRIVATE,
    COMMUNITY
}

// MARK: - Health Events

enum class HealthEventType(val label: String) {
    SYMPTOM("Symptom"),
    HYPOTHESIS("Hypothesis"),
    DOCTOR_VISIT("Doctor Visit"),
    DIAGNOSIS("Diagnosis"),
    TREATMENT("Treatment"),
    NOTE("Note")
}

enum class HealthEventStatus {
    OPEN, RESOLVED, DISMISSED
}
