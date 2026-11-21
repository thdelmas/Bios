package com.bios.app.config

/**
 * Middle East region configs.
 *
 * Emergency numbers verified against Wikipedia's
 * "List of emergency telephone numbers". Numbers vary widely — Israel
 * uses 101 (Magen David Adom), UAE 998 (NAATC ambulance), Saudi Arabia
 * 997 (Red Crescent), Qatar 999 (HMAS), Iran 115 (EMS), Lebanon 140
 * (Red Cross EMS). 911 / 112 are usually accepted as international
 * roaming fallbacks in Gulf states even when not the documented
 * domestic number.
 *
 * Glucose unit: mmol/L is the regional clinical norm.
 */
internal object RegionConfigsMiddleEast {

    val configs: Map<String, RegionConfig> = listOf(
        standardRegion("IL", "Israel", "101", "Ministry of Health"),     // 101 MDA EMS
        standardRegion("TR", "Türkiye", "112", "TİTCK"),
        standardRegion("AE", "United Arab Emirates", "998", "DoH-AD"),   // 998 NAATC EMS
        standardRegion(
            regionCode = "SA",
            displayName = "Saudi Arabia",
            emergencyNumber = "997",                                     // 997 Saudi Red Crescent
            regulatoryBody = "SFDA",
        ),
        standardRegion(
            regionCode = "QA",
            displayName = "Qatar",
            emergencyNumber = "999",
            regulatoryBody = "MOPH",
        ),
        standardRegion("IR", "Iran", "115", "IFDA"),
        standardRegion(
            regionCode = "IQ",
            displayName = "Iraq",
            emergencyNumber = "122",
            regulatoryBody = "Ministry of Health",
        ),
        standardRegion("JO", "Jordan", "911", "JFDA"),
        standardRegion(
            regionCode = "LB",
            displayName = "Lebanon",
            emergencyNumber = "140",                                     // Lebanese Red Cross EMS
            regulatoryBody = "Ministry of Public Health",
        ),
        standardRegion(
            regionCode = "SY",
            displayName = "Syria",
            emergencyNumber = "110",                                     // Syrian Arab Red Crescent
            regulatoryBody = "Ministry of Health",
        ),
        standardRegion(
            regionCode = "YE",
            displayName = "Yemen",
            emergencyNumber = "119",
            regulatoryBody = "SBDMA",
        ),
    ).associateBy { it.regionCode }
}
