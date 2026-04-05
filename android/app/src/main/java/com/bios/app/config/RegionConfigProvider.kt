package com.bios.app.config

import com.bios.app.model.MetricType
import java.util.Locale

/**
 * Provides region-specific configuration based on device locale.
 * New regions require only a new config entry — no code changes.
 */
object RegionConfigProvider {

    private val configs: Map<String, RegionConfig> = mapOf(
        "US" to usConfig(),
        "GB" to gbConfig(),
        "EU" to euConfig(),
        "CA" to caConfig(),
        "AU" to auConfig(),
        "JP" to jpConfig()
    )

    /** Default config used when no region-specific config exists. */
    private val defaultConfig = euConfig()

    /**
     * Returns the config for the device's current locale.
     * Falls back to EU config (metric, conservative privacy).
     */
    fun forCurrentLocale(): RegionConfig {
        val country = Locale.getDefault().country
        return configs[country] ?: defaultConfig
    }

    /**
     * Returns config for a specific region code (ISO 3166-1 alpha-2).
     */
    fun forRegion(regionCode: String): RegionConfig {
        return configs[regionCode] ?: defaultConfig
    }

    /**
     * All supported region codes.
     */
    fun supportedRegions(): Set<String> = configs.keys

    // -- Region definitions --

    private fun usConfig() = RegionConfig(
        regionCode = "US",
        displayName = "United States",
        unitOverrides = mapOf(
            MetricType.SKIN_TEMPERATURE to UnitDisplay("°F", ::celsiusToFahrenheit, ::fahrenheitToCelsius),
            MetricType.SKIN_TEMPERATURE_DEVIATION to UnitDisplay("Δ°F", ::celsiusDeltaToFahrenheit, ::fahrenheitDeltaToCelsius),
            MetricType.BASAL_BODY_TEMPERATURE to UnitDisplay("°F", ::celsiusToFahrenheit, ::fahrenheitToCelsius)
        ),
        clinicalThresholds = ClinicalThresholds(
            spo2ConcernThreshold = 95.0,
            spo2UrgentThreshold = 92.0,
            feverDeltaCelsius = 0.5,
            highFeverCelsius = 39.4,
            tachycardiaBpm = 100,
            bradycardiaBpm = 50,
            glucoseInMmol = false,
            fastingGlucoseConcern = 100.0,  // mg/dL (ADA pre-diabetes threshold)
            hypertensionSystolic = 130,     // ACC/AHA 2017 guideline
            hypertensionDiastolic = 80
        ),
        regulatory = RegulatoryConfig(
            reproductiveDataIsolation = true,   // post-Dobbs protection
            defaultRetentionDays = 90,
            explicitCommunityConsent = true,
            fhirProfileUrl = "http://hl7.org/fhir/us/core",
            regulatoryBody = "FDA",
            alertDisclaimer = "Bios is not an FDA-cleared medical device. Alerts are informational and do not constitute medical advice."
        )
    )

    private fun gbConfig() = RegionConfig(
        regionCode = "GB",
        displayName = "United Kingdom",
        unitOverrides = mapOf(
            // UK uses Celsius for body temp but stones/lbs for weight — no weight metric in Bios yet
        ),
        clinicalThresholds = ClinicalThresholds(
            spo2ConcernThreshold = 95.0,
            spo2UrgentThreshold = 92.0,
            feverDeltaCelsius = 0.5,
            highFeverCelsius = 39.0,
            tachycardiaBpm = 100,
            bradycardiaBpm = 50,
            glucoseInMmol = true,
            fastingGlucoseConcern = 5.5,    // mmol/L (NICE pre-diabetes threshold)
            hypertensionSystolic = 140,     // NICE guideline (higher than US)
            hypertensionDiastolic = 90
        ),
        regulatory = RegulatoryConfig(
            reproductiveDataIsolation = false,
            defaultRetentionDays = 90,
            explicitCommunityConsent = true,  // UK GDPR
            fhirProfileUrl = "https://fhir.hl7.org.uk",
            regulatoryBody = "MHRA",
            alertDisclaimer = "Bios is not a registered medical device. Alerts are informational and do not constitute medical advice."
        )
    )

    private fun euConfig() = RegionConfig(
        regionCode = "EU",
        displayName = "European Union",
        unitOverrides = emptyMap(),  // all metrics already SI/metric
        clinicalThresholds = ClinicalThresholds(
            spo2ConcernThreshold = 95.0,
            spo2UrgentThreshold = 92.0,
            feverDeltaCelsius = 0.5,
            highFeverCelsius = 39.0,
            tachycardiaBpm = 100,
            bradycardiaBpm = 50,
            glucoseInMmol = true,
            fastingGlucoseConcern = 5.6,    // mmol/L (IDF threshold)
            hypertensionSystolic = 140,     // ESC/ESH guideline
            hypertensionDiastolic = 90
        ),
        regulatory = RegulatoryConfig(
            reproductiveDataIsolation = false,
            defaultRetentionDays = 90,
            explicitCommunityConsent = true,  // GDPR
            fhirProfileUrl = null,            // varies by country
            regulatoryBody = "EMA",
            alertDisclaimer = "Bios is not a CE-marked medical device. Alerts are informational and do not constitute medical advice."
        )
    )

    private fun caConfig() = RegionConfig(
        regionCode = "CA",
        displayName = "Canada",
        unitOverrides = mapOf(
            // Canada uses Celsius but sometimes Fahrenheit informally — stick with Celsius
        ),
        clinicalThresholds = ClinicalThresholds(
            spo2ConcernThreshold = 95.0,
            spo2UrgentThreshold = 92.0,
            feverDeltaCelsius = 0.5,
            highFeverCelsius = 39.0,
            tachycardiaBpm = 100,
            bradycardiaBpm = 50,
            glucoseInMmol = true,
            fastingGlucoseConcern = 5.6,    // mmol/L (Diabetes Canada)
            hypertensionSystolic = 140,     // Hypertension Canada
            hypertensionDiastolic = 90
        ),
        regulatory = RegulatoryConfig(
            reproductiveDataIsolation = false,
            defaultRetentionDays = 90,
            explicitCommunityConsent = true,  // PIPEDA
            fhirProfileUrl = "https://www.infoway-inforoute.ca/fhir",
            regulatoryBody = "Health Canada",
            alertDisclaimer = "Bios is not a Health Canada licensed medical device. Alerts are informational and do not constitute medical advice."
        )
    )

    private fun auConfig() = RegionConfig(
        regionCode = "AU",
        displayName = "Australia",
        unitOverrides = emptyMap(),
        clinicalThresholds = ClinicalThresholds(
            spo2ConcernThreshold = 95.0,
            spo2UrgentThreshold = 92.0,
            feverDeltaCelsius = 0.5,
            highFeverCelsius = 39.0,
            tachycardiaBpm = 100,
            bradycardiaBpm = 50,
            glucoseInMmol = true,
            fastingGlucoseConcern = 5.5,    // mmol/L (RACGP)
            hypertensionSystolic = 140,
            hypertensionDiastolic = 90
        ),
        regulatory = RegulatoryConfig(
            reproductiveDataIsolation = false,
            defaultRetentionDays = 90,
            explicitCommunityConsent = true,
            fhirProfileUrl = "http://hl7.org.au/fhir",
            regulatoryBody = "TGA",
            alertDisclaimer = "Bios is not a TGA-registered medical device. Alerts are informational and do not constitute medical advice."
        )
    )

    private fun jpConfig() = RegionConfig(
        regionCode = "JP",
        displayName = "Japan",
        unitOverrides = emptyMap(),
        clinicalThresholds = ClinicalThresholds(
            spo2ConcernThreshold = 96.0,    // Japanese guidelines slightly higher
            spo2UrgentThreshold = 93.0,
            feverDeltaCelsius = 0.5,
            highFeverCelsius = 38.5,         // Japanese fever classification lower
            tachycardiaBpm = 100,
            bradycardiaBpm = 50,
            glucoseInMmol = false,
            fastingGlucoseConcern = 110.0,   // mg/dL (JDS threshold)
            hypertensionSystolic = 140,      // JSH guideline
            hypertensionDiastolic = 90
        ),
        regulatory = RegulatoryConfig(
            reproductiveDataIsolation = false,
            defaultRetentionDays = 60,       // shorter default per APPI
            explicitCommunityConsent = true,  // APPI
            fhirProfileUrl = "http://jpfhir.jp",
            regulatoryBody = "PMDA",
            alertDisclaimer = "Biosは医療機器ではありません。アラートは情報提供を目的としており、医学的助言ではありません。"
        )
    )

    // -- Unit conversion helpers --

    private fun celsiusToFahrenheit(c: Double): Double = c * 9.0 / 5.0 + 32.0
    private fun fahrenheitToCelsius(f: Double): Double = (f - 32.0) * 5.0 / 9.0
    private fun celsiusDeltaToFahrenheit(dc: Double): Double = dc * 9.0 / 5.0
    private fun fahrenheitDeltaToCelsius(df: Double): Double = df * 5.0 / 9.0
}
