package com.bios.app.engine

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Wraps a TensorFlow Lite model for on-device anomaly scoring.
 *
 * The model takes a fixed-size feature vector of normalized health metrics
 * and outputs an anomaly probability score (0.0 = normal, 1.0 = anomalous).
 *
 * Expected model input: [1, FEATURE_COUNT] float array
 * Expected model output: [1, 1] float (anomaly probability)
 *
 * Feature vector layout (normalized to z-scores from personal baseline):
 *   [0] heart_rate
 *   [1] heart_rate_variability
 *   [2] resting_heart_rate
 *   [3] blood_oxygen
 *   [4] respiratory_rate
 *   [5] skin_temperature_deviation
 *   [6] sleep_duration
 *   [7] steps
 *   [8] active_calories
 *
 * Ship a placeholder model initially; retrain with federated learning later.
 */
class TFLiteAnomalyModel private constructor(
    private val interpreter: Interpreter
) : AutoCloseable {

    /**
     * Scores the feature vector. Returns an anomaly probability [0.0, 1.0].
     * Features should be z-scores from personal baselines.
     */
    fun score(features: FloatArray): Float {
        require(features.size == FEATURE_COUNT) {
            "Expected $FEATURE_COUNT features, got ${features.size}"
        }

        val inputBuffer = ByteBuffer.allocateDirect(FEATURE_COUNT * 4)
            .order(ByteOrder.nativeOrder())
        for (f in features) {
            inputBuffer.putFloat(f)
        }
        inputBuffer.rewind()

        val outputBuffer = ByteBuffer.allocateDirect(4)
            .order(ByteOrder.nativeOrder())

        interpreter.run(inputBuffer, outputBuffer)

        outputBuffer.rewind()
        return outputBuffer.float.coerceIn(0f, 1f)
    }

    /**
     * Runs scoring without a TFLite model loaded, using a simple heuristic
     * as a fallback. Returns an anomaly probability based on the number
     * and magnitude of z-score deviations.
     */
    override fun close() {
        interpreter.close()
    }

    companion object {
        const val FEATURE_COUNT = 9
        const val MODEL_VERSION = 1
        private const val MODEL_FILENAME = "anomaly_detector.tflite"
        const val ANOMALY_THRESHOLD = 0.65f

        /**
         * Loads the TFLite model from the app's assets.
         * Returns null if the model file is not present.
         */
        fun load(context: Context): TFLiteAnomalyModel? {
            return try {
                val modelBuffer = loadModelFile(context, MODEL_FILENAME)
                val options = Interpreter.Options().apply {
                    setNumThreads(2)
                }
                TFLiteAnomalyModel(Interpreter(modelBuffer, options))
            } catch (_: Throwable) {
                null
            }
        }

        /**
         * Heuristic fallback scorer when no TFLite model is available.
         * Uses a simple rule: if the mean absolute z-score exceeds 2.0
         * and 3+ features deviate, flag as anomalous.
         */
        fun heuristicScore(features: FloatArray): Float {
            val absScores = features.map { kotlin.math.abs(it) }
            val deviating = absScores.count { it > 1.5f }
            val deviatingMean = if (deviating > 0) {
                absScores.filter { it > 1.5f }.average().toFloat()
            } else 0f

            return when {
                deviating >= 4 && deviatingMean > 2.0f -> 0.9f
                deviating >= 4 -> 0.8f
                deviating >= 3 && deviatingMean > 2.0f -> 0.75f
                deviating >= 3 -> 0.65f
                deviating >= 2 && deviatingMean > 2.0f -> 0.6f
                deviating >= 2 -> 0.4f
                deviating >= 1 && deviatingMean > 2.5f -> 0.5f
                else -> (0.1f * absScores.average().toFloat()).coerceAtMost(0.3f)
            }
        }

        /**
         * Builds a feature vector from z-scores for each metric type.
         * Missing metrics default to 0.0 (no deviation).
         */
        fun buildFeatureVector(zScores: Map<String, Double>): FloatArray {
            val features = FloatArray(FEATURE_COUNT)
            features[0] = zScores["heart_rate"]?.toFloat() ?: 0f
            features[1] = zScores["heart_rate_variability"]?.toFloat() ?: 0f
            features[2] = zScores["resting_heart_rate"]?.toFloat() ?: 0f
            features[3] = zScores["blood_oxygen"]?.toFloat() ?: 0f
            features[4] = zScores["respiratory_rate"]?.toFloat() ?: 0f
            features[5] = zScores["skin_temperature_deviation"]?.toFloat() ?: 0f
            features[6] = zScores["sleep_duration"]?.toFloat() ?: 0f
            features[7] = zScores["steps"]?.toFloat() ?: 0f
            features[8] = zScores["active_calories"]?.toFloat() ?: 0f
            return features
        }

        private fun loadModelFile(context: Context, filename: String): MappedByteBuffer {
            val fd = context.assets.openFd(filename)
            val inputStream = FileInputStream(fd.fileDescriptor)
            val channel = inputStream.channel
            return channel.map(
                FileChannel.MapMode.READ_ONLY,
                fd.startOffset,
                fd.declaredLength
            )
        }
    }
}
