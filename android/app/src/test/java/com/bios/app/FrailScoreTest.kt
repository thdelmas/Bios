package com.bios.app

import com.bios.app.physiology.FrailCategory
import com.bios.app.physiology.FrailResponses
import com.bios.app.physiology.FrailScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the FRAIL questionnaire scoring (Morley 2012, IANA).
 *
 * The five items each contribute 0 or 1 to a 0–5 total; the cutoffs are
 * 0 → ROBUST, 1–2 → PRE_FRAIL, ≥3 → FRAIL. Only FRAIL activates
 * [com.bios.app.physiology.PhysiologyState.FRAILTY_FLAG] in
 * [com.bios.app.physiology.FrailAssessmentStore].
 */
class FrailScoreTest {

    private val robust = FrailResponses()

    @Test
    fun all_negative_scores_zero_and_is_robust() {
        assertEquals(0, FrailScore.score(robust))
        assertEquals(FrailCategory.ROBUST, FrailScore.category(robust))
        assertFalse(FrailScore.shouldActivateFrailtyFlag(robust))
    }

    @Test
    fun all_positive_scores_five_and_is_frail() {
        val r = FrailResponses(
            fatigueMostOfTime = true,
            cannotClimbTenSteps = true,
            cannotWalkSeveralHundredYards = true,
            illnessCount = 11,
            unintentionalWeightLossOver5Percent = true,
        )
        assertEquals(5, FrailScore.score(r))
        assertEquals(FrailCategory.FRAIL, FrailScore.category(r))
        assertTrue(FrailScore.shouldActivateFrailtyFlag(r))
    }

    @Test
    fun single_positive_item_is_pre_frail() {
        for (r in listOf(
            FrailResponses(fatigueMostOfTime = true),
            FrailResponses(cannotClimbTenSteps = true),
            FrailResponses(cannotWalkSeveralHundredYards = true),
            FrailResponses(illnessCount = 5),
            FrailResponses(unintentionalWeightLossOver5Percent = true),
        )) {
            assertEquals("score for $r", 1, FrailScore.score(r))
            assertEquals(FrailCategory.PRE_FRAIL, FrailScore.category(r))
            assertFalse(FrailScore.shouldActivateFrailtyFlag(r))
        }
    }

    @Test
    fun two_positive_items_is_pre_frail_not_frail() {
        val r = FrailResponses(
            fatigueMostOfTime = true,
            cannotClimbTenSteps = true,
        )
        assertEquals(2, FrailScore.score(r))
        assertEquals(FrailCategory.PRE_FRAIL, FrailScore.category(r))
        assertFalse(FrailScore.shouldActivateFrailtyFlag(r))
    }

    @Test
    fun three_positive_items_is_frail_and_activates_flag() {
        val r = FrailResponses(
            fatigueMostOfTime = true,
            cannotClimbTenSteps = true,
            illnessCount = 5,
        )
        assertEquals(3, FrailScore.score(r))
        assertEquals(FrailCategory.FRAIL, FrailScore.category(r))
        assertTrue(FrailScore.shouldActivateFrailtyFlag(r))
    }

    @Test
    fun illness_count_below_cutoff_does_not_score() {
        // Morley 2012 specifies ≥5 of the 11-item physician-told list as the
        // Illnesses-item cutoff. 4 should not score; 5 should.
        val below = FrailResponses(illnessCount = 4)
        assertEquals(0, FrailScore.score(below))

        val at = FrailResponses(illnessCount = 5)
        assertEquals(1, FrailScore.score(at))

        val above = FrailResponses(illnessCount = 11)
        assertEquals(1, FrailScore.score(above))
    }

    @Test
    fun illness_count_out_of_range_is_rejected() {
        // The Illnesses item draws from an 11-condition reference list. The
        // store validates owner-entered counts before persistence to keep
        // future analyses honest.
        for (bad in listOf(-1, 12, 100)) {
            try {
                FrailResponses(illnessCount = bad)
                throw AssertionError("Expected IllegalArgumentException for illnessCount=$bad")
            } catch (e: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun categorize_boundaries_match_morley_2012() {
        assertEquals(FrailCategory.ROBUST, FrailScore.categorize(0))
        assertEquals(FrailCategory.PRE_FRAIL, FrailScore.categorize(1))
        assertEquals(FrailCategory.PRE_FRAIL, FrailScore.categorize(2))
        assertEquals(FrailCategory.FRAIL, FrailScore.categorize(3))
        assertEquals(FrailCategory.FRAIL, FrailScore.categorize(4))
        assertEquals(FrailCategory.FRAIL, FrailScore.categorize(5))
    }

    @Test
    fun cutoff_constants_match_morley_2012() {
        assertEquals(5, FrailScore.ILLNESS_BURDEN_CUTOFF)
        assertEquals(3, FrailScore.FRAIL_CUTOFF)
    }
}
