package com.bios.app.config

/**
 * Europe region configs: UK, EU (umbrella fallback), and the EU-as-final-
 * fallback rules referenced by [RegionConfigProvider]. Individual EU
 * member-state configs are not enumerated yet — the EU umbrella applies
 * GDPR + EMA + ESC/ESH defaults that work for every member state.
 *
 * Emergency numbers verified against Wikipedia's
 * "List of emergency telephone numbers". The EU-wide 112 line is the
 * canonical number across all member states; 999 is the British line.
 */
internal object RegionConfigsEurope {

    val configs: Map<String, RegionConfig> = mapOf(
        "GB" to gb(),
        "EU" to eu(),
    )

    /** Reused by [RegionConfigProvider] as the EU-wide ultimate fallback. */
    val euFallback: RegionConfig = eu()

    private fun gb() = RegionConfig(
        regionCode = "GB",
        displayName = "United Kingdom",
        unitOverrides = emptyMap(),
        clinicalThresholds = defaultThresholds(
            glucoseInMmol = true,
            fastingGlucoseConcern = 5.5,  // NICE pre-diabetes
        ),
        regulatory = RegulatoryConfig(
            reproductiveDataIsolation = false,
            defaultRetentionDays = 90,
            explicitCommunityConsent = true,  // UK GDPR
            fhirProfileUrl = "https://fhir.hl7.org.uk",
            regulatoryBody = "MHRA",
            alertDisclaimer = "Bios is not a registered medical device. " +
                "Alerts are informational and do not constitute medical advice.",
        ),
        emergencyNumber = "999",  // 112 also accepted EU-wide
        climateZone = ClimateZone.TEMPERATE,
        defaultLatitudeDeg = 54.0,
        defaultElevationM = 80.0,
    )

    private fun eu() = RegionConfig(
        regionCode = "EU",
        displayName = "European Union",
        unitOverrides = emptyMap(),
        clinicalThresholds = defaultThresholds(
            glucoseInMmol = true,
            fastingGlucoseConcern = 5.6,  // IDF / ESC
        ),
        regulatory = RegulatoryConfig(
            reproductiveDataIsolation = false,
            defaultRetentionDays = 90,
            explicitCommunityConsent = true,  // GDPR
            fhirProfileUrl = null,
            regulatoryBody = "EMA",
            alertDisclaimer = "Bios is not a CE-marked medical device. " +
                "Alerts are informational and do not constitute medical advice.",
        ),
        emergencyNumber = "112",
        climateZone = ClimateZone.TEMPERATE,
        defaultLatitudeDeg = 50.5,
        defaultElevationM = 150.0,
    )
}
