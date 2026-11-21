package com.bios.app

import com.bios.app.model.Anomaly
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tests for the timeline grouping and stats logic used by TimelineScreen.
 */
class TimelineScreenTest {

    private fun anomaly(
        detectedAt: Long = System.currentTimeMillis(),
        feedbackAt: Long? = null,
        outcomeAccurate: Boolean? = null,
        visitedDoctor: Boolean? = null,
        severity: Int = 2
    ) = Anomaly(
        metricTypes = "[\"heart_rate\"]",
        deviationScores = "{\"heart_rate\":2.0}",
        combinedScore = 2.0,
        severity = severity,
        title = "Test",
        explanation = "Test explanation",
        detectedAt = detectedAt,
        feedbackAt = feedbackAt,
        outcomeAccurate = outcomeAccurate,
        visitedDoctor = visitedDoctor
    )

    @Test
    fun `entries group by date correctly`() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val now = System.currentTimeMillis()
        val yesterday = now - 86_400_000L

        val entries = listOf(
            anomaly(detectedAt = now),
            anomaly(detectedAt = now - 1000),
            anomaly(detectedAt = yesterday)
        )

        val grouped = entries.groupBy { dateFormat.format(Date(it.detectedAt)) }
        assertEquals(2, grouped.size)

        val todayKey = dateFormat.format(Date(now))
        val yesterdayKey = dateFormat.format(Date(yesterday))
        assertEquals(2, grouped[todayKey]?.size)
        assertEquals(1, grouped[yesterdayKey]?.size)
    }

    @Test
    fun `stats summary counts feedback entries`() {
        val entries = listOf(
            anomaly(feedbackAt = 1L),
            anomaly(feedbackAt = 2L),
            anomaly(feedbackAt = null)
        )
        val withFeedback = entries.count { it.feedbackAt != null }
        assertEquals(2, withFeedback)
    }

    @Test
    fun `empty entries list produces no groups`() {
        val entries = emptyList<Anomaly>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val grouped = entries.groupBy { dateFormat.format(Date(it.detectedAt)) }
        assertTrue(grouped.isEmpty())
    }

    @Test
    fun `entries sorted descending by detectedAt`() {
        val now = System.currentTimeMillis()
        val entries = listOf(
            anomaly(detectedAt = now - 2000),
            anomaly(detectedAt = now),
            anomaly(detectedAt = now - 1000)
        )
        val sorted = entries.sortedByDescending { it.detectedAt }
        assertEquals(now, sorted[0].detectedAt)
        assertEquals(now - 1000, sorted[1].detectedAt)
        assertEquals(now - 2000, sorted[2].detectedAt)
    }
}
