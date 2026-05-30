package com.bios.app

import com.bios.app.alerts.AlertManager
import com.bios.app.model.AlertTier
import com.bios.app.model.Anomaly
import com.bios.app.model.InterventionLevel
import com.bios.app.physiology.PhysiologyState
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for AlertManager's pure notification decisions: the NOTICE-and-above
 * notify gate and the goals-of-care channel selection (#184). These call the
 * real AlertManager decision functions directly (no Android context needed),
 * so a regression in either is caught here instead of hiding behind a copy.
 */
class AlertManagerTest {

    // Adapters over the real AlertManager decision functions — these
    // re-implement no logic, they only shape an Anomaly / (state, level) into
    // the arguments the production functions take.
    private fun shouldNotify(anomaly: Anomaly): Boolean =
        AlertManager.shouldNotify(AlertTier.fromLevel(anomaly.severity))

    private fun resolveChannel(
        tier: AlertTier,
        state: PhysiologyState,
        level: InterventionLevel,
    ): String = AlertManager.resolveChannel(
        tier,
        AlertManager.isUrgentEscalationSuppressedFor(tier, state, level),
    ).first

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

    // -- #184: goals-of-care escalation gate --

    @Test
    fun `urgent alert in standard state with full goals lands on urgent channel`() {
        // Happy path: no goals of care declared (or full treatment
        // declared). URGENT alerts must escalate as before — this is
        // the regression guard for owners who never visit the
        // goals-of-care screen.
        assertEquals(
            AlertManager.CHANNEL_URGENT,
            resolveChannel(AlertTier.URGENT, PhysiologyState.STANDARD, InterventionLevel.FULL),
        )
        assertEquals(
            AlertManager.CHANNEL_URGENT,
            resolveChannel(AlertTier.URGENT, PhysiologyState.STANDARD, InterventionLevel.UNSPECIFIED),
        )
    }

    @Test
    fun `urgent alert in hospice mode lands on notice channel`() {
        // Silencing path: URGENT alert is demoted to the low-
        // importance NOTICE channel, so no sound or vibration
        // fires. The downstream SMS-to-emergency-contact / tap-to-
        // call escalation (#215) consults the same gate and skips.
        assertEquals(
            AlertManager.CHANNEL_NOTICE,
            resolveChannel(AlertTier.URGENT, PhysiologyState.HOSPICE_MODE, InterventionLevel.UNSPECIFIED),
        )
    }

    @Test
    fun `urgent alert with comfort-only intervention lands on notice channel`() {
        // Symmetric path via GoalsOfCare rather than the hospice
        // mode toggle.
        assertEquals(
            AlertManager.CHANNEL_NOTICE,
            resolveChannel(AlertTier.URGENT, PhysiologyState.STANDARD, InterventionLevel.COMFORT_ONLY),
        )
    }

    @Test
    fun `goals-of-care note text avoids prohibited push-side phrases`() {
        // AlertContentPolicy bans second-person lifestyle judgments,
        // wellness-score language, guilt mechanics, and gamification
        // on the push side. The goals-of-care suppression note is
        // appended to push-side alert text, so it must pass the
        // same bar. Mirror the policy's checks here so the constant
        // is pinned at the test boundary.
        val text = AlertManager.GOALS_OF_CARE_NOTE.lowercase()
        val forbidden = listOf(
            "you should", "you need to", "you must", "you failed",
            "your health score", "grade:", "streak", "achievement",
        )
        for (phrase in forbidden) {
            assertFalse(
                "GOALS_OF_CARE_NOTE must not contain '$phrase'",
                text.contains(phrase)
            )
        }
    }
}
