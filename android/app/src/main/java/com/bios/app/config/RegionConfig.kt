package com.bios.app.config

import com.bios.contracts.MetricType

/**
 * Region-specific configuration for localized health thresholds, unit display,
 * and regulatory compliance. Adding a new locale means adding a new config —
 * not new code paths.
 *
 * [climateZone] and [defaultLatitudeDeg] / [defaultElevationM] feed
 * [com.bios.app.config.EnvironmentalContextProvider] (#197). They are
 * *defaults* per region — the owner can override every one of them from
 * Settings → Environmental context, and nothing is auto-derived from GPS
 * without explicit permission.
 */
data class RegionConfig(
    val regionCode: String,
    val displayName: String,
    val unitOverrides: Map<MetricType, UnitDisplay>,
    val clinicalThresholds: ClinicalThresholds,
    val regulatory: RegulatoryConfig,
    /**
     * Local emergency services number for the region. Surfaced on alerts
     * recommending professional escalation. `null` when no canonical number
     * exists — UI falls back to "your local emergency services".
     */
    val emergencyNumber: String? = null,
    /**
     * `true` when this locale sits inside a recognised indigenous data-
     * sovereignty framework (e.g. Te Mana Raraunga in NZ, NACCHO in AU,
     * CARE Principles for Indigenous Data Governance). Surfaced as a footnote
     * on data-export UI. Cross-ref: INDIGENOUS_AMERICAS_POV §2.5, OCEANIC_ARCTIC_POV.
     */
    val indigenousFlagged: Boolean = false,
    /**
     * Whether tropical / endemic-disease patterns (malaria, dengue, chikungunya,
     * leptospirosis, scrub typhus, ciguatera) should fire in this region (#196,
     * audit gap §2.6). Cross-ref: AFRICAN_TRADITIONAL_POV §2.6, OTHER_ASIAN_SYSTEMS_POV §2-3,
     * OCEANIC_ARCTIC_POV §2.6. Owner override via [com.bios.app.physiology.OwnerCondition].
     */
    val tropicalDiseaseRelevant: Boolean = false,
    /**
     * Default climate zone for this region. Owner-overridable from
     * Settings → Environmental context. Used to seed heat / cold pattern
     * thresholds when the owner has not entered a value.
     */
    val climateZone: ClimateZone = ClimateZone.TEMPERATE,
    /**
     * Default latitude in degrees north (negative = southern hemisphere)
     * for daylight-hours computation. Country centroid is plenty for
     * photoperiod math — the owner sets a precise value if they want one.
     */
    val defaultLatitudeDeg: Double = 45.0,
    /**
     * Default elevation in metres for the region centroid. Most countries
     * sit near sea level; the Andean / Tibetan / Ethiopian highland regions
     * carry meaningful baseline elevation. Owner-overridable.
     */
    val defaultElevationM: Double = 0.0,
)

/**
 * Köppen-derived broad climate band (#197). Five bins cover the gradient
 * from tropical heat to Arctic cold without committing the codebase to a
 * full Köppen vocabulary. Sets the default heat/cold thresholds the
 * `heat_exhaustion_screen` and `hypothermia_screen` patterns use.
 */
enum class ClimateZone(val displayName: String) {
    TROPICAL("Tropical"),
    SUBTROPICAL("Subtropical"),
    TEMPERATE("Temperate"),
    COLD("Cold (continental / boreal)"),
    ARCTIC("Arctic / polar")
}

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
 * Three-band clinical classification for a lab value. Always declared in
 * ascending value order — [normalCeiling] < [borderlineCeiling] — and the
 * [concerningDirection] flag picks which extreme is clinically concerning:
 *
 *  - [BandDirection.ABOVE] (default): high values are concerning. Typical
 *    for hsCRP, HbA1c, LDL, ApoB, triglycerides — values at or above
 *    [borderlineCeiling] classify as [BiomarkerBand.CONCERNING].
 *  - [BandDirection.BELOW]: low values are concerning. Typical for HDL,
 *    25-OH vitamin D — values below [normalCeiling] classify as
 *    [BiomarkerBand.CONCERNING].
 *
 * For markers that are concerning at **both** extremes (TSH is the canonical
 * case: low → hyperthyroid, high → hypothyroid), [lowCeiling] adds a second
 * CONCERNING band on the low side without breaking the simple ABOVE shape.
 * Only meaningful when [concerningDirection] is `ABOVE`.
 *
 * Comparisons are inclusive at the lower edge and exclusive at the upper
 * edge so values sitting exactly on a published cut-off slot into the
 * higher-risk band by clinical convention (e.g. HbA1c = 6.5% reads as
 * diabetic; HDL = 40 reads as borderline, not concerning).
 */
data class BiomarkerBands(
    val normalCeiling: Double,
    val borderlineCeiling: Double,
    val concerningDirection: BandDirection = BandDirection.ABOVE,
    val lowCeiling: Double? = null,
) {
    init {
        require(normalCeiling < borderlineCeiling) {
            "normalCeiling ($normalCeiling) must be < borderlineCeiling ($borderlineCeiling)"
        }
        if (lowCeiling != null) {
            require(concerningDirection == BandDirection.ABOVE) {
                "lowCeiling only applies when concerningDirection = ABOVE (BELOW already treats low values as concerning)"
            }
            require(lowCeiling < normalCeiling) {
                "lowCeiling ($lowCeiling) must be < normalCeiling ($normalCeiling)"
            }
        }
    }

    fun classify(value: Double): BiomarkerBand {
        if (lowCeiling != null && value < lowCeiling) return BiomarkerBand.CONCERNING
        return when (concerningDirection) {
            BandDirection.ABOVE -> when {
                value < normalCeiling -> BiomarkerBand.NORMAL
                value < borderlineCeiling -> BiomarkerBand.BORDERLINE
                else -> BiomarkerBand.CONCERNING
            }
            BandDirection.BELOW -> when {
                value >= borderlineCeiling -> BiomarkerBand.NORMAL
                value >= normalCeiling -> BiomarkerBand.BORDERLINE
                else -> BiomarkerBand.CONCERNING
            }
        }
    }
}

enum class BiomarkerBand { NORMAL, BORDERLINE, CONCERNING }

/** Which end of the value range is clinically concerning. */
enum class BandDirection { ABOVE, BELOW }

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
    /**
     * Required disclaimer text for health alerts. English source-of-truth
     * — the [com.bios.app.alerts.AlertTextResolver]-style lookup using
     * [alertDisclaimerKey] takes precedence when an Android [android.content.Context]
     * is available, so callers running outside the framework (tests, headless
     * jobs) still receive the disclaimer in English.
     */
    val alertDisclaimer: String,
    /**
     * Resource name (in `values/pattern_strings.xml` and locale overlays)
     * for the localized form of [alertDisclaimer]. Resolved at notification
     * render time. Null when no localized resource exists yet — caller
     * falls back to [alertDisclaimer]. See issue #210.
     */
    val alertDisclaimerKey: String? = null,
)
