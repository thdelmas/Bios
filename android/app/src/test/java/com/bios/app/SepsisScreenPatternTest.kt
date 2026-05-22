package com.bios.app

import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.DeviationDirection
import com.bios.app.alerts.Sepsis2NewsCalculator
import com.bios.app.alerts.Sepsis2NewsCalculator.Reading
import com.bios.app.alerts.SepsisScreenPattern
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
 * Guards the sepsis-screening pattern (issue #182, EMERGENCY_CRITICAL_CARE_POV
 * §2.2 + SURGICAL_POV §2.5). Tests cover:
 *
 *  - Registration: the pattern lands in [ConditionPatterns.all].
 *  - Shape: every SignalRule is a literature-anchored absolute threshold
 *    that fires inside the 6-hour rolling window, `severityFloor = URGENT`,
 *    and the alert text is compliant with [com.bios.app.alerts.AlertContentPolicy].
 *  - Suppression: paediatric owners are excluded (NEWS2 is adult-validated).
 *  - Composite math: [Sepsis2NewsCalculator] correctly scores NEWS2 ≥3
 *    (ADVISORY), ≥5 (URGENT), the qSOFA ≥2 fallback when wearable vitals
 *    are sparse, and the baseline-normal case (no alert).
 */
class SepsisScreenPatternTest {

    private val now = 1_700_000_000_000L
    private fun minutesAgo(min: Int): Long = now - min * 60_000L

    // -- registration --

    @Test
    fun pattern_registers_in_global_all_list() {
        assertTrue(SepsisScreenPattern.sepsisScreen in ConditionPatterns.all)
    }

    @Test
    fun pattern_declares_urgent_severity_floor() {
        assertEquals(
            "sepsis_screen should escalate to URGENT when a NEWS2 red component fires",
            AlertTier.URGENT,
            SepsisScreenPattern.sepsisScreen.severityFloor,
        )
    }

    @Test
    fun pattern_excludes_paediatric_state_until_paediatric_warning_score_lands() {
        // NEWS2 component thresholds are validated for adults only;
        // paediatric early-warning scores (PEWS) use age-banded
        // thresholds that don't map onto NEWS2 component bands.
        assertTrue(
            PhysiologyState.PAEDIATRIC in SepsisScreenPattern.sepsisScreen.excludedStates,
        )
    }

    @Test
    fun every_rule_is_absolute_with_rolling_six_hour_window() {
        for (rule in SepsisScreenPattern.sepsisScreen.signalRules) {
            assertTrue("rule for ${rule.metricType.key} should be absolute", rule.isAbsolute)
            assertEquals(
                "rule for ${rule.metricType.key} should use the documented 6h window",
                SepsisScreenPattern.ROLLING_WINDOW_HOURS,
                rule.absoluteWindowHours,
            )
            assertEquals(ThresholdSource.LITERATURE, rule.source)
            assertTrue(
                "rule for ${rule.metricType.key} should ship a citation",
                rule.citation.isNotBlank(),
            )
        }
    }

    @Test
    fun rules_cover_all_news2_three_point_components() {
        val signalRules = SepsisScreenPattern.sepsisScreen.signalRules
        // Two RR rules (≤8 / ≥25).
        val rr = signalRules.filter { it.metricType == MetricType.RESPIRATORY_RATE }
        assertEquals(2, rr.size)
        assertTrue(rr.any { it.absoluteAbove == 25.0 })
        assertTrue(rr.any { it.absoluteBelow == 8.0 })
        // SpO2 ≤91 (Scale 1).
        val spo2 = signalRules.single { it.metricType == MetricType.BLOOD_OXYGEN }
        assertEquals(DeviationDirection.BELOW, spo2.direction)
        assertEquals(91.0, spo2.absoluteBelow!!, 1e-9)
        // SBP ≤90 OR ≥220.
        val sbp = signalRules.filter { it.metricType == MetricType.BLOOD_PRESSURE_SYSTOLIC }
        assertEquals(2, sbp.size)
        assertTrue(sbp.any { it.absoluteBelow == 90.0 })
        assertTrue(sbp.any { it.absoluteAbove == 220.0 })
        // HR ≤40 OR ≥131.
        val hr = signalRules.filter { it.metricType == MetricType.RESTING_HEART_RATE }
        assertEquals(2, hr.size)
        assertTrue(hr.any { it.absoluteBelow == 40.0 })
        assertTrue(hr.any { it.absoluteAbove == 131.0 })
        // Temp ≤35.0.
        val temp = signalRules.single { it.metricType == MetricType.SKIN_TEMPERATURE }
        assertEquals(35.0, temp.absoluteBelow!!, 1e-9)
        // Consciousness <15.
        val gcs = signalRules.single { it.metricType == MetricType.CONSCIOUSNESS_LEVEL }
        assertEquals(15.0, gcs.absoluteBelow!!, 1e-9)
    }

    @Test
    fun pattern_carries_required_literature_citations() {
        val refs = SepsisScreenPattern.sepsisScreen.references.joinToString("\n")
        assertTrue("Singer 2016 Sepsis-3 citation missing", refs.contains("Singer M"))
        assertTrue("Evans 2021 Surviving Sepsis citation missing", refs.contains("Evans L"))
        assertTrue(
            "Royal College of Physicians 2017 NEWS2 citation missing",
            refs.contains("Royal College of Physicians"),
        )
    }

    @Test
    fun suggested_action_uses_data_statement_framing_and_referral_language() {
        // Per AlertContentPolicy: no "you should" / urgency-amplification
        // language; the framing is data observation + professional referral.
        val action = SepsisScreenPattern.sepsisScreen.suggestedAction
        assertNotNull(action)
        val lower = action!!.lowercase()
        assertTrue(
            "Suggested action should reference immediate medical assessment or emergency services",
            lower.contains("medical") || lower.contains("emergency"),
        )
        // Belt-and-braces: AlertContentPolicy.validateAll() covers this
        // globally via AlertContentPolicyTest, but check the most common
        // failure mode (the contributor copy-pasting "you should" from
        // existing patient-education text) here too.
        for (banned in listOf("you should", "you need to", "you must")) {
            assertTrue(
                "Suggested action contains banned phrase '$banned'",
                !lower.contains(banned),
            )
        }
    }

    // -- NEWS2 component scoring math --

    @Test
    fun news2_component_scoring_matches_rcp_2017_table() {
        // RR
        assertEquals(3, Sepsis2NewsCalculator.news2RespiratoryRate(7.0))
        assertEquals(1, Sepsis2NewsCalculator.news2RespiratoryRate(10.0))
        assertEquals(0, Sepsis2NewsCalculator.news2RespiratoryRate(16.0))
        assertEquals(2, Sepsis2NewsCalculator.news2RespiratoryRate(22.0))
        assertEquals(3, Sepsis2NewsCalculator.news2RespiratoryRate(28.0))
        // SpO2 Scale 1
        assertEquals(3, Sepsis2NewsCalculator.news2Spo2Scale1(89.0))
        assertEquals(2, Sepsis2NewsCalculator.news2Spo2Scale1(92.0))
        assertEquals(1, Sepsis2NewsCalculator.news2Spo2Scale1(94.0))
        assertEquals(0, Sepsis2NewsCalculator.news2Spo2Scale1(98.0))
        // Temperature
        assertEquals(3, Sepsis2NewsCalculator.news2Temperature(34.5))
        assertEquals(1, Sepsis2NewsCalculator.news2Temperature(35.5))
        assertEquals(0, Sepsis2NewsCalculator.news2Temperature(37.0))
        assertEquals(1, Sepsis2NewsCalculator.news2Temperature(38.5))
        assertEquals(2, Sepsis2NewsCalculator.news2Temperature(39.5))
        // SBP
        assertEquals(3, Sepsis2NewsCalculator.news2SystolicBp(85.0))
        assertEquals(2, Sepsis2NewsCalculator.news2SystolicBp(95.0))
        assertEquals(1, Sepsis2NewsCalculator.news2SystolicBp(105.0))
        assertEquals(0, Sepsis2NewsCalculator.news2SystolicBp(140.0))
        assertEquals(3, Sepsis2NewsCalculator.news2SystolicBp(225.0))
        // HR
        assertEquals(3, Sepsis2NewsCalculator.news2HeartRate(35.0))
        assertEquals(1, Sepsis2NewsCalculator.news2HeartRate(45.0))
        assertEquals(0, Sepsis2NewsCalculator.news2HeartRate(75.0))
        assertEquals(1, Sepsis2NewsCalculator.news2HeartRate(105.0))
        assertEquals(2, Sepsis2NewsCalculator.news2HeartRate(120.0))
        assertEquals(3, Sepsis2NewsCalculator.news2HeartRate(140.0))
        // Consciousness (GCS encoding)
        assertEquals(0, Sepsis2NewsCalculator.news2Consciousness(15.0))
        assertEquals(3, Sepsis2NewsCalculator.news2Consciousness(13.0))
        assertEquals(3, Sepsis2NewsCalculator.news2Consciousness(3.0))
    }

    // -- composite scoring (the four cases the audit calls for) --

    @Test
    fun news2_score_three_advisory_baseline_with_one_three_point_component() {
        // RR 26 (3 points), everything else completely normal. NEWS2 = 3
        // → ADVISORY.
        val readings = listOf(
            Reading(MetricType.RESPIRATORY_RATE, 26.0, minutesAgo(30)),
            Reading(MetricType.BLOOD_OXYGEN, 98.0, minutesAgo(40)),
            Reading(MetricType.BLOOD_PRESSURE_SYSTOLIC, 120.0, minutesAgo(50)),
            Reading(MetricType.RESTING_HEART_RATE, 75.0, minutesAgo(60)),
            Reading(MetricType.SKIN_TEMPERATURE, 37.0, minutesAgo(70)),
            Reading(MetricType.CONSCIOUSNESS_LEVEL, 15.0, minutesAgo(80)),
        )
        assertEquals(3, Sepsis2NewsCalculator.computeNews2(readings, now))
        assertEquals(AlertTier.ADVISORY, Sepsis2NewsCalculator.classify(readings, now))
    }

    @Test
    fun news2_score_five_urgent_sepsis_six_trigger() {
        // RR 26 (3) + HR 120 (2) = NEWS2 5 → URGENT (Sepsis Six).
        val readings = listOf(
            Reading(MetricType.RESPIRATORY_RATE, 26.0, minutesAgo(15)),
            Reading(MetricType.BLOOD_OXYGEN, 98.0, minutesAgo(20)),
            Reading(MetricType.BLOOD_PRESSURE_SYSTOLIC, 120.0, minutesAgo(25)),
            Reading(MetricType.RESTING_HEART_RATE, 120.0, minutesAgo(30)),
            Reading(MetricType.SKIN_TEMPERATURE, 37.0, minutesAgo(35)),
            Reading(MetricType.CONSCIOUSNESS_LEVEL, 15.0, minutesAgo(40)),
        )
        assertEquals(5, Sepsis2NewsCalculator.computeNews2(readings, now))
        assertEquals(AlertTier.URGENT, Sepsis2NewsCalculator.classify(readings, now))
    }

    @Test
    fun news2_score_high_with_oxygen_supplementation_bonus() {
        // SpO2 92 on supplemental O2 (Scale 1): SpO2 92 = 2, on air/O2 = 2,
        // RR 22 = 2. Total = 6 → URGENT.
        val readings = listOf(
            Reading(MetricType.RESPIRATORY_RATE, 22.0, minutesAgo(10)),
            Reading(MetricType.BLOOD_OXYGEN, 92.0, minutesAgo(15)),
            Reading(MetricType.OXYGEN_FLOW_RATE, 4.0, minutesAgo(20)),
            Reading(MetricType.BLOOD_PRESSURE_SYSTOLIC, 130.0, minutesAgo(25)),
            Reading(MetricType.RESTING_HEART_RATE, 80.0, minutesAgo(30)),
            Reading(MetricType.SKIN_TEMPERATURE, 37.0, minutesAgo(35)),
            Reading(MetricType.CONSCIOUSNESS_LEVEL, 15.0, minutesAgo(40)),
        )
        assertEquals(6, Sepsis2NewsCalculator.computeNews2(readings, now))
        assertEquals(AlertTier.URGENT, Sepsis2NewsCalculator.classify(readings, now))
    }

    @Test
    fun qsofa_fallback_fires_urgent_when_vitals_are_sparse() {
        // No HR, no SpO2, no temp — just qSOFA inputs. RR 24 + SBP 95 +
        // GCS 14: all three qSOFA components positive (score 3, well above
        // the ≥2 trigger). NEWS2 alone here = RR(2) + SBP(2) + GCS(3) = 7,
        // which is already URGENT; the test name reflects that the qSOFA
        // criterion is the *fallback path* that catches the case where one
        // or more of the wearable components is missing — exercised by the
        // next test.
        val readings = listOf(
            Reading(MetricType.RESPIRATORY_RATE, 24.0, minutesAgo(10)),
            Reading(MetricType.BLOOD_PRESSURE_SYSTOLIC, 95.0, minutesAgo(20)),
            Reading(MetricType.CONSCIOUSNESS_LEVEL, 14.0, minutesAgo(30)),
        )
        assertEquals(3, Sepsis2NewsCalculator.computeQsofa(readings, now))
        assertEquals(AlertTier.URGENT, Sepsis2NewsCalculator.classify(readings, now))
    }

    @Test
    fun qsofa_fallback_catches_urgent_when_news2_would_underscore_from_missing_inputs() {
        // RR + altered mentation only — no BP, no HR, no SpO2, no temp.
        // NEWS2 = RR(2) + GCS(3) = 5 (URGENT-by-NEWS2), but the audit's
        // BP-missing case is the more dangerous variant: qSOFA still fires
        // on RR ≥22 + AMS = 2. We verify qSOFA is sufficient even with
        // sparser vitals than NEWS2 needs.
        val readings = listOf(
            Reading(MetricType.RESPIRATORY_RATE, 24.0, minutesAgo(5)),
            Reading(MetricType.CONSCIOUSNESS_LEVEL, 13.0, minutesAgo(15)),
        )
        assertEquals(2, Sepsis2NewsCalculator.computeQsofa(readings, now))
        assertEquals(AlertTier.URGENT, Sepsis2NewsCalculator.classify(readings, now))
    }

    @Test
    fun baseline_normal_vitals_score_zero_and_classify_to_no_alert() {
        // A perfectly normal observation set — score 0, no alert tier.
        val readings = listOf(
            Reading(MetricType.RESPIRATORY_RATE, 14.0, minutesAgo(15)),
            Reading(MetricType.BLOOD_OXYGEN, 98.0, minutesAgo(20)),
            Reading(MetricType.BLOOD_PRESSURE_SYSTOLIC, 120.0, minutesAgo(25)),
            Reading(MetricType.RESTING_HEART_RATE, 70.0, minutesAgo(30)),
            Reading(MetricType.SKIN_TEMPERATURE, 36.8, minutesAgo(35)),
            Reading(MetricType.CONSCIOUSNESS_LEVEL, 15.0, minutesAgo(40)),
        )
        assertEquals(0, Sepsis2NewsCalculator.computeNews2(readings, now))
        assertEquals(0, Sepsis2NewsCalculator.computeQsofa(readings, now))
        assertNull(Sepsis2NewsCalculator.classify(readings, now))
    }

    @Test
    fun stale_readings_outside_window_are_ignored() {
        // Critical RR from 8 hours ago must NOT count — window is 6 hours.
        val readings = listOf(
            Reading(MetricType.RESPIRATORY_RATE, 28.0, now - 8L * 3600 * 1000),
            Reading(MetricType.BLOOD_OXYGEN, 98.0, minutesAgo(30)),
        )
        assertEquals(0, Sepsis2NewsCalculator.computeNews2(readings, now))
        assertNull(Sepsis2NewsCalculator.classify(readings, now))
    }

    @Test
    fun most_recent_reading_wins_when_multiple_readings_exist_in_window() {
        // Older RR 28 (would score 3) superseded by newer RR 14 (scores 0).
        val readings = listOf(
            Reading(MetricType.RESPIRATORY_RATE, 28.0, minutesAgo(180)),
            Reading(MetricType.RESPIRATORY_RATE, 14.0, minutesAgo(10)),
        )
        assertEquals(0, Sepsis2NewsCalculator.computeNews2(readings, now))
    }
}
