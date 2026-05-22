package com.bios.app

import com.bios.app.alerts.AfibRhythmPattern
import com.bios.app.alerts.AutonomicPatternShiftPattern
import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.DeviationDirection
import com.bios.app.alerts.ThresholdSource
import com.bios.app.model.AlertTier
import com.bios.app.model.ConditionCategory
import com.bios.contracts.MetricDomain
import com.bios.contracts.MetricType
import com.bios.contracts.MetricUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the PPG-derived AFib screening pattern (#180, cardiology audit §2.1).
 *
 * The pattern reads the [MetricType.IRREGULAR_RHYTHM_BURDEN] derived metric
 * (per-session 0/100 verdicts written by
 * [com.bios.app.engine.RhythmClassifier]) and fires when the median over a
 * 7-day window exceeds 50 % — i.e. the **majority** of recent PPG sessions
 * were scored irregularly irregular. ADVISORY-only by default (no severity
 * floor) per audit §3.1: PPG-only AFib screening must stay pull-side.
 */
class AfibRhythmPatternTest {

    // ---- registration ----

    @Test
    fun pattern_is_registered_in_global_all_list() {
        assertTrue(AfibRhythmPattern.paroxysmalAfibScreen in ConditionPatterns.all)
    }

    @Test
    fun pattern_id_is_paroxysmal_afib_screen() {
        // Distinct from the old "afib_screen" id (now retired into the
        // renamed autonomic_pattern_shift pattern) so external listeners
        // don't confuse the two surfaces.
        assertEquals("paroxysmal_afib_screen", AfibRhythmPattern.paroxysmalAfibScreen.id)
    }

    @Test
    fun pattern_lives_in_the_cardiovascular_category() {
        assertEquals(ConditionCategory.CARDIOVASCULAR, AfibRhythmPattern.paroxysmalAfibScreen.category)
    }

    // ---- the rhythm-burden anchor rule ----

    @Test
    fun gates_on_irregular_rhythm_burden_as_an_absolute_threshold() {
        val rule = AfibRhythmPattern.paroxysmalAfibScreen.signalRules
            .first { it.metricType == MetricType.IRREGULAR_RHYTHM_BURDEN }
        assertTrue("burden rule must anchor the pattern", rule.required)
        assertTrue("burden rule must be absolute, not baseline-relative", rule.isAbsolute)
        // Per-session verdicts are 0/100; median > 50 means majority irregular.
        assertEquals(50.0, rule.absoluteAbove!!, 1e-9)
        assertNull(rule.absoluteBelow)
        assertEquals(168, rule.absoluteWindowHours)  // 7-day rolling window
        assertEquals(5, rule.absoluteMinReadings)
        assertEquals(ThresholdSource.LITERATURE, rule.source)
        assertTrue("burden rule must ship a citation", rule.citation.isNotBlank())
    }

    @Test
    fun fires_on_a_single_signal_no_corroborators_required() {
        // The rhythm-burden metric is itself the diagnostic signal; no need
        // to gate it behind RHR / SpO2 corroborators.
        assertEquals(1, AfibRhythmPattern.paroxysmalAfibScreen.minActiveSignals)
        assertEquals(1, AfibRhythmPattern.paroxysmalAfibScreen.signalRules.size)
    }

    // ---- severity must stay ADVISORY (pull-side) ----

    @Test
    fun severity_floor_is_unset_so_screen_stays_ADVISORY_at_most() {
        // Cardiology audit §3.1: PPG-only AFib screening must not push as
        // URGENT. Default cap (ADVISORY) is what we want — explicitly
        // omitting the severityFloor keeps the engine in pull-side mode.
        assertNull(
            "PPG-only AFib screen must stay pull-side; no severity floor",
            AfibRhythmPattern.paroxysmalAfibScreen.severityFloor
        )
    }

    @Test
    fun severity_floor_must_not_be_URGENT() {
        // Belt-and-braces: even if a future contributor sets a floor, it
        // must never escalate this pattern to URGENT push.
        val floor = AfibRhythmPattern.paroxysmalAfibScreen.severityFloor
        assertTrue(
            "PPG-only AFib screen must never push as URGENT",
            floor == null || floor != AlertTier.URGENT
        )
    }

    // ---- text references the canonical literature ----

    @Test
    fun references_cite_Apple_Heart_Study_mAFA_II_Fitbit_Heart_Study_and_Tateno_Glass() {
        val refs = AfibRhythmPattern.paroxysmalAfibScreen.references.joinToString("\n")
        assertTrue("must cite Apple Heart Study (Perez 2019)", refs.contains("Perez"))
        assertTrue("must cite mAFA-II (Guo 2019)", refs.contains("Guo"))
        assertTrue("must cite Fitbit Heart Study (Lubitz 2022)", refs.contains("Lubitz"))
        assertTrue("must cite Tateno-Glass 2001", refs.contains("Tateno"))
    }

    // ---- text discipline ----

    @Test
    fun explanation_recommends_clinical_ECG_confirmation() {
        // PPG-based AFib screening has ~34 % PPV (Perez 2019). The
        // suggestedAction must hand off to a clinical ECG; this is the
        // manifesto-aligned framing the audit prescribes.
        val action = AfibRhythmPattern.paroxysmalAfibScreen.suggestedAction!!
        assertTrue(
            "suggestedAction must direct the owner to a clinical ECG",
            action.lowercase().contains("ecg")
        )
    }

    @Test
    fun pattern_does_not_claim_diagnosis() {
        val explanation = AfibRhythmPattern.paroxysmalAfibScreen.explanation
        // The framing must say "screening", "irregular", or similar — never
        // claim a diagnosis. The pattern surface is informational only.
        assertTrue(
            "explanation must frame this as screening, not diagnosis",
            explanation.lowercase().contains("screen") ||
                explanation.lowercase().contains("predictive value") ||
                explanation.lowercase().contains("not diagnostic")
        )
    }

    // ---- contract surface for the new MetricType ----

    @Test
    fun irregular_rhythm_burden_is_a_cardiovascular_percent_metric() {
        assertEquals(MetricDomain.CARDIOVASCULAR, MetricType.IRREGULAR_RHYTHM_BURDEN.domain)
        assertEquals(MetricUnit.PERCENT, MetricType.IRREGULAR_RHYTHM_BURDEN.unit)
    }

    @Test
    fun irregular_rhythm_burden_does_not_allow_manual_entry() {
        // The value is derived from a PPG capture's RR series, not
        // something the owner can self-report.
        assertEquals(false, MetricType.IRREGULAR_RHYTHM_BURDEN.allowsManualEntry)
    }

    // ---- renamed pattern still works ----

    @Test
    fun autonomic_pattern_shift_is_still_registered() {
        // The previous atrialFibrillationScreen pattern is now named
        // autonomic_pattern_shift. The honest name keeps the original
        // pattern alive (it does still capture an autonomic perturbation)
        // without claiming AFib detection it never did.
        val ids = ConditionPatterns.all.map { it.id }
        assertTrue("renamed autonomic_pattern_shift must still be registered", "autonomic_pattern_shift" in ids)
    }

    @Test
    fun old_afib_screen_id_is_no_longer_present() {
        // The old "afib_screen" id is retired — the new
        // paroxysmal_afib_screen pattern takes its semantic place, and the
        // renamed pattern uses autonomic_pattern_shift.
        val ids = ConditionPatterns.all.map { it.id }.toSet()
        assertTrue("legacy afib_screen id must be retired", "afib_screen" !in ids)
    }

    @Test
    fun autonomic_pattern_shift_keeps_the_same_signal_rules() {
        // The rename is text + id only; the underlying autonomic-deviation
        // detection (HRV irregularity + RHR rise + SpO2 dip) is preserved
        // because that pattern is still useful — just not an AFib screen.
        val pattern = AutonomicPatternShiftPattern.autonomicPatternShift
        val metrics = pattern.signalRules.map { it.metricType }.toSet()
        assertTrue(MetricType.HEART_RATE_VARIABILITY in metrics)
        assertTrue(MetricType.RESTING_HEART_RATE in metrics)
        assertTrue(MetricType.BLOOD_OXYGEN in metrics)
    }

    @Test
    fun autonomic_pattern_shift_uses_IRREGULAR_HRV_direction() {
        val pattern = AutonomicPatternShiftPattern.autonomicPatternShift
        val hrvRule = pattern.signalRules.first { it.metricType == MetricType.HEART_RATE_VARIABILITY }
        assertEquals(DeviationDirection.IRREGULAR, hrvRule.direction)
    }

    @Test
    fun suggested_action_exists_on_both_patterns() {
        assertNotNull(AfibRhythmPattern.paroxysmalAfibScreen.suggestedAction)
        assertNotNull(AutonomicPatternShiftPattern.autonomicPatternShift.suggestedAction)
    }
}
