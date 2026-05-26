package com.bios.app

import com.bios.app.engine.WebsterRescoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM coverage of [WebsterRescoring] (#244 Cut 1b). Pins each
 * of Webster's rescoring rules a-e individually plus several
 * integration scenarios that exercise the published behavior.
 *
 * Convention: `true` = wake, `false` = sleep. Helpers below produce
 * BooleanArrays from compact W/S strings to keep the per-rule
 * fixtures readable.
 */
class WebsterRescoringTest {

    @Test
    fun rescore_returns_a_new_array_and_does_not_mutate_the_input() {
        val input = pattern("WWWWS")
        val output = WebsterRescoring.rescore(input)
        assertNotSame(input, output)
        // Original input still has S at index 4.
        assertFalse(input[4])
    }

    @Test
    fun empty_input_returns_empty_array() {
        val output = WebsterRescoring.rescore(BooleanArray(0))
        assertEquals(0, output.size)
    }

    @Test
    fun all_sleep_input_stays_all_sleep() {
        // No wake bouts → no rules fire.
        val output = WebsterRescoring.rescore(pattern("SSSSSSSSSSSSSSSS"))
        assertTrue(output.none { it })
    }

    @Test
    fun all_wake_input_stays_all_wake() {
        val output = WebsterRescoring.rescore(pattern("WWWWWWWWWWWWWWWW"))
        assertTrue(output.all { it })
    }

    // -- Rule a: ≥4 wake → next 1 sleep becomes wake --

    @Test
    fun rule_a_fires_after_exactly_4_wake() {
        // 4W then 1S → S flips to W per rule a.
        // After the flip we expect WWWWW (the 5th sample is wake).
        val output = WebsterRescoring.rescore(pattern("WWWWS"))
        assertPatternEquals("WWWWW", output)
    }

    @Test
    fun rule_a_does_not_fire_after_only_3_wake() {
        // 3 wake samples is below the rule-a threshold.
        val output = WebsterRescoring.rescore(pattern("WWWS"))
        assertPatternEquals("WWWS", output)
    }

    @Test
    fun rule_a_only_extends_one_sample() {
        // 4W then 2S → only the first S flips (rule a says "next 1").
        // The second S remains S because Webster reads the ORIGINAL
        // preceding-wake-run (which is interrupted by the first S),
        // not the rescored array.
        val output = WebsterRescoring.rescore(pattern("WWWWSS"))
        assertPatternEquals("WWWWWS", output)
    }

    // -- Rule b: ≥10 wake → next 3 sleep become wake --

    @Test
    fun rule_b_fires_after_exactly_10_wake() {
        val output = WebsterRescoring.rescore(pattern("WWWWWWWWWWSSSS"))
        // 10W + 3 flipped W + final S = 13W then S.
        assertPatternEquals("WWWWWWWWWWWWWS", output)
    }

    @Test
    fun rule_b_chooses_3_sample_extension_over_rule_a_single() {
        // Both rule a (≥4) and rule b (≥10) qualify; rule b's longer
        // extension wins.
        val output = WebsterRescoring.rescore(pattern("WWWWWWWWWWS"))
        assertPatternEquals("WWWWWWWWWWW", output)
    }

    // -- Rule c: ≥15 wake → next 4 sleep become wake --

    @Test
    fun rule_c_fires_after_exactly_15_wake() {
        val input = pattern("WWWWWWWWWWWWWWW" + "SSSSS")
        val output = WebsterRescoring.rescore(input)
        // 15W + 4 flipped W + final S.
        assertPatternEquals("WWWWWWWWWWWWWWW" + "WWWWS", output)
    }

    @Test
    fun rule_c_chooses_4_sample_extension_over_rules_a_and_b() {
        val input = pattern("WWWWWWWWWWWWWWW" + "S")
        val output = WebsterRescoring.rescore(input)
        assertPatternEquals("WWWWWWWWWWWWWWWW", output)
    }

    // -- Rule d: sleep run ≤6 flanked by wake ≥10 → recoded as wake --

    @Test
    fun rule_d_absorbs_5_sleep_between_two_long_wake_runs() {
        val input = pattern(
            "WWWWWWWWWW" +  // 10 wake
            "SSSSS" +       // 5 sleep (≤6) — should be absorbed
            "WWWWWWWWWW"    // 10 wake
        )
        val output = WebsterRescoring.rescore(input)
        assertTrue("brief sleep should absorb to wake", output.all { it })
    }

    @Test
    fun rule_d_does_not_fire_when_sleep_run_exceeds_6() {
        // 7 sleep samples > rule d's ≤6 max. The wake-run extension
        // rules a-c still apply at the boundary.
        val input = pattern(
            "WWWWWWWWWW" +  // 10 wake
            "SSSSSSS" +     // 7 sleep
            "WWWWWWWWWW"
        )
        val output = WebsterRescoring.rescore(input)
        // 10W (rule b extends 3 on the first S since preceding wake = 10),
        // then 4 surviving S, then 10W. Total wake = 10 + 3 + 10 = 23.
        // (Rule b doesn't re-fire on subsequent sleeps because the
        // preceding-wake-run is read from the ORIGINAL — interrupted
        // by the first sleep.)
        assertEquals(23, output.count { it })
    }

    @Test
    fun rule_d_does_not_fire_when_one_flank_is_too_short() {
        // Only 9 wake on the right → rule d's ≥10 flanking threshold
        // fails on that side.
        val input = pattern(
            "WWWWWWWWWW" +  // 10 wake
            "SSS" +         // 3 sleep
            "WWWWWWWWW"     // 9 wake (short)
        )
        val output = WebsterRescoring.rescore(input)
        // Rule d fails. Rule a-c forward extension still fires on first S.
        // 10W + rule b extends 3 → 13W + 9W = 22W, total 22.
        assertEquals(22, output.count { it })
    }

    // -- Rule e: sleep run ≤10 flanked by wake ≥20 --

    @Test
    fun rule_e_absorbs_10_sleep_between_two_20_wake_runs() {
        val input = pattern(
            "W".repeat(20) +
            "S".repeat(10) +
            "W".repeat(20)
        )
        val output = WebsterRescoring.rescore(input)
        assertTrue("rule-e absorption should produce all wake", output.all { it })
    }

    @Test
    fun rule_e_does_not_fire_when_sleep_run_exceeds_10() {
        // 11 sleep samples — above rule e's ≤10 ceiling.
        val input = pattern(
            "W".repeat(20) +
            "S".repeat(11) +
            "W".repeat(20)
        )
        val output = WebsterRescoring.rescore(input)
        // Rule e fails. Rules a-c still fire on the first sleep.
        // 20W + rule c extends 4 = 24W, then 7S, then 20W. Total 44 wake.
        assertEquals(44, output.count { it })
    }

    // -- Integration --

    @Test
    fun forward_extension_and_brief_sleep_absorption_compose_cleanly() {
        // Long wake → brief sleep → long wake → moderate sleep → long wake
        // Brief sleep gets absorbed; moderate gets the forward extension
        // but not full absorption.
        val input = pattern(
            "W".repeat(20) +    // long wake
            "S".repeat(4) +     // brief sleep — absorbed by rule d
            "W".repeat(15) +    // long wake
            "S".repeat(8) +     // moderate sleep — rule c extends 4
            "W".repeat(15)      // long wake
        )
        val output = WebsterRescoring.rescore(input)
        // Expected: 20W + 4W (absorbed) + 15W + 4W (rule c extension) + 4S + 15W
        assertEquals(58, output.count { it })
        // Confirm the 4-sleep gap survives in the right place.
        val expectedSleepCount = 4
        assertEquals(expectedSleepCount, output.count { !it })
    }

    @Test
    fun rules_do_not_fire_at_array_start_or_end_when_run_context_is_missing() {
        // Array starts with sleep — no preceding wake at all.
        val startSleep = WebsterRescoring.rescore(pattern("SSSWWWWWW"))
        assertPatternEquals("SSSWWWWWW", startSleep)

        // Array ends with sleep after a long wake — rule c fires, but
        // only as many samples as remain.
        val endShort = WebsterRescoring.rescore(pattern("W".repeat(15) + "SS"))
        // Rule c wants to extend 4 but only 2 samples remain. Both flip.
        assertPatternEquals("W".repeat(15) + "WW", endShort)
    }

    // -- helpers --

    private fun pattern(s: String): BooleanArray = BooleanArray(s.length) { s[it] == 'W' }

    private fun assertPatternEquals(expected: String, actual: BooleanArray) {
        val actualStr = actual.joinToString("") { if (it) "W" else "S" }
        assertEquals(expected, actualStr)
    }
}
