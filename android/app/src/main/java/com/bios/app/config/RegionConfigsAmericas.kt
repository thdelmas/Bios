package com.bios.app.config

import com.bios.contracts.MetricType

/**
 * Americas region configs: anchor regions (US, CA), Canadian
 * Indigenous-territory variants, and Latin America from Mexico to Tierra
 * del Fuego.
 *
 * Emergency numbers verified against Wikipedia's
 * "List of emergency telephone numbers" plus per-country EMS references
 * (SAMU services in BR/CL/PE, 911 unifications across most of LATAM).
 * Where a country runs separate police and EMS lines, the EMS / ambulance
 * number is preferred since Bios alerts are vital-signs alerts.
 *
 * `indigenousFlagged = true` set for Canadian territories (Nunavut, NWT,
 * Yukon) — these regions sit under the First Nations principles of OCAP®
 * (Ownership, Control, Access, Possession) for Indigenous data
 * sovereignty. Cross-reference: docs/audits/INDIGENOUS_AMERICAS_POV.md
 * §2.5 ("es_MX, pt_BR silently substitute ESC/ESH cutoffs").
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
        // Caribbean + Central America (#196): tropical-flagged for dengue/chikungunya
        // endemicity. SV stays default to mirror PR #225's authoritative list.
        standardRegion("DO", "Dominican Republic", "911", "DIGEMAPS", tropicalDiseaseRelevant = true),
        standardRegion("JM", "Jamaica", "110", "MOHWJM", tropicalDiseaseRelevant = true),
        standardRegion("GT", "Guatemala", "122", "MSPAS", tropicalDiseaseRelevant = true),
        standardRegion("HN", "Honduras", "911", "ARSA", tropicalDiseaseRelevant = true),
        standardRegion("SV", "El Salvador", "911", "MINSAL"),
        standardRegion("NI", "Nicaragua", "118", "MINSA", tropicalDiseaseRelevant = true),
        standardRegion("CU", "Cuba", "185", "MINSAP", tropicalDiseaseRelevant = true),
        standardRegion("HT", "Haiti", "118", "MSPP", tropicalDiseaseRelevant = true),
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
        clinicalThresholds = defaultThresholds(
            glucoseInMmol = false,
            fastingGlucoseConcern = 100.0,
            hypertensionSystolic = 130,
            hypertensionDiastolic = 80,
            highFeverCelsius = 39.4,
        ),
        regulatory = RegulatoryConfig(
            reproductiveDataIsolation = true,
            defaultRetentionDays = 90,
            explicitCommunityConsent = true,
            fhirProfileUrl = "http://hl7.org/fhir/us/core",
            regulatoryBody = "FDA",
            alertDisclaimer = "Bios is not an FDA-cleared medical device. " +
                "Alerts are informational and do not constitute medical advice.",
        ),
        emergencyNumber = "911",
        climateZone = ClimateZone.TEMPERATE,
        defaultLatitudeDeg = 39.5,
        defaultElevationM = 250.0,
    )

    private fun ca() = RegionConfig(
        regionCode = "CA",
        displayName = "Canada",
        unitOverrides = emptyMap(),
        clinicalThresholds = mmolGlucoseDefaults(),
        regulatory = RegulatoryConfig(
            reproductiveDataIsolation = false,
            defaultRetentionDays = 90,
            explicitCommunityConsent = true,
            fhirProfileUrl = "https://www.infoway-inforoute.ca/fhir",
            regulatoryBody = "Health Canada",
            alertDisclaimer = "Bios is not a Health Canada licensed medical device. " +
                "Alerts are informational and do not constitute medical advice.",
        ),
        emergencyNumber = "911",
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
    // Glucose: mmol/L is the regional norm in most of LATAM clinical
    // labs, though mg/dL persists in some private-lab reports. Defaulting
    // to mmol/L matches WHO/IDF guidance and the Spanish-language EMS
    // pattern (most countries report glucose in mmol/L on official
    // forms). Per-country override possible if a survey shows otherwise.

    // Tropical-flagged regions activate TropicalDiseasePatterns (#196). CDC
    // endemicity maps for dengue / chikungunya / malaria converge on the
    // Americas tropics: Mexico through tropical South America, plus the
    // Caribbean. AR/CL/UY/CR stay default (subtropical / temperate).
    private fun mx() = standardRegion(
        regionCode = "MX", displayName = "Mexico",
        emergencyNumber = "911", regulatoryBody = "COFEPRIS",
        tropicalDiseaseRelevant = true,
    )

    private fun ar() = standardRegion(
        regionCode = "AR", displayName = "Argentina",
        emergencyNumber = "107", regulatoryBody = "ANMAT",
    )

    private fun br() = standardRegion(
        regionCode = "BR", displayName = "Brazil",
        emergencyNumber = "192", regulatoryBody = "ANVISA",
        tropicalDiseaseRelevant = true,
    )

    private fun cl() = standardRegion(
        regionCode = "CL", displayName = "Chile",
        emergencyNumber = "131", regulatoryBody = "ISP",
    )

    private fun co() = standardRegion(
        regionCode = "CO", displayName = "Colombia",
        emergencyNumber = "123", regulatoryBody = "INVIMA",
        tropicalDiseaseRelevant = true,
    )

    private fun pe() = standardRegion(
        regionCode = "PE", displayName = "Peru",
        emergencyNumber = "106", regulatoryBody = "DIGEMID",
        tropicalDiseaseRelevant = true,
    )

    private fun ve() = standardRegion(
        regionCode = "VE", displayName = "Venezuela",
        emergencyNumber = "171", regulatoryBody = "INHRR",
        tropicalDiseaseRelevant = true,
    )

    private fun ec() = standardRegion(
        regionCode = "EC", displayName = "Ecuador",
        emergencyNumber = "911", regulatoryBody = "ARCSA",
        tropicalDiseaseRelevant = true,
    )

    private fun bo() = standardRegion(
        regionCode = "BO", displayName = "Bolivia",
        emergencyNumber = "118", regulatoryBody = "AGEMED",
        tropicalDiseaseRelevant = true,
    )

    private fun uy() = standardRegion(
        regionCode = "UY", displayName = "Uruguay",
        emergencyNumber = "911", regulatoryBody = "MSP",
    )

    private fun py() = standardRegion(
        regionCode = "PY", displayName = "Paraguay",
        emergencyNumber = "911", regulatoryBody = "DINAVISA",
        tropicalDiseaseRelevant = true,
    )

    private fun cr() = standardRegion(
        regionCode = "CR", displayName = "Costa Rica",
        emergencyNumber = "911", regulatoryBody = "Ministerio de Salud",
    )

    private fun pa() = standardRegion(
        regionCode = "PA", displayName = "Panama",
        emergencyNumber = "911", regulatoryBody = "MINSA",
        tropicalDiseaseRelevant = true,
    )
}
