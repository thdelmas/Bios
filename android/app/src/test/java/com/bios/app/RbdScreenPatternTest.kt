package com.bios.app

import com.bios.app.alerts.AlertContentPolicy
import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.RbdScreenPattern
import com.bios.app.alerts.ThresholdSource
import com.bios.app.model.AlertTier
import com.bios.app.model.ConditionCategory
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the RBD screen pattern (#206, NEUROLOGY_POV §2.7).
 *
 * Manifesto pins:
 *  - ADVISORY only (never URGENT).
 *  - Pull-side only (`pullSideOnly = true`). System notifications must
 *    not fire — the only mechanism is the alert log the owner navigates to.
 *  - Alert text contains no push-prognostic language (Parkinson's risk,
 *    synucleinopathy, neurodegenerative, prodromal, "you may be developing").
 *  - Aggregates ≥ MIN_EVENT_NIGHTS event-flagged nights inside the 4-week
 *    window (28 days).
 *  - Cites Postuma 2019, Schenck 2013, and the wearable-proxy literature.
 */
class RbdScreenPatternTest {

    // ---- registration ----

    @Test
    fun rbd_screen_pattern_is_registered_in_global_all_list() {
        assertTrue(RbdScreenPattern.rbdScreen in ConditionPatterns.all)
    }

    @Test
    fun pattern_id_is_rbd_screen() {
        assertEquals("rbd_screen", RbdScreenPattern.rbdScreen.id)
    }

    @Test
    fun pattern_lives_in_the_NEUROLOGICAL_category() {
        assertEquals(ConditionCategory.NEUROLOGICAL, RbdScreenPattern.rbdScreen.category)
    }

    // ---- pull-side-only invariant (the manifesto guard) ----

    @Test
    fun rbd_pattern_is_pull_side_only_no_push_notification() {
        assertTrue(
            "RBD screen must be pull-side only — NEUROLOGY_POV §3.3 forbids " +
                "push-side prognostic communication.",
            RbdScreenPattern.rbdScreen.pullSideOnly,
        )
    }

    @Test
    fun rbd_pattern_severity_floor_is_unset() {
        // ADVISORY default cap is what we want. Even if a future
        // contributor sets a floor, it must never be URGENT.
        val floor = RbdScreenPattern.rbdScreen.severityFloor
        assertTrue(
            "RBD screen must never push as URGENT",
            floor == null || floor != AlertTier.URGENT,
        )
    }

    @Test
    fun rbd_pattern_is_advisory_only_never_urgent() {
        assertNull(
            "RBD screen must not declare URGENT severity floor — the audit " +
                "(§3.3) is explicit that no push-side prognostic surface fires here",
            RbdScreenPattern.rbdScreen.severityFloor
                ?.takeIf { it == AlertTier.URGENT },
        )
    }

    // ---- window semantics ----

    @Test
    fun rbd_pattern_uses_a_4_week_window() {
        val rule = RbdScreenPattern.rbdScreen.signalRules.single()
        assertEquals(28 * 24, rule.absoluteWindowHours)
    }

    @Test
    fun rbd_pattern_requires_minimum_event_nights() {
        val rule = RbdScreenPattern.rbdScreen.signalRules.single()
        // The literature operating point is ≥3 event-flagged nights in
        // the 4-week window.
        assertEquals(RbdScreenPattern.MIN_EVENT_NIGHTS, rule.absoluteMinReadings)
        // The absolute cutoff sits at MIN_EVENT_NIGHTS - 0.5 so a value
        // of exactly MIN_EVENT_NIGHTS flagged nights crosses it.
        assertNotNull(rule.absoluteAbove)
        assertTrue(rule.absoluteAbove!! < RbdScreenPattern.MIN_EVENT_NIGHTS.toDouble())
        assertTrue(rule.absoluteAbove!! > (RbdScreenPattern.MIN_EVENT_NIGHTS - 1).toDouble())
    }

    @Test
    fun rbd_pattern_gates_on_rbd_event_flag_metric() {
        val rule = RbdScreenPattern.rbdScreen.signalRules.single()
        assertEquals(MetricType.RBD_EVENT_FLAG, rule.metricType)
        assertTrue(rule.required)
        assertTrue(rule.isAbsolute)
        assertEquals(ThresholdSource.LITERATURE, rule.source)
        assertTrue("burden rule must ship a citation", rule.citation.isNotBlank())
    }

    @Test
    fun rbd_pattern_fires_with_min_event_nights_or_more_over_4_weeks() {
        // Engine semantics: the absoluteAbove check uses `>= cutoff`.
        // With a cutoff of 2.5, an event count of 3.0 (median over the
        // 28-day window) crosses; 2.0 does not. This pins that the
        // pattern fires at exactly ≥3 event-flagged nights and not at 2.
        val rule = RbdScreenPattern.rbdScreen.signalRules.single()
        val cutoff = rule.absoluteAbove!!
        assertTrue("3 flagged nights must cross cutoff", 3.0 >= cutoff)
        assertFalse("2 flagged nights must NOT cross cutoff", 2.0 >= cutoff)
    }

    // ---- AlertContentPolicy compliance (the critical guard) ----

    @Test
    fun rbd_pattern_passes_alert_content_policy() {
        val violations = AlertContentPolicy.validate(RbdScreenPattern.rbdScreen)
        assertTrue(
            violations.joinToString("\n") {
                "${it.pattern.id}::${it.field} contains '${it.prohibitedPhrase}': ${it.context}"
            },
            violations.isEmpty(),
        )
    }

    @Test
    fun rbd_pattern_text_does_not_contain_prognostic_push_language() {
        // The audit-explicit ban: no push-side prognostic language for
        // synucleinopathy / neurodegenerative risk. Pin every banned
        // phrase in code so any future text change failing the manifesto
        // guard fails the build.
        val banned = listOf(
            "parkinson's risk",
            "parkinsons risk",
            "synucleinopathy",
            "neurodegenerative",
            "prodromal",
            "you may develop",
            "you may be developing",
            "you are at risk of",
            "you're at risk of",
            // Belt-and-braces: also catch close substitutes.
            "may develop parkinson",
            "may develop dementia",
            "may develop msa",
        )
        val pattern = RbdScreenPattern.rbdScreen
        val haystack = listOf(
            pattern.title,
            pattern.explanation,
            pattern.suggestedAction ?: "",
            pattern.earlyDetection,
            pattern.prevention,
            pattern.healing,
            pattern.risks,
        ).joinToString(" ").lowercase()
        for (phrase in banned) {
            assertFalse(
                "rbd_screen alert text must not contain '$phrase' — " +
                    "manifesto §3.3 push-side prognostic ban (#206)",
                haystack.contains(phrase),
            )
        }
    }

    @Test
    fun content_policy_rejects_prognostic_phrases_in_any_pattern() {
        // CI-pinned: AlertContentPolicy bans these phrases globally, not
        // just on the RBD pattern. Construct an off-list pattern and
        // assert the validator catches each one.
        val phrases = listOf(
            "Parkinson's risk",
            "synucleinopathy",
            "you may be developing",
            "you may develop",
        )
        for (phrase in phrases) {
            val violating = com.bios.app.alerts.ConditionPattern(
                id = "test_prognostic_ban_$phrase",
                title = "Test prognostic banned phrase",
                category = ConditionCategory.NEUROLOGICAL,
                signalRules = emptyList(),
                minActiveSignals = 1,
                explanation = "This contains $phrase which should be banned.",
                suggestedAction = "Test",
            )
            val violations = AlertContentPolicy.validate(violating)
            assertTrue(
                "AlertContentPolicy must reject '$phrase' on the push side",
                violations.isNotEmpty(),
            )
        }
    }

    // ---- citations ----

    @Test
    fun rbd_pattern_cites_postuma_schenck_and_wearable_proxy_literature() {
        val pattern = RbdScreenPattern.rbdScreen
        val text = pattern.signalRules.joinToString(" ") { it.citation } +
            " " + pattern.references.joinToString(" ")
        assertTrue("must cite Postuma 2019", text.contains("Postuma"))
        assertTrue("must cite Schenck", text.contains("Schenck"))
        assertTrue("must cite Louter 2014 (accel-during-REM proxy)", text.contains("Louter"))
    }

    // ---- explanation framing ----

    @Test
    fun rbd_pattern_explanation_frames_as_screening_not_diagnosis() {
        val explanation = RbdScreenPattern.rbdScreen.explanation.lowercase()
        assertTrue(
            "explanation must frame as screening / informational, not diagnostic",
            explanation.contains("screening") ||
                explanation.contains("informational") ||
                explanation.contains("not diagnostic"),
        )
    }

    @Test
    fun rbd_pattern_suggested_action_references_clinical_referral() {
        val action = RbdScreenPattern.rbdScreen.suggestedAction!!.lowercase()
        assertTrue(
            "suggested action must reference healthcare provider / sleep medicine / polysomnography",
            action.contains("healthcare provider") ||
                action.contains("sleep medicine") ||
                action.contains("polysomnography"),
        )
    }
}
