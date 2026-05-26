package com.bios.app

import com.bios.app.alerts.ConditionPatterns
import com.bios.app.physiology.PhysiologyState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the PhysiologyState gating of cycle-anomaly pattern for
 * perimenopausal owners (#209, audit gap §2.12).
 *
 * STRAW+10 names variable cycle length as the defining marker of the
 * menopausal transition. The `menstrual_cycle_anomaly` pattern's
 * baseline-deviation premise breaks during peri — flagging variability
 * as anomaly is exactly the normative-misread the audit warns against.
 *
 * The fix lands on [com.bios.app.alerts.ConditionPattern.excludedStates]
 * — adding [PhysiologyState.PERIMENOPAUSE] and
 * [PhysiologyState.POSTMENOPAUSE] to the menstrual-cycle-anomaly pattern.
 * This test pins the exclusion so a regression won't quietly false-fire
 * peri/post-menopausal owners.
 */
class PerimenopauseGatingTest {

    @Test
    fun menstrual_cycle_anomaly_pattern_is_excluded_in_perimenopause() {
        val pattern = ConditionPatterns.menstrualCycleAnomaly
        assertTrue(
            "menstrual_cycle_anomaly must exclude PERIMENOPAUSE — cycle " +
                "variability IS the perimenopausal physiology",
            PhysiologyState.PERIMENOPAUSE in pattern.excludedStates,
        )
    }

    @Test
    fun menstrual_cycle_anomaly_pattern_is_excluded_in_postmenopause() {
        val pattern = ConditionPatterns.menstrualCycleAnomaly
        assertTrue(
            "menstrual_cycle_anomaly must exclude POSTMENOPAUSE — there " +
                "is no cycle to flag anomalies on",
            PhysiologyState.POSTMENOPAUSE in pattern.excludedStates,
        )
    }

    @Test
    fun menstrual_cycle_anomaly_still_fires_for_standard_adult() {
        val pattern = ConditionPatterns.menstrualCycleAnomaly
        assertFalse(
            "The pattern still applies to the default STANDARD adult state",
            PhysiologyState.STANDARD in pattern.excludedStates,
        )
    }

    @Test
    fun menopause_transition_convenience_set_contains_both_stages() {
        // Pin the convenience set the pattern code subscribes to. If a
        // future refactor renames either constant, the pattern's
        // excludedStates lookup loses coverage silently — this test
        // catches that.
        assertTrue(PhysiologyState.PERIMENOPAUSE in PhysiologyState.MENOPAUSE_TRANSITION)
        assertTrue(PhysiologyState.POSTMENOPAUSE in PhysiologyState.MENOPAUSE_TRANSITION)
    }
}
