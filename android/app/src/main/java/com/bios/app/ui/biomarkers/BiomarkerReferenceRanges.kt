package com.bios.app.ui.biomarkers

import com.bios.contracts.MetricType

/**
 * Population-average descriptive reference ranges for the biomarker
 * dashboard (#137). Used only to colour a value as *within / below /
 * above* — a passthrough of what the lab itself prints next to the
 * number. Bios does not evaluate the person from these.
 *
 * Per-substance citations live in [BiomarkerReferences] in
 * `alerts/BiomarkerReference.kt`; this file is the structured numeric
 * counterpart so the UI doesn't have to parse free-text. Sex- and
 * age-dependent assays (testosterone, estradiol, IGF-1) are
 * deliberately absent — a single cutoff would be misleading and the
 * dashboard surfaces them un-coloured instead.
 *
 * Each [ReferenceRange] uses null for an unbounded side (some markers
 * have only a low or only a high cutoff in routine clinical use).
 */
data class ReferenceRange(
    val low: Double?,
    val high: Double?,
    val units: String,
) {
    enum class Classification { BELOW, WITHIN, ABOVE, UNKNOWN }

    fun classify(value: Double): Classification {
        if (low != null && value < low) return Classification.BELOW
        if (high != null && value > high) return Classification.ABOVE
        if (low == null && high == null) return Classification.UNKNOWN
        return Classification.WITHIN
    }
}

object BiomarkerReferenceRanges {

    val ranges: Map<MetricType, ReferenceRange> = mapOf(
        // Lipids — ATP III / current AHA guidance for primary prevention.
        MetricType.TOTAL_CHOLESTEROL to ReferenceRange(low = null, high = 200.0, units = "mg/dL"),
        MetricType.LDL_CHOLESTEROL to ReferenceRange(low = null, high = 100.0, units = "mg/dL"),
        MetricType.HDL_CHOLESTEROL to ReferenceRange(low = 40.0, high = null, units = "mg/dL"),
        MetricType.TRIGLYCERIDES to ReferenceRange(low = null, high = 150.0, units = "mg/dL"),
        MetricType.APO_B to ReferenceRange(low = null, high = 90.0, units = "mg/dL"),

        // Glycemic — ADA criteria.
        MetricType.HBA1C to ReferenceRange(low = null, high = 5.7, units = "%"),
        MetricType.FASTING_GLUCOSE to ReferenceRange(low = 70.0, high = 99.0, units = "mg/dL"),
        // Fasting insulin "optimal" ranges vary widely in literature;
        // < 10 µIU/mL is the commonly cited insulin-sensitive bound.
        MetricType.FASTING_INSULIN to ReferenceRange(low = null, high = 10.0, units = "µIU/mL"),
        // HOMA-IR (Matthews 1985): < 2.0 generally insulin-sensitive.
        MetricType.HOMA_IR to ReferenceRange(low = null, high = 2.0, units = "score"),

        // Inflammation — Ridker / AHA cardiovascular-risk bands.
        MetricType.HSCRP to ReferenceRange(low = null, high = 1.0, units = "mg/L"),
        // Iron — sex-averaged adult range; the dashboard treats the
        // single number as descriptive only.
        MetricType.FERRITIN to ReferenceRange(low = 30.0, high = 300.0, units = "ng/mL"),

        // Thyroid — typical adult assay normal ranges.
        MetricType.TSH to ReferenceRange(low = 0.4, high = 4.0, units = "mIU/L"),
        MetricType.FREE_T4 to ReferenceRange(low = 0.8, high = 1.8, units = "ng/dL"),
        MetricType.FREE_T3 to ReferenceRange(low = 2.3, high = 4.2, units = "pg/mL"),

        // Vitamins — typical lab reference ranges.
        MetricType.VITAMIN_D_25OH to ReferenceRange(low = 30.0, high = 100.0, units = "ng/mL"),
        MetricType.VITAMIN_B12 to ReferenceRange(low = 200.0, high = 900.0, units = "pg/mL"),
        MetricType.FOLATE to ReferenceRange(low = 5.4, high = null, units = "ng/mL"),
        MetricType.MAGNESIUM to ReferenceRange(low = 1.7, high = 2.2, units = "mg/dL"),

        // Hematology — sex-averaged adult ranges.
        MetricType.HEMOGLOBIN to ReferenceRange(low = 12.0, high = 17.5, units = "g/dL"),
        MetricType.HEMATOCRIT to ReferenceRange(low = 36.0, high = 50.0, units = "%"),
        MetricType.WBC to ReferenceRange(low = 4.5, high = 11.0, units = "10⁹/L"),
        MetricType.RBC to ReferenceRange(low = 4.2, high = 6.1, units = "10¹²/L"),
        MetricType.PLATELETS to ReferenceRange(low = 150.0, high = 450.0, units = "10⁹/L"),

        // Endocrine — morning cortisol; other endocrine markers
        // (testosterone, estradiol, IGF-1) are deliberately absent here
        // because a sex/age-independent cutoff would be misleading.
        MetricType.CORTISOL to ReferenceRange(low = 6.0, high = 23.0, units = "µg/dL"),

        // Coagulation panel (#352). Reference ranges are lab-dependent (reagent
        // and analyzer specific); the values here match common adult ranges for
        // dashboard colouring only — the lab's own range travels alongside the
        // value when imported via FHIR.
        MetricType.PROTHROMBIN_TIME to ReferenceRange(low = 9.0, high = 13.0, units = "s"),
        MetricType.INR to ReferenceRange(low = 0.85, high = 1.20, units = "score"),
        MetricType.QUICK_INDEX to ReferenceRange(low = 70.0, high = 130.0, units = "%"),
        MetricType.APTT to ReferenceRange(low = 25.0, high = 40.0, units = "s"),
        MetricType.APTT_RATIO to ReferenceRange(low = 0.86, high = 1.20, units = "score"),

        // Absolute leukocyte differential (#353). Sex-averaged adult ranges
        // (Williams Hematology 10th ed.); reference range is conservative
        // — analyzer- and population-specific bands belong to the lab.
        MetricType.ABSOLUTE_LYMPHOCYTE_COUNT to ReferenceRange(low = 1000.0, high = 4800.0, units = "/µL"),
        MetricType.ABSOLUTE_MONOCYTE_COUNT to ReferenceRange(low = 100.0, high = 800.0, units = "/µL"),
        MetricType.ABSOLUTE_EOSINOPHIL_COUNT to ReferenceRange(low = null, high = 500.0, units = "/µL"),
        MetricType.ABSOLUTE_BASOPHIL_COUNT to ReferenceRange(low = null, high = 200.0, units = "/µL"),
    )

    fun forMetric(metric: MetricType): ReferenceRange? = ranges[metric]
}
