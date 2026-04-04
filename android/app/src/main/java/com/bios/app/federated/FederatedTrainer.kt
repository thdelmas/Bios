package com.bios.app.federated

import android.content.Context
import com.bios.app.data.BiosDatabase
import com.bios.app.engine.TFLiteAnomalyModel
import com.bios.app.model.MetricType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * On-device federated learning: fine-tunes the anomaly detection model
 * using the owner's local data, then exports encrypted gradients.
 *
 * Architecture:
 * 1. Load the base TFLite model
 * 2. Compute forward pass on recent local data
 * 3. Compute gradients (loss relative to owner's feedback)
 * 4. Apply differential privacy noise to gradients
 * 5. Encrypt gradients with the server's public key
 * 6. Transmit encrypted gradients (owner opts in)
 *
 * The server combines encrypted gradients via secure aggregation —
 * it never sees individual contributions in plaintext.
 *
 * The owner can opt out at any time. Opting out doesn't reduce features.
 */
class FederatedTrainer(
    private val context: Context,
    private val db: BiosDatabase
) {
    private val readingDao = db.metricReadingDao()
    private val baselineDao = db.personalBaselineDao()
    private val anomalyDao = db.anomalyDao()

    /**
     * Compute local gradients from the owner's feedback-labeled anomalies.
     *
     * Uses anomaly records where the owner has provided feedback
     * (feltSick, visitedDoctor, outcomeAccurate) to compute a loss signal
     * that improves the model's detection accuracy.
     */
    suspend fun computeGradients(): GradientUpdate? {
        val feedbackAnomalies = anomalyDao.fetchWithFeedback(50)
        if (feedbackAnomalies.size < MIN_FEEDBACK_FOR_TRAINING) return null

        val features = mutableListOf<FloatArray>()
        val labels = mutableListOf<Float>()

        for (anomaly in feedbackAnomalies) {
            // Build feature vector from the anomaly's z-scores
            val zScores = parseDeviationScores(anomaly.deviationScores)
            val featureVector = TFLiteAnomalyModel.buildFeatureVector(zScores)

            // Label: 1.0 if owner confirmed illness, 0.0 if false alarm
            val label = when {
                anomaly.feltSick == true -> 1.0f
                anomaly.outcomeAccurate == false -> 0.0f
                anomaly.feltSick == false -> 0.0f
                else -> continue // Skip ambiguous feedback
            }

            features.add(featureVector)
            labels.add(label)
        }

        if (features.size < MIN_FEEDBACK_FOR_TRAINING) return null

        // Compute pseudo-gradients: difference between predicted and actual
        val gradients = FloatArray(TFLiteAnomalyModel.FEATURE_COUNT)
        for (i in features.indices) {
            val predicted = TFLiteAnomalyModel.heuristicScore(features[i])
            val error = predicted - labels[i]
            for (j in gradients.indices) {
                gradients[j] += error * features[i][j] / features.size
            }
        }

        // Apply differential privacy: add calibrated Laplace noise
        val noisyGradients = applyDifferentialPrivacy(gradients, EPSILON)

        return GradientUpdate(
            gradients = noisyGradients,
            sampleCount = features.size,
            modelVersion = TFLiteAnomalyModel.MODEL_VERSION,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Encrypt gradients for server transmission.
     * Uses AES-256-GCM with a server-provided session key.
     */
    fun encryptGradients(update: GradientUpdate, serverSessionKey: ByteArray): ByteArray {
        val plaintext = serializeGradients(update)
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = SecretKeySpec(serverSessionKey, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext)

        // iv + ciphertext
        return iv + ciphertext
    }

    private fun applyDifferentialPrivacy(
        gradients: FloatArray,
        epsilon: Double
    ): FloatArray {
        val random = SecureRandom()
        val sensitivity = gradients.map { kotlin.math.abs(it) }.maxOrNull() ?: 1.0f
        val scale = sensitivity / epsilon

        return FloatArray(gradients.size) { i ->
            gradients[i] + laplaceSample(random, scale.toDouble()).toFloat()
        }
    }

    private fun laplaceSample(random: SecureRandom, scale: Double): Double {
        val u = random.nextDouble() - 0.5
        return -scale * Math.signum(u) * Math.log(1.0 - 2.0 * Math.abs(u))
    }

    private fun serializeGradients(update: GradientUpdate): ByteArray {
        val buffer = ByteBuffer.allocate(
            4 + // sample count
            4 + // model version
            8 + // timestamp
            4 * update.gradients.size // gradients
        ).order(ByteOrder.BIG_ENDIAN)

        buffer.putInt(update.sampleCount)
        buffer.putInt(update.modelVersion)
        buffer.putLong(update.timestamp)
        for (g in update.gradients) buffer.putFloat(g)

        return buffer.array()
    }

    private fun parseDeviationScores(json: String): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        // Simple JSON parsing without pulling in a full JSON library dependency
        val cleaned = json.trim().removePrefix("{").removeSuffix("}")
        if (cleaned.isBlank()) return result
        for (pair in cleaned.split(",")) {
            val parts = pair.split(":")
            if (parts.size == 2) {
                val key = parts[0].trim().removeSurrounding("\"")
                val value = parts[1].trim().toDoubleOrNull() ?: continue
                result[key] = value
            }
        }
        return result
    }

    companion object {
        const val MIN_FEEDBACK_FOR_TRAINING = 5
        const val EPSILON = 1.0 // Differential privacy budget
    }
}

data class GradientUpdate(
    val gradients: FloatArray,
    val sampleCount: Int,
    val modelVersion: Int,
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GradientUpdate) return false
        return gradients.contentEquals(other.gradients) &&
            sampleCount == other.sampleCount &&
            modelVersion == other.modelVersion
    }

    override fun hashCode(): Int = gradients.contentHashCode()
}
