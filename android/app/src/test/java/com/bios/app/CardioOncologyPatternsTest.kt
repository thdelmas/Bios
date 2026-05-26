package com.bios.app

import com.bios.app.alerts.CardioOncologyPatterns
import com.bios.app.alerts.ConditionPattern
import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.DeviationDirection
import com.bios.app.alerts.ThresholdSource
import com.bios.app.model.AlertTier
import com.bios.app.physiology.CancerTherapyDrugClass
import com.bios.app.physiology.PhysiologyState
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the cardio-oncology + treatment-toxicity surveillance patterns
 * (#201, audit convergence from ONCOLOGY_POV §2.3-2.5, CARDIOLOGY_POV §2.3,
 * Emergency Medicine, Geriatrics).
 *
 * Two invariants matter most:
 *
 *  1. **State gating** — each pattern declares `requiredStates` so it is
 *     only evaluated when the owner has declared the matching treatment
 *     state. Healthy / non-treatment owners must never see these alerts.
 *  2. **Drug-class gating** — most patterns declare `requiredDrugClasses`
 *     because the toxicity profile is class-specific (anthracycline
 *     cardiotoxicity ≠ ICI pneumonitis).
 *
 * The behavioural unit-test for the trigger / silence path lives in
 * [AnomalyDetectorCardioOncologyTest] (state-gated routing through the
 * detector). This file pins the static contract of the patterns
 * themselves — citations, thresholds, severity floors.
 */
class CardioOncologyPatternsTest {

    // -- registration --

    @Test
    fun all_cardio_oncology_patterns_register_in_global_all_list() {
        for (pattern in CardioOncologyPatterns.all) {
            assertTrue(
                "${pattern.id} should be registered in ConditionPatterns.all",
                pattern in ConditionPatterns.all,
            )
        }
    }

    @Test
    fun seven_patterns_ship_in_this_wave() {
        // Neutropenic fever, anthracycline + trastuzumab cardiotoxicity,
        // ICI pneumonitis / thyroiditis / colitis, and cachexia.
        assertEquals(7, CardioOncologyPatterns.all.size)
    }

    // -- state gating: every pattern requires a cancer-treatment state --

    @Test
    fun every_pattern_declares_a_non_empty_requiredStates_set() {
        for (pattern in CardioOncologyPatterns.all) {
            assertTrue(
                "${pattern.id} should declare requiredStates so it stays silent in healthy populations",
                pattern.requiredStates.isNotEmpty(),
            )
        }
    }

    @Test
    fun every_required_state_is_a_cancer_treatment_state() {
        for (pattern in CardioOncologyPatterns.all) {
            for (state in pattern.requiredStates) {
                assertTrue(
                    "${pattern.id} requires $state which should be a CANCER_TREATMENT state",
                    state in PhysiologyState.CANCER_TREATMENT,
                )
            }
        }
    }

    // -- neutropenic fever screen --

    @Test
    fun neutropenic_fever_gates_on_active_chemotherapy_state() {
        val pattern = CardioOncologyPatterns.neutropenicFeverScreen
        assertEquals(setOf(PhysiologyState.ON_ACTIVE_CHEMOTHERAPY), pattern.requiredStates)
        // No drug-class restriction — any cytotoxic chemo can neutropenize.
        assertTrue(pattern.requiredDrugClasses.isEmpty())
    }

    @Test
    fun neutropenic_fever_anchors_on_absolute_neutrophil_count() {
        val pattern = CardioOncologyPatterns.neutropenicFeverScreen
        val anc = pattern.signalRules.first { it.metricType == MetricType.ABSOLUTE_NEUTROPHIL_COUNT }
        assertTrue("ANC must be required so wearable triad alone never fires", anc.required)
        assertTrue(anc.isAbsolute)
        assertEquals(1500.0, anc.absoluteBelow!!, 1e-9)
        assertEquals(ThresholdSource.LITERATURE, anc.source)
    }

    @Test
    fun neutropenic_fever_carries_URGENT_severity_floor() {
        // True sepsis-class oncology emergency — IDSA 2010 hour-1 antibiotics.
        assertEquals(AlertTier.URGENT, CardioOncologyPatterns.neutropenicFeverScreen.severityFloor)
    }

    @Test
    fun neutropenic_fever_action_text_invokes_oncology_emergency_protocol() {
        val action = CardioOncologyPatterns.neutropenicFeverScreen.suggestedAction
        assertNotNull(action)
        // Manifesto-clean phrasing requirement from the issue brief.
        assertTrue(
            "Should say 'vitals suggestive of', never 'you have'",
            CardioOncologyPatterns.neutropenicFeverScreen.title.contains("suggestive of", ignoreCase = true),
        )
        assertTrue(
            "Should reference immediate medical assessment / emergency department",
            action!!.contains("immediate medical assessment", ignoreCase = true) ||
                action.contains("emergency", ignoreCase = true),
        )
    }

    // -- anthracycline cardiotoxicity screen --

    @Test
    fun anthracycline_screen_gates_on_state_and_drug_class() {
        val pattern = CardioOncologyPatterns.anthracyclineCardiotoxicityScreen
        assertEquals(setOf(PhysiologyState.ON_ACTIVE_CHEMOTHERAPY), pattern.requiredStates)
        assertEquals(setOf(CancerTherapyDrugClass.ANTHRACYCLINE), pattern.requiredDrugClasses)
    }

    @Test
    fun anthracycline_screen_carries_ASCO_cardio_oncology_biomarkers() {
        val pattern = CardioOncologyPatterns.anthracyclineCardiotoxicityScreen
        val metrics = pattern.signalRules.map { it.metricType }.toSet()
        // ASCO 2017 + AHA 2018 cardio-oncology surveillance panel:
        // RHR + activity + NT-proBNP + troponin.
        assertTrue(MetricType.RESTING_HEART_RATE in metrics)
        assertTrue(MetricType.ACTIVE_MINUTES in metrics)
        assertTrue(MetricType.NT_PRO_BNP_PG_PER_ML in metrics)
        assertTrue(MetricType.TROPONIN_NG_PER_L in metrics)
    }

    @Test
    fun anthracycline_screen_uses_clinical_biomarker_cutoffs() {
        val pattern = CardioOncologyPatterns.anthracyclineCardiotoxicityScreen
        val ntProBnp = pattern.signalRules.first { it.metricType == MetricType.NT_PRO_BNP_PG_PER_ML }
        val troponin = pattern.signalRules.first { it.metricType == MetricType.TROPONIN_NG_PER_L }
        // ESC 2021 HF heart-failure exclusion upper bound — same cutoff
        // the cardio-oncology surveillance literature uses.
        assertEquals(125.0, ntProBnp.absoluteAbove!!, 1e-9)
        // 99th-percentile upper reference limit for sex-pooled hs-cTn.
        assertEquals(14.0, troponin.absoluteAbove!!, 1e-9)
    }

    // -- trastuzumab cardiotoxicity screen --

    @Test
    fun trastuzumab_screen_gates_on_state_and_trastuzumab_drug_class() {
        val pattern = CardioOncologyPatterns.trastuzumabCardiotoxicityScreen
        assertEquals(setOf(PhysiologyState.ON_ACTIVE_CHEMOTHERAPY), pattern.requiredStates)
        assertEquals(setOf(CancerTherapyDrugClass.TRASTUZUMAB), pattern.requiredDrugClasses)
    }

    // -- ICI pneumonitis screen --

    @Test
    fun ici_pneumonitis_gates_on_ICI_state_and_drug_class() {
        val pattern = CardioOncologyPatterns.iciPneumonitisScreen
        assertEquals(setOf(PhysiologyState.ON_IMMUNE_CHECKPOINT_INHIBITOR), pattern.requiredStates)
        assertEquals(setOf(CancerTherapyDrugClass.ICI_PD1_PDL1), pattern.requiredDrugClasses)
    }

    @Test
    fun ici_pneumonitis_carries_URGENT_severity_floor() {
        // ICI pneumonitis is the leading mortality driver among ICI irAEs.
        assertEquals(AlertTier.URGENT, CardioOncologyPatterns.iciPneumonitisScreen.severityFloor)
    }

    @Test
    fun ici_pneumonitis_anchors_on_SpO2_decline() {
        val pattern = CardioOncologyPatterns.iciPneumonitisScreen
        val spo2 = pattern.signalRules.first { it.metricType == MetricType.BLOOD_OXYGEN }
        assertEquals(DeviationDirection.BELOW, spo2.direction)
        assertFalse("SpO2 is baseline-relative, not absolute", spo2.isAbsolute)
    }

    // -- ICI thyroiditis screen --

    @Test
    fun ici_thyroiditis_gates_on_ICI_state_and_drug_class() {
        val pattern = CardioOncologyPatterns.iciThyroiditisScreen
        assertEquals(setOf(PhysiologyState.ON_IMMUNE_CHECKPOINT_INHIBITOR), pattern.requiredStates)
        assertEquals(setOf(CancerTherapyDrugClass.ICI_PD1_PDL1), pattern.requiredDrugClasses)
    }

    @Test
    fun ici_thyroiditis_requires_TSH_lab_as_anchor() {
        val pattern = CardioOncologyPatterns.iciThyroiditisScreen
        val tsh = pattern.signalRules.first { it.metricType == MetricType.TSH }
        assertTrue("TSH must be required so wearable signals alone never fire", tsh.required)
        assertTrue(tsh.isAbsolute)
        assertEquals(0.4, tsh.absoluteBelow!!, 1e-9)
    }

    // -- ICI colitis screen --

    @Test
    fun ici_colitis_gates_on_ICI_state_and_drug_class() {
        val pattern = CardioOncologyPatterns.iciColitisScreen
        assertEquals(setOf(PhysiologyState.ON_IMMUNE_CHECKPOINT_INHIBITOR), pattern.requiredStates)
        assertEquals(setOf(CancerTherapyDrugClass.ICI_PD1_PDL1), pattern.requiredDrugClasses)
    }

    @Test
    fun ici_colitis_uses_advisory_default_severity() {
        // ICI colitis grade 1-2 is not an emergency; suggestedAction
        // captures the grade-3+ branch with the diarrhoea-severity gate.
        assertNull(CardioOncologyPatterns.iciColitisScreen.severityFloor)
    }

    // -- cachexia screen --

    @Test
    fun cachexia_screen_gates_on_any_cancer_treatment_state() {
        val pattern = CardioOncologyPatterns.cancerCachexiaScreen
        // Class-agnostic — cachexia is treatment-class-agnostic.
        assertEquals(PhysiologyState.CANCER_TREATMENT, pattern.requiredStates)
        assertTrue(pattern.requiredDrugClasses.isEmpty())
    }

    @Test
    fun cachexia_anchors_on_body_mass_trajectory() {
        val pattern = CardioOncologyPatterns.cancerCachexiaScreen
        val bodyMass = pattern.signalRules.first { it.metricType == MetricType.BODY_MASS }
        assertEquals(DeviationDirection.BELOW, bodyMass.direction)
    }

    // -- citation hygiene --

    @Test
    fun every_rule_carries_a_literature_citation() {
        for (pattern in CardioOncologyPatterns.all) {
            for (rule in pattern.signalRules) {
                assertEquals(
                    "${pattern.id}/${rule.metricType.key} should be LITERATURE-sourced",
                    ThresholdSource.LITERATURE,
                    rule.source,
                )
                assertTrue(
                    "${pattern.id}/${rule.metricType.key} should ship a citation string",
                    rule.citation.isNotBlank(),
                )
            }
        }
    }

    @Test
    fun every_pattern_lists_at_least_one_guideline_reference() {
        for (pattern in CardioOncologyPatterns.all) {
            assertTrue(
                "${pattern.id} should list at least one guideline reference",
                pattern.references.isNotEmpty(),
            )
        }
    }

    // -- AlertContentPolicy compliance --

    @Test
    fun every_pattern_passes_AlertContentPolicy() {
        // The push-side unsolicited-judgment ban applies to all condition
        // text — title, explanation, suggestedAction, and the optional
        // pull-side fields. Cardio-oncology patterns are especially
        // sensitive: phrasing slippage from "vitals suggestive of …" into
        // "you have …" would violate the manifesto.
        for (pattern in CardioOncologyPatterns.all) {
            val violations = com.bios.app.alerts.AlertContentPolicy.validate(pattern)
            assertTrue(
                "${pattern.id} violates AlertContentPolicy: " +
                    violations.joinToString(", ") { "${it.field}:${it.prohibitedPhrase}" },
                violations.isEmpty(),
            )
        }
    }
}
