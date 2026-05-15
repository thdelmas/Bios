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
    fun `all baseline-relative signal rules have positive thresholds`() {
        // Absolute-threshold rules carry their cutoff in absoluteAbove /
        // absoluteBelow and intentionally leave thresholdSigma at 0.
        for (pattern in ConditionPatterns.all) {
            for (rule in pattern.signalRules) {
                if (rule.isAbsolute) continue
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
    fun `all baseline-relative signal rules have positive duration hours`() {
        // Absolute-threshold rules read the latest lab via fetchLatest — no
        // time window is meaningful, so minDurationHours is intentionally 0.
        for (pattern in ConditionPatterns.all) {
            for (rule in pattern.signalRules) {
                if (rule.isAbsolute) continue
                assertTrue(
                    "Pattern ${pattern.id}, metric ${rule.metricType}: duration must be positive",
                    rule.minDurationHours > 0
                )
            }
        }
    }

    @Test
    fun `every absolute-threshold rule sets at least one bound`() {
        for (pattern in ConditionPatterns.all) {
            for (rule in pattern.signalRules) {
                if (!rule.isAbsolute) continue
                assertTrue(
                    "Pattern ${pattern.id}, metric ${rule.metricType}: " +
                        "isAbsolute true but neither absoluteAbove nor absoluteBelow set",
                    rule.absoluteAbove != null || rule.absoluteBelow != null
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
    fun `circadian disruption pattern is registered in the global all list`() {
        assertTrue(ConditionPatterns.circadianDisruption in ConditionPatterns.all)
    }

    @Test
    fun `circadian disruption gates on AMBIENT_LIGHT irregularity as the required rule`() {
        val pattern = ConditionPatterns.circadianDisruption
        val lightRule = pattern.signalRules.first {
            it.metricType == com.bios.contracts.MetricType.AMBIENT_LIGHT
        }
        assertTrue(
            "AMBIENT_LIGHT must be the required gate so sleep drift alone never fires the pattern",
            lightRule.required
        )
        assertEquals(DeviationDirection.IRREGULAR, lightRule.direction)
        assertEquals(168, lightRule.minDurationHours)
    }

    @Test
    fun `circadian disruption corroborators cover sleep depth and timing`() {
        val pattern = ConditionPatterns.circadianDisruption
        val corroborators = pattern.signalRules
            .filter { !it.required }
            .map { it.metricType }
            .toSet()
        // Sleep depth (SLEEP_STAGE BELOW) + timing variability (SLEEP_DURATION
        // IRREGULAR) — the two halves of the sleep-architecture response to
        // misaligned light exposure.
        assertTrue(com.bios.contracts.MetricType.SLEEP_STAGE in corroborators)
        assertTrue(com.bios.contracts.MetricType.SLEEP_DURATION in corroborators)
    }

    @Test
    fun `circadian disruption requires at least one corroborator alongside the light gate`() {
        assertEquals(2, ConditionPatterns.circadianDisruption.minActiveSignals)
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
