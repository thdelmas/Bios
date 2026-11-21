package com.bios.app.config

/**
 * Builders shared by the per-continent region tables, so adding a region
 * means stating only what varies (regulatory body, glucose display unit,
 * the data-sovereignty flag).
 */

/**
 * Builder for a metric-system region that displays glucose in mmol/L and
 * takes the default data-handling rules, the shape that fits most configs.
 */
internal fun standardRegion(
    regionCode: String,
    displayName: String,
    regulatoryBody: String,
    indigenousFlagged: Boolean = false,
) = RegionConfig(
    regionCode = regionCode,
    displayName = displayName,
    unitOverrides = emptyMap(),
    glucoseInMmol = true,
    regulatory = RegulatoryConfig(
        reproductiveDataIsolation = false,
        defaultRetentionDays = 90,
        fhirProfileUrl = null,
        regulatoryBody = regulatoryBody,
    ),
    indigenousFlagged = indigenousFlagged,
)

// -- Unit conversion helpers --
// Shared by region configs that override metric→imperial unit display
// (currently only US). Defined here so per-continent files can reference
// them without redeclaring or pulling in [RegionConfigProvider].

internal fun celsiusToFahrenheit(c: Double): Double = c * 9.0 / 5.0 + 32.0
internal fun fahrenheitToCelsius(f: Double): Double = (f - 32.0) * 5.0 / 9.0
internal fun celsiusDeltaToFahrenheit(dc: Double): Double = dc * 9.0 / 5.0
internal fun fahrenheitDeltaToCelsius(df: Double): Double = df * 5.0 / 9.0
