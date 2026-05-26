package com.bios.app

import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.DeviationDirection
import com.bios.app.alerts.RespiratoryExacerbationPatterns
import com.bios.app.alerts.ThresholdSource
import com.bios.app.model.AlertTier
import com.bios.app.physiology.PhysiologyState
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the COPD / asthma exacerbation patterns (#161, audit gap §2.8).
 * Both patterns gate on multi-signal convergence of baseline-relative
 * respiratory signals — distinct from the existing
 * [com.bios.app.alerts.BaselineDeviationPatterns.respiratoryInfection] pattern
 * which uses acute thresholds and temperature.
 */
class RespiratoryExacerbationPatternsTest {

    @Test
    fun both_patterns_register_in_global_all_list() {
        assertTrue(RespiratoryExacerbationPatterns.copdExacerbationSignature in ConditionPatterns.all)
        assertTrue(RespiratoryExacerbationPatterns.asthmaExacerbationSignature in ConditionPatterns.all)
        // Acute screens (#200) also register
        assertTrue(RespiratoryExacerbationPatterns.acuteCopdExacerbationScreen in ConditionPatterns.all)
        assertTrue(RespiratoryExacerbationPatterns.acuteAsthmaExacerbationScreen in ConditionPatterns.all)
    }

    @Test
    fun every_rule_carries_a_literature_citation() {
        for (pattern in RespiratoryExacerbationPatterns.all) {
            for (rule in pattern.signalRules) {
                assertEquals(
                    "${pattern.id}/${rule.metricType.key} should be LITERATURE-sourced",
                    ThresholdSource.LITERATURE,
                    rule.source
                )
                assertTrue(rule.citation.isNotBlank())
            }
        }
    }

    // -- copd_exacerbation --

    @Test
    fun copd_signature_uses_baseline_relative_SpO2_below_not_absolute_cutoff() {
        // Key clinical point: COPD owners have lower SpO2 baselines than
        // non-COPD owners. The pattern fires on baseline-relative drift,
        // not absolute SpO2 cutoffs — that's the differentiator from the
        // emergency-vital absolute-cutoff path.
        val spo2 = RespiratoryExacerbationPatterns.copdExacerbationSignature.signalRules
            .first { it.metricType == MetricType.BLOOD_OXYGEN }
        assertFalse(
            "SpO2 rule must be baseline-relative, not absolute — COPD owners have lower baselines",
            spo2.isAbsolute
        )
        assertEquals(DeviationDirection.BELOW, spo2.direction)
        assertEquals(168, spo2.minDurationHours)
    }

    @Test
    fun copd_signature_carries_RR_activity_fragmentation_corroborators() {
        val pattern = RespiratoryExacerbationPatterns.copdExacerbationSignature
        val metrics = pattern.signalRules.map { it.metricType }.toSet()
        assertTrue(MetricType.BLOOD_OXYGEN in metrics)
        assertTrue(MetricType.RESPIRATORY_RATE in metrics)
        assertTrue(MetricType.ACTIVE_MINUTES in metrics)
        assertTrue(MetricType.SLEEP_FRAGMENTATION_INDEX in metrics)
    }

    @Test
    fun copd_signature_requires_at_least_three_signals_to_fire() {
        // 4-signal pattern with 3-of-4 requirement — single-signal noise
        // doesn't fire (a 1-day flu, a strenuous-hike SpO2 drop).
        assertEquals(3, RespiratoryExacerbationPatterns.copdExacerbationSignature.minActiveSignals)
    }

    @Test
    fun copd_signature_does_not_declare_a_severity_floor() {
        // Trend / screening pattern, not an emergency — uses the
        // classifier output. The emergency SpO2 path lives in
        // EmergencyVitalPatterns.spo2Critical with absolute cutoff.
        assertNull(RespiratoryExacerbationPatterns.copdExacerbationSignature.severityFloor)
    }

    // -- asthma_exacerbation --

    @Test
    fun asthma_signature_weights_RR_and_fragmentation_higher_than_SpO2() {
        // SpO2 is a LATE sign in asthma — symptom cluster (nocturnal
        // symptoms via fragmentation, RR up) leads. The pattern weights
        // the symptom-cluster signals higher than SpO2.
        val pattern = RespiratoryExacerbationPatterns.asthmaExacerbationSignature
        val rr = pattern.signalRules.first { it.metricType == MetricType.RESPIRATORY_RATE }
        val fragmentation = pattern.signalRules.first { it.metricType == MetricType.SLEEP_FRAGMENTATION_INDEX }
        val spo2 = pattern.signalRules.first { it.metricType == MetricType.BLOOD_OXYGEN }
        assertTrue(
            "RR (${rr.weight}) should be weighted ≥ SpO2 (${spo2.weight}) — SpO2 is a late sign in asthma",
            rr.weight >= spo2.weight,
        )
        assertTrue(
            "Fragmentation (${fragmentation.weight}) should be weighted ≥ SpO2 (${spo2.weight})",
            fragmentation.weight >= spo2.weight,
        )
    }

    @Test
    fun asthma_signature_requires_at_least_three_signals_to_fire() {
        assertEquals(3, RespiratoryExacerbationPatterns.asthmaExacerbationSignature.minActiveSignals)
    }

    @Test
    fun trend_signatures_use_baseline_relative_seven_day_windows() {
        // The trend-based signatures (copd_exacerbation, asthma_exacerbation)
        // are all-baseline-relative 7-day windows. The acute screens, by
        // contrast, anchor on absolute SpO2 cutoffs in shorter windows —
        // those have a separate set of guarantees below.
        val trendPatterns = listOf(
            RespiratoryExacerbationPatterns.copdExacerbationSignature,
            RespiratoryExacerbationPatterns.asthmaExacerbationSignature,
        )
        for (pattern in trendPatterns) {
            for (rule in pattern.signalRules) {
                assertFalse(
                    "${pattern.id}/${rule.metricType.key} should be baseline-relative",
                    rule.isAbsolute
                )
                assertEquals(
                    "${pattern.id}/${rule.metricType.key} should use 7-day window",
                    168, rule.minDurationHours
                )
            }
        }
    }

    // -- acute screens (#200) --

    @Test
    fun acute_asthma_screen_anchors_on_absolute_spo2_cutoff_of_90() {
        // GINA 2024: SpO2 ≤90 % in an asthma exacerbation = moderate-to-
        // severe. The acute screen fires on a single SpO2 reading at this
        // cutoff, not on a multi-day trend.
        val spo2 = RespiratoryExacerbationPatterns.acuteAsthmaExacerbationScreen
            .signalRules.first { it.metricType == MetricType.BLOOD_OXYGEN }
        assertTrue("SpO2 rule must be absolute", spo2.isAbsolute)
        assertTrue("SpO2 rule must be required", spo2.required)
        assertEquals(90.0, spo2.absoluteBelow!!, 0.0)
    }

    @Test
    fun acute_copd_screen_anchors_on_absolute_spo2_cutoff_of_88() {
        // GOLD 2024: SpO2 ≤88 % in COPD = moderate-to-severe exacerbation.
        // The 88 % threshold accounts for COPD owners' typically lower
        // baseline relative to the general population (versus 90 % asthma).
        val spo2 = RespiratoryExacerbationPatterns.acuteCopdExacerbationScreen
            .signalRules.first { it.metricType == MetricType.BLOOD_OXYGEN }
        assertTrue("SpO2 rule must be absolute", spo2.isAbsolute)
        assertTrue("SpO2 rule must be required", spo2.required)
        assertEquals(88.0, spo2.absoluteBelow!!, 0.0)
    }

    @Test
    fun acute_asthma_screen_has_urgent_severity_floor() {
        // SpO2 ≤90 % with respiratory symptoms warrants immediate medical
        // assessment — engine default ADVISORY cap is not enough.
        assertEquals(
            AlertTier.URGENT,
            RespiratoryExacerbationPatterns.acuteAsthmaExacerbationScreen.severityFloor,
        )
    }

    @Test
    fun acute_copd_screen_has_urgent_severity_floor() {
        assertEquals(
            AlertTier.URGENT,
            RespiratoryExacerbationPatterns.acuteCopdExacerbationScreen.severityFloor,
        )
    }

    @Test
    fun acute_asthma_screen_gates_on_known_asthma_or_paediatric_state() {
        // KNOWN_ASTHMA gating prevents healthy-population false positives.
        // PAEDIATRIC is included because paediatric asthma is the
        // PAEDIATRICS_POV §2.9 audit-derived target.
        val required = RespiratoryExacerbationPatterns
            .acuteAsthmaExacerbationScreen.requiredStates
        assertTrue(PhysiologyState.KNOWN_ASTHMA in required)
        assertTrue(PhysiologyState.PAEDIATRIC in required)
        // STANDARD owner should NOT be in the required set — that's the
        // whole point of the gating axis.
        assertFalse(PhysiologyState.STANDARD in required)
    }

    @Test
    fun acute_copd_screen_gates_on_known_copd_state() {
        val required = RespiratoryExacerbationPatterns
            .acuteCopdExacerbationScreen.requiredStates
        assertEquals(setOf(PhysiologyState.KNOWN_COPD), required)
        // Healthy owner (STANDARD) must not see this fire.
        assertFalse(PhysiologyState.STANDARD in required)
    }

    @Test
    fun acute_asthma_screen_includes_environmental_corroborators_but_not_required() {
        // Environmental rules add context when AMBIENT_HUMIDITY_PCT and
        // AMBIENT_TEMPERATURE_C are on the bus, but the pattern must fire
        // without them — corroborators must never be `required`.
        val pattern = RespiratoryExacerbationPatterns.acuteAsthmaExacerbationScreen
        val humidity = pattern.signalRules.firstOrNull {
            it.metricType == MetricType.AMBIENT_HUMIDITY_PCT
        }
        val temperature = pattern.signalRules.firstOrNull {
            it.metricType == MetricType.AMBIENT_TEMPERATURE_C
        }
        assertNotNull("Humidity corroborator must be wired", humidity)
        assertNotNull("Temperature corroborator must be wired", temperature)
        assertFalse(
            "Humidity corroborator must not be required (graceful absence)",
            humidity!!.required,
        )
        assertFalse(
            "Temperature corroborator must not be required",
            temperature!!.required,
        )
    }

    @Test
    fun acute_asthma_screen_includes_peak_expiratory_flow_corroborator() {
        // GINA 2024 home monitoring anchors on PEF versus personal best.
        // The acute screen wires PEF as a high-weight (but not required)
        // corroborator so a paediatric owner with a peak-flow meter sees
        // the signal feed in, but an owner without a meter still gets the
        // pattern from SpO2 + tachycardia + activity.
        val pef = RespiratoryExacerbationPatterns.acuteAsthmaExacerbationScreen
            .signalRules.firstOrNull { it.metricType == MetricType.PEAK_EXPIRATORY_FLOW_LMIN }
        assertNotNull("PEF rule must be wired into the asthma screen", pef)
        assertFalse("PEF corroborator must not be required", pef!!.required)
        assertTrue("PEF weight should be ≥1.0", pef.weight >= 1.0)
    }

    @Test
    fun acute_copd_screen_does_not_include_peak_expiratory_flow_rule() {
        // PEF is asthma's home-monitoring anchor (GINA 2024); GOLD 2024
        // anchors COPD home monitoring on SpO2 + RR + activity tolerance,
        // not PEF. Sanity check the two screens don't share rules they
        // shouldn't.
        val metrics = RespiratoryExacerbationPatterns.acuteCopdExacerbationScreen
            .signalRules.map { it.metricType }.toSet()
        assertFalse(MetricType.PEAK_EXPIRATORY_FLOW_LMIN in metrics)
    }

    @Test
    fun acute_screens_cite_gina_and_gold() {
        // Manifesto-aligned: literature anchors must be specific, not
        // hand-waved. GINA 2024 anchors asthma; GOLD 2024 anchors COPD.
        val asthmaCitations = RespiratoryExacerbationPatterns
            .acuteAsthmaExacerbationScreen.signalRules.map { it.citation }
            .joinToString(" ")
        assertTrue(
            "Asthma screen must cite GINA",
            asthmaCitations.contains("GINA", ignoreCase = true),
        )
        val copdCitations = RespiratoryExacerbationPatterns
            .acuteCopdExacerbationScreen.signalRules.map { it.citation }
            .joinToString(" ")
        assertTrue(
            "COPD screen must cite GOLD",
            copdCitations.contains("GOLD", ignoreCase = true),
        )
    }
}
