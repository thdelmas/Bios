package com.bios.app.config

import com.bios.contracts.MetricType

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
    val hypertensionDiastolic: Int,
    /**
     * Clinical bands (low risk / borderline / high risk) for biomarker
     * readings, indexed by [MetricType]. Values in the metric's native unit
     * (matching the contract). Missing entries mean Bios has no clinical
     * band defined for that biomarker yet — the reading is shown without
     * a risk classification.
     */
    val biomarkerBands: Map<MetricType, BiomarkerBands> = emptyMap()
)

/**
 * Three-band clinical classification for a lab value. Bands ascend
 * monotonically: [normalCeiling] is the upper bound of the
 * `NORMAL` band, [borderlineCeiling] is the upper bound of the
 * `BORDERLINE` band; values at or above [borderlineCeiling] are `HIGH`.
 *
 * Use [classify] to map a measurement to a band — the comparisons are
 * inclusive at the lower edge and exclusive at the upper edge so values
 * that sit exactly on the published cut-off slot into the higher-risk
 * band by clinical convention (e.g. HbA1c = 6.5% reads as diabetic).
 */
data class BiomarkerBands(
    val normalCeiling: Double,
    val borderlineCeiling: Double
) {
    init {
        require(normalCeiling < borderlineCeiling) {
            "normalCeiling ($normalCeiling) must be < borderlineCeiling ($borderlineCeiling)"
        }
    }

    fun classify(value: Double): BiomarkerBand = when {
        value < normalCeiling -> BiomarkerBand.NORMAL
        value < borderlineCeiling -> BiomarkerBand.BORDERLINE
        else -> BiomarkerBand.HIGH
    }
}

enum class BiomarkerBand { NORMAL, BORDERLINE, HIGH }

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
