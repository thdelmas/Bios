package com.bios.app

import com.bios.app.alerts.AlertContentPolicy
import com.bios.app.alerts.CompanionConditionPatterns
import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.DeviationDirection
import com.bios.app.alerts.ThresholdSource
import com.bios.app.model.ConditionCategory
import com.bios.contracts.MetricType
import com.bios.contracts.MetricUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionConditionPatternsTest {

    private val ids = setOf(
        "fall_orthostatic_pattern",
        "fall_neurological_pattern",
        "fall_hypoglycemia_pattern",
        "check_in_decline_pattern",
        "substance_use_cv_load_pattern",
        "craving_sleep_debt_pattern",
        "cessation_recovery_pattern",
    )

    @Test
    fun `all expected companion patterns are registered in the engine`() {
        val registered = ConditionPatterns.all.map { it.id }.toSet()
        for (id in ids) {
            assertTrue("$id missing from ConditionPatterns.all", id in registered)
        }
    }

    @Test
    fun `every companion pattern includes at least one event-driven required rule`() {
        for (p in CompanionConditionPatterns.all) {
            val hasRequiredEvent = p.signalRules.any {
                it.required && it.metricType.unit == MetricUnit.EVENT
            }
            assertTrue(
                "Pattern ${p.id} must gate on a required EVENT-unit rule so " +
                    "vital-sign drift alone never triggers it",
                hasRequiredEvent
            )
        }
    }

    @Test
    fun `every companion pattern passes the alert content policy`() {
        for (p in CompanionConditionPatterns.all) {
            val violations = AlertContentPolicy.validate(p)
            assertTrue(
                "Pattern ${p.id} violates content policy: " +
                    violations.joinToString { "${it.field}=${it.prohibitedPhrase}" },
                violations.isEmpty()
            )
        }
    }

    @Test
    fun `cessation_recovery uses ABSENT direction on its gating rule`() {
        val p = CompanionConditionPatterns.cessationRecovery
        val gate = p.signalRules.firstOrNull { it.required }
        assertNotNull(gate)
        assertEquals(DeviationDirection.ABSENT, gate!!.direction)
        assertEquals(MetricType.TOBACCO_USE, gate.metricType)
    }

    @Test
    fun `fall patterns are categorised as SAFETY or METABOLIC`() {
        val fallPatterns = CompanionConditionPatterns.all.filter { it.id.startsWith("fall_") }
        assertTrue("Expected fall_* patterns to exist", fallPatterns.size >= 3)
        for (p in fallPatterns) {
            assertTrue(
                "${p.id} category=${p.category} is not a fall-appropriate clinical bucket",
                p.category == ConditionCategory.SAFETY || p.category == ConditionCategory.METABOLIC
            )
        }
    }

    @Test
    fun `every companion rule cites either LITERATURE or COMPANION source`() {
        for (p in CompanionConditionPatterns.all) {
            for (rule in p.signalRules) {
                assertTrue(
                    "Pattern ${p.id} rule ${rule.metricType} must be backed by literature or " +
                        "originate from a companion (current source: ${rule.source})",
                    rule.source == ThresholdSource.LITERATURE || rule.source == ThresholdSource.COMPANION
                )
            }
        }
    }
}
