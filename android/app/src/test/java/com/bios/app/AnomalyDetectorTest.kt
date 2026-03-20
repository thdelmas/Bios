package com.bios.app

import com.bios.app.model.AlertTier
import com.bios.app.model.MetricType
import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.DeviationDirection
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/**
 * Tests for AnomalyDetector's pure logic: severity classification and explanation building.
 * These mirror the private methods for direct unit testing.
 */
class AnomalyDetectorTest {

    // Mirror of AnomalyDetector.classifySeverity
    private fun classifySeverity(
        activeSignals: Int,
        combinedScore: Double,
        totalRules: Int
    ): AlertTier {
        val signalRatio = activeSignals.toDouble() / totalRules.toDouble()
        return when {
            combinedScore > 3.0 || signalRatio > 0.8 -> AlertTier.ADVISORY
            combinedScore > 2.0 || signalRatio > 0.5 -> AlertTier.NOTICE
            else -> AlertTier.OBSERVATION
        }
    }

    // Mirror of AnomalyDetector.buildExplanation
    private fun buildExplanation(
        patternExplanation: String,
        deviations: Map<MetricType, Double>
    ): String {
        val parts = deviations.entries
            .sortedByDescending { abs(it.value) }
            .map { (metric, zScore) ->
                val direction = if (zScore > 0) "above" else "below"
                val sigmas = String.format("%.1f", abs(zScore))
                "Your ${metric.readableName.lowercase()} is $sigmas standard deviations $direction your personal baseline."
            }
            .toMutableList()

        parts.add(patternExplanation)
        return parts.joinToString(" ")
    }

    // --- classifySeverity tests ---

    @Test
    fun `high combined score triggers advisory`() {
        val tier = classifySeverity(activeSignals = 2, combinedScore = 3.5, totalRules = 6)
        assertEquals(AlertTier.ADVISORY, tier)
    }

    @Test
    fun `high signal ratio triggers advisory`() {
        val tier = classifySeverity(activeSignals = 5, combinedScore = 1.0, totalRules = 6)
        assertEquals(AlertTier.ADVISORY, tier)
    }

    @Test
    fun `medium combined score triggers notice`() {
        val tier = classifySeverity(activeSignals = 2, combinedScore = 2.5, totalRules = 6)
        assertEquals(AlertTier.NOTICE, tier)
    }

    @Test
    fun `medium signal ratio triggers notice`() {
        val tier = classifySeverity(activeSignals = 4, combinedScore = 1.0, totalRules = 6)
        assertEquals(AlertTier.NOTICE, tier)
    }

    @Test
    fun `low score and low ratio triggers observation`() {
        val tier = classifySeverity(activeSignals = 1, combinedScore = 1.5, totalRules = 6)
        assertEquals(AlertTier.OBSERVATION, tier)
    }

    @Test
    fun `boundary combined score 3 is notice not advisory`() {
        val tier = classifySeverity(activeSignals = 1, combinedScore = 3.0, totalRules = 6)
        assertEquals(AlertTier.NOTICE, tier)
    }

    @Test
    fun `boundary combined score 2 is observation not notice`() {
        val tier = classifySeverity(activeSignals = 1, combinedScore = 2.0, totalRules = 6)
        assertEquals(AlertTier.OBSERVATION, tier)
    }

    // --- buildExplanation tests ---

    @Test
    fun `explanation includes deviations sorted by magnitude`() {
        val deviations = mapOf(
            MetricType.RESTING_HEART_RATE to 2.0,
            MetricType.HEART_RATE_VARIABILITY to -3.0,
            MetricType.SKIN_TEMPERATURE_DEVIATION to 1.5
        )

        val explanation = buildExplanation("Pattern explanation.", deviations)

        // HRV (|3.0|) should come first, then RHR (|2.0|), then temp (|1.5|)
        val hrvIdx = explanation.indexOf("heart rate variability")
        val rhrIdx = explanation.indexOf("resting heart rate")
        val tempIdx = explanation.indexOf("skin temperature deviation")

        assertTrue("HRV should appear before RHR", hrvIdx < rhrIdx)
        assertTrue("RHR should appear before temp", rhrIdx < tempIdx)
    }

    @Test
    fun `explanation shows above for positive z-scores`() {
        val deviations = mapOf(MetricType.RESTING_HEART_RATE to 2.5)
        val explanation = buildExplanation("Test.", deviations)

        assertTrue(explanation.contains("above your personal baseline"))
    }

    @Test
    fun `explanation shows below for negative z-scores`() {
        val deviations = mapOf(MetricType.HEART_RATE_VARIABILITY to -2.0)
        val explanation = buildExplanation("Test.", deviations)

        assertTrue(explanation.contains("below your personal baseline"))
    }

    @Test
    fun `explanation ends with pattern explanation`() {
        val deviations = mapOf(MetricType.HEART_RATE to 1.5)
        val patternExplanation = "This is the pattern explanation."
        val explanation = buildExplanation(patternExplanation, deviations)

        assertTrue(explanation.endsWith(patternExplanation))
    }

    // --- AlertTier tests ---

    @Test
    fun `alert tier ordering`() {
        assertTrue(AlertTier.OBSERVATION < AlertTier.NOTICE)
        assertTrue(AlertTier.NOTICE < AlertTier.ADVISORY)
        assertTrue(AlertTier.ADVISORY < AlertTier.URGENT)
    }

    @Test
    fun `alert tier fromLevel roundtrips`() {
        for (tier in AlertTier.entries) {
            assertEquals(tier, AlertTier.fromLevel(tier.level))
        }
    }

    @Test
    fun `alert tier fromLevel unknown returns observation`() {
        assertEquals(AlertTier.OBSERVATION, AlertTier.fromLevel(99))
    }
}
