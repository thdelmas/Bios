package com.bios.app.config

import com.bios.contracts.MetricType

/**
 * Americas region configs: anchor regions (US, CA), Canadian
 * Indigenous-territory variants, and Latin America from Mexico to Tierra
 * del Fuego.
 *
 * `indigenousFlagged = true` set for Canadian territories (Nunavut, NWT,
 * Yukon) — these regions sit under the First Nations principles of OCAP®
 * (Ownership, Control, Access, Possession) for Indigenous data
 * sovereignty.
 */
internal object RegionConfigsAmericas {

    val configs: Map<String, RegionConfig> = listOf(
        us(),
        ca(),
        // Canadian Indigenous-majority territories — OCAP® applies
        canadianTerritory("CA-NU", "Nunavut"),
        canadianTerritory("CA-NT", "Northwest Territories"),
        canadianTerritory("CA-YT", "Yukon"),
        // Latin America
        mx(),
        ar(),
        br(),
        cl(),
        co(),
        pe(),
        ve(),
        ec(),
        bo(),
        uy(),
        py(),
        cr(),
        pa(),
        // Caribbean + Central America
        standardRegion("DO", "Dominican Republic", "DIGEMAPS"),
        standardRegion("JM", "Jamaica", "MOHWJM"),
        standardRegion("GT", "Guatemala", "MSPAS"),
        standardRegion("HN", "Honduras", "ARSA"),
        standardRegion("SV", "El Salvador", "MINSAL"),
        standardRegion("NI", "Nicaragua", "MINSA"),
        standardRegion("CU", "Cuba", "MINSAP"),
        standardRegion("HT", "Haiti", "MSPP"),
    ).associateBy { it.regionCode }

    // -- Anchor regions --

    private fun us() = RegionConfig(
        regionCode = "US",
        displayName = "United States",
        unitOverrides = mapOf(
            MetricType.SKIN_TEMPERATURE to UnitDisplay("°F", ::celsiusToFahrenheit, ::fahrenheitToCelsius),
            MetricType.SKIN_TEMPERATURE_DEVIATION to UnitDisplay(
                "Δ°F", ::celsiusDeltaToFahrenheit, ::fahrenheitDeltaToCelsius,
            ),
            MetricType.BASAL_BODY_TEMPERATURE to UnitDisplay(
                "°F", ::celsiusToFahrenheit, ::fahrenheitToCelsius,
            ),
        ),
        glucoseInMmol = false,
        regulatory = RegulatoryConfig(
            reproductiveDataIsolation = true,
            defaultRetentionDays = 90,
            explicitCommunityConsent = true,
            fhirProfileUrl = "http://hl7.org/fhir/us/core",
            regulatoryBody = "FDA",
        ),
        climateZone = ClimateZone.TEMPERATE,
        defaultLatitudeDeg = 39.5,
        defaultElevationM = 250.0,
    )

    private fun ca() = RegionConfig(
        regionCode = "CA",
        displayName = "Canada",
        unitOverrides = emptyMap(),
        glucoseInMmol = true,
        regulatory = RegulatoryConfig(
            reproductiveDataIsolation = false,
            defaultRetentionDays = 90,
            explicitCommunityConsent = true,
            fhirProfileUrl = "https://www.infoway-inforoute.ca/fhir",
            regulatoryBody = "Health Canada",
        ),
        climateZone = ClimateZone.COLD,
        defaultLatitudeDeg = 56.0,
        defaultElevationM = 200.0,
    )

    /**
     * Canadian territory variant inheriting the federal CA config but
     * flagged for Indigenous data sovereignty (OCAP®). Used when the
     * device locale exposes a territory subdivision.
     */
    private fun canadianTerritory(code: String, name: String) = ca().copy(
        regionCode = code,
        displayName = "Canada — $name",
        indigenousFlagged = true,
    )

    // -- Latin America (per-country tuning) --
    // Glucose is displayed in mmol/L by default; mg/dL persists on some
    // private-lab reports, and a per-country override is possible.

    private fun mx() = standardRegion(
        regionCode = "MX", displayName = "Mexico",
        regulatoryBody = "COFEPRIS",
    )

    private fun ar() = standardRegion(
        regionCode = "AR", displayName = "Argentina",
        regulatoryBody = "ANMAT",
    )

    private fun br() = standardRegion(
        regionCode = "BR", displayName = "Brazil",
        regulatoryBody = "ANVISA",
    )

    private fun cl() = standardRegion(
        regionCode = "CL", displayName = "Chile",
        regulatoryBody = "ISP",
    )

    private fun co() = standardRegion(
        regionCode = "CO", displayName = "Colombia",
        regulatoryBody = "INVIMA",
    )

    private fun pe() = standardRegion(
        regionCode = "PE", displayName = "Peru",
        regulatoryBody = "DIGEMID",
    )

    private fun ve() = standardRegion(
        regionCode = "VE", displayName = "Venezuela",
        regulatoryBody = "INHRR",
    )

    private fun ec() = standardRegion(
        regionCode = "EC", displayName = "Ecuador",
        regulatoryBody = "ARCSA",
    )

    private fun bo() = standardRegion(
        regionCode = "BO", displayName = "Bolivia",
        regulatoryBody = "AGEMED",
    )

    private fun uy() = standardRegion(
        regionCode = "UY", displayName = "Uruguay",
        regulatoryBody = "MSP",
    )

    private fun py() = standardRegion(
        regionCode = "PY", displayName = "Paraguay",
        regulatoryBody = "DINAVISA",
    )

    private fun cr() = standardRegion(
        regionCode = "CR", displayName = "Costa Rica",
        regulatoryBody = "Ministerio de Salud",
    )

    private fun pa() = standardRegion(
        regionCode = "PA", displayName = "Panama",
        regulatoryBody = "MINSA",
    )
}
