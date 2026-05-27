package com.bios.app

import com.bios.app.model.SourceType
import com.bios.contracts.MetricDomain
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the selectors that the biomarker entry surface depends on:
 *  - the SELF_REPORTED source key string is what gets persisted into the
 *    data_sources row that owns every manual lab value;
 *  - filtering MetricType by MetricDomain.BIOMARKER is the canonical way the
 *    entry screen builds its picker list.
 *
 * If either of these silently changes, manually entered lab values get
 * orphaned or the picker drops biomarkers, and a unit test is the cheapest
 * place to catch it.
 */
class BiomarkerEntrySelectorsTest {

    @Test
    fun self_reported_source_key_is_stable() {
        // The key is persisted into data_sources.sourceType for every
        // manually entered lab; renaming it would orphan existing rows.
        assertEquals("self_reported", SourceType.SELF_REPORTED.key)
    }

    @Test
    fun biomarker_domain_filter_includes_every_shipped_biomarker_key() {
        val expected = setOf(
            MetricType.HBA1C, MetricType.HSCRP,
            MetricType.TOTAL_CHOLESTEROL, MetricType.LDL_CHOLESTEROL,
            MetricType.HDL_CHOLESTEROL, MetricType.TRIGLYCERIDES,
            MetricType.APO_B,
            MetricType.VITAMIN_D_25OH,
            MetricType.TSH, MetricType.FREE_T4, MetricType.FREE_T3,
            MetricType.HEMOGLOBIN, MetricType.HEMATOCRIT,
            MetricType.WBC, MetricType.RBC, MetricType.PLATELETS,
            // Glycemic-extended / iron / endocrine / micronutrient (#24).
            MetricType.FASTING_GLUCOSE, MetricType.FASTING_INSULIN, MetricType.HOMA_IR,
            MetricType.FERRITIN,
            MetricType.TESTOSTERONE_TOTAL, MetricType.ESTRADIOL,
            MetricType.CORTISOL, MetricType.IGF_1,
            MetricType.VITAMIN_B12, MetricType.FOLATE, MetricType.MAGNESIUM,
            // Renal / hepatic (#158).
            MetricType.EGFR, MetricType.CREATININE,
            MetricType.ALT, MetricType.AST, MetricType.GGT,
            // Cardio-oncology biomarkers (#201).
            MetricType.NT_PRO_BNP_PG_PER_ML,
            MetricType.TROPONIN_NG_PER_L,
            MetricType.ABSOLUTE_NEUTROPHIL_COUNT,
            // Wave-1 biomarker expansion (BLUEPRINT_PROTOCOL_AUDIT §3.1).
            MetricType.LIPOPROTEIN_A, MetricType.HOMOCYSTEINE,
            MetricType.BUN, MetricType.CALCIUM_SERUM,
            MetricType.CARBON_DIOXIDE, MetricType.CHLORIDE,
            MetricType.PHOSPHATE, MetricType.SODIUM,
            MetricType.POTASSIUM, MetricType.URIC_ACID,
            MetricType.ALBUMIN, MetricType.ALKALINE_PHOSPHATASE,
            MetricType.BILIRUBIN_TOTAL, MetricType.TOTAL_PROTEIN,
            MetricType.AMYLASE, MetricType.LIPASE,
            MetricType.MCV, MetricType.MCH, MetricType.MCHC,
            MetricType.RDW, MetricType.MPV,
            MetricType.NEUTROPHILS_PCT, MetricType.LYMPHOCYTES_PCT,
            MetricType.MONOCYTES_PCT, MetricType.EOSINOPHILS_PCT,
            MetricType.BASOPHILS_PCT,
            MetricType.IRON_SERUM, MetricType.IRON_SATURATION_PCT, MetricType.TIBC,
            MetricType.FSH, MetricType.LH, MetricType.SHBG,
            MetricType.AMH, MetricType.TESTOSTERONE_FREE,
            MetricType.PROLACTIN, MetricType.DHEA_SULFATE,
            // Wave-2 biomarker expansion (BLUEPRINT_PROTOCOL_AUDIT §3.2).
            MetricType.THYROID_PEROXIDASE_AB, MetricType.THYROGLOBULIN_AB,
            MetricType.PSA_TOTAL, MetricType.PSA_FREE,
            MetricType.TELOMERE_LENGTH, MetricType.CORONARY_CALCIUM_SCORE,
            MetricType.BONE_DENSITY_T_SCORE, MetricType.PTAU_217,
            MetricType.VITAMIN_K2, MetricType.VITAMIN_A_RETINOL,
            MetricType.VITAMIN_E_ALPHA_TOCOPHEROL,
            // Coagulation panel (#352).
            MetricType.PROTHROMBIN_TIME, MetricType.INR,
            MetricType.QUICK_INDEX, MetricType.APTT, MetricType.APTT_RATIO,
            // Absolute leukocyte differential (#353).
            MetricType.ABSOLUTE_LYMPHOCYTE_COUNT,
            MetricType.ABSOLUTE_MONOCYTE_COUNT,
            MetricType.ABSOLUTE_EOSINOPHIL_COUNT,
            MetricType.ABSOLUTE_BASOPHIL_COUNT,
            // Standard CRP (#354) — distinct from HSCRP.
            MetricType.CRP,
            // Wave 6 expansion (#339) — D-dimer, reverse T3, omega-3 index,
            // leptin, adiponectin. ALP and Lp(a) ride in the Wave-1 block above.
            MetricType.D_DIMER,
            MetricType.REVERSE_T3,
            MetricType.OMEGA_3_INDEX,
            MetricType.LEPTIN,
            MetricType.ADIPONECTIN,
            // Epigenetic age clocks (slow-rolling, lab-imported).
            MetricType.EPIGENETIC_AGE_DUNEDIN_PACE,
            MetricType.EPIGENETIC_AGE_GRIM,
            MetricType.EPIGENETIC_AGE_PHENO,
            MetricType.EPIGENETIC_AGE_HORVATH,
        )
        val actual = MetricType.entries.filter { it.domain == MetricDomain.BIOMARKER }.toSet()
        assertEquals(expected, actual)
    }

    @Test
    fun biomarker_domain_contains_no_streaming_sensor_keys() {
        // Spot-check: keys with continuous sensor writers (HR, HRV, sleep, etc.)
        // must NOT land in BIOMARKER — the entry surface and the
        // baseline/anomaly engines key off the domain split.
        val streaming = listOf(
            MetricType.HEART_RATE,
            MetricType.HEART_RATE_VARIABILITY,
            MetricType.BLOOD_GLUCOSE,
            MetricType.SLEEP_STAGE,
            MetricType.BODY_MASS,
        )
        for (t in streaming) {
            assertTrue(
                "${t.key} should not be in BIOMARKER domain",
                t.domain != MetricDomain.BIOMARKER
            )
        }
    }
}
