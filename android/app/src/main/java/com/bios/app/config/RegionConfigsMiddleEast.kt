package com.bios.app.config

/**
 * Middle East region configs.
 *
 * Glucose unit: mmol/L is the usual unit on lab reports in the region.
 */
internal object RegionConfigsMiddleEast {

    val configs: Map<String, RegionConfig> = listOf(
        standardRegion("IL", "Israel", "Ministry of Health"),
        standardRegion("TR", "Türkiye", "TİTCK"),
        standardRegion("AE", "United Arab Emirates", "DoH-AD"),
        standardRegion(
            regionCode = "SA",
            displayName = "Saudi Arabia",
            regulatoryBody = "SFDA",
        ),
        standardRegion(
            regionCode = "QA",
            displayName = "Qatar",
            regulatoryBody = "MOPH",
        ),
        standardRegion("IR", "Iran", "IFDA"),
        standardRegion(
            regionCode = "IQ",
            displayName = "Iraq",
            regulatoryBody = "Ministry of Health",
        ),
        standardRegion("JO", "Jordan", "JFDA"),
        standardRegion(
            regionCode = "LB",
            displayName = "Lebanon",
            regulatoryBody = "Ministry of Public Health",
        ),
        standardRegion(
            regionCode = "SY",
            displayName = "Syria",
            regulatoryBody = "Ministry of Health",
        ),
        standardRegion(
            regionCode = "YE",
            displayName = "Yemen",
            regulatoryBody = "SBDMA",
        ),
    ).associateBy { it.regionCode }
}
