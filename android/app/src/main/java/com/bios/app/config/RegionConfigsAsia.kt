package com.bios.app.config

/**
 * Asia region configs: Japan (anchor), East Asia, South Asia, South-East
 * Asia, and Central Asia.
 *
 * Emergency numbers verified against Wikipedia's
 * "List of emergency telephone numbers" with cross-reference for EMS-
 * specific lines (China 120, Korea 119, India 108, Thailand 1669,
 * Indonesia 119 / 118, Philippines 911, Vietnam 115, Pakistan 115).
 * Where the ambulance line differs from the general emergency line, the
 * EMS / ambulance number is preferred for vital-signs alerts.
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
        standardRegion("CN", "China", "120", "NMPA"),          // 120 EMS
        standardRegion("KR", "South Korea", "119", "MFDS"),
        standardRegion("HK", "Hong Kong", "999", "DH"),
        standardRegion("TW", "Taiwan", "119", "TFDA"),
        standardRegion("MN", "Mongolia", "103", "DRA"),
        // South Asia
        standardRegion("IN", "India", "108", "CDSCO"),         // 108 unified EMS
        standardRegion("PK", "Pakistan", "115", "DRAP"),       // 115 Edhi ambulance
        standardRegion("BD", "Bangladesh", "199", "DGDA"),
        standardRegion("LK", "Sri Lanka", "1990", "NMRA"),     // Suwa Seriya
        standardRegion("NP", "Nepal", "102", "DDA"),
        standardRegion("BT", "Bhutan", "112", "DRA"),
        // South-East Asia
        standardRegion("VN", "Vietnam", "115", "DAV"),
        standardRegion("TH", "Thailand", "1669", "Thai FDA"),  // Narenthorn EMS
        standardRegion("MY", "Malaysia", "999", "NPRA"),
        standardRegion("SG", "Singapore", "995", "HSA"),
        standardRegion("ID", "Indonesia", "119", "BPOM"),
        standardRegion("PH", "Philippines", "911", "FDA-PH"),
        standardRegion("MM", "Myanmar", "192", "FDA-MM"),
        standardRegion("KH", "Cambodia", "119", "DDF"),
        standardRegion("LA", "Laos", "1623", "FDD"),
        standardRegion("BN", "Brunei", "991", "MOH"),
        // Central Asia
        standardRegion("KZ", "Kazakhstan", "103", "NCEHS"),
        standardRegion("UZ", "Uzbekistan", "103", "Pharmacy Agency"),
    ).associateBy { it.regionCode }

    /** Reused by [RegionConfigProvider] as Asia's continental fallback. */
    val asiaFallback: RegionConfig get() = configs.getValue("JP")

    private fun jp() = RegionConfig(
        regionCode = "JP",
        displayName = "Japan",
        unitOverrides = emptyMap(),
        clinicalThresholds = defaultThresholds(
            glucoseInMmol = false,
            fastingGlucoseConcern = 110.0,   // JDS
            spo2ConcernThreshold = 96.0,
            spo2UrgentThreshold = 93.0,
            highFeverCelsius = 38.5,
        ),
        regulatory = RegulatoryConfig(
            reproductiveDataIsolation = false,
            defaultRetentionDays = 60,
            explicitCommunityConsent = true,  // APPI
            fhirProfileUrl = "http://jpfhir.jp",
            regulatoryBody = "PMDA",
            alertDisclaimer = "Biosは医療機器ではありません。アラートは情報提供を目的としており、医学的助言ではありません。",
        ),
        emergencyNumber = "119",
    )
}
