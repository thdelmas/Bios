package com.bios.app.config

/**
 * Africa region configs: Sub-Saharan + Maghreb / North Africa.
 *
 * Emergency numbers verified against Wikipedia's
 * "List of emergency telephone numbers" plus per-country EMS references.
 * Many African countries operate parallel police and EMS lines without
 * a unified number — the EMS / ambulance line is preferred where it
 * exists (10177 in SA, 999 in KE / UG / ZW / BW). Where the EMS service
 * is unreliable or non-existent at national scale, the closest practical
 * dispatch number is used and the owner-facing UI should still display
 * "your local emergency services" as the fallback phrasing.
 *
 * Glucose unit: mmol/L is the African clinical norm (WHO-aligned).
 * Regulatory bodies: many countries have a national authority (e.g.
 * NAFDAC in NG, SAHPRA in ZA, EFDA in ET); when no authority is
 * verifiable, "Ministry of Health" or the WHO regional office is the
 * fallback. Cross-reference: docs/audits/AFRICAN_TRADITIONAL_POV.md.
 */
internal object RegionConfigsAfrica {

    val configs: Map<String, RegionConfig> = listOf(
        // -- Sub-Saharan --
        standardRegion("NG", "Nigeria", "112", "NAFDAC"),
        standardRegion("KE", "Kenya", "999", "PPB"),
        standardRegion("ZA", "South Africa", "10177", "SAHPRA"),
        standardRegion("GH", "Ghana", "193", "FDA-GH"),
        standardRegion("ET", "Ethiopia", "907", "EFDA"),
        standardRegion("TZ", "Tanzania", "114", "TMDA"),
        standardRegion("UG", "Uganda", "999", "NDA-UG"),
        standardRegion("MZ", "Mozambique", "117", "ANARME"),
        standardRegion("SN", "Senegal", "15", "DPM"),
        standardRegion("CI", "Côte d'Ivoire", "185", "DPML"),
        standardRegion("CM", "Cameroon", "119", "MoH"),
        standardRegion("AO", "Angola", "115", "INAMED"),
        standardRegion("ZW", "Zimbabwe", "999", "MCAZ"),
        standardRegion("BW", "Botswana", "911", "BoMRA"),
        standardRegion("NA", "Namibia", "211", "NMRC"),
        standardRegion("MW", "Malawi", "998", "PMRA"),
        standardRegion("ZM", "Zambia", "902", "ZAMRA"),
        standardRegion("RW", "Rwanda", "912", "Rwanda FDA"),
        standardRegion("CD", "DR Congo", "118", "ACOREP"),
        standardRegion("MG", "Madagascar", "124", "Agence du Médicament"),

        // -- Maghreb / North Africa --
        standardRegion("DZ", "Algeria", "14", "ANPP"),
        standardRegion("MA", "Morocco", "15", "DMP"),
        standardRegion("TN", "Tunisia", "190", "DPM-TN"),
        standardRegion("EG", "Egypt", "123", "EDA"),
        standardRegion("LY", "Libya", "193", "FDC-LY"),
    ).associateBy { it.regionCode }

    /** Reused by [RegionConfigProvider] as Africa's continental fallback. */
    val africaFallback: RegionConfig get() = configs.getValue("ZA")
}
