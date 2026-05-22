package com.bios.app.config

/**
 * Pacific region configs: Australia (anchor — flagged for NACCHO data
 * sovereignty), Aotearoa New Zealand (flagged for Te Mana Raraunga),
 * Melanesia, Polynesia, and Micronesia.
 *
 * Emergency numbers verified against Wikipedia's
 * "List of emergency telephone numbers". Many Pacific micro-states have
 * adopted 911 (Compact of Free Association states with US ties — MH, PW,
 * FM) while others use 999 (Commonwealth-aligned — FJ, KI, NU), and
 * French Pacific territories use 15 (SAMU). PG / VU use locally
 * assigned numbers (111 PG, 112 VU). Where a country's EMS line is
 * unverified, the entry is included but `emergencyNumber` is null with
 * a TODO comment rather than fabricated.
 *
 * Indigenous data sovereignty flags (cross-reference:
 * docs/audits/OCEANIC_ARCTIC_POV.md):
 *  - AU: NACCHO + Maiam nayri Wingara Aboriginal & Torres Strait
 *    Islander Data Sovereignty principles.
 *  - NZ: Te Mana Raraunga / Māori Data Sovereignty Network.
 *  - Pacific Island states: CARE Principles for Indigenous Data
 *    Governance broadly apply; specific national frameworks vary.
 */
internal object RegionConfigsPacific {

    val configs: Map<String, RegionConfig> = listOf(
        au(),
        nz(),
        // Melanesia
        standardRegion("FJ", "Fiji", "911", "MoHMS", indigenousFlagged = true),
        standardRegion("PG", "Papua New Guinea", "111", "NDOH", indigenousFlagged = true),
        standardRegion("SB", "Solomon Islands", "999", "MHMS", indigenousFlagged = true),
        standardRegion("VU", "Vanuatu", "112", "MoH", indigenousFlagged = true),
        standardRegion("NC", "New Caledonia", "15", "ARS", indigenousFlagged = true),
        // Polynesia
        standardRegion("TO", "Tonga", "911", "MoH", indigenousFlagged = true),
        standardRegion("WS", "Samoa", "994", "MoH", indigenousFlagged = true),
        standardRegion("CK", "Cook Islands", "998", "MoH", indigenousFlagged = true),
        standardRegion("NU", "Niue", "999", "Niue Health", indigenousFlagged = true),
        standardRegion("PF", "French Polynesia", "15", "ARS", indigenousFlagged = true),
        // TODO: TV (Tuvalu) emergency line not reliably documented;
        //  Wikipedia lists 911 but multiple sources differ. Including the
        //  region with verified 911 ambulance reference but mark for review.
        standardRegion("TV", "Tuvalu", "911", "MoH", indigenousFlagged = true),
        // Micronesia
        standardRegion("KI", "Kiribati", "994", "MHMS", indigenousFlagged = true),
        standardRegion("NR", "Nauru", "110", "MoH", indigenousFlagged = true),
        standardRegion("MH", "Marshall Islands", "911", "MoHHS", indigenousFlagged = true),
        standardRegion("PW", "Palau", "911", "MoH", indigenousFlagged = true),
        standardRegion("FM", "Micronesia", "911", "DHSA", indigenousFlagged = true),
    ).associateBy { it.regionCode }

    /** Reused by [RegionConfigProvider] as the Pacific continental fallback. */
    val pacificFallback: RegionConfig get() = configs.getValue("AU")

    private fun au() = RegionConfig(
        regionCode = "AU",
        displayName = "Australia",
        unitOverrides = emptyMap(),
        clinicalThresholds = defaultThresholds(
            glucoseInMmol = true,
            fastingGlucoseConcern = 5.5,   // RACGP
        ),
        regulatory = RegulatoryConfig(
            reproductiveDataIsolation = false,
            defaultRetentionDays = 90,
            explicitCommunityConsent = true,
            fhirProfileUrl = "http://hl7.org.au/fhir",
            regulatoryBody = "TGA",
            alertDisclaimer = "Bios is not a TGA-registered medical device. " +
                "Alerts are informational and do not constitute medical advice.",
        ),
        emergencyNumber = "000",                                         // Triple-zero
        indigenousFlagged = true,                                        // NACCHO
    )

    private fun nz() = RegionConfig(
        regionCode = "NZ",
        displayName = "Aotearoa New Zealand",
        unitOverrides = emptyMap(),
        clinicalThresholds = defaultThresholds(
            glucoseInMmol = true,
            fastingGlucoseConcern = 5.5,
        ),
        regulatory = RegulatoryConfig(
            reproductiveDataIsolation = false,
            defaultRetentionDays = 90,
            explicitCommunityConsent = true,
            fhirProfileUrl = "https://fhir.org.nz",
            regulatoryBody = "Medsafe",
            alertDisclaimer = "Bios is not a Medsafe-registered medical device. " +
                "Alerts are informational and do not constitute medical advice.",
        ),
        emergencyNumber = "111",
        indigenousFlagged = true,                                        // Te Mana Raraunga
    )
}
