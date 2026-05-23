package com.bios.app

import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.DeviationDirection
import com.bios.app.alerts.EmergencyVitalPatterns
import com.bios.app.alerts.PaediatricEmergencyVitalPatterns
import com.bios.app.alerts.ThresholdSource
import com.bios.app.model.AlertTier
import com.bios.app.physiology.PhysiologyState
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the PALS-anchored paediatric emergency vital-sign patterns
 * (#198). Each band gets one tachycardia + one bradycardia pattern,
 * scoped via [com.bios.app.alerts.ConditionPattern.requiredStates] to
 * the matching [PhysiologyState] sub-band.
 *
 * Threshold table the test pins (per the audit issue):
 *   NEONATE   tachy ≥220   brady ≤80
 *   INFANT    tachy ≥200   brady ≤60
 *   TODDLER   tachy ≥180   brady ≤50
 *   PRESCHOOL tachy ≥160   brady ≤45
 *   SCHOOL    tachy ≥140   brady ≤40
 *   ADOLESCENT tachy ≥135  brady ≤40
 */
class PaediatricEmergencyVitalPatternsTest {

    // -- registration --

    @Test
    fun all_paediatric_emergency_patterns_register_in_global_all_list() {
        for (pattern in PaediatricEmergencyVitalPatterns.all) {
            assertTrue(
                "${pattern.id} should be in ConditionPatterns.all",
                pattern in ConditionPatterns.all,
            )
        }
        // 6 tachy + 6 brady = 12 patterns total.
        assertEquals(12, PaediatricEmergencyVitalPatterns.all.size)
    }

    @Test
    fun paediatric_emergency_patterns_are_picked_up_by_emergency_vital_patterns_aggregate() {
        // EmergencyVitalPatterns.all aggregates adult + paediatric so a
        // single import surface stays the entry point. Verify the
        // paediatric set is included.
        for (pattern in PaediatricEmergencyVitalPatterns.all) {
            assertTrue(
                "${pattern.id} should appear in EmergencyVitalPatterns.all",
                pattern in EmergencyVitalPatterns.all,
            )
        }
    }

    // -- shape contract --

    @Test
    fun every_paediatric_pattern_is_a_single_rule_required_absolute_gate() {
        for (pattern in PaediatricEmergencyVitalPatterns.all) {
            assertEquals(
                "${pattern.id} should fire on a single absolute rule",
                1,
                pattern.signalRules.size,
            )
            assertEquals(1, pattern.minActiveSignals)
            val rule = pattern.signalRules.single()
            assertEquals(MetricType.RESTING_HEART_RATE, rule.metricType)
            assertTrue("${pattern.id} rule should be required", rule.required)
            assertTrue("${pattern.id} rule should be absolute", rule.isAbsolute)
            assertEquals(ThresholdSource.LITERATURE, rule.source)
            assertTrue(
                "${pattern.id} should ship a citation",
                rule.citation.isNotBlank(),
            )
            assertEquals(AlertTier.URGENT, pattern.severityFloor)
            assertEquals(
                "${pattern.id} should be gated by exactly one requiredState",
                1,
                pattern.requiredStates.size,
            )
        }
    }

    @Test
    fun every_paediatric_pattern_carries_professional_referral_language() {
        for (pattern in PaediatricEmergencyVitalPatterns.all) {
            val action = pattern.suggestedAction
            assertNotNull("${pattern.id} should ship a suggestedAction", action)
            assertTrue(
                "${pattern.id} suggestedAction should reference professional / emergency care",
                action!!.contains("medical", ignoreCase = true) ||
                    action.contains("emergency", ignoreCase = true) ||
                    action.contains("healthcare", ignoreCase = true) ||
                    action.contains("provider", ignoreCase = true) ||
                    action.contains("paediatric", ignoreCase = true),
            )
        }
    }

    @Test
    fun paediatric_patterns_never_claim_diagnosis() {
        // AlertContentPolicy guard: the text reports what the threshold
        // crossed, not "your child has X."
        val forbidden = listOf(
            "your child has",
            "has sepsis",
            "has tachyarrhythmia",
            "diagnosed",
        )
        for (pattern in PaediatricEmergencyVitalPatterns.all) {
            val combined = listOfNotNull(
                pattern.explanation, pattern.suggestedAction, pattern.earlyDetection,
            ).joinToString(" ").lowercase()
            for (phrase in forbidden) {
                assertTrue(
                    "${pattern.id} should not contain '$phrase'",
                    !combined.contains(phrase),
                )
            }
        }
    }

    // -- tachycardia ceilings --

    @Test
    fun neonate_tachycardia_fires_at_or_above_220_bpm() {
        assertCeiling(PaediatricEmergencyVitalPatterns.neonateTachycardia, 220.0)
        assertRequiredState(
            PaediatricEmergencyVitalPatterns.neonateTachycardia,
            PhysiologyState.NEONATE_0_28D,
        )
    }

    @Test
    fun infant_tachycardia_fires_at_or_above_200_bpm() {
        assertCeiling(PaediatricEmergencyVitalPatterns.infantTachycardia, 200.0)
        assertRequiredState(
            PaediatricEmergencyVitalPatterns.infantTachycardia,
            PhysiologyState.INFANT_1M_12M,
        )
    }

    @Test
    fun toddler_tachycardia_fires_at_or_above_180_bpm() {
        // The audit's specific example: a healthy toddler's resting HR
        // routinely sits at 120; the adult ≥130 cutoff false-fires.
        // The PALS-anchored toddler ceiling is 180.
        assertCeiling(PaediatricEmergencyVitalPatterns.toddlerTachycardia, 180.0)
        assertRequiredState(
            PaediatricEmergencyVitalPatterns.toddlerTachycardia,
            PhysiologyState.TODDLER_1Y_3Y,
        )
    }

    @Test
    fun preschool_tachycardia_fires_at_or_above_160_bpm() {
        assertCeiling(PaediatricEmergencyVitalPatterns.preschoolTachycardia, 160.0)
        assertRequiredState(
            PaediatricEmergencyVitalPatterns.preschoolTachycardia,
            PhysiologyState.PRESCHOOL_3Y_5Y,
        )
    }

    @Test
    fun school_age_tachycardia_fires_at_or_above_140_bpm() {
        assertCeiling(PaediatricEmergencyVitalPatterns.schoolAgeTachycardia, 140.0)
        assertRequiredState(
            PaediatricEmergencyVitalPatterns.schoolAgeTachycardia,
            PhysiologyState.SCHOOL_AGE_6Y_12Y,
        )
    }

    @Test
    fun adolescent_tachycardia_fires_at_or_above_135_bpm() {
        assertCeiling(PaediatricEmergencyVitalPatterns.adolescentTachycardia, 135.0)
        assertRequiredState(
            PaediatricEmergencyVitalPatterns.adolescentTachycardia,
            PhysiologyState.ADOLESCENT_13Y_18Y,
        )
    }

    // -- bradycardia floors --

    @Test
    fun neonate_bradycardia_fires_at_or_below_80_bpm() {
        // The audit's specific example: a neonate at 80 bpm is in real
        // bradycardia; the adult ≤35 floor stays silent.
        assertFloor(PaediatricEmergencyVitalPatterns.neonateBradycardia, 80.0)
        assertRequiredState(
            PaediatricEmergencyVitalPatterns.neonateBradycardia,
            PhysiologyState.NEONATE_0_28D,
        )
    }

    @Test
    fun infant_bradycardia_fires_at_or_below_60_bpm() {
        assertFloor(PaediatricEmergencyVitalPatterns.infantBradycardia, 60.0)
        assertRequiredState(
            PaediatricEmergencyVitalPatterns.infantBradycardia,
            PhysiologyState.INFANT_1M_12M,
        )
    }

    @Test
    fun toddler_bradycardia_fires_at_or_below_50_bpm() {
        assertFloor(PaediatricEmergencyVitalPatterns.toddlerBradycardia, 50.0)
        assertRequiredState(
            PaediatricEmergencyVitalPatterns.toddlerBradycardia,
            PhysiologyState.TODDLER_1Y_3Y,
        )
    }

    @Test
    fun preschool_bradycardia_fires_at_or_below_45_bpm() {
        assertFloor(PaediatricEmergencyVitalPatterns.preschoolBradycardia, 45.0)
        assertRequiredState(
            PaediatricEmergencyVitalPatterns.preschoolBradycardia,
            PhysiologyState.PRESCHOOL_3Y_5Y,
        )
    }

    @Test
    fun school_age_bradycardia_fires_at_or_below_40_bpm() {
        assertFloor(PaediatricEmergencyVitalPatterns.schoolAgeBradycardia, 40.0)
        assertRequiredState(
            PaediatricEmergencyVitalPatterns.schoolAgeBradycardia,
            PhysiologyState.SCHOOL_AGE_6Y_12Y,
        )
    }

    @Test
    fun adolescent_bradycardia_fires_at_or_below_40_bpm() {
        assertFloor(PaediatricEmergencyVitalPatterns.adolescentBradycardia, 40.0)
        assertRequiredState(
            PaediatricEmergencyVitalPatterns.adolescentBradycardia,
            PhysiologyState.ADOLESCENT_13Y_18Y,
        )
    }

    // -- adult patterns are now suppressed for paediatric owners --

    @Test
    fun adult_tachycardia_excludes_every_paediatric_band() {
        for (state in PhysiologyState.PAEDIATRIC_ALL) {
            assertTrue(
                "adult tachycardia should be excluded for $state",
                state in EmergencyVitalPatterns.tachycardiaCritical.excludedStates,
            )
        }
    }

    @Test
    fun adult_bradycardia_excludes_every_paediatric_band() {
        for (state in PhysiologyState.PAEDIATRIC_ALL) {
            assertTrue(
                "adult bradycardia should be excluded for $state",
                state in EmergencyVitalPatterns.bradycardiaCritical.excludedStates,
            )
        }
    }

    // -- helpers --

    private fun assertCeiling(
        pattern: com.bios.app.alerts.ConditionPattern,
        expected: Double,
    ) {
        val rule = pattern.signalRules.single()
        assertEquals(DeviationDirection.ABOVE, rule.direction)
        assertEquals(expected, rule.absoluteAbove!!, 1e-9)
        assertNull(rule.absoluteBelow)
    }

    private fun assertFloor(
        pattern: com.bios.app.alerts.ConditionPattern,
        expected: Double,
    ) {
        val rule = pattern.signalRules.single()
        assertEquals(DeviationDirection.BELOW, rule.direction)
        assertEquals(expected, rule.absoluteBelow!!, 1e-9)
        assertNull(rule.absoluteAbove)
    }

    private fun assertRequiredState(
        pattern: com.bios.app.alerts.ConditionPattern,
        expected: PhysiologyState,
    ) {
        assertEquals(setOf(expected), pattern.requiredStates)
    }
}
