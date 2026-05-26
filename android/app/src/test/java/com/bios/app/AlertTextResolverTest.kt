package com.bios.app

import com.bios.app.alerts.AlertTextResolver
import com.bios.app.alerts.BaselineDeviationPatterns
import com.bios.app.alerts.ConditionPattern
import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.SignalRule
import com.bios.app.alerts.DeviationDirection
import com.bios.app.alerts.ThresholdSource
import com.bios.app.model.ConditionCategory
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [AlertTextResolver] — the localization-layer indirection
 * introduced in issue #210.
 *
 * These tests run without an Android `Context` (no Robolectric), so they
 * exercise the **fallback path**: when no Context is available, callers
 * must continue to receive the English source-of-truth text directly
 * from the Kotlin pattern fields. The Android-context-driven overlay
 * resolution is verified via instrumentation tests at run time and via
 * the static `pattern_strings.xml` declaration table below.
 */
class AlertTextResolverTest {

    @Test
    fun `explanation key follows pattern_id_explanation convention`() {
        val pattern = BaselineDeviationPatterns.infectionOnset
        assertEquals(
            "pattern_infection_onset_explanation",
            AlertTextResolver.explanationKey(pattern.id),
        )
    }

    @Test
    fun `suggested action key follows pattern_id_suggested_action convention`() {
        val pattern = BaselineDeviationPatterns.cardiovascularStress
        assertEquals(
            "pattern_cardiovascular_stress_suggested_action",
            AlertTextResolver.suggestedActionKey(pattern.id),
        )
    }

    @Test
    fun `resource keys lowercase the pattern id`() {
        // Defensive: even if someone introduces a mixed-case id by
        // accident, the lookup convention stays lowercase. Matches the
        // Android resource-name validity rules.
        val pattern = ConditionPattern(
            id = "Test_Pattern_ID",
            title = "Test",
            category = ConditionCategory.INFECTIOUS,
            signalRules = listOf(
                SignalRule(
                    MetricType.RESTING_HEART_RATE,
                    DeviationDirection.ABOVE, 1.5, 24, 1.0,
                    ThresholdSource.ENGINEERING,
                )
            ),
            minActiveSignals = 1,
            explanation = "fallback text",
            suggestedAction = null,
        )
        assertEquals(
            "pattern_test_pattern_id_explanation",
            AlertTextResolver.explanationKey(pattern.id),
        )
    }

    @Test
    fun `all registered patterns have non-blank explanation source fields`() {
        // Source-of-truth fallback: if the localization overlay misses a
        // key, the Kotlin field must still hold something renderable.
        // This guards against accidental empty assignments.
        for (pattern in ConditionPatterns.all) {
            assertTrue(
                "Pattern ${pattern.id} has blank explanation source",
                pattern.explanation.isNotBlank(),
            )
        }
    }

    @Test
    fun `every pattern's explanation key is unique across the registry`() {
        // Localization assumes resource-name uniqueness. A duplicated
        // pattern id would collapse two patterns onto one overlay string,
        // silently mis-rendering whichever lost the race.
        val keys = ConditionPatterns.all.map {
            AlertTextResolver.explanationKey(it.id)
        }
        assertEquals(
            "duplicate explanation keys: ${keys.groupBy { it }.filter { it.value.size > 1 }.keys}",
            keys.size, keys.toSet().size,
        )
    }
}
