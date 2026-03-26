package com.bios.app

import com.bios.app.engine.TFLiteAnomalyModel
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for TFLiteAnomalyModel helper functions (feature building, heuristic scoring).
 * The actual TFLite interpreter requires Android; these test the pure logic.
 */
class TFLiteAnomalyModelTest {

    // -- Feature vector building --

    @Test
    fun `buildFeatureVector has correct size`() {
        val features = TFLiteAnomalyModel.buildFeatureVector(emptyMap())
        assertEquals(TFLiteAnomalyModel.FEATURE_COUNT, features.size)
    }

    @Test
    fun `buildFeatureVector defaults missing metrics to zero`() {
        val features = TFLiteAnomalyModel.buildFeatureVector(emptyMap())
        features.forEach { assertEquals(0f, it) }
    }

    @Test
    fun `buildFeatureVector maps metrics to correct positions`() {
        val zScores = mapOf(
            "heart_rate" to 1.5,
            "heart_rate_variability" to -2.0,
            "resting_heart_rate" to 0.5,
            "blood_oxygen" to -1.0,
            "respiratory_rate" to 2.5,
            "skin_temperature_deviation" to 0.8,
            "sleep_duration" to -0.3,
            "steps" to 1.2,
            "active_calories" to -0.7
        )

        val features = TFLiteAnomalyModel.buildFeatureVector(zScores)

        assertEquals(1.5f, features[0], 0.01f)   // heart_rate
        assertEquals(-2.0f, features[1], 0.01f)  // hrv
        assertEquals(0.5f, features[2], 0.01f)   // resting_hr
        assertEquals(-1.0f, features[3], 0.01f)  // blood_oxygen
        assertEquals(2.5f, features[4], 0.01f)   // respiratory_rate
        assertEquals(0.8f, features[5], 0.01f)   // skin_temp_dev
        assertEquals(-0.3f, features[6], 0.01f)  // sleep_duration
        assertEquals(1.2f, features[7], 0.01f)   // steps
        assertEquals(-0.7f, features[8], 0.01f)  // active_calories
    }

    @Test
    fun `buildFeatureVector ignores unknown metric keys`() {
        val zScores = mapOf("unknown_metric" to 5.0, "heart_rate" to 1.0)
        val features = TFLiteAnomalyModel.buildFeatureVector(zScores)

        assertEquals(1.0f, features[0], 0.01f)
        // unknown_metric not mapped, rest should be 0
        assertEquals(0f, features[1])
    }

    // -- Heuristic fallback scoring --

    @Test
    fun `heuristicScore returns low for normal features`() {
        val features = FloatArray(TFLiteAnomalyModel.FEATURE_COUNT) { 0f }
        val score = TFLiteAnomalyModel.heuristicScore(features)
        assertTrue(score < TFLiteAnomalyModel.ANOMALY_THRESHOLD)
    }

    @Test
    fun `heuristicScore returns high for many large deviations`() {
        val features = FloatArray(TFLiteAnomalyModel.FEATURE_COUNT) { 3.0f }
        val score = TFLiteAnomalyModel.heuristicScore(features)
        assertTrue(score > TFLiteAnomalyModel.ANOMALY_THRESHOLD)
    }

    @Test
    fun `heuristicScore returns moderate for mixed deviations`() {
        val features = FloatArray(TFLiteAnomalyModel.FEATURE_COUNT) { 0f }
        features[0] = 2.0f  // heart_rate elevated
        features[1] = -2.5f // hrv dropped
        features[4] = 1.8f  // respiratory_rate up
        val score = TFLiteAnomalyModel.heuristicScore(features)
        assertTrue(score > 0.3f)
    }

    @Test
    fun `heuristicScore in 0 to 1 range`() {
        val variations = listOf(
            FloatArray(TFLiteAnomalyModel.FEATURE_COUNT) { 0f },
            FloatArray(TFLiteAnomalyModel.FEATURE_COUNT) { 5f },
            FloatArray(TFLiteAnomalyModel.FEATURE_COUNT) { -3f },
            floatArrayOf(0f, 0f, 0f, 0f, 4f, 0f, 0f, 0f, 0f)
        )

        for (features in variations) {
            val score = TFLiteAnomalyModel.heuristicScore(features)
            assertTrue("Score $score out of range", score in 0f..1f)
        }
    }

    @Test
    fun `heuristicScore is higher with more deviating features`() {
        val twoDeviating = FloatArray(TFLiteAnomalyModel.FEATURE_COUNT) { 0f }
        twoDeviating[0] = 2.5f
        twoDeviating[1] = 2.5f

        val fourDeviating = FloatArray(TFLiteAnomalyModel.FEATURE_COUNT) { 0f }
        fourDeviating[0] = 2.5f
        fourDeviating[1] = 2.5f
        fourDeviating[2] = 2.5f
        fourDeviating[3] = 2.5f

        val scoreLow = TFLiteAnomalyModel.heuristicScore(twoDeviating)
        val scoreHigh = TFLiteAnomalyModel.heuristicScore(fourDeviating)

        assertTrue(scoreHigh > scoreLow)
    }

    // -- Constants --

    @Test
    fun `FEATURE_COUNT is 9`() {
        assertEquals(9, TFLiteAnomalyModel.FEATURE_COUNT)
    }

    @Test
    fun `ANOMALY_THRESHOLD is reasonable`() {
        assertTrue(TFLiteAnomalyModel.ANOMALY_THRESHOLD > 0.5f)
        assertTrue(TFLiteAnomalyModel.ANOMALY_THRESHOLD < 0.9f)
    }
}
