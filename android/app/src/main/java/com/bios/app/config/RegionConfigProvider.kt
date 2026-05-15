package com.bios.app.config

import com.bios.contracts.MetricType
import java.util.Locale

/**
 * Provides region-specific configuration based on device locale.
 * New regions require only a new config entry — no code changes.
 */
object RegionConfigProvider {

    /**
     * Biomarker bands that are clinically identical across all six supported
     * regions. ADA / WHO / NICE / JSCC all use the same cut-offs for these
     * two assays; SI ↔ US unit conversions are handled by [UnitDisplay], not
     * here. When a future biomarker has region-divergent bands (e.g. JDS
     * uses different HbA1c bands historically; CRP cut-offs are universal),
     * the region-specific config can override.
     *
     * Declared before [configs] so the eager region-config initializers
     * below see it as initialized (Kotlin object props init top-down).
     */
    private val universalBiomarkerBands: Map<MetricType, BiomarkerBands> = mapOf(
        // hsCRP (mg/L): <1.0 low, 1.0–3.0 moderate, ≥3.0 high
        // (Ridker 2003; Pearson AHA/CDC 2003)
        MetricType.HSCRP to BiomarkerBands(normalCeiling = 1.0, borderlineCeiling = 3.0),
        // HbA1c (%): <5.7 normal, 5.7–6.5 prediabetic, ≥6.5 diabetic (ADA 2024)
        MetricType.HBA1C to BiomarkerBands(normalCeiling = 5.7, borderlineCeiling = 6.5),
        // Total cholesterol (mg/dL): <200 desirable, 200–239 borderline, ≥240 high
        // (NCEP ATP III; AHA 2018 guideline)
        MetricType.TOTAL_CHOLESTEROL to BiomarkerBands(
            normalCeiling = 200.0, borderlineCeiling = 240.0,
        ),
        // LDL-C (mg/dL): <100 optimal, 100–160 borderline, ≥160 high (NCEP ATP III)
        MetricType.LDL_CHOLESTEROL to BiomarkerBands(
            normalCeiling = 100.0, borderlineCeiling = 160.0,
        ),
        // HDL-C (mg/dL): <40 concerning low, 40–60 borderline, ≥60 protective
        // — INVERSE direction: low HDL is the clinical concern (NCEP ATP III)
        MetricType.HDL_CHOLESTEROL to BiomarkerBands(
            normalCeiling = 40.0, borderlineCeiling = 60.0,
            concerningDirection = BandDirection.BELOW,
        ),
        // Triglycerides (mg/dL): <150 normal, 150–200 borderline, ≥200 high
        // (NCEP ATP III)
        MetricType.TRIGLYCERIDES to BiomarkerBands(
            normalCeiling = 150.0, borderlineCeiling = 200.0,
        ),
        // ApoB (mg/dL): <90 desirable, 90–120 borderline, ≥120 high
        // (Sniderman et al. 2019; AACC 2023 — particle-count gold standard)
        MetricType.APO_B to BiomarkerBands(
            normalCeiling = 90.0, borderlineCeiling = 120.0,
        ),
        // 25-OH vitamin D (ng/mL): <20 deficient, 20–30 insufficient, ≥30 sufficient
        // — INVERSE direction: low values are the clinical concern
        // (Holick et al. 2011 — Endocrine Society Clinical Practice Guideline)
        MetricType.VITAMIN_D_25OH to BiomarkerBands(
            normalCeiling = 20.0, borderlineCeiling = 30.0,
            concerningDirection = BandDirection.BELOW,
        ),
        // TSH (mIU/L): <0.4 hyperthyroid, 0.4–4.0 normal, 4.0–10.0 subclinical
        // hypothyroid, ≥10.0 overt hypothyroid. Bidirectional: both extremes
        // are clinical concerns (AACE/ATA 2012; ATA 2014 — adult guidelines).
        MetricType.TSH to BiomarkerBands(
            normalCeiling = 4.0, borderlineCeiling = 10.0,
            concerningDirection = BandDirection.ABOVE,
            lowCeiling = 0.4,
        ),
        // Free T4 (ng/dL) — hypo-focused window. <0.8 overt hypothyroid,
        // 0.8–1.0 borderline-low, ≥1.0 normal-for-hypo-screening. The
        // hyperthyroid case is covered by the TSH low-extreme and by Free T3
        // (which rises earlier than Free T4 in T3-toxicosis presentations);
        // a single-axis BiomarkerBands can't express both a low-concern and
        // a high-concern threshold, and Free T4 is the lower-yield half of
        // that pair, so it stays hypo-focused here. (AACE 2012)
        MetricType.FREE_T4 to BiomarkerBands(
            normalCeiling = 0.8, borderlineCeiling = 1.0,
            concerningDirection = BandDirection.BELOW,
        ),
        // Free T3 (pg/mL): <2.3 low (hypothyroid), 2.3–4.2 normal,
        // 4.2–6.5 borderline-high (subclinical / early thyrotoxicosis),
        // ≥6.5 overt hyperthyroidism / T3-toxicosis. Bidirectional via
        // [lowCeiling] so both extremes flag — Free T3 is the most
        // sensitive single marker for T3-predominant hyperthyroidism, which
        // can present with elevated Free T3 before Free T4 leaves range.
        // (Ross DS et al. 2016 — ATA Hyperthyroidism Guideline;
        // Garber JR et al. 2012 — AACE/ATA reference ranges)
        MetricType.FREE_T3 to BiomarkerBands(
            normalCeiling = 4.2, borderlineCeiling = 6.5,
            concerningDirection = BandDirection.ABOVE,
            lowCeiling = 2.3,
        ),
        // -- CBC panel --
        // Hemoglobin (g/dL): WHO anemia thresholds (<13 men, <12 non-pregnant
        // women). Single threshold uses the female floor (12) as universal
        // CONCERNING; 12–13.5 is the sex-dependent BORDERLINE zone where a
        // male reading is anemic but a female reading is borderline; ≥13.5
        // is NORMAL across sexes. (WHO 2011 — Haemoglobin concentrations
        // for the diagnosis of anaemia)
        MetricType.HEMOGLOBIN to BiomarkerBands(
            normalCeiling = 12.0, borderlineCeiling = 13.5,
            concerningDirection = BandDirection.BELOW,
        ),
        // Hematocrit (%): <36 anemic (female threshold; male anemia floor
        // is ~41 but using the female value as the universal CONCERNING
        // floor mirrors the hemoglobin treatment), 36–40 borderline-low,
        // ≥40 normal. (Williams Hematology 10th ed. — adult reference
        // ranges; WHO 2011)
        MetricType.HEMATOCRIT to BiomarkerBands(
            normalCeiling = 36.0, borderlineCeiling = 40.0,
            concerningDirection = BandDirection.BELOW,
        ),
        // WBC (giga/L = ×10⁹/L): 4.5–11 normal, <4.5 leukopenia (low extreme
        // via lowCeiling), 11–15 borderline-high leukocytosis (mild
        // infection / stress response window), ≥15 marked leukocytosis.
        // Bidirectional — both extremes are clinically meaningful.
        // (Williams Hematology 10th ed.; George 2009)
        MetricType.WBC to BiomarkerBands(
            normalCeiling = 11.0, borderlineCeiling = 15.0,
            concerningDirection = BandDirection.ABOVE,
            lowCeiling = 4.5,
        ),
        // RBC (tera/L = ×10¹²/L): <4.0 indicates anemia (combined-sex
        // conservative floor — female lower bound 4.2, male 4.7), 4.0–4.5
        // borderline-low, ≥4.5 normal. (Williams Hematology 10th ed.)
        MetricType.RBC to BiomarkerBands(
            normalCeiling = 4.0, borderlineCeiling = 4.5,
            concerningDirection = BandDirection.BELOW,
        ),
        // Platelets (giga/L = ×10⁹/L): NIH/standard hematology grades
        // thrombocytopenia: <100 clinically significant (CONCERNING),
        // 100–150 mild (BORDERLINE), ≥150 normal. High extreme
        // (thrombocytosis) is less common as an initial screening concern;
        // a single-axis BELOW direction matches the dominant clinical use.
        // (NCI Common Terminology Criteria; Williams Hematology 10th ed.)
        MetricType.PLATELETS to BiomarkerBands(
            normalCeiling = 100.0, borderlineCeiling = 150.0,
            concerningDirection = BandDirection.BELOW,
        ),
    )

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
            hypertensionDiastolic = 80,
            biomarkerBands = universalBiomarkerBands
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
            hypertensionDiastolic = 90,
            biomarkerBands = universalBiomarkerBands
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
            hypertensionDiastolic = 90,
            biomarkerBands = universalBiomarkerBands
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
            hypertensionDiastolic = 90,
            biomarkerBands = universalBiomarkerBands
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
            hypertensionDiastolic = 90,
            biomarkerBands = universalBiomarkerBands
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
            hypertensionDiastolic = 90,
            biomarkerBands = universalBiomarkerBands
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
