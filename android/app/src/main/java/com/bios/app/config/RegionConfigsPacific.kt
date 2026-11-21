package com.bios.app.config

/**
 * Pacific region configs: Australia (anchor — flagged for NACCHO data
 * sovereignty), Aotearoa New Zealand (flagged for Te Mana Raraunga),
 * Melanesia, Polynesia, and Micronesia.
 *
 * Indigenous data sovereignty flags:
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
        standardRegion("FJ", "Fiji", "MoHMS", indigenousFlagged = true),
        standardRegion("PG", "Papua New Guinea", "NDOH", indigenousFlagged = true),
        standardRegion("SB", "Solomon Islands", "MHMS", indigenousFlagged = true),
        standardRegion("VU", "Vanuatu", "MoH", indigenousFlagged = true),
        standardRegion("NC", "New Caledonia", "ARS", indigenousFlagged = true),
        // Polynesia
        standardRegion("TO", "Tonga", "MoH", indigenousFlagged = true),
        standardRegion("WS", "Samoa", "MoH", indigenousFlagged = true),
        standardRegion("CK", "Cook Islands", "MoH", indigenousFlagged = true),
        standardRegion("NU", "Niue", "Niue Health", indigenousFlagged = true),
        standardRegion("PF", "French Polynesia", "ARS", indigenousFlagged = true),
        standardRegion("TV", "Tuvalu", "MoH", indigenousFlagged = true),
        // Micronesia
        standardRegion("KI", "Kiribati", "MHMS", indigenousFlagged = true),
        standardRegion("NR", "Nauru", "MoH", indigenousFlagged = true),
        standardRegion("MH", "Marshall Islands", "MoHHS", indigenousFlagged = true),
        standardRegion("PW", "Palau", "MoH", indigenousFlagged = true),
        standardRegion("FM", "Micronesia", "DHSA", indigenousFlagged = true),
    ).associateBy { it.regionCode }

    /** Reused by [RegionConfigProvider] as the Pacific continental fallback. */
    val pacificFallback: RegionConfig get() = configs.getValue("AU")

    private fun au() = RegionConfig(
        regionCode = "AU",
        displayName = "Australia",
        unitOverrides = emptyMap(),
        glucoseInMmol = true,
        regulatory = RegulatoryConfig(
            reproductiveDataIsolation = false,
            defaultRetentionDays = 90,
            fhirProfileUrl = "http://hl7.org.au/fhir",
            regulatoryBody = "TGA",
        ),
        indigenousFlagged = true,                                        // NACCHO
    )

    private fun nz() = RegionConfig(
        regionCode = "NZ",
        displayName = "Aotearoa New Zealand",
        unitOverrides = emptyMap(),
        glucoseInMmol = true,
        regulatory = RegulatoryConfig(
            reproductiveDataIsolation = false,
            defaultRetentionDays = 90,
            fhirProfileUrl = "https://fhir.org.nz",
            regulatoryBody = "Medsafe",
        ),
        indigenousFlagged = true,                                        // Te Mana Raraunga
    )
}
