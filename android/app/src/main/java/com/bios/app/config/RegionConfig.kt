package com.bios.app.config

import com.bios.contracts.MetricType

/**
 * Region-specific configuration for unit display and data-handling rules.
 * Adding a new locale means adding a new config, not new code paths.
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
