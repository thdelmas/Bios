package com.bios.app.config

/**
 * Europe region configs: UK, EU (umbrella fallback), and the EU-as-final-
 * fallback rules referenced by [RegionConfigProvider]. Individual EU
 * member-state configs are not enumerated yet — the EU umbrella applies
 * GDPR defaults that work for every member state.
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
        glucoseInMmol = true,
        regulatory = RegulatoryConfig(
            reproductiveDataIsolation = false,
            defaultRetentionDays = 90,
            fhirProfileUrl = "https://fhir.hl7.org.uk",
            regulatoryBody = "MHRA",
        ),
    )

    private fun eu() = RegionConfig(
        regionCode = "EU",
        displayName = "European Union",
        unitOverrides = emptyMap(),
        glucoseInMmol = true,
        regulatory = RegulatoryConfig(
            reproductiveDataIsolation = false,
            defaultRetentionDays = 90,
            fhirProfileUrl = null,
            regulatoryBody = "EMA",
        ),
    )
}
