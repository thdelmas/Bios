package com.bios.app

import com.bios.app.model.MetricType
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln
import kotlin.random.Random

/**
 * Tests for CommunityAggregator's pure logic: binning, bracketing,
 * differential privacy noise, and contribution delay randomization.
 * Mirrors private methods for direct unit testing.
 */
class ContributionWorkerTest {

    // Mirror of CommunityAggregator.binValue
    private fun binValue(metricType: MetricType, value: Double): String {
        val binSize = when (metricType) {
            MetricType.HEART_RATE, MetricType.RESTING_HEART_RATE -> 5.0
            MetricType.HEART_RATE_VARIABILITY -> 10.0
            MetricType.BLOOD_OXYGEN -> 1.0
            MetricType.RESPIRATORY_RATE -> 2.0
            MetricType.STEPS -> 1000.0
            MetricType.SLEEP_STAGE -> 1.0
            else -> 5.0
        }
        val lower = (value / binSize).toInt() * binSize
        val upper = lower + binSize
        return "${lower.toInt()}-${upper.toInt()}"
    }

    // Mirror of CommunityAggregator.bracketSampleCount
    private fun bracketSampleCount(count: Int): String {
        return when {
            count < 50 -> "low"
            count < 200 -> "medium"
            count < 500 -> "high"
            else -> "very_high"
        }
    }

    // Mirror of CommunityAggregator.estimateSensitivity
    private fun estimateSensitivity(metricType: MetricType): Double {
        return when (metricType) {
            MetricType.HEART_RATE -> 5.0
            MetricType.HEART_RATE_VARIABILITY -> 10.0
            MetricType.RESTING_HEART_RATE -> 3.0
            MetricType.BLOOD_OXYGEN -> 2.0
            MetricType.RESPIRATORY_RATE -> 2.0
            MetricType.STEPS -> 500.0
            MetricType.SLEEP_STAGE -> 0.5
            else -> 5.0
        }
    }

    // Mirror of CommunityAggregator.nextContributionDelayMillis
    private fun nextContributionDelayMillis(): Long {
        val minHours = 18
        val maxHours = 30
        return (minHours + Random.nextInt(maxHours - minHours)) * 3600L * 1000
    }

    // Mirror of CommunityAggregator.laplacianNoise
    private fun laplacianNoise(sensitivity: Double, epsilon: Double): Double {
        val scale = sensitivity / epsilon
        val u = Random.nextDouble() - 0.5
        return -scale * u.sign() * ln(1 - 2 * abs(u))
    }

    private fun Double.sign(): Double = if (this >= 0) 1.0 else -1.0

    // --- binValue tests ---

    @Test
    fun `heart rate 67 bins to 65-70`() {
        assertEquals("65-70", binValue(MetricType.HEART_RATE, 67.0))
    }

    @Test
    fun `heart rate 70 bins to 70-75`() {
        assertEquals("70-75", binValue(MetricType.HEART_RATE, 70.0))
    }

    @Test
    fun `resting heart rate uses same 5-bpm bins as heart rate`() {
        assertEquals("60-65", binValue(MetricType.RESTING_HEART_RATE, 62.0))
    }

    @Test
    fun `HRV 45 bins to 40-50`() {
        assertEquals("40-50", binValue(MetricType.HEART_RATE_VARIABILITY, 45.0))
    }

    @Test
    fun `blood oxygen 97 bins to 97-98`() {
        assertEquals("97-98", binValue(MetricType.BLOOD_OXYGEN, 97.3))
    }

    @Test
    fun `respiratory rate 16 bins to 16-18`() {
        assertEquals("16-18", binValue(MetricType.RESPIRATORY_RATE, 16.5))
    }

    @Test
    fun `steps 8500 bins to 8000-9000`() {
        assertEquals("8000-9000", binValue(MetricType.STEPS, 8500.0))
    }

    @Test
    fun `bin format is always lower-upper integers`() {
        val result = binValue(MetricType.HEART_RATE, 72.3)
        val parts = result.split("-")
        assertEquals(2, parts.size)
        assertNotNull(parts[0].toIntOrNull())
        assertNotNull(parts[1].toIntOrNull())
    }

    // --- bracketSampleCount tests ---

    @Test
    fun `fewer than 50 samples is low`() {
        assertEquals("low", bracketSampleCount(10))
        assertEquals("low", bracketSampleCount(49))
    }

    @Test
    fun `50 to 199 samples is medium`() {
        assertEquals("medium", bracketSampleCount(50))
        assertEquals("medium", bracketSampleCount(199))
    }

    @Test
    fun `200 to 499 samples is high`() {
        assertEquals("high", bracketSampleCount(200))
        assertEquals("high", bracketSampleCount(499))
    }

    @Test
    fun `500 or more samples is very_high`() {
        assertEquals("very_high", bracketSampleCount(500))
        assertEquals("very_high", bracketSampleCount(10000))
    }

    // --- estimateSensitivity tests ---

    @Test
    fun `all contributable metrics have positive sensitivity`() {
        val contributable = listOf(
            MetricType.HEART_RATE,
            MetricType.HEART_RATE_VARIABILITY,
            MetricType.RESTING_HEART_RATE,
            MetricType.BLOOD_OXYGEN,
            MetricType.RESPIRATORY_RATE,
            MetricType.STEPS,
            MetricType.SLEEP_STAGE
        )
        for (metric in contributable) {
            assertTrue(
                "Sensitivity for ${metric.key} must be positive",
                estimateSensitivity(metric) > 0.0
            )
        }
    }

    @Test
    fun `unknown metric type falls back to default sensitivity`() {
        assertEquals(5.0, estimateSensitivity(MetricType.ACTIVE_CALORIES), 0.001)
    }

    // --- nextContributionDelayMillis tests ---

    @Test
    fun `contribution delay is between 18 and 30 hours`() {
        val minMillis = 18 * 3600 * 1000L
        val maxMillis = 30 * 3600 * 1000L

        repeat(100) {
            val delay = nextContributionDelayMillis()
            assertTrue("Delay $delay should be >= 18h", delay >= minMillis)
            assertTrue("Delay $delay should be < 30h", delay < maxMillis)
        }
    }

    // --- laplacianNoise tests ---

    @Test
    fun `laplacian noise has zero mean over many samples`() {
        val samples = (1..10000).map { laplacianNoise(5.0, 1.0) }
        val mean = samples.average()
        assertTrue(
            "Mean of laplacian noise should be near 0 (was $mean)",
            abs(mean) < 0.5
        )
    }

    @Test
    fun `laplacian noise scale increases with lower epsilon`() {
        val highPrivacy = (1..5000).map { laplacianNoise(5.0, 0.1) }
        val lowPrivacy = (1..5000).map { laplacianNoise(5.0, 10.0) }

        val highVar = highPrivacy.map { it * it }.average()
        val lowVar = lowPrivacy.map { it * it }.average()

        assertTrue("Lower epsilon should produce more noise", highVar > lowVar)
    }
}
