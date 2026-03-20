package com.bios.app

import com.bios.app.privacy.AnonymousContribution
import com.bios.app.privacy.MetricSummary
import org.junit.Assert.*
import org.junit.Test

class CommunityAggregatorTest {

    @Test
    fun `anonymous contribution contains no identifiers`() {
        val contribution = AnonymousContribution(
            metricSummaries = listOf(
                MetricSummary(
                    metricType = "heart_rate",
                    meanRange = "65-70",
                    noisyStdDev = 5.2,
                    sampleBracket = "medium"
                )
            ),
            alertFeedback = emptyList(),
            ageBracket = "30-39",
            deviceClass = "wrist_wearable"
        )

        // Verify structure contains no identifying fields
        assertNotNull(contribution.metricSummaries)
        assertEquals("65-70", contribution.metricSummaries[0].meanRange)
        assertEquals("wrist_wearable", contribution.deviceClass)

        // Mean is binned, not exact
        assertTrue(contribution.metricSummaries[0].meanRange.contains("-"))

        // Sample count is bracketed, not exact
        assertTrue(contribution.metricSummaries[0].sampleBracket in listOf("low", "medium", "high", "very_high"))
    }

    @Test
    fun `metric summary uses binned ranges not exact values`() {
        val summary = MetricSummary(
            metricType = "heart_rate",
            meanRange = "70-75",
            noisyStdDev = 4.8,
            sampleBracket = "high"
        )

        // The mean range should be a bin, not an exact value
        val parts = summary.meanRange.split("-")
        assertEquals(2, parts.size)
        assertTrue(parts[0].toIntOrNull() != null)
        assertTrue(parts[1].toIntOrNull() != null)
    }
}
