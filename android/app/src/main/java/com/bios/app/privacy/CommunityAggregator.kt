package com.bios.app.privacy

import com.bios.app.data.BiosDatabase
import com.bios.app.engine.Stats
import com.bios.app.model.MetricType
import com.bios.app.model.PrivacyTier
import kotlin.math.ln
import kotlin.random.Random

/**
 * On-device aggregation engine for Community tier users.
 *
 * Computes statistical summaries from local data, strips all identifiers,
 * applies differential privacy noise, and produces anonymous statistical vectors
 * that can be transmitted to the server without revealing individual data.
 *
 * Key properties:
 * - Anonymization happens ON-DEVICE, before transmission
 * - The server never sees raw readings, timestamps, or identifiers
 * - Output cannot be linked back to this device
 * - Contribution frequency is randomized to prevent timing attacks
 */
class CommunityAggregator(private val db: BiosDatabase) {

    private val readingDao = db.metricReadingDao()
    private val anomalyDao = db.anomalyDao()

    companion object {
        /**
         * Differential privacy epsilon. Lower = more privacy, less utility.
         * 1.0 is a reasonable balance for health aggregates.
         */
        const val EPSILON = 1.0

        /** Metrics we aggregate for community contributions. */
        val CONTRIBUTABLE_METRICS = listOf(
            MetricType.HEART_RATE,
            MetricType.HEART_RATE_VARIABILITY,
            MetricType.RESTING_HEART_RATE,
            MetricType.BLOOD_OXYGEN,
            MetricType.RESPIRATORY_RATE,
            MetricType.STEPS,
            MetricType.SLEEP_STAGE
        )
    }

    /**
     * Generate an anonymous statistical vector from local data.
     * Returns null if insufficient data for a meaningful contribution.
     */
    suspend fun generateContribution(
        userAgeBracket: String? = null,
        deviceClass: String = "wrist_wearable"
    ): AnonymousContribution? {
        // Need at least 7 days of data to produce a useful aggregate
        val oldest = readingDao.oldestTimestamp() ?: return null
        val ageDays = (System.currentTimeMillis() - oldest) / (24 * 3600 * 1000)
        if (ageDays < 7) return null

        val metricSummaries = mutableListOf<MetricSummary>()

        // Use the last 7 days of data
        val endMillis = System.currentTimeMillis()
        val startMillis = endMillis - 7L * 24 * 3600 * 1000

        for (metricType in CONTRIBUTABLE_METRICS) {
            val values = readingDao.fetchValues(metricType.key, startMillis, endMillis)
            if (values.size < 10) continue

            val stats = Stats.compute(values)

            // Apply differential privacy noise (Laplace mechanism)
            val sensitivity = estimateSensitivity(metricType)
            val noisyMean = stats.mean + laplacianNoise(sensitivity, EPSILON)
            val noisyStdDev = maxOf(0.0, stats.stdDev + laplacianNoise(sensitivity / 2, EPSILON))

            // Bin the mean into a range (further anonymization)
            val binnedRange = binValue(metricType, noisyMean)

            metricSummaries.add(
                MetricSummary(
                    metricType = metricType.key,
                    meanRange = binnedRange,
                    noisyStdDev = noisyStdDev,
                    sampleBracket = bracketSampleCount(values.size)
                )
            )
        }

        if (metricSummaries.isEmpty()) return null

        // Collect alert feedback (anonymized)
        val alertFeedback = generateAlertFeedback(startMillis, endMillis)

        return AnonymousContribution(
            metricSummaries = metricSummaries,
            alertFeedback = alertFeedback,
            ageBracket = userAgeBracket,
            deviceClass = deviceClass
            // No account ID, no device ID, no timestamps
        )
    }

    /**
     * Determine a randomized delay before next contribution.
     * Prevents timing-based deanonymization.
     */
    fun nextContributionDelayMillis(): Long {
        // Random delay between 18-30 hours
        val minHours = 18
        val maxHours = 30
        return (minHours + Random.nextInt(maxHours - minHours)) * 3600L * 1000
    }

    // MARK: - Differential privacy

    /**
     * Generate Laplacian noise for differential privacy.
     * Laplace(0, sensitivity/epsilon)
     */
    private fun laplacianNoise(sensitivity: Double, epsilon: Double): Double {
        val scale = sensitivity / epsilon
        val u = Random.nextDouble() - 0.5
        return -scale * u.sign() * ln(1 - 2 * kotlin.math.abs(u))
    }

    private fun Double.sign(): Double = if (this >= 0) 1.0 else -1.0

    /**
     * Estimate the sensitivity (max single-record influence) for each metric.
     * Conservative estimates based on physiological ranges.
     */
    private fun estimateSensitivity(metricType: MetricType): Double {
        return when (metricType) {
            MetricType.HEART_RATE -> 5.0           // bpm
            MetricType.HEART_RATE_VARIABILITY -> 10.0  // ms
            MetricType.RESTING_HEART_RATE -> 3.0   // bpm
            MetricType.BLOOD_OXYGEN -> 2.0         // %
            MetricType.RESPIRATORY_RATE -> 2.0     // breaths/min
            MetricType.STEPS -> 500.0              // steps
            MetricType.SLEEP_STAGE -> 0.5          // stage value
            else -> 5.0
        }
    }

    /**
     * Bin a metric value into an anonymous range.
     * The server only sees "HR mean in 65-70 bpm", not the exact value.
     */
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

    /**
     * Bracket sample counts to prevent fingerprinting by data volume.
     */
    private fun bracketSampleCount(count: Int): String {
        return when {
            count < 50 -> "low"
            count < 200 -> "medium"
            count < 500 -> "high"
            else -> "very_high"
        }
    }

    /**
     * Generate anonymized alert feedback.
     */
    private suspend fun generateAlertFeedback(
        startMillis: Long,
        endMillis: Long
    ): List<AlertFeedbackEntry> {
        val anomalies = anomalyDao.fetchRecent(50)
        return anomalies
            .filter { it.detectedAt in startMillis..endMillis }
            .mapNotNull { anomaly ->
                val patternId = anomaly.patternId ?: return@mapNotNull null
                AlertFeedbackEntry(
                    patternId = patternId,
                    outcome = if (anomaly.acknowledged) "dismissed" else "pending"
                    // No anomaly ID, no timestamp, no severity detail
                )
            }
    }
}

// MARK: - Data classes for anonymous contributions

/**
 * A fully anonymous statistical vector. Contains no identifiers,
 * no timestamps, no raw values, and cannot be linked to a specific user.
 */
data class AnonymousContribution(
    val metricSummaries: List<MetricSummary>,
    val alertFeedback: List<AlertFeedbackEntry>,
    val ageBracket: String?,        // e.g., "30-39" (user-provided, optional)
    val deviceClass: String         // e.g., "wrist_wearable" (not brand/model)
)

data class MetricSummary(
    val metricType: String,         // e.g., "heart_rate"
    val meanRange: String,          // e.g., "65-70" (binned, noisy)
    val noisyStdDev: Double,        // differentially private
    val sampleBracket: String       // "low", "medium", "high", "very_high"
)

data class AlertFeedbackEntry(
    val patternId: String,          // e.g., "infection_onset"
    val outcome: String             // "dismissed" or "pending"
)
