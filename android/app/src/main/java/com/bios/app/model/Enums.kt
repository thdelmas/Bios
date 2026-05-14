package com.bios.app.model

// MetricType / MetricUnit / MetricDomain were extracted to the :bios-contracts
// module (Phase 7.4) so companions can consume the inter-app surface from a
// single artifact. Import them from `com.bios.contracts` directly.

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
    PHONE_SENSOR("phone_sensor"),
    CAMERA_PPG("camera_ppg")
}

enum class SensorType {
    OPTICAL_HR, ECG, ACCELEROMETER, THERMISTOR,
    PPG_CAMERA, CGM, PULSE_OXIMETER, BAROMETER,
    MICROPHONE, DERIVED
}

// Kind of data a `DataSource` produces. Engine policies key off this:
// `BaselineEngine` and `SignalQualityFilter` only consume SENSOR; self-reports
// and active-test results route to a separate, descriptive (not evaluative)
// engine. Decided in docs/SELF_REPORTED_DATA_HOME.md.
enum class ReadingKind {
    SENSOR,
    SELF_REPORTED,
    DERIVED,
    ACTIVE_TEST
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
    MENTAL_HEALTH, INFECTIOUS, WOMENS_HEALTH, RECOVERY, SAFETY
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
