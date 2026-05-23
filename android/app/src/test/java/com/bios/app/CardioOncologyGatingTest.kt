package com.bios.app

import com.bios.app.alerts.CardioOncologyPatterns
import com.bios.app.alerts.ConditionPattern
import com.bios.app.alerts.ConditionPatterns
import com.bios.app.physiology.CancerTherapyDrugClass
import com.bios.app.physiology.PhysiologyState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioural gate for the cardio-oncology surveillance patterns (#201):
 * mirrors [com.bios.app.engine.AnomalyDetector.applicablePatterns] so
 * the routing contract is unit-testable without a database fixture.
 *
 * The most important invariant: cardio-oncology patterns must NEVER be
 * evaluated for owners in healthy / non-treatment states. The
 * `requiredStates` axis (added in this PR alongside the long-standing
 * `excludedStates` axis) is what enforces that contract.
 *
 * Secondary invariant: most patterns are drug-class-specific. An owner
 * declaring `ON_ACTIVE_CHEMOTHERAPY` without picking a drug class still
 * gets the neutropenic-fever screen (class-agnostic) but does NOT see
 * the anthracycline or trastuzumab cardiotoxicity screens until the
 * matching class is declared.
 */
class CardioOncologyGatingTest {

    /**
     * Mirror of [com.bios.app.engine.AnomalyDetector.applicablePatterns].
     * Kept as a local copy so the test pins the contract rather than the
     * implementation; if the detector changes, this test reads as a spec.
     */
    private fun applicable(
        state: PhysiologyState,
        drugClass: CancerTherapyDrugClass,
    ): List<ConditionPattern> =
        ConditionPatterns.all.filter { pattern ->
            state !in pattern.excludedStates &&
                (pattern.requiredStates.isEmpty() || state in pattern.requiredStates) &&
                (pattern.requiredDrugClasses.isEmpty() || drugClass in pattern.requiredDrugClasses)
        }

    private fun isCardioOnc(pattern: ConditionPattern) =
        pattern in CardioOncologyPatterns.all

    // -- silence in healthy / non-treatment populations --

    @Test
    fun standard_adult_never_sees_any_cardio_oncology_pattern() {
        val applicable = applicable(PhysiologyState.STANDARD, CancerTherapyDrugClass.NONE)
        assertFalse(
            "Standard adult must never see cardio-oncology patterns — manifesto-level invariant for #201",
            applicable.any { isCardioOnc(it) },
        )
    }

    @Test
    fun no_non_treatment_state_sees_cardio_oncology_patterns() {
        // Every non-cancer state must keep the cardio-oncology surface silent.
        val nonCancerStates = PhysiologyState.entries.filter { it !in PhysiologyState.CANCER_TREATMENT }
        for (state in nonCancerStates) {
            // Even with a drug class set (e.g. residual from a past
            // regimen), the state gate alone blocks the patterns.
            for (drugClass in CancerTherapyDrugClass.entries) {
                val applicable = applicable(state, drugClass)
                assertFalse(
                    "$state must not see cardio-oncology patterns (drugClass=$drugClass)",
                    applicable.any { isCardioOnc(it) },
                )
            }
        }
    }

    // -- routing in cancer-treatment states --

    @Test
    fun chemo_state_without_drug_class_only_sees_class_agnostic_patterns() {
        // ON_ACTIVE_CHEMOTHERAPY + NONE: neutropenic fever fires (no drug
        // class restriction); anthracycline / trastuzumab screens do not
        // (they require a specific drug class).
        val applicable = applicable(PhysiologyState.ON_ACTIVE_CHEMOTHERAPY, CancerTherapyDrugClass.NONE)
        val ids = applicable.map { it.id }.toSet()
        assertTrue(
            "Neutropenic fever screen is class-agnostic and should fire on ON_ACTIVE_CHEMOTHERAPY alone",
            "cardio_onc_neutropenic_fever_screen" in ids,
        )
        assertFalse(
            "Anthracycline screen requires drug-class ANTHRACYCLINE",
            "cardio_onc_anthracycline_cardiotoxicity_screen" in ids,
        )
        assertFalse(
            "Trastuzumab screen requires drug-class TRASTUZUMAB",
            "cardio_onc_trastuzumab_cardiotoxicity_screen" in ids,
        )
    }

    @Test
    fun chemo_state_with_anthracycline_class_sees_anthracycline_screen() {
        val applicable = applicable(
            PhysiologyState.ON_ACTIVE_CHEMOTHERAPY, CancerTherapyDrugClass.ANTHRACYCLINE,
        )
        val ids = applicable.map { it.id }.toSet()
        assertTrue("cardio_onc_anthracycline_cardiotoxicity_screen" in ids)
        // Trastuzumab still suppressed.
        assertFalse("cardio_onc_trastuzumab_cardiotoxicity_screen" in ids)
    }

    @Test
    fun chemo_state_with_trastuzumab_class_sees_trastuzumab_screen() {
        val applicable = applicable(
            PhysiologyState.ON_ACTIVE_CHEMOTHERAPY, CancerTherapyDrugClass.TRASTUZUMAB,
        )
        val ids = applicable.map { it.id }.toSet()
        assertTrue("cardio_onc_trastuzumab_cardiotoxicity_screen" in ids)
        assertFalse("cardio_onc_anthracycline_cardiotoxicity_screen" in ids)
    }

    @Test
    fun ici_state_with_ICI_class_sees_pneumonitis_thyroiditis_colitis() {
        val applicable = applicable(
            PhysiologyState.ON_IMMUNE_CHECKPOINT_INHIBITOR, CancerTherapyDrugClass.ICI_PD1_PDL1,
        )
        val ids = applicable.map { it.id }.toSet()
        assertTrue("cardio_onc_ici_pneumonitis_screen" in ids)
        assertTrue("cardio_onc_ici_thyroiditis_screen" in ids)
        assertTrue("cardio_onc_ici_colitis_screen" in ids)
    }

    @Test
    fun ici_state_does_not_see_chemo_cardiotoxicity_screens() {
        val applicable = applicable(
            PhysiologyState.ON_IMMUNE_CHECKPOINT_INHIBITOR, CancerTherapyDrugClass.ICI_PD1_PDL1,
        )
        val ids = applicable.map { it.id }.toSet()
        // Anthracycline + trastuzumab screens require ON_ACTIVE_CHEMOTHERAPY,
        // not ICI state.
        assertFalse("cardio_onc_anthracycline_cardiotoxicity_screen" in ids)
        assertFalse("cardio_onc_trastuzumab_cardiotoxicity_screen" in ids)
        // Neutropenic fever screen is also chemo-gated.
        assertFalse("cardio_onc_neutropenic_fever_screen" in ids)
    }

    @Test
    fun cachexia_fires_in_every_cancer_treatment_state() {
        // Class-agnostic, state-required on the full CANCER_TREATMENT set.
        for (state in PhysiologyState.CANCER_TREATMENT) {
            val applicable = applicable(state, CancerTherapyDrugClass.NONE)
            assertTrue(
                "Cachexia screen should fire in $state",
                applicable.any { it.id == "cardio_onc_cancer_cachexia_screen" },
            )
        }
    }

    // -- legacy patterns continue to fire across all states --

    @Test
    fun standard_state_still_sees_legacy_patterns() {
        val applicable = applicable(PhysiologyState.STANDARD, CancerTherapyDrugClass.NONE)
        // Cardio-onc patterns are filtered out; everything else should be
        // visible. The legacy excludedStates contract is unchanged. Non-
        // cardio-onc patterns that ship `requiredStates` (asthma / COPD /
        // HF gating, paediatric-band patterns) won't fire in STANDARD by
        // design — those are excluded from the comparison too.
        val nonCardioOnc = applicable.filter { !isCardioOnc(it) }
        val expected = ConditionPatterns.all.filter { pattern ->
            !isCardioOnc(pattern) &&
                PhysiologyState.STANDARD !in pattern.excludedStates &&
                (pattern.requiredStates.isEmpty() || PhysiologyState.STANDARD in pattern.requiredStates)
        }
        assertTrue(
            "STANDARD should see every legacy pattern with empty/STANDARD-compatible requiredStates",
            nonCardioOnc.containsAll(expected),
        )
    }
}
