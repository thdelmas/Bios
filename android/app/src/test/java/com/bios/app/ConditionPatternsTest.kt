package com.bios.app

import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.DeviationDirection
import org.junit.Assert.*
import org.junit.Test

class ConditionPatternsTest {

    @Test
    fun `all patterns have unique ids`() {
        val ids = ConditionPatterns.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `all patterns have non-empty titles`() {
        for (pattern in ConditionPatterns.all) {
            assertTrue("Pattern ${pattern.id} has empty title", pattern.title.isNotBlank())
        }
    }

    @Test
    fun `all patterns have non-empty explanations`() {
        for (pattern in ConditionPatterns.all) {
            assertTrue("Pattern ${pattern.id} has empty explanation", pattern.explanation.isNotBlank())
        }
    }

    @Test
    fun `minActiveSignals is at most the number of signal rules`() {
        for (pattern in ConditionPatterns.all) {
            assertTrue(
                "Pattern ${pattern.id}: minActiveSignals (${pattern.minActiveSignals}) > rules (${pattern.signalRules.size})",
                pattern.minActiveSignals <= pattern.signalRules.size
            )
        }
    }

    @Test
    fun `all signal rules have positive thresholds`() {
        for (pattern in ConditionPatterns.all) {
            for (rule in pattern.signalRules) {
                assertTrue(
                    "Pattern ${pattern.id}, metric ${rule.metricType}: threshold must be positive",
                    rule.thresholdSigma > 0.0
                )
            }
        }
    }

    @Test
    fun `all signal rules have positive weights`() {
        for (pattern in ConditionPatterns.all) {
            for (rule in pattern.signalRules) {
                assertTrue(
                    "Pattern ${pattern.id}, metric ${rule.metricType}: weight must be positive",
                    rule.weight > 0.0
                )
            }
        }
    }

    @Test
    fun `all signal rules have positive duration hours`() {
        for (pattern in ConditionPatterns.all) {
            for (rule in pattern.signalRules) {
                assertTrue(
                    "Pattern ${pattern.id}, metric ${rule.metricType}: duration must be positive",
                    rule.minDurationHours > 0
                )
            }
        }
    }

    @Test
    fun `infection onset pattern requires 3 signals from 6 rules`() {
        val pattern = ConditionPatterns.infectionOnset
        assertEquals(3, pattern.minActiveSignals)
        assertEquals(6, pattern.signalRules.size)
    }

    @Test
    fun `infection onset monitors correct metrics`() {
        val metricKeys = ConditionPatterns.infectionOnset.signalRules.map { it.metricType.key }
        assertTrue(metricKeys.contains("resting_heart_rate"))
        assertTrue(metricKeys.contains("heart_rate_variability"))
        assertTrue(metricKeys.contains("skin_temperature_deviation"))
        assertTrue(metricKeys.contains("respiratory_rate"))
    }

    @Test
    fun `sleep disruption pattern requires 2 signals`() {
        assertEquals(2, ConditionPatterns.sleepDisruption.minActiveSignals)
    }

    @Test
    fun `cardiovascular stress pattern requires 2 signals`() {
        assertEquals(2, ConditionPatterns.cardiovascularStress.minActiveSignals)
    }

    @Test
    fun `all patterns have a suggested action`() {
        for (pattern in ConditionPatterns.all) {
            assertNotNull("Pattern ${pattern.id} should have a suggested action", pattern.suggestedAction)
            assertTrue(
                "Pattern ${pattern.id} suggested action should not be blank",
                pattern.suggestedAction!!.isNotBlank()
            )
        }
    }
}
