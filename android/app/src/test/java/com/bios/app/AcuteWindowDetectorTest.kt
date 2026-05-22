package com.bios.app

import com.bios.app.alerts.AcuteWindowPatterns
import com.bios.app.engine.AcuteWindowDetector
import com.bios.app.engine.AcuteWindowDetector.Reading
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Engine-level tests for the acute-window detector (#190). Exercise the
 * pure-function pattern evaluators on [AcuteWindowDetector.Companion]
 * without touching SQLCipher / Room, so the rate-of-change and
 * consecutive-reading math is testable in isolation — same shape
 * [com.bios.app.alerts.Sepsis2NewsCalculator] established for sepsis.
 *
 * The DB-backed [AcuteWindowDetector.runAcuteDetection] flow is covered
 * by the existing [AnomalyDetectorTest] in-memory-DB integration fixture.
 */
class AcuteWindowDetectorTest {

    private val now = 1_700_000_000_000L
    private fun minutesAgo(min: Int): Long = now - min * 60_000L

    // -- anaphylaxis: positive case --

    @Test
    fun anaphylaxis_fires_on_hr_rising_30bpm_plus_spo2_drop() {
        val readings = listOf(
            Reading(MetricType.HEART_RATE, 85.0, minutesAgo(9)),
            Reading(MetricType.HEART_RATE, 110.0, minutesAgo(5)),
            Reading(MetricType.HEART_RATE, 130.0, minutesAgo(1)),
            Reading(MetricType.BLOOD_OXYGEN, 91.0, minutesAgo(4)),
            Reading(MetricType.BLOOD_OXYGEN, 90.0, minutesAgo(2)),
        )
        val anomaly = AcuteWindowDetector.evaluateAnaphylaxis(
            AcuteWindowPatterns.anaphylaxisScreen, readings, now,
        )
        assertNotNull("Anaphylaxis should fire on Δ30 HR + sustained SpO2 ≤92", anomaly)
        assertEquals("anaphylaxis_screen", anomaly!!.patternId)
        // URGENT tier from severityFloor.
        assertEquals(3, anomaly.severity)
    }

    // -- anaphylaxis: negative cases (noise, single reading, exercise-only) --

    @Test
    fun anaphylaxis_does_not_fire_on_single_isolated_hr_reading() {
        val readings = listOf(
            Reading(MetricType.HEART_RATE, 145.0, minutesAgo(2)),
            Reading(MetricType.BLOOD_OXYGEN, 90.0, minutesAgo(3)),
            Reading(MetricType.BLOOD_OXYGEN, 91.0, minutesAgo(1)),
        )
        assertNull(
            "Anaphylaxis must not fire on a single HR reading (no rate-of-change)",
            AcuteWindowDetector.evaluateAnaphylaxis(
                AcuteWindowPatterns.anaphylaxisScreen, readings, now,
            ),
        )
    }

    @Test
    fun anaphylaxis_does_not_fire_when_hr_rise_lacks_spo2_drop() {
        // HR rises 80 → 125. SpO2 stays normal — no bronchospasm. This is
        // the exercise pattern; must not fire.
        val readings = listOf(
            Reading(MetricType.HEART_RATE, 80.0, minutesAgo(9)),
            Reading(MetricType.HEART_RATE, 105.0, minutesAgo(5)),
            Reading(MetricType.HEART_RATE, 125.0, minutesAgo(1)),
            Reading(MetricType.BLOOD_OXYGEN, 98.0, minutesAgo(4)),
            Reading(MetricType.BLOOD_OXYGEN, 97.0, minutesAgo(2)),
        )
        assertNull(
            "Exercise-only HR rise without SpO2 drop must not fire anaphylaxis",
            AcuteWindowDetector.evaluateAnaphylaxis(
                AcuteWindowPatterns.anaphylaxisScreen, readings, now,
            ),
        )
    }

    @Test
    fun anaphylaxis_does_not_fire_on_noisy_baseline_readings() {
        val readings = listOf(
            Reading(MetricType.HEART_RATE, 70.0, minutesAgo(9)),
            Reading(MetricType.HEART_RATE, 80.0, minutesAgo(5)),
            Reading(MetricType.HEART_RATE, 75.0, minutesAgo(1)),
            Reading(MetricType.BLOOD_OXYGEN, 97.0, minutesAgo(4)),
            Reading(MetricType.BLOOD_OXYGEN, 96.0, minutesAgo(2)),
            Reading(MetricType.BLOOD_OXYGEN, 98.0, minutesAgo(1)),
        )
        assertNull(
            AcuteWindowDetector.evaluateAnaphylaxis(
                AcuteWindowPatterns.anaphylaxisScreen, readings, now,
            ),
        )
    }

    // -- opioid respiratory depression: positive case --

    @Test
    fun opioid_pattern_fires_on_rr_le_8_two_consecutive_with_spo2_le_88() {
        val readings = listOf(
            Reading(MetricType.RESPIRATORY_RATE, 7.0, minutesAgo(7)),
            Reading(MetricType.RESPIRATORY_RATE, 6.0, minutesAgo(3)),
            Reading(MetricType.BLOOD_OXYGEN, 86.0, minutesAgo(4)),
            Reading(MetricType.BLOOD_OXYGEN, 84.0, minutesAgo(1)),
        )
        val anomaly = AcuteWindowDetector.evaluateOpioidRespiratoryDepression(
            AcuteWindowPatterns.opioidRespiratoryDepressionScreen, readings, now,
        )
        assertNotNull("Opioid pattern should fire on sustained RR ≤8 + SpO2 ≤88", anomaly)
        assertEquals("opioid_respiratory_depression_screen", anomaly!!.patternId)
    }

    // -- opioid respiratory depression: negative cases --

    @Test
    fun opioid_pattern_does_not_fire_on_single_low_rr_reading() {
        val readings = listOf(
            Reading(MetricType.RESPIRATORY_RATE, 7.0, minutesAgo(3)),
            Reading(MetricType.BLOOD_OXYGEN, 86.0, minutesAgo(2)),
            Reading(MetricType.BLOOD_OXYGEN, 85.0, minutesAgo(1)),
        )
        assertNull(
            "Single isolated RR reading must not fire opioid pattern",
            AcuteWindowDetector.evaluateOpioidRespiratoryDepression(
                AcuteWindowPatterns.opioidRespiratoryDepressionScreen, readings, now,
            ),
        )
    }

    @Test
    fun opioid_pattern_tolerates_known_copd_on_o2_via_adequate_spo2() {
        // The audit explicitly calls this out: a COPD-on-home-O2 owner
        // can have low RR with adequate SpO2 thanks to supplemental O2.
        // The two-signal requirement (RR ≤8 AND SpO2 ≤88) means adequate
        // SpO2 alone suppresses the alert even when RR is low.
        val readings = listOf(
            Reading(MetricType.RESPIRATORY_RATE, 7.0, minutesAgo(7)),
            Reading(MetricType.RESPIRATORY_RATE, 7.0, minutesAgo(3)),
            Reading(MetricType.BLOOD_OXYGEN, 94.0, minutesAgo(4)),
            Reading(MetricType.BLOOD_OXYGEN, 95.0, minutesAgo(1)),
        )
        assertNull(
            "Low RR with adequate SpO2 (COPD-on-O2) must not fire opioid pattern",
            AcuteWindowDetector.evaluateOpioidRespiratoryDepression(
                AcuteWindowPatterns.opioidRespiratoryDepressionScreen, readings, now,
            ),
        )
    }

    @Test
    fun opioid_pattern_does_not_fire_on_normal_rr_and_spo2() {
        val readings = listOf(
            Reading(MetricType.RESPIRATORY_RATE, 14.0, minutesAgo(5)),
            Reading(MetricType.RESPIRATORY_RATE, 16.0, minutesAgo(1)),
            Reading(MetricType.BLOOD_OXYGEN, 97.0, minutesAgo(3)),
        )
        assertNull(
            AcuteWindowDetector.evaluateOpioidRespiratoryDepression(
                AcuteWindowPatterns.opioidRespiratoryDepressionScreen, readings, now,
            ),
        )
    }

    // -- DKA: positive case --

    @Test
    fun dka_pattern_fires_on_sustained_glucose_ge_250_plus_tachycardia() {
        val readings = listOf(
            Reading(MetricType.BLOOD_GLUCOSE, 290.0, minutesAgo(60 * 8)),
            Reading(MetricType.BLOOD_GLUCOSE, 310.0, minutesAgo(60 * 4)),
            Reading(MetricType.BLOOD_GLUCOSE, 335.0, minutesAgo(60 * 1)),
            Reading(MetricType.HEART_RATE, 108.0, minutesAgo(30)),
            Reading(MetricType.HEART_RATE, 115.0, minutesAgo(5)),
        )
        val anomaly = AcuteWindowDetector.evaluateDka(
            AcuteWindowPatterns.dkaScreen, readings, now,
        )
        assertNotNull("DKA should fire on sustained glucose ≥250 + tachycardia", anomaly)
        assertEquals("dka_screen", anomaly!!.patternId)
    }

    // -- DKA: negative cases --

    @Test
    fun dka_pattern_does_not_fire_on_single_post_prandial_glucose_spike() {
        val readings = listOf(
            Reading(MetricType.BLOOD_GLUCOSE, 280.0, minutesAgo(60)),
            Reading(MetricType.HEART_RATE, 105.0, minutesAgo(45)),
            Reading(MetricType.HEART_RATE, 110.0, minutesAgo(5)),
        )
        assertNull(
            "Single post-prandial glucose spike must not fire DKA",
            AcuteWindowDetector.evaluateDka(AcuteWindowPatterns.dkaScreen, readings, now),
        )
    }

    @Test
    fun dka_pattern_does_not_fire_on_high_glucose_without_tachycardia() {
        val readings = listOf(
            Reading(MetricType.BLOOD_GLUCOSE, 280.0, minutesAgo(60 * 6)),
            Reading(MetricType.BLOOD_GLUCOSE, 295.0, minutesAgo(60 * 3)),
            Reading(MetricType.BLOOD_GLUCOSE, 310.0, minutesAgo(60)),
            Reading(MetricType.HEART_RATE, 75.0, minutesAgo(30)),
            Reading(MetricType.HEART_RATE, 78.0, minutesAgo(5)),
        )
        assertNull(
            "Hyperglycaemia without compensatory tachycardia must not fire DKA",
            AcuteWindowDetector.evaluateDka(AcuteWindowPatterns.dkaScreen, readings, now),
        )
    }

    // -- hypotensive shock: positive cases --

    @Test
    fun shock_pattern_fires_on_sbp_le_90_plus_hr_gt_100() {
        val readings = listOf(
            Reading(MetricType.BLOOD_PRESSURE_SYSTOLIC, 85.0, minutesAgo(3)),
            Reading(MetricType.HEART_RATE, 115.0, minutesAgo(2)),
        )
        val anomaly = AcuteWindowDetector.evaluateHypotensiveShock(
            AcuteWindowPatterns.hypotensiveShockScreen, readings, now,
        )
        assertNotNull("Shock should fire on SBP ≤90 + HR >100", anomaly)
        assertEquals("hypotensive_shock_screen", anomaly!!.patternId)
        assertEquals(3, anomaly.severity)
    }

    @Test
    fun shock_pattern_fires_on_map_below_65_from_concurrent_sbp_dbp() {
        // SBP 95 / DBP 45 ⇒ MAP = 45 + (95−45)/3 = 61.67 (<65) with HR 108.
        val ts = minutesAgo(2)
        val readings = listOf(
            Reading(MetricType.BLOOD_PRESSURE_SYSTOLIC, 95.0, ts),
            Reading(MetricType.BLOOD_PRESSURE_DIASTOLIC, 45.0, ts),
            Reading(MetricType.HEART_RATE, 108.0, minutesAgo(1)),
        )
        val anomaly = AcuteWindowDetector.evaluateHypotensiveShock(
            AcuteWindowPatterns.hypotensiveShockScreen, readings, now,
        )
        assertNotNull(
            "Shock should fire on MAP <65 even when SBP >90",
            anomaly,
        )
    }

    // -- hypotensive shock: negative cases --

    @Test
    fun shock_pattern_does_not_fire_on_low_sbp_alone_without_tachycardia() {
        val readings = listOf(
            Reading(MetricType.BLOOD_PRESSURE_SYSTOLIC, 88.0, minutesAgo(3)),
            Reading(MetricType.HEART_RATE, 65.0, minutesAgo(2)),
        )
        assertNull(
            "Low SBP without compensatory tachycardia must not fire shock",
            AcuteWindowDetector.evaluateHypotensiveShock(
                AcuteWindowPatterns.hypotensiveShockScreen, readings, now,
            ),
        )
    }

    @Test
    fun shock_pattern_does_not_fire_on_normal_sbp_and_normal_hr() {
        val readings = listOf(
            Reading(MetricType.BLOOD_PRESSURE_SYSTOLIC, 122.0, minutesAgo(3)),
            Reading(MetricType.HEART_RATE, 78.0, minutesAgo(1)),
        )
        assertNull(
            AcuteWindowDetector.evaluateHypotensiveShock(
                AcuteWindowPatterns.hypotensiveShockScreen, readings, now,
            ),
        )
    }

    // -- MAP computation correctness --

    @Test
    fun map_below_65_detects_low_map_from_concurrent_sbp_dbp_pair() {
        val ts = minutesAgo(2)
        val sbp = listOf(Reading(MetricType.BLOOD_PRESSURE_SYSTOLIC, 90.0, ts))
        val dbp = listOf(Reading(MetricType.BLOOD_PRESSURE_DIASTOLIC, 40.0, ts))
        // MAP = 40 + (90−40)/3 = 56.67 — below 65.
        assertTrue(AcuteWindowDetector.computeAnyLowMap(sbp, dbp, 65.0))
    }

    @Test
    fun map_below_65_does_not_match_when_pair_is_too_far_apart_in_time() {
        val sbp = listOf(Reading(MetricType.BLOOD_PRESSURE_SYSTOLIC, 90.0, now))
        val dbp = listOf(Reading(MetricType.BLOOD_PRESSURE_DIASTOLIC, 40.0, minutesAgo(2)))
        assertFalse(AcuteWindowDetector.computeAnyLowMap(sbp, dbp, 65.0))
    }

    @Test
    fun map_below_65_is_false_when_no_diastolic_readings_present() {
        val sbp = listOf(Reading(MetricType.BLOOD_PRESSURE_SYSTOLIC, 90.0, now))
        assertFalse(AcuteWindowDetector.computeAnyLowMap(sbp, emptyList(), 65.0))
    }

    @Test
    fun map_below_65_is_false_for_normal_bp_pair() {
        val ts = now
        val sbp = listOf(Reading(MetricType.BLOOD_PRESSURE_SYSTOLIC, 130.0, ts))
        val dbp = listOf(Reading(MetricType.BLOOD_PRESSURE_DIASTOLIC, 80.0, ts))
        assertFalse(AcuteWindowDetector.computeAnyLowMap(sbp, dbp, 65.0))
    }

    // -- dispatcher dispatches by pattern id --

    @Test
    fun evaluate_pattern_dispatcher_returns_null_for_unknown_pattern_id() {
        val anaphylaxisLike = AcuteWindowPatterns.anaphylaxisScreen.copy(
            id = "totally_unknown_pattern",
        )
        assertNull(
            AcuteWindowDetector.evaluatePattern(anaphylaxisLike, emptyList(), now),
        )
    }
}
