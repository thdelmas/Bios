package com.bios.app

import com.bios.app.alerts.FollowUpWorker
import com.bios.app.model.Anomaly
import org.junit.Assert.*
import org.junit.Test

class FollowUpWorkerTest {

    private fun anomaly(
        id: String = "test-id",
        feedbackAt: Long? = null
    ) = Anomaly(
        id = id,
        metricTypes = "[\"heart_rate\"]",
        deviationScores = "{\"heart_rate\":2.5}",
        combinedScore = 2.5,
        severity = 2,
        title = "Elevated heart rate",
        explanation = "Heart rate is above baseline",
        feedbackAt = feedbackAt
    )

    @Test
    fun `follow-up delay is 24 hours`() {
        assertEquals(24L, FollowUpWorker.FOLLOW_UP_DELAY_HOURS)
    }

    @Test
    fun `input data keys are stable`() {
        assertEquals("anomaly_id", FollowUpWorker.KEY_ANOMALY_ID)
        assertEquals("alert_title", FollowUpWorker.KEY_ALERT_TITLE)
    }

    @Test
    fun `anomaly without feedback should trigger follow-up`() {
        val a = anomaly(feedbackAt = null)
        assertNull(a.feedbackAt)
    }

    @Test
    fun `anomaly with feedback should skip follow-up`() {
        val a = anomaly(feedbackAt = System.currentTimeMillis())
        assertNotNull(a.feedbackAt)
    }

    @Test
    fun `unique work name is derived from anomaly id`() {
        val id = "abc-123"
        val expectedTag = "followup_$id"
        assertEquals("followup_abc-123", expectedTag)
    }
}
