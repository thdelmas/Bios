package com.bios.app.config

/**
 * Common defaults + helpers used to build region configs without restating
 * the universal-ish clinical thresholds (SpO2, fever, tachy/brady, ESC/ESH
 * BP cutoffs) for every locale.
 *
 * The intent is that adding a new region in [RegionConfigProvider]'s
 * continental tables means picking the few values that genuinely vary
 * (emergency number, regulatoryBody, glucose unit, indigenousFlagged) and
 * inheriting the rest. Where a region documents a meaningfully different
 * value (US lower BP cutoff, JP higher SpO2 floor), the per-region builder
 * passes a non-default override.
 *
 * "ESC/ESH cutoffs" defaults below are not a claim that every country in
 * the world uses ESC/ESH guidelines — they are the WHO-aligned reference
 * Bios uses where no country-specific guideline has been verified, and
 * matches the rule of thumb in docs/audits/INDIGENOUS_AMERICAS_POV.md §2.5
 * (es_MX, pt_BR currently silently substitute these; this refactor makes
 * that substitution explicit and replaceable per region).
 */

/** WHO / ESC / ESH defaults that hold in most jurisdictions. */
internal fun defaultThresholds(
    glucoseInMmol: Boolean,
    fastingGlucoseConcern: Double,
    hypertensionSystolic: Int = 140,
    hypertensionDiastolic: Int = 90,
    spo2ConcernThreshold: Double = 95.0,
    spo2UrgentThreshold: Double = 92.0,
    highFeverCelsius: Double = 39.0,
    feverDeltaCelsius: Double = 0.5,
    tachycardiaBpm: Int = 100,
    bradycardiaBpm: Int = 50,
) = ClinicalThresholds(
    spo2ConcernThreshold = spo2ConcernThreshold,
    spo2UrgentThreshold = spo2UrgentThreshold,
    feverDeltaCelsius = feverDeltaCelsius,
    highFeverCelsius = highFeverCelsius,
    tachycardiaBpm = tachycardiaBpm,
    bradycardiaBpm = bradycardiaBpm,
    glucoseInMmol = glucoseInMmol,
    fastingGlucoseConcern = fastingGlucoseConcern,
    hypertensionSystolic = hypertensionSystolic,
    hypertensionDiastolic = hypertensionDiastolic,
    biomarkerBands = universalBiomarkerBands,
)

/**
 * Builds the default informational disclaimer for a region. Localised
 * variants can be passed by the per-region builder; this is the English
 * fallback ("Bios is not a registered medical device. Alerts are
 * informational and do not constitute medical advice.").
 *
 * This text is push-side and must comply with [AlertContentPolicy] —
 * factual statement, no "you should" / no judgment.
 */
internal fun defaultDisclaimer(): String =
    "Bios is not a registered medical device. " +
        "Alerts are informational and do not constitute medical advice."

/**
 * Standard mmol/L config (WHO / IDF — fasting concern 5.6 mmol/L).
 * Used as the default for the vast majority of regions outside US/JP.
 */
internal fun mmolGlucoseDefaults() = defaultThresholds(
    glucoseInMmol = true,
    fastingGlucoseConcern = 5.6,
)

/**
 * Convenience builder for a "metric-system, mmol/L glucose, WHO defaults"
 * region — the shape that fits most LATAM, African, Middle-Eastern,
 * Asian and Pacific configs. Per-region overrides flow through optional
 * named arguments.
 */
internal fun standardRegion(
    regionCode: String,
    displayName: String,
    emergencyNumber: String?,
    regulatoryBody: String,
    indigenousFlagged: Boolean = false,
    tropicalDiseaseRelevant: Boolean = false,
    thresholds: ClinicalThresholds = mmolGlucoseDefaults(),
    disclaimer: String = defaultDisclaimer(),
    fhirProfileUrl: String? = null,
    reproductiveDataIsolation: Boolean = false,
    explicitCommunityConsent: Boolean = true,
    defaultRetentionDays: Int = 90,
) = RegionConfig(
    regionCode = regionCode,
    displayName = displayName,
    unitOverrides = emptyMap(),
    clinicalThresholds = thresholds,
    regulatory = RegulatoryConfig(
        reproductiveDataIsolation = reproductiveDataIsolation,
        defaultRetentionDays = defaultRetentionDays,
        explicitCommunityConsent = explicitCommunityConsent,
        fhirProfileUrl = fhirProfileUrl,
        regulatoryBody = regulatoryBody,
        alertDisclaimer = disclaimer,
    ),
    emergencyNumber = emergencyNumber,
    indigenousFlagged = indigenousFlagged,
    tropicalDiseaseRelevant = tropicalDiseaseRelevant,
)

// -- Unit conversion helpers --
// Shared by region configs that override metric→imperial unit display
// (currently only US). Defined here so per-continent files can reference
// them without redeclaring or pulling in [RegionConfigProvider].

internal fun celsiusToFahrenheit(c: Double): Double = c * 9.0 / 5.0 + 32.0
internal fun fahrenheitToCelsius(f: Double): Double = (f - 32.0) * 5.0 / 9.0
internal fun celsiusDeltaToFahrenheit(dc: Double): Double = dc * 9.0 / 5.0
internal fun fahrenheitDeltaToCelsius(df: Double): Double = df * 5.0 / 9.0
