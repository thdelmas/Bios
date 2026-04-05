package com.bios.app.engine

import android.util.Log

/**
 * Tracks detection pipeline latency as a core UX metric.
 *
 * SLO targets:
 * - Data ingestion to anomaly detection: < 5 minutes
 * - Anomaly detection to notification delivery: < 10 seconds
 * - End-to-end (data arrival to owner notification): < 6 minutes
 *
 * All timing is local and on-device. No telemetry leaves the device.
 */
class DetectionLatencyTracker {

    private val measurements = mutableListOf<LatencyMeasurement>()
    private val maxMeasurements = 500

    /**
     * Records a latency measurement for a pipeline stage.
     */
    fun record(stage: PipelineStage, durationMs: Long, metadata: Map<String, String> = emptyMap()) {
        val measurement = LatencyMeasurement(
            stage = stage,
            durationMs = durationMs,
            timestamp = System.currentTimeMillis(),
            metadata = metadata
        )

        synchronized(measurements) {
            measurements.add(measurement)
            if (measurements.size > maxMeasurements) {
                measurements.removeAt(0)
            }
        }

        // Log SLO violations
        val sloMs = stage.sloTargetMs
        if (sloMs != null && durationMs > sloMs) {
            Log.w(TAG, "SLO violation: ${stage.label} took ${durationMs}ms (target: ${sloMs}ms)")
        }
    }

    /**
     * Wraps a suspend block and records its duration for the given stage.
     */
    suspend fun <T> track(
        stage: PipelineStage,
        metadata: Map<String, String> = emptyMap(),
        block: suspend () -> T
    ): T {
        val start = System.currentTimeMillis()
        val result = block()
        record(stage, System.currentTimeMillis() - start, metadata)
        return result
    }

    /**
     * Returns percentile latencies for a given stage over the measurement window.
     */
    fun percentiles(stage: PipelineStage): LatencyPercentiles? {
        val stageMeasurements = synchronized(measurements) {
            measurements.filter { it.stage == stage }.map { it.durationMs }.sorted()
        }
        if (stageMeasurements.isEmpty()) return null

        return LatencyPercentiles(
            stage = stage,
            count = stageMeasurements.size,
            p50 = percentile(stageMeasurements, 50),
            p90 = percentile(stageMeasurements, 90),
            p99 = percentile(stageMeasurements, 99),
            max = stageMeasurements.last(),
            sloTargetMs = stage.sloTargetMs,
            sloViolations = stage.sloTargetMs?.let { slo ->
                stageMeasurements.count { it > slo }
            } ?: 0
        )
    }

    /**
     * Returns a summary of all pipeline stages.
     */
    fun summary(): List<LatencyPercentiles> {
        return PipelineStage.entries.mapNotNull { percentiles(it) }
    }

    /**
     * Clears all measurements. Called on data wipe.
     */
    fun clear() {
        synchronized(measurements) { measurements.clear() }
    }

    private fun percentile(sorted: List<Long>, p: Int): Long {
        if (sorted.isEmpty()) return 0
        val index = (p / 100.0 * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
        return sorted[index]
    }

    companion object {
        private const val TAG = "DetectionLatency"
    }
}

/**
 * Pipeline stages with SLO targets.
 */
enum class PipelineStage(val label: String, val sloTargetMs: Long?) {
    /** Time to pull data from adapters into the local DB. */
    DATA_INGEST("Data Ingestion", 60_000),

    /** Time to recompute personal baselines from recent readings. */
    BASELINE_COMPUTATION("Baseline Computation", 30_000),

    /** Time to run all condition pattern evaluations. */
    PATTERN_DETECTION("Pattern Detection", 10_000),

    /** Time to run ML model inference. */
    ML_INFERENCE("ML Inference", 5_000),

    /** Time from anomaly creation to OS notification delivery. */
    NOTIFICATION_DELIVERY("Notification Delivery", 10_000),

    /** End-to-end: data arrival → owner sees notification. */
    END_TO_END("End-to-End", 360_000),

    /** Time to compute and send LETHE agent status update. */
    AGENT_STATUS_UPDATE("Agent Status Update", 5_000)
}

data class LatencyMeasurement(
    val stage: PipelineStage,
    val durationMs: Long,
    val timestamp: Long,
    val metadata: Map<String, String>
)

data class LatencyPercentiles(
    val stage: PipelineStage,
    val count: Int,
    val p50: Long,
    val p90: Long,
    val p99: Long,
    val max: Long,
    val sloTargetMs: Long?,
    val sloViolations: Int
)
