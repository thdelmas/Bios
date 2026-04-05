package com.bios.app.config

import com.bios.app.model.MetricType

/**
 * Region-specific configuration for localized health thresholds, unit display,
 * and regulatory compliance. Adding a new locale means adding a new config —
 * not new code paths.
 */
data class RegionConfig(
    val regionCode: String,
    val displayName: String,
    val unitOverrides: Map<MetricType, UnitDisplay>,
    val clinicalThresholds: ClinicalThresholds,
    val regulatory: RegulatoryConfig
)

/**
 * Locale-aware unit display. The internal data model always stores SI/metric;
 * this controls presentation only.
 */
data class UnitDisplay(
    val symbol: String,
    val fromMetric: (Double) -> Double,
    val toMetric: (Double) -> Double
)

/**
 * Absolute clinical thresholds that vary by regional medical standards.
 * These complement the personal-baseline sigma thresholds in ConditionPatterns —
 * they provide hard floors/ceilings based on clinical consensus.
 */
data class ClinicalThresholds(
    /** SpO2 below this is clinically significant (%, typically 95). */
    val spo2ConcernThreshold: Double,
    /** SpO2 below this is urgent (%, typically 92). */
    val spo2UrgentThreshold: Double,
    /** Fever threshold for skin temperature delta (°C, varies by region). */
    val feverDeltaCelsius: Double,
    /** High fever absolute threshold (°C, typically 39.4). */
    val highFeverCelsius: Double,
    /** Resting HR above this in adults is tachycardic (bpm, typically 100). */
    val tachycardiaBpm: Int,
    /** Resting HR below this in non-athletes is bradycardic (bpm, typically 50). */
    val bradycardiaBpm: Int,
    /** Blood glucose unit for display: true = mmol/L, false = mg/dL. */
    val glucoseInMmol: Boolean,
    /** Fasting glucose concern threshold (in the display unit). */
    val fastingGlucoseConcern: Double,
    /** Blood pressure systolic hypertension stage 1 threshold (mmHg). */
    val hypertensionSystolic: Int,
    /** Blood pressure diastolic hypertension stage 1 threshold (mmHg). */
    val hypertensionDiastolic: Int
)

/**
 * Regulatory and compliance configuration per region.
 * Controls data handling behavior that varies by jurisdiction.
 */
data class RegulatoryConfig(
    /** Whether reproductive health data requires separate storage/consent (e.g., US post-Dobbs). */
    val reproductiveDataIsolation: Boolean,
    /** Maximum default data retention days before prompting review. */
    val defaultRetentionDays: Int,
    /** Whether the region requires explicit consent for any community data sharing. */
    val explicitCommunityConsent: Boolean,
    /** Whether FHIR export should default to the region's national FHIR profile. */
    val fhirProfileUrl: String?,
    /** Regulatory body name for disclaimers (e.g., "FDA", "EMA", "TGA"). */
    val regulatoryBody: String,
    /** Required disclaimer text for health alerts. */
    val alertDisclaimer: String
)
