package com.bios.app.config

/**
 * Asia region configs: Japan (anchor), East Asia, South Asia, South-East
 * Asia, and Central Asia.
 *
 * Glucose unit defaults: most of Asia clinically reports glucose in
 * mmol/L (China, India, Korea, ASEAN labs), with the notable exception
 * of Japan (mg/dL per JDS). South-Asian and SE-Asian configs inherit
 * mmol/L; per-country evidence can override.
 */
internal object RegionConfigsAsia {

    val configs: Map<String, RegionConfig> = listOf(
        jp(),
        // East Asia
        standardRegion("CN", "China", "NMPA"),
        standardRegion("KR", "South Korea", "MFDS"),
        standardRegion("HK", "Hong Kong", "DH"),
        standardRegion("TW", "Taiwan", "TFDA"),
        standardRegion("MN", "Mongolia", "DRA"),
        // South Asia
        standardRegion("IN", "India", "CDSCO"),
        standardRegion("PK", "Pakistan", "DRAP"),
        standardRegion("BD", "Bangladesh", "DGDA"),
        standardRegion("LK", "Sri Lanka", "NMRA"),
        standardRegion("NP", "Nepal", "DDA"),
        standardRegion("BT", "Bhutan", "DRA"),
        // South-East Asia
        standardRegion("VN", "Vietnam", "DAV"),
        standardRegion("TH", "Thailand", "Thai FDA"),
        standardRegion("MY", "Malaysia", "NPRA"),
        standardRegion("SG", "Singapore", "HSA"),
        standardRegion("ID", "Indonesia", "BPOM"),
        standardRegion("PH", "Philippines", "FDA-PH"),
        standardRegion("MM", "Myanmar", "FDA-MM"),
        standardRegion("KH", "Cambodia", "DDF"),
        standardRegion("LA", "Laos", "FDD"),
        standardRegion("BN", "Brunei", "MOH"),
        // Central Asia
        standardRegion("KZ", "Kazakhstan", "NCEHS"),
        standardRegion("UZ", "Uzbekistan", "Pharmacy Agency"),
    ).associateBy { it.regionCode }

    /** Reused by [RegionConfigProvider] as Asia's continental fallback. */
    val asiaFallback: RegionConfig get() = configs.getValue("JP")

    private fun jp() = RegionConfig(
        regionCode = "JP",
        displayName = "Japan",
        unitOverrides = emptyMap(),
        glucoseInMmol = false,
        regulatory = RegulatoryConfig(
            reproductiveDataIsolation = false,
            defaultRetentionDays = 60,
            fhirProfileUrl = "http://jpfhir.jp",
            regulatoryBody = "PMDA",
        ),
    )
}
