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
            // Biomarker-panel expansion (#203) — hepatic completion,
            // cardiovascular amplifiers, functional-medicine, thyroid
            // autoimmunity, endocrine, cardiometabolic.
            MetricType.TOTAL_BILIRUBIN_UMOL_PER_L,
            MetricType.ALBUMIN_G_PER_L,
            MetricType.ALP_U_PER_L,
            MetricType.LP_A_NMOL_PER_L,
            MetricType.D_DIMER_NG_PER_ML,
            MetricType.HOMOCYSTEINE_UMOL_PER_L,
            MetricType.URIC_ACID_UMOL_PER_L,
            MetricType.TIBC_UMOL_PER_L,
            MetricType.REVERSE_T3_NG_PER_DL,
            MetricType.THYROID_PEROXIDASE_AB_KIU_PER_L,
            MetricType.DHEA_S_UMOL_PER_L,
            MetricType.SHBG_NMOL_PER_L,
            MetricType.OMEGA_3_INDEX_PCT,
            MetricType.LEPTIN_NG_PER_ML,
            MetricType.ADIPONECTIN_MG_PER_L,
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
