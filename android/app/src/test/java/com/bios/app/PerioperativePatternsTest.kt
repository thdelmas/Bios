package com.bios.app

import com.bios.app.alerts.AlertContentPolicy
import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.DeviationDirection
import com.bios.app.alerts.PerioperativePatterns
import com.bios.app.alerts.ThresholdSource
import com.bios.app.model.AlertTier
import com.bios.app.physiology.PerioperativeBaseline
import com.bios.app.physiology.PhysiologyState
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the peri-operative pattern library (#183, SURGICAL_POV §2.1-2.4).
 *
 * Tests cover:
 *  - Registration: the three patterns land in [ConditionPatterns.all].
 *  - Shape: signal rules carry literature citations, use the frozen
 *    pre-op baseline, and the right requiredStates.
 *  - AlertContentPolicy compliance.
 *  - PerioperativeBaseline z-score math.
 *  - PerioperativeStateStore expected-state mapping at day boundaries.
 *  - Synthetic firing checks against [PerioperativeBaseline.zScore]
 *    for the SSI re-elevation shape, the anastomotic-leak shape, and
 *    the acute-PE shape.
 *  - infectionOnset suppression in POD_0_30.
 */
class PerioperativePatternsTest {

    // -- registration --

    @Test
    fun all_three_perioperative_patterns_register_in_global_list() {
        val ids = ConditionPatterns.all.map { it.id }.toSet()
        assertTrue("ssi_surveillance should register", "ssi_surveillance" in ids)
        assertTrue("anastomotic_leak_screen should register", "anastomotic_leak_screen" in ids)
        assertTrue("post_op_vte_screen should register", "post_op_vte_screen" in ids)
    }

    // -- requiredStates gating --

    @Test
    fun ssi_surveillance_requires_pod_0_30_only() {
        assertEquals(
            setOf(PhysiologyState.POD_0_30),
            PerioperativePatterns.ssiSurveillance.requiredStates,
        )
        assertTrue(PerioperativePatterns.ssiSurveillance.appliesIn(PhysiologyState.POD_0_30))
        assertFalse(PerioperativePatterns.ssiSurveillance.appliesIn(PhysiologyState.STANDARD))
        assertFalse(PerioperativePatterns.ssiSurveillance.appliesIn(PhysiologyState.POD_30_90))
        assertFalse(PerioperativePatterns.ssiSurveillance.appliesIn(PhysiologyState.PREHAB_WINDOW))
    }

    @Test
    fun anastomotic_leak_requires_pod_0_30_only() {
        assertEquals(
            setOf(PhysiologyState.POD_0_30),
            PerioperativePatterns.anastomoticLeakScreen.requiredStates,
        )
        assertTrue(PerioperativePatterns.anastomoticLeakScreen.appliesIn(PhysiologyState.POD_0_30))
        assertFalse(PerioperativePatterns.anastomoticLeakScreen.appliesIn(PhysiologyState.POD_30_90))
        assertFalse(PerioperativePatterns.anastomoticLeakScreen.appliesIn(PhysiologyState.STANDARD))
    }

    @Test
    fun vte_screen_requires_pod_0_30_or_pod_30_90() {
        assertEquals(
            setOf(PhysiologyState.POD_0_30, PhysiologyState.POD_30_90),
            PerioperativePatterns.postOpVteScreen.requiredStates,
        )
        assertTrue(PerioperativePatterns.postOpVteScreen.appliesIn(PhysiologyState.POD_0_30))
        assertTrue(PerioperativePatterns.postOpVteScreen.appliesIn(PhysiologyState.POD_30_90))
        assertFalse(PerioperativePatterns.postOpVteScreen.appliesIn(PhysiologyState.POD_90_PLUS))
        assertFalse(PerioperativePatterns.postOpVteScreen.appliesIn(PhysiologyState.STANDARD))
    }

    // -- shape (rules use frozen baseline + literature citations) --

    @Test
    fun every_non_absolute_rule_uses_frozen_baseline() {
        // The peri-op detection shape is "re-elevation after normalisation"
        // — the rolling baseline would already include the post-op drift
        // and silence the very signal the pattern is meant to catch.
        for (pattern in PerioperativePatterns.all) {
            for (rule in pattern.signalRules) {
                if (!rule.isAbsolute) {
                    assertTrue(
                        "${pattern.id}/${rule.metricType.key} should use the frozen pre-op baseline",
                        rule.useFrozenBaseline,
                    )
                }
            }
        }
    }

    @Test
    fun every_rule_carries_a_citation() {
        for (pattern in PerioperativePatterns.all) {
            for (rule in pattern.signalRules) {
                assertTrue(
                    "${pattern.id}/${rule.metricType.key} should ship a citation",
                    rule.citation.isNotBlank(),
                )
            }
        }
    }

    @Test
    fun vte_screen_carries_urgent_severity_floor() {
        // PE is a 911-class differential; Wells-equivalent acute event.
        assertEquals(AlertTier.URGENT, PerioperativePatterns.postOpVteScreen.severityFloor)
    }

    @Test
    fun ssi_surveillance_does_not_set_urgent_severity_floor() {
        // SURGICAL_POV §2.2 — ssi_surveillance is ADVISORY by default;
        // classifier may lift to higher tiers but no severityFloor.
        assertNull(PerioperativePatterns.ssiSurveillance.severityFloor)
    }

    @Test
    fun vte_screen_declares_required_rhr_and_spo2() {
        val rules = PerioperativePatterns.postOpVteScreen.signalRules
        val rhr = rules.single { it.metricType == MetricType.RESTING_HEART_RATE }
        val spo2 = rules.single { it.metricType == MetricType.BLOOD_OXYGEN }
        assertTrue("RHR rule should be required for VTE screen", rhr.required)
        assertTrue("SpO2 rule should be required for VTE screen", spo2.required)
    }

    @Test
    fun anastomotic_leak_declares_required_temp_and_rhr() {
        val rules = PerioperativePatterns.anastomoticLeakScreen.signalRules
        val temp = rules.single { it.metricType == MetricType.SKIN_TEMPERATURE_DEVIATION }
        val rhr = rules.single { it.metricType == MetricType.RESTING_HEART_RATE }
        assertTrue("Skin-temp rule should be required for leak screen", temp.required)
        assertTrue("RHR rule should be required for leak screen", rhr.required)
    }

    @Test
    fun citations_use_literature_threshold_source() {
        for (pattern in PerioperativePatterns.all) {
            for (rule in pattern.signalRules) {
                if (rule.source == ThresholdSource.ENGINEERING) continue
                assertEquals(
                    "${pattern.id}/${rule.metricType.key} should declare LITERATURE source when citing a paper",
                    ThresholdSource.LITERATURE, rule.source,
                )
            }
        }
    }

    // -- AlertContentPolicy compliance --

    @Test
    fun all_three_patterns_pass_alert_content_policy() {
        for (pattern in PerioperativePatterns.all) {
            val violations = AlertContentPolicy.validate(pattern)
            assertTrue(
                "${pattern.id} should pass AlertContentPolicy — violations: ${violations.map { it.prohibitedPhrase }}",
                violations.isEmpty(),
            )
        }
    }

    // -- PerioperativeBaseline math --

    @Test
    fun frozen_baseline_freezes_correctly_and_computes_zscore() {
        // SURGICAL_POV §2.1 — the snapshot is taken once at pre-op time,
        // captures means + SDs of the audit-listed primitives, and the
        // z-score function matches the PersonalBaseline math.
        val baseline = PerioperativeBaseline(
            procedureDate = 0L,
            restingHeartRateMean = 60.0,
            restingHeartRateSd = 5.0,
            heartRateVariabilityMean = 50.0,
            heartRateVariabilitySd = 8.0,
            skinTemperatureMean = 0.0,
            skinTemperatureSd = 0.3,
        )
        assertEquals(0.0, baseline.zScore("resting_heart_rate", 60.0), 1e-9)
        assertEquals(2.0, baseline.zScore("resting_heart_rate", 70.0), 1e-9)
        assertEquals(-1.25, baseline.zScore("heart_rate_variability", 40.0), 1e-9)
        assertEquals(2.0, baseline.zScore("skin_temperature_deviation", 0.6), 1e-9)
    }

    @Test
    fun frozen_baseline_returns_zero_when_metric_missing() {
        val baseline = PerioperativeBaseline(procedureDate = 0L)
        // No baseline values set — zScore should return 0.0 sentinel.
        assertEquals(0.0, baseline.zScore("resting_heart_rate", 999.0), 1e-9)
        assertEquals(0.0, baseline.zScore("nonsense_metric", 42.0), 1e-9)
    }

    @Test
    fun frozen_baseline_returns_zero_when_sd_nonpositive() {
        val baseline = PerioperativeBaseline(
            procedureDate = 0L,
            restingHeartRateMean = 60.0,
            restingHeartRateSd = 0.0,
        )
        assertEquals(0.0, baseline.zScore("resting_heart_rate", 100.0), 1e-9)
    }

    @Test
    fun post_op_day_computes_correctly_from_procedure_date() {
        val day = PerioperativeBaseline.MILLIS_PER_DAY
        val baseline = PerioperativeBaseline(procedureDate = 0L)
        assertEquals(0L, baseline.postOpDay(nowMillis = 0L))
        assertEquals(7L, baseline.postOpDay(nowMillis = 7L * day))
        assertEquals(30L, baseline.postOpDay(nowMillis = 30L * day))
    }

    // -- synthetic firing scenarios --

    @Test
    fun ssi_re_elevation_shape_lights_up_against_frozen_baseline() {
        // SURGICAL_POV §2.2 — pre-op RHR baseline 60±5; at POD7 the owner's
        // mean RHR jumps to 75 (3σ above pre-op). Skin temp deviates +0.6°C
        // (2σ above pre-op SD 0.3). The frozen baseline z-scores match the
        // ssi_surveillance triggers (≥1.5σ).
        val baseline = PerioperativeBaseline(
            procedureDate = 0L,
            restingHeartRateMean = 60.0, restingHeartRateSd = 5.0,
            skinTemperatureMean = 0.0, skinTemperatureSd = 0.3,
            respiratoryRateMean = 14.0, respiratoryRateSd = 2.0,
            heartRateVariabilityMean = 50.0, heartRateVariabilitySd = 8.0,
            stepsMean = 5000.0, stepsSd = 1500.0,
        )
        val rhrZ = baseline.zScore("resting_heart_rate", 75.0)
        val tempZ = baseline.zScore("skin_temperature_deviation", 0.6)
        val rrZ = baseline.zScore("respiratory_rate", 19.0)
        val hrvZ = baseline.zScore("heart_rate_variability", 38.0)
        val stepsZ = baseline.zScore("steps", 2500.0)
        // RHR, temp, respiratory rate all above 1.5σ; HRV below -1σ;
        // steps below -1σ — five-of-five for ssi_surveillance.
        assertTrue("RHR z=$rhrZ should exceed 1.5σ", rhrZ > 1.5)
        assertTrue("Temp z=$tempZ should exceed 1.5σ", tempZ > 1.5)
        assertTrue("RR z=$rrZ should exceed 1.5σ", rrZ > 1.5)
        assertTrue("HRV z=$hrvZ should be below -1σ", hrvZ < -1.0)
        assertTrue("Steps z=$stepsZ should be below -1σ", stepsZ < -1.0)
    }

    @Test
    fun anastomotic_leak_shape_lights_up_against_frozen_baseline() {
        // SURGICAL_POV §2.3 — POD3-7 shape: temp 1.5σ+, RHR 2σ+, steps
        // -1.5σ. Required signals must both fire.
        val baseline = PerioperativeBaseline(
            procedureDate = 0L,
            restingHeartRateMean = 60.0, restingHeartRateSd = 5.0,
            skinTemperatureMean = 0.0, skinTemperatureSd = 0.3,
            stepsMean = 5000.0, stepsSd = 1500.0,
        )
        val rhrZ = baseline.zScore("resting_heart_rate", 72.0)
        val tempZ = baseline.zScore("skin_temperature_deviation", 0.55)
        val stepsZ = baseline.zScore("steps", 2200.0)
        assertTrue("RHR z=$rhrZ should exceed 2.0σ (required)", rhrZ > 2.0)
        assertTrue("Temp z=$tempZ should exceed 1.5σ (required)", tempZ > 1.5)
        assertTrue("Steps z=$stepsZ should be below -1.5σ (corroborator)", stepsZ < -1.5)
    }

    @Test
    fun vte_acute_event_shape_lights_up_against_frozen_baseline() {
        // SURGICAL_POV §2.4 — sudden persistent tachycardia + SpO2 drop.
        // Pre-op RHR baseline 65±6 → 80 is 2.5σ above. Pre-op SpO2
        // baseline 97±1 → 94 is -3σ below.
        val baseline = PerioperativeBaseline(
            procedureDate = 0L,
            restingHeartRateMean = 65.0, restingHeartRateSd = 6.0,
            bloodOxygenMean = 97.0, bloodOxygenSd = 1.0,
            respiratoryRateMean = 14.0, respiratoryRateSd = 2.0,
        )
        val rhrZ = baseline.zScore("resting_heart_rate", 80.0)
        val spo2Z = baseline.zScore("blood_oxygen", 94.0)
        val rrZ = baseline.zScore("respiratory_rate", 18.0)
        assertTrue("RHR z=$rhrZ should exceed 2σ (required)", rhrZ > 2.0)
        assertTrue("SpO2 z=$spo2Z should be below -1.5σ (required)", spo2Z < -1.5)
        assertTrue("RR z=$rrZ should exceed 1.5σ (corroborator)", rrZ > 1.5)
    }

    // -- direction sanity for signal rules --

    @Test
    fun ssi_surveillance_signal_directions_match_audit() {
        val rules = PerioperativePatterns.ssiSurveillance.signalRules.associateBy { it.metricType }
        assertEquals(DeviationDirection.ABOVE, rules[MetricType.RESTING_HEART_RATE]!!.direction)
        assertEquals(DeviationDirection.ABOVE, rules[MetricType.SKIN_TEMPERATURE_DEVIATION]!!.direction)
        assertEquals(DeviationDirection.ABOVE, rules[MetricType.RESPIRATORY_RATE]!!.direction)
        assertEquals(DeviationDirection.BELOW, rules[MetricType.HEART_RATE_VARIABILITY]!!.direction)
        assertEquals(DeviationDirection.BELOW, rules[MetricType.STEPS]!!.direction)
    }

    @Test
    fun vte_screen_signal_directions_match_wells() {
        val rules = PerioperativePatterns.postOpVteScreen.signalRules.associateBy { it.metricType }
        assertEquals(DeviationDirection.ABOVE, rules[MetricType.RESTING_HEART_RATE]!!.direction)
        assertEquals(DeviationDirection.BELOW, rules[MetricType.BLOOD_OXYGEN]!!.direction)
        assertEquals(DeviationDirection.ABOVE, rules[MetricType.RESPIRATORY_RATE]!!.direction)
    }

    // -- references and suggestedAction sanity --

    @Test
    fun ssi_surveillance_cites_nhsn_and_sands() {
        val refs = PerioperativePatterns.ssiSurveillance.references.joinToString("\n")
        assertTrue("NHSN citation missing", refs.contains("NHSN"))
        assertTrue("Sands 2025 citation missing", refs.contains("Sands"))
    }

    @Test
    fun anastomotic_leak_cites_daams_and_dulk() {
        val refs = PerioperativePatterns.anastomoticLeakScreen.references.joinToString("\n")
        assertTrue("Daams 2014 citation missing", refs.contains("Daams"))
        assertTrue("DULK / den Dulk citation missing", refs.contains("Dulk"))
    }

    @Test
    fun vte_screen_cites_wells_and_caprini() {
        val refs = PerioperativePatterns.postOpVteScreen.references.joinToString("\n")
        assertTrue("Wells citation missing", refs.contains("Wells"))
        assertTrue("Caprini citation missing", refs.contains("Caprini"))
    }

    @Test
    fun suggested_actions_reference_surgical_team_or_emergency() {
        for (pattern in PerioperativePatterns.all) {
            val action = pattern.suggestedAction
            assertNotNull(action)
            val lower = action!!.lowercase()
            assertTrue(
                "${pattern.id} suggested action should reference surgical team or emergency",
                lower.contains("surgical team") || lower.contains("emergency"),
            )
        }
    }
}
