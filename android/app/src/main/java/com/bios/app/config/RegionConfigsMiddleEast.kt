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
 *
 * Saudi-specific text uses Modern Standard Arabic for the disclaimer
 * since MSA reads cleanly across the GCC; non-GCC Arabic-speaking
 * countries use the same MSA disclaimer.
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
            disclaimer = ARABIC_DISCLAIMER,
        ),
        standardRegion(
            regionCode = "QA",
            displayName = "Qatar",
            emergencyNumber = "999",
            regulatoryBody = "MOPH",
            disclaimer = ARABIC_DISCLAIMER,
        ),
        standardRegion("IR", "Iran", "115", "IFDA"),
        standardRegion(
            regionCode = "IQ",
            displayName = "Iraq",
            emergencyNumber = "122",
            regulatoryBody = "Ministry of Health",
            disclaimer = ARABIC_DISCLAIMER,
        ),
        standardRegion("JO", "Jordan", "911", "JFDA"),
        standardRegion(
            regionCode = "LB",
            displayName = "Lebanon",
            emergencyNumber = "140",                                     // Lebanese Red Cross EMS
            regulatoryBody = "Ministry of Public Health",
            disclaimer = ARABIC_DISCLAIMER,
        ),
        standardRegion(
            regionCode = "SY",
            displayName = "Syria",
            emergencyNumber = "110",                                     // Syrian Arab Red Crescent
            regulatoryBody = "Ministry of Health",
            disclaimer = ARABIC_DISCLAIMER,
        ),
        standardRegion(
            regionCode = "YE",
            displayName = "Yemen",
            emergencyNumber = "119",
            regulatoryBody = "SBDMA",
            disclaimer = ARABIC_DISCLAIMER,
        ),
    ).associateBy { it.regionCode }

    /** Arabic-language disclaimer reused across GCC + Mashriq configs. */
    private const val ARABIC_DISCLAIMER =
        "Bios ليس جهازًا طبيًا مسجلًا. التنبيهات لأغراض إعلامية ولا تشكل نصيحة طبية."
}
