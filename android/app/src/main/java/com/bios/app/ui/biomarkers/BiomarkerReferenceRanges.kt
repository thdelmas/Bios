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

        // -- #203 biomarker-panel expansion --
        // Hepatic completion (AASLD 2017 — abnormal-liver-chemistry guidance).
        // Total bilirubin: typical adult reference 5.1-20.5 µmol/L
        // (0.3-1.2 mg/dL). Conservative upper bound 20.
        MetricType.TOTAL_BILIRUBIN_UMOL_PER_L to ReferenceRange(low = null, high = 20.0, units = "µmol/L"),
        // Albumin (g/L) — synthetic-function marker. Adult reference 35-50.
        MetricType.ALBUMIN_G_PER_L to ReferenceRange(low = 35.0, high = 50.0, units = "g/L"),
        // Alkaline phosphatase (U/L) — adult reference 30-130. Higher in
        // adolescents (bone-isoform) and pregnancy; the screening cutoff
        // applies to non-pregnant adults.
        MetricType.ALP_U_PER_L to ReferenceRange(low = 30.0, high = 130.0, units = "U/L"),

        // Cardiovascular risk amplifiers.
        // Lp(a) — EAS 2022 / Reyes-Soffer AHA 2022. <75 nmol/L is below the
        // population-percentile threshold for elevated cardiovascular risk
        // (some labs use 50 nmol/L). No lower bound (low Lp(a) is benign).
        MetricType.LP_A_NMOL_PER_L to ReferenceRange(low = null, high = 75.0, units = "nmol/L"),
        // D-dimer — <500 ng/mL FEU is the standard age-independent cutoff;
        // age-adjusted cutoff (age × 10) is used after 50 in some protocols.
        MetricType.D_DIMER_NG_PER_ML to ReferenceRange(low = null, high = 500.0, units = "ng/mL FEU"),

        // Functional-medicine markers.
        // Homocysteine (µmol/L) — AHA 2009 scientific statement: <15
        // generally normal; "optimal" functional-medicine cutoff is <10
        // (Smith Refsum 2018). Using the conservative clinical bound 15.
        MetricType.HOMOCYSTEINE_UMOL_PER_L to ReferenceRange(low = null, high = 15.0, units = "µmol/L"),
        // Uric acid (µmol/L) — sex-stratified reference ranges (140-340 ♀;
        // 200-420 ♂). Using the more permissive 420 male upper bound as the
        // universal cutoff to avoid sex-misclassification on female readings
        // sitting at the male edge.
        MetricType.URIC_ACID_UMOL_PER_L to ReferenceRange(low = 140.0, high = 420.0, units = "µmol/L"),
        // TIBC (µmol/L) — adult reference 45-72. Iron-deficiency anemia
        // raises TIBC; anemia of chronic disease lowers it.
        MetricType.TIBC_UMOL_PER_L to ReferenceRange(low = 45.0, high = 72.0, units = "µmol/L"),

        // Thyroid (functional-medicine extension).
        // Reverse T3 (ng/dL) — typical reference range 9.2-24.1. Elevated
        // values in euthyroid sick syndrome / nonthyroidal illness.
        MetricType.REVERSE_T3_NG_PER_DL to ReferenceRange(low = 9.2, high = 24.1, units = "ng/dL"),
        // TPO antibodies (kIU/L) — <35 generally negative (ATA 2014). Some
        // assays report <9 / <40; the 35 floor is the most-cited cutoff.
        MetricType.THYROID_PEROXIDASE_AB_KIU_PER_L to ReferenceRange(low = null, high = 35.0, units = "kIU/L"),

        // Endocrine (functional-medicine extension).
        // DHEA-S and SHBG are sex- and age-dependent — a single cutoff is
        // misleading. Surfaced un-coloured (no entry here) so the dashboard
        // shows the value without classification, same treatment as
        // testosterone / estradiol / IGF-1 above.

        // Cardiometabolic — leptin and adiponectin are also sex/BMI-
        // dependent; omitted intentionally. Omega-3 index has a clear
        // population threshold.
        // Omega-3 index (% — proportion of EPA + DHA in RBC membrane).
        // Harris 2008: <4% high cardiovascular risk; 4-8% intermediate;
        // ≥8% cardioprotective. Using 4 as low / 8 as high.
        MetricType.OMEGA_3_INDEX_PCT to ReferenceRange(low = 4.0, high = 8.0, units = "%"),
    )

    fun forMetric(metric: MetricType): ReferenceRange? = ranges[metric]
}
