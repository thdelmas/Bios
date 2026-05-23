package com.bios.app

import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.DeviationDirection
import com.bios.app.alerts.EmergencyVitalPatterns
import com.bios.app.alerts.EnvironmentalPatterns
import com.bios.app.alerts.ThresholdSource
import com.bios.app.model.ConditionCategory
import com.bios.app.model.ElevationBand
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the environmental-context condition patterns from audit gap #197.
 * Five audits converge on this: SOWA_RIGPA §2.1 (altitude), Indigenous
 * Americas (Andes/Mesoamerica), Oceanic/Arctic (cold + Pacific heat),
 * African Traditional (climate), Modern Non-Allopathic (environmental
 * medicine).
 */
class EnvironmentalPatternsTest {

    // -- registration --

    @Test
    fun all_environmental_patterns_register_in_global_all_list() {
        assertTrue(EnvironmentalPatterns.heatExhaustionScreen in ConditionPatterns.all)
        assertTrue(EnvironmentalPatterns.hypothermiaScreen in ConditionPatterns.all)
        assertTrue(EnvironmentalPatterns.altitudeSicknessScreen in ConditionPatterns.all)
        assertTrue(EnvironmentalPatterns.saadWinterPattern in ConditionPatterns.all)
    }

    @Test
    fun every_environmental_pattern_is_classified_environmental_category() {
        for (pattern in EnvironmentalPatterns.all) {
            assertEquals(
                "${pattern.id} should classify as ENVIRONMENTAL category",
                ConditionCategory.ENVIRONMENTAL,
                pattern.category
            )
        }
    }

    @Test
    fun no_environmental_pattern_declares_an_urgent_severity_floor() {
        // Per the manifesto framing the audit cross-references: heat-stroke
        // and severe hypothermia hard-cutoffs would go in EmergencyVitalPatterns
        // (a separate URGENT-tier path); these advisory-tier screens stop at
        // the classifier's default ceiling.
        for (pattern in EnvironmentalPatterns.all) {
            assertNull(
                "${pattern.id} should leave severityFloor null — advisory only",
                pattern.severityFloor
            )
        }
    }

    @Test
    fun every_environmental_pattern_carries_at_least_one_literature_citation() {
        for (pattern in EnvironmentalPatterns.all) {
            assertTrue(
                "${pattern.id} should ship at least one citation",
                pattern.references.isNotEmpty()
            )
        }
    }

    // -- heat_exhaustion_screen --

    @Test
    fun heat_exhaustion_requires_ambient_temp_above_32C_as_gate() {
        val pattern = EnvironmentalPatterns.heatExhaustionScreen
        val gate = pattern.signalRules.first { it.metricType == MetricType.AMBIENT_TEMPERATURE_C }
        assertTrue("ambient temp rule should be required", gate.required)
        assertTrue("ambient temp rule should be an absolute cutoff", gate.isAbsolute)
        assertEquals(32.0, gate.absoluteAbove!!, 1e-9)
        assertEquals(DeviationDirection.ABOVE, gate.direction)
        assertEquals(ThresholdSource.LITERATURE, gate.source)
    }

    @Test
    fun heat_exhaustion_includes_skin_temp_tachycardia_activity_drop_corroborators() {
        val pattern = EnvironmentalPatterns.heatExhaustionScreen
        val types = pattern.signalRules.map { it.metricType }.toSet()
        assertTrue(MetricType.SKIN_TEMPERATURE_DEVIATION in types)
        assertTrue(MetricType.HEART_RATE in types)
        assertTrue(MetricType.STEPS in types)
    }

    // -- hypothermia_screen --

    @Test
    fun hypothermia_requires_ambient_temp_below_5C_as_gate() {
        val pattern = EnvironmentalPatterns.hypothermiaScreen
        val gate = pattern.signalRules.first { it.metricType == MetricType.AMBIENT_TEMPERATURE_C }
        assertTrue("ambient temp rule should be required", gate.required)
        assertTrue("ambient temp rule should be an absolute cutoff", gate.isAbsolute)
        assertEquals(5.0, gate.absoluteBelow!!, 1e-9)
        assertEquals(DeviationDirection.BELOW, gate.direction)
    }

    // -- altitude_sickness_screen --

    @Test
    fun altitude_sickness_signals_cover_tachycardia_sleep_resp_rate_and_spo2() {
        val pattern = EnvironmentalPatterns.altitudeSicknessScreen
        val types = pattern.signalRules.map { it.metricType }.toSet()
        assertTrue(MetricType.HEART_RATE in types)
        assertTrue(MetricType.SLEEP_EFFICIENCY in types)
        assertTrue(MetricType.RESPIRATORY_RATE in types)
        assertTrue(MetricType.BLOOD_OXYGEN in types)
        assertEquals(3, pattern.minActiveSignals)
    }

    // -- saad_winter_pattern --

    @Test
    fun saad_winter_requires_short_day_photoperiod_as_gate() {
        val pattern = EnvironmentalPatterns.saadWinterPattern
        val gate = pattern.signalRules.first { it.metricType == MetricType.DAYLIGHT_HOURS }
        assertTrue("daylight rule should be required", gate.required)
        assertEquals(10.0, gate.absoluteBelow!!, 1e-9)
    }

    @Test
    fun saad_winter_explanation_clarifies_not_a_diagnosis() {
        val pattern = EnvironmentalPatterns.saadWinterPattern
        val explanation = pattern.explanation.lowercase()
        // The pattern body must signal that this is a data observation, not a
        // diagnostic claim. Matches the PSYCHIATRY_POV cross-reference.
        assertTrue(
            "saad_winter explanation should clarify the observation-only posture",
            explanation.contains("observation") || explanation.contains("data")
        )
        assertTrue(
            "saad_winter explanation should not claim a diagnosis",
            !explanation.contains("you have seasonal") && !explanation.contains("diagnosed")
        )
    }

    // -- elevation-adjusted SpO2 cutoff (the spo2Critical fix) --

    @Test
    fun spo2_critical_falls_to_78_above_3500m() {
        val rule = EmergencyVitalPatterns.spo2Critical.signalRules.single()
        // Sea-level baseline preserved.
        assertEquals(85.0, rule.absoluteBelow!!, 1e-9)
        // Sea-level lookup returns the base cutoff.
        assertEquals(85.0, rule.resolvedAbsoluteBelow(ElevationBand.SEA_LEVEL)!!, 1e-9)
        // VERY_HIGH: 2500–3500 m → 80 % cutoff
        assertEquals(80.0, rule.resolvedAbsoluteBelow(ElevationBand.VERY_HIGH)!!, 1e-9)
        // EXTREME: > 3500 m → 78 % cutoff (the SOWA_RIGPA audit case)
        assertEquals(78.0, rule.resolvedAbsoluteBelow(ElevationBand.EXTREME)!!, 1e-9)
    }

    @Test
    fun spo2_critical_null_band_falls_back_to_base_cutoff() {
        val rule = EmergencyVitalPatterns.spo2Critical.signalRules.single()
        // Owner with no recorded environmental context → sea-level cutoff.
        assertEquals(85.0, rule.resolvedAbsoluteBelow(null)!!, 1e-9)
    }

    @Test
    fun spo2_critical_keeps_severity_floor_urgent_with_elevation_adjustment() {
        // The elevation modulation must not weaken the URGENT escalation
        // path — a confirmed sub-floor reading is still URGENT, just at the
        // altitude-adjusted threshold.
        val pattern = EmergencyVitalPatterns.spo2Critical
        assertNotNull(pattern.severityFloor)
        assertEquals(
            "spo2Critical should remain URGENT after the altitude adjustment",
            com.bios.app.model.AlertTier.URGENT,
            pattern.severityFloor
        )
    }
}
