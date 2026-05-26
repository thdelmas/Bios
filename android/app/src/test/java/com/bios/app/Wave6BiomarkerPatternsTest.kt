package com.bios.app

import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.ThresholdSource
import com.bios.app.alerts.Wave6BiomarkerPatterns
import com.bios.app.config.RegionConfigProvider
import com.bios.contracts.MetricDomain
import com.bios.contracts.MetricType
import com.bios.contracts.MetricUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the wave-6 biomarker condition patterns shipped by #203 — the
 * renal-function-decline and hepatic-injury screens — plus the
 * biomarker-panel expansion contract surface (new MetricType keys, units,
 * LOINC mappings, reference ranges).
 *
 * Same shape contract as Wave 5: lab anchors via `required = true` +
 * absolute thresholds, with literature citations per rule. ADVISORY tier;
 * AlertContentPolicy compliant.
 */
class Wave6BiomarkerPatternsTest {

    // -- registration --

    @Test
    fun wave6_patterns_are_registered_in_global_all_list() {
        assertTrue(Wave6BiomarkerPatterns.renalFunctionDeclineScreen in ConditionPatterns.all)
        assertTrue(Wave6BiomarkerPatterns.hepaticInjuryScreen in ConditionPatterns.all)
    }

    @Test
    fun every_wave6_pattern_carries_a_literature_citation_per_rule() {
        for (pattern in Wave6BiomarkerPatterns.all) {
            for (rule in pattern.signalRules) {
                assertEquals(
                    "${pattern.id}/${rule.metricType.key} should be LITERATURE-sourced",
                    ThresholdSource.LITERATURE,
                    rule.source
                )
                assertTrue(
                    "${pattern.id}/${rule.metricType.key} should ship a citation string",
                    rule.citation.isNotBlank()
                )
            }
        }
    }

    // -- renal_function_decline_screen --

    @Test
    fun renal_decline_screen_gates_on_eGFR_below_60() {
        val rule = Wave6BiomarkerPatterns.renalFunctionDeclineScreen.signalRules
            .first { it.metricType == MetricType.EGFR }
        assertTrue("eGFR rule should anchor the pattern", rule.required)
        assertTrue(rule.isAbsolute)
        assertEquals(60.0, rule.absoluteBelow!!, 1e-9)
    }

    @Test
    fun renal_decline_screen_requires_creatinine_corroborator() {
        val rule = Wave6BiomarkerPatterns.renalFunctionDeclineScreen.signalRules
            .first { it.metricType == MetricType.CREATININE }
        assertTrue("creatinine corroborator should be required for the screening tier", rule.required)
        assertEquals(1.3, rule.absoluteAbove!!, 1e-9)
    }

    @Test
    fun renal_decline_screen_offers_urate_optional_corroborator() {
        val rule = Wave6BiomarkerPatterns.renalFunctionDeclineScreen.signalRules
            .first { it.metricType == MetricType.URIC_ACID_UMOL_PER_L }
        assertFalse("urate corroborator should be optional", rule.required)
        assertEquals(420.0, rule.absoluteAbove!!, 1e-9)
    }

    // -- hepatic_injury_screen --

    @Test
    fun hepatic_injury_screen_gates_on_ALT_above_3x_ULN() {
        val rule = Wave6BiomarkerPatterns.hepaticInjuryScreen.signalRules
            .first { it.metricType == MetricType.ALT }
        assertTrue("ALT rule should anchor the pattern", rule.required)
        assertTrue(rule.isAbsolute)
        assertEquals(100.0, rule.absoluteAbove!!, 1e-9)
    }

    @Test
    fun hepatic_injury_screen_carries_AST_GGT_bilirubin_albumin_corroborators() {
        val pattern = Wave6BiomarkerPatterns.hepaticInjuryScreen
        val corroborators = pattern.signalRules.filter { !it.required }.map { it.metricType }.toSet()
        assertTrue(MetricType.AST in corroborators)
        assertTrue(MetricType.GGT in corroborators)
        assertTrue(MetricType.TOTAL_BILIRUBIN_UMOL_PER_L in corroborators)
        assertTrue(MetricType.ALBUMIN_G_PER_L in corroborators)
    }

    // -- biomarker-panel expansion contract surface --

    @Test
    fun new_biomarkers_resolve_via_MetricType_entries() {
        val newKeys = listOf(
            "total_bilirubin_umol_per_l",
            "albumin_g_per_l",
            "alp_u_per_l",
            "lp_a_nmol_per_l",
            "d_dimer_ng_per_ml",
            "homocysteine_umol_per_l",
            "uric_acid_umol_per_l",
            "tibc_umol_per_l",
            "reverse_t3_ng_per_dl",
            "thyroid_peroxidase_ab_kiu_per_l",
            "dhea_s_umol_per_l",
            "shbg_nmol_per_l",
            "omega_3_index_pct",
            "leptin_ng_per_ml",
            "adiponectin_mg_per_l",
        )
        for (key in newKeys) {
            val type = MetricType.fromKey(key)
            assertNotNull("MetricType.fromKey('$key') should resolve", type)
            assertEquals("$key should live in the BIOMARKER domain", MetricDomain.BIOMARKER, type!!.domain)
            assertTrue("$key should allow manual entry (lab-drawn)", type.allowsManualEntry)
        }
    }

    @Test
    fun new_biomarker_units_match_clinically_conventional_units() {
        assertEquals(MetricUnit.UMOL_PER_L, MetricType.TOTAL_BILIRUBIN_UMOL_PER_L.unit)
        assertEquals(MetricUnit.G_PER_L, MetricType.ALBUMIN_G_PER_L.unit)
        assertEquals(MetricUnit.U_PER_L, MetricType.ALP_U_PER_L.unit)
        assertEquals(MetricUnit.NMOL_PER_L, MetricType.LP_A_NMOL_PER_L.unit)
        assertEquals(MetricUnit.NG_PER_ML, MetricType.D_DIMER_NG_PER_ML.unit)
        assertEquals(MetricUnit.UMOL_PER_L, MetricType.HOMOCYSTEINE_UMOL_PER_L.unit)
        assertEquals(MetricUnit.UMOL_PER_L, MetricType.URIC_ACID_UMOL_PER_L.unit)
        assertEquals(MetricUnit.UMOL_PER_L, MetricType.TIBC_UMOL_PER_L.unit)
        assertEquals(MetricUnit.NG_PER_DL, MetricType.REVERSE_T3_NG_PER_DL.unit)
        assertEquals(MetricUnit.KIU_PER_L, MetricType.THYROID_PEROXIDASE_AB_KIU_PER_L.unit)
        assertEquals(MetricUnit.UMOL_PER_L, MetricType.DHEA_S_UMOL_PER_L.unit)
        assertEquals(MetricUnit.NMOL_PER_L, MetricType.SHBG_NMOL_PER_L.unit)
        assertEquals(MetricUnit.PERCENT, MetricType.OMEGA_3_INDEX_PCT.unit)
        assertEquals(MetricUnit.NG_PER_ML, MetricType.LEPTIN_NG_PER_ML.unit)
        assertEquals(MetricUnit.MG_PER_L, MetricType.ADIPONECTIN_MG_PER_L.unit)
    }

    @Test
    fun new_biomarker_bands_populated_for_default_region() {
        // Each pattern-anchored or universally-banded biomarker must have a
        // BiomarkerBands entry in the default region config so the dashboard
        // colours readings correctly. DHEA-S, SHBG, leptin, adiponectin, TIBC
        // are deliberately absent (sex/age-dependent or population-dependent
        // — see RegionConfigProvider comments).
        val bands = RegionConfigProvider.forRegion("EU").clinicalThresholds.biomarkerBands
        val mustHaveBands = listOf(
            MetricType.TOTAL_BILIRUBIN_UMOL_PER_L,
            MetricType.ALBUMIN_G_PER_L,
            MetricType.ALP_U_PER_L,
            MetricType.LP_A_NMOL_PER_L,
            MetricType.D_DIMER_NG_PER_ML,
            MetricType.HOMOCYSTEINE_UMOL_PER_L,
            MetricType.URIC_ACID_UMOL_PER_L,
            MetricType.THYROID_PEROXIDASE_AB_KIU_PER_L,
            MetricType.REVERSE_T3_NG_PER_DL,
            MetricType.OMEGA_3_INDEX_PCT,
        )
        for (type in mustHaveBands) {
            assertNotNull("${type.key} should have a clinical band defined", bands[type])
        }
    }
}
