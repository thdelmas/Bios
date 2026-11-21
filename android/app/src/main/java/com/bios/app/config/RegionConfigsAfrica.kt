package com.bios.app.config

/**
 * Africa region configs: Sub-Saharan + Maghreb / North Africa.
 *
 * Glucose unit: mmol/L is the usual unit on lab reports in the region.
 * Regulatory bodies: many countries have a national authority (e.g.
 * NAFDAC in NG, SAHPRA in ZA, EFDA in ET); when no authority is
 * verifiable, "Ministry of Health" or the WHO regional office is the
 * fallback. Cross-reference: docs/audits/AFRICAN_TRADITIONAL_POV.md.
 */
internal object RegionConfigsAfrica {

    val configs: Map<String, RegionConfig> = listOf(
        // -- Sub-Saharan --
        standardRegion("NG", "Nigeria", "NAFDAC"),
        standardRegion("KE", "Kenya", "PPB"),
        standardRegion("ZA", "South Africa", "SAHPRA"),
        standardRegion("GH", "Ghana", "FDA-GH"),
        standardRegion("ET", "Ethiopia", "EFDA"),
        standardRegion("TZ", "Tanzania", "TMDA"),
        standardRegion("UG", "Uganda", "NDA-UG"),
        standardRegion("MZ", "Mozambique", "ANARME"),
        standardRegion("SN", "Senegal", "DPM"),
        standardRegion("CI", "Côte d'Ivoire", "DPML"),
        standardRegion("CM", "Cameroon", "MoH"),
        standardRegion("AO", "Angola", "INAMED"),
        standardRegion("ZW", "Zimbabwe", "MCAZ"),
        standardRegion("BW", "Botswana", "BoMRA"),
        standardRegion("NA", "Namibia", "NMRC"),
        standardRegion("MW", "Malawi", "PMRA"),
        standardRegion("ZM", "Zambia", "ZAMRA"),
        standardRegion("RW", "Rwanda", "Rwanda FDA"),
        standardRegion("CD", "DR Congo", "ACOREP"),
        standardRegion("MG", "Madagascar", "Agence du Médicament"),

        // -- Maghreb / North Africa --
        standardRegion("DZ", "Algeria", "ANPP"),
        standardRegion("MA", "Morocco", "DMP"),
        standardRegion("TN", "Tunisia", "DPM-TN"),
        standardRegion("EG", "Egypt", "EDA"),
        standardRegion("LY", "Libya", "FDC-LY"),
    ).associateBy { it.regionCode }

    /** Reused by [RegionConfigProvider] as Africa's continental fallback. */
    val africaFallback: RegionConfig get() = configs.getValue("ZA")
}
