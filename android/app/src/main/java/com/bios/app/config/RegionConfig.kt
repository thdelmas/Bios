package com.bios.app.config

import com.bios.contracts.MetricType

/**
 * Region-specific configuration for unit display and data-handling rules.
 * Adding a new locale means adding a new config, not new code paths.
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
    /** Blood glucose unit for display: true = mmol/L, false = mg/dL. */
    val glucoseInMmol: Boolean,
    val regulatory: RegulatoryConfig,
    /**
     * `true` when this locale sits inside a recognised indigenous data-
     * sovereignty framework (e.g. Te Mana Raraunga in NZ, NACCHO in AU,
     * CARE Principles for Indigenous Data Governance). Surfaced as a footnote
     * on data-export UI. Cross-ref: INDIGENOUS_AMERICAS_POV §2.5, OCEANIC_ARCTIC_POV.
     */
    val indigenousFlagged: Boolean = false,
    /**
     * Default climate zone for this region. Owner-overridable from
     * Settings → Environmental context.
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
 * full Köppen vocabulary.
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
    /** Regulatory body of the region (e.g., "FDA", "EMA", "TGA"). */
    val regulatoryBody: String,
)
