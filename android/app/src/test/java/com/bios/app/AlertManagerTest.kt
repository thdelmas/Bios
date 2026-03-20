package com.bios.app

import com.bios.app.model.AlertTier
import com.bios.app.model.Anomaly
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for AlertManager notification tier filtering logic.
 * AlertManager.sendNotification only sends for NOTICE and above;
 * we test that classification here without needing an Android context.
 */
class AlertManagerTest {

    // Mirror of AlertManager.sendNotification's filtering logic
    private fun shouldNotify(anomaly: Anomaly): Boolean {
        val tier = AlertTier.fromLevel(anomaly.severity)
        return tier >= AlertTier.NOTICE
    }

    private fun anomaly(severity: AlertTier) = Anomaly(
        metricTypes = "[\"heart_rate\"]",
        deviationScores = "{\"heart_rate\":2.0}",
        combinedScore = 2.0,
        patternId = "test",
        severity = severity.level,
        title = "Test Alert",
        explanation = "Test explanation"
    )

    @Test
    fun `observation does not trigger notification`() {
        assertFalse(shouldNotify(anomaly(AlertTier.OBSERVATION)))
    }

    @Test
    fun `notice triggers notification`() {
        assertTrue(shouldNotify(anomaly(AlertTier.NOTICE)))
    }

    @Test
    fun `advisory triggers notification`() {
        assertTrue(shouldNotify(anomaly(AlertTier.ADVISORY)))
    }

    @Test
    fun `urgent triggers notification`() {
        assertTrue(shouldNotify(anomaly(AlertTier.URGENT)))
    }

    @Test
    fun `anomaly default values are reasonable`() {
        val a = anomaly(AlertTier.NOTICE)
        assertFalse(a.acknowledged)
        assertNull(a.acknowledgedAt)
        assertTrue(a.detectedAt > 0)
        assertTrue(a.id.isNotBlank())
    }

    @Test
    fun `anomaly id is unique per instance`() {
        val a1 = anomaly(AlertTier.NOTICE)
        val a2 = anomaly(AlertTier.NOTICE)
        assertNotEquals(a1.id, a2.id)
    }
}
