package com.bios.app

import com.bios.app.alerts.AlertContentPolicy
import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.DeviationDirection
import com.bios.app.alerts.PsychiatryPatterns
import com.bios.app.alerts.ThresholdSource
import com.bios.app.model.AlertTier
import com.bios.app.model.ConditionCategory
import com.bios.app.physiology.PhysiologyState
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the psychiatry-context patterns (#205, PSYCHIATRY_POV §2.1–§2.7 +
 * OBGYN_POV perinatal + EMERGENCY MEDICINE OUD audit convergence).
 *
 * The tests pin the manifesto guards in code — these patterns have the
 * highest iatrogenic-anxiety risk of any audit-derived surface, so:
 *
 *  - Each pattern is ADVISORY only (never URGENT — the URGENT cases live
 *    in the existing emergency / NEWS2 pathways).
 *  - Each pattern declares a `requiredStates` gate so it only fires when
 *    the owner has explicitly named the underlying context.
 *  - All text is [AlertContentPolicy]-compliant — no second-person
 *    judgement, no scores, no guilt language, no "you may be entering
 *    mania" / "you may be depressed".
 *  - The manifesto-banned do-not-implement list (suicidality
 *    auto-detection, eating-disorder auto-detection, voice/face/text
 *    emotional-state inference, composite mental-health score) is pinned
 *    by absence — no such pattern exists in [ConditionPatterns.all].
 */
class PsychiatryPatternsTest {

    // -- registration --

    @Test
    fun all_psychiatry_patterns_register_in_global_all_list() {
        assertTrue(PsychiatryPatterns.bipolarManiaProdromeScreen in ConditionPatterns.all)
        assertTrue(PsychiatryPatterns.anxietyPanicScreen in ConditionPatterns.all)
        assertTrue(PsychiatryPatterns.postpartumMoodScreen in ConditionPatterns.all)
        assertTrue(PsychiatryPatterns.opioidUseRespiratoryPatternScreen in ConditionPatterns.all)
    }

    // -- ADVISORY-only invariant --

    @Test
    fun every_psychiatry_pattern_is_advisory_only_never_urgent() {
        for (pattern in PsychiatryPatterns.all) {
            assertNull(
                "${pattern.id} must not declare URGENT severity floor — the URGENT cases live in EmergencyVitalPatterns / SepsisScreenPattern; psychiatry surfaces are ADVISORY-only (manifesto + audit §2.4)",
                pattern.severityFloor?.takeIf { it == AlertTier.URGENT },
            )
        }
    }

    // -- requiredStates gate (owner-declared context) --

    @Test
    fun every_psychiatry_pattern_gates_on_an_owner_declared_state() {
        for (pattern in PsychiatryPatterns.all) {
            assertFalse(
                "${pattern.id} must declare requiredStates so it only fires under an owner-declared context — Bios never infers mental-health / OUD context",
                pattern.requiredStates.isEmpty(),
            )
        }
    }

    @Test
    fun bipolar_pattern_requires_known_bipolar_disorder_state() {
        assertEquals(
            setOf(PhysiologyState.KNOWN_BIPOLAR_DISORDER),
            PsychiatryPatterns.bipolarManiaProdromeScreen.requiredStates,
        )
    }

    @Test
    fun anxiety_pattern_requires_known_gad_state() {
        assertEquals(
            setOf(PhysiologyState.KNOWN_GAD),
            PsychiatryPatterns.anxietyPanicScreen.requiredStates,
        )
    }

    @Test
    fun postpartum_pattern_requires_postpartum_state() {
        assertEquals(
            setOf(PhysiologyState.POSTPARTUM),
            PsychiatryPatterns.postpartumMoodScreen.requiredStates,
        )
    }

    @Test
    fun opioid_pattern_requires_known_oud_treatment_state() {
        assertEquals(
            setOf(PhysiologyState.KNOWN_OUD_TREATMENT),
            PsychiatryPatterns.opioidUseRespiratoryPatternScreen.requiredStates,
        )
    }

    // -- bipolar mania prodrome --

    @Test
    fun bipolar_pattern_signals_reduced_sleep_and_elevated_activity() {
        val rules = PsychiatryPatterns.bipolarManiaProdromeScreen.signalRules
        val sleep = rules.single { it.metricType == MetricType.SLEEP_DURATION }
        val steps = rules.single { it.metricType == MetricType.STEPS }

        assertEquals(DeviationDirection.BELOW, sleep.direction)
        // The Seoul Nat'l Univ 2024 signature: activity stays up while
        // sleep drops — the inverse of healthy sleep reduction.
        assertEquals(DeviationDirection.ABOVE, steps.direction)
    }

    @Test
    fun bipolar_pattern_cites_seoul_2024_and_bauer_2020() {
        val pattern = PsychiatryPatterns.bipolarManiaProdromeScreen
        val allCitations = pattern.signalRules.joinToString(" ") { it.citation } +
            " " + pattern.references.joinToString(" ")
        assertTrue(
            "bipolar pattern must cite Seoul Nat'l Univ 2024 digital phenotyping (audit §2.1)",
            allCitations.contains("Seoul"),
        )
        assertTrue(
            "bipolar pattern must cite Bauer 2020 (audit §2.1)",
            allCitations.contains("Bauer"),
        )
    }

    // -- anxiety / panic --

    @Test
    fun anxiety_pattern_signals_sympathetic_arousal_shape() {
        val rules = PsychiatryPatterns.anxietyPanicScreen.signalRules
        val hr = rules.single { it.metricType == MetricType.HEART_RATE }
        val hrv = rules.single { it.metricType == MetricType.HEART_RATE_VARIABILITY }

        assertEquals(DeviationDirection.ABOVE, hr.direction)
        assertEquals(DeviationDirection.BELOW, hrv.direction)
    }

    @Test
    fun anxiety_pattern_evaluates_on_short_window_for_acute_episodes() {
        // Panic / acute anxiety episodes are minutes-to-hours events,
        // not multi-day trends — short evaluation windows distinguish
        // them from the multi-day mental_health_correlate pattern.
        val rules = PsychiatryPatterns.anxietyPanicScreen.signalRules
        for (rule in rules) {
            assertTrue(
                "${rule.metricType} should evaluate over a short window (<= 4h) for acute episode detection",
                rule.minDurationHours <= 4,
            )
        }
    }

    // -- postpartum mood --

    @Test
    fun postpartum_pattern_requires_convergence_to_distinguish_from_normative_disruption() {
        // Postpartum sleep disruption is normative; this pattern only
        // fires on the 3-signal convergence (sleep + activity + rhythm)
        // that distinguishes mood-related shifts from ordinary
        // newborn-care fragmentation.
        val pattern = PsychiatryPatterns.postpartumMoodScreen
        assertTrue(
            "postpartum pattern needs ≥3 active signals to distinguish from normative postpartum sleep disruption",
            pattern.minActiveSignals >= 3,
        )
    }

    @Test
    fun postpartum_pattern_cites_acog_and_epds_framework() {
        val pattern = PsychiatryPatterns.postpartumMoodScreen
        val refs = pattern.references.joinToString(" ")
        assertTrue("postpartum pattern must cite ACOG perinatal-mental-health guidance", refs.contains("ACOG"))
        assertTrue("postpartum pattern must cite EPDS / Cox 1987", refs.contains("EPDS") || refs.contains("Cox"))
    }

    // -- opioid respiratory pattern --

    @Test
    fun opioid_pattern_uses_absolute_thresholds_not_baseline_relative() {
        // RR ≤10 and SpO2 ≤92 are clinical-literature absolute cutoffs,
        // not personal-baseline z-scores — Nandakumar 2019 UW SAFE
        // operates on absolute values.
        val rules = PsychiatryPatterns.opioidUseRespiratoryPatternScreen.signalRules
        val rr = rules.single { it.metricType == MetricType.RESPIRATORY_RATE }
        val spo2 = rules.single { it.metricType == MetricType.BLOOD_OXYGEN }

        assertEquals(10.0, rr.absoluteBelow!!, 1e-9)
        assertEquals(92.0, spo2.absoluteBelow!!, 1e-9)
        assertTrue("RR rule is required (gate)", rr.required)
        assertTrue("SpO2 rule is required (gate)", spo2.required)
    }

    @Test
    fun opioid_pattern_is_advisory_only_urgent_handled_elsewhere() {
        // The URGENT case (RR ≤8) is handled by the NEWS2 / sepsis-screen
        // pathway and the dedicated opioid_respiratory_depression_screen
        // (#190 / #221). This pattern is the early-warning trend only.
        assertNull(
            "opioid early-warning pattern is ADVISORY only — URGENT case lives in SepsisScreenPattern / EmergencyVitalPatterns",
            PsychiatryPatterns.opioidUseRespiratoryPatternScreen.severityFloor,
        )
    }

    @Test
    fun opioid_pattern_cites_nandakumar_2019() {
        val pattern = PsychiatryPatterns.opioidUseRespiratoryPatternScreen
        val text = pattern.signalRules.joinToString(" ") { it.citation } +
            " " + pattern.references.joinToString(" ")
        assertTrue("opioid pattern must cite Nandakumar 2019 UW SAFE", text.contains("Nandakumar"))
    }

    // -- AlertContentPolicy compliance (the critical guard) --

    @Test
    fun every_psychiatry_pattern_passes_alert_content_policy() {
        for (pattern in PsychiatryPatterns.all) {
            val violations = AlertContentPolicy.validate(pattern)
            assertTrue(
                violations.joinToString("\n") {
                    "${it.pattern.id}::${it.field} contains '${it.prohibitedPhrase}': ${it.context}"
                },
                violations.isEmpty(),
            )
        }
    }

    @Test
    fun no_psychiatry_pattern_says_you_may_be_entering_mania_or_you_may_be_depressed() {
        // The audit-explicit ban: psychiatry alerts never make
        // first-person predictions about the owner's mental state.
        // This pins the manifesto guard at the text layer.
        val banned = listOf(
            "you may be entering mania",
            "you may be depressed",
            "you are depressed",
            "you have depression",
            "you have bipolar",
            "you may have",
            "you might be",
            "mental health score",
            "depression score",
            "mania score",
            "anxiety score",
        )
        for (pattern in PsychiatryPatterns.all) {
            val haystack = listOf(
                pattern.title, pattern.explanation, pattern.suggestedAction ?: "",
                pattern.earlyDetection, pattern.prevention, pattern.healing, pattern.risks,
            ).joinToString(" ").lowercase()
            for (phrase in banned) {
                assertFalse(
                    "${pattern.id} must not contain '$phrase' — manifesto-explicit don't",
                    haystack.contains(phrase),
                )
            }
        }
    }

    // -- manifesto: do-not-implement guards (pinned by absence) --

    @Test
    fun no_suicidality_auto_detection_pattern_exists() {
        // Manifesto-explicit don't (audit §2.4): suicidality is never
        // auto-detected. Pin by absence — any future pattern accidentally
        // adding "suicid*" to its identity will fail this test.
        for (pattern in ConditionPatterns.all) {
            val haystack = (pattern.id + " " + pattern.title).lowercase()
            assertFalse(
                "${pattern.id} appears to reference suicidality auto-detection — manifesto-banned (audit §2.4)",
                haystack.contains("suicid"),
            )
        }
    }

    @Test
    fun no_eating_disorder_auto_detection_pattern_exists() {
        // Audit §2.7: eating-disorder detection is owner-administered
        // only (not auto-detected). Pin by absence.
        for (pattern in ConditionPatterns.all) {
            val haystack = (pattern.id + " " + pattern.title).lowercase()
            assertFalse(
                "${pattern.id} appears to auto-detect an eating disorder — audit §2.7 says owner-administered only",
                haystack.contains("eating_disorder") || haystack.contains("anorexia") ||
                    haystack.contains("bulimia") || haystack.contains("binge_eating"),
            )
        }
    }

    @Test
    fun no_composite_mental_health_score_pattern_exists() {
        // Manifesto: no composite "mental health score". Pin by absence.
        for (pattern in ConditionPatterns.all) {
            val haystack = (pattern.id + " " + pattern.title).lowercase()
            assertFalse(
                "${pattern.id} appears to be a composite mental-health score — manifesto-banned",
                haystack.contains("mental_health_score") || haystack.contains("mental-health score") ||
                    haystack.contains("wellness_score") || haystack.contains("wellbeing_score"),
            )
        }
    }

    // -- shape hygiene --

    @Test
    fun every_psychiatry_pattern_carries_a_suggested_action_with_clinical_referral() {
        for (pattern in PsychiatryPatterns.all) {
            val action = pattern.suggestedAction
            assertNotNull("${pattern.id} should ship a suggestedAction", action)
            val lower = action!!.lowercase()
            assertTrue(
                "${pattern.id} suggestedAction should reference the owner's clinician / care team / emergency services",
                lower.contains("clinician") || lower.contains("provider") ||
                    lower.contains("prescriber") || lower.contains("care plan") ||
                    lower.contains("care team") || lower.contains("emergency") ||
                    lower.contains("healthcare"),
            )
        }
    }

    @Test
    fun every_psychiatry_pattern_carries_literature_citations() {
        for (pattern in PsychiatryPatterns.all) {
            // Either the signalRules carry LITERATURE citations or the
            // pattern-level references list does. Most carry both.
            val ruleCitations = pattern.signalRules.any {
                it.source == ThresholdSource.LITERATURE && it.citation.isNotBlank()
            }
            val refs = pattern.references.isNotEmpty()
            assertTrue(
                "${pattern.id} must ship at least one literature citation (audit traceability)",
                ruleCitations || refs,
            )
        }
    }

    @Test
    fun every_psychiatry_pattern_uses_mental_health_or_respiratory_category() {
        // Three are MENTAL_HEALTH; the opioid-respiratory one is
        // RESPIRATORY (it's a respiratory-rate / SpO2 pattern that
        // happens to gate on an OUD-treatment context).
        for (pattern in PsychiatryPatterns.all) {
            assertTrue(
                "${pattern.id} should be MENTAL_HEALTH or RESPIRATORY",
                pattern.category == ConditionCategory.MENTAL_HEALTH ||
                    pattern.category == ConditionCategory.RESPIRATORY,
            )
        }
    }
}
