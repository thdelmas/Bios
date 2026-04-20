package com.bios.app.engine

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Summary statistics for auditing camera-PPG RMSSD (or any HRV metric)
 * against a reference device like a chest-strap ECG. Designed to support
 * the paired-reading validation described in docs/CAMERA_PPG.md.
 *
 * This lives in the main source set (not test-only) so a future dev-menu
 * tool can surface the numbers to the owner without a code change.
 */
object PpgValidation {

    /**
     * Compare [camera] and [reference] readings taken in the same session.
     * Both lists must have the same length (one camera reading per
     * reference reading); throws [IllegalArgumentException] otherwise.
     */
    fun compare(camera: List<Double>, reference: List<Double>): Report {
        require(camera.size == reference.size) {
            "Paired readings must have equal length (camera=${camera.size}, reference=${reference.size})"
        }
        require(camera.isNotEmpty()) { "Need at least one paired reading" }

        val errors = camera.zip(reference) { c, r -> c - r }
        val absErrors = errors.map { abs(it) }

        val mae = absErrors.average()
        val bias = errors.average()
        val rmse = sqrt(errors.map { it * it }.average())
        val maxAbs = absErrors.max()
        val withinThreshold = absErrors.count { it <= TYPICAL_THRESHOLD_MS }

        return Report(
            n = camera.size,
            mae = mae,
            bias = bias,
            rmse = rmse,
            maxAbsError = maxAbs,
            withinThresholdCount = withinThreshold,
            thresholdMs = TYPICAL_THRESHOLD_MS
        )
    }

    /**
     * Snapshot of validation statistics. All error values are in the units
     * of the supplied readings (ms for RMSSD, bpm for HR, etc.).
     *
     * - [mae] — mean absolute error (how far off on average, regardless of direction).
     * - [bias] — mean signed error (camera − reference); negative means the camera
     *   systematically under-reads.
     * - [rmse] — root-mean-square error (penalises large deviations more than [mae]).
     * - [maxAbsError] — worst single-reading deviation.
     * - [withinThresholdCount] — how many pairs fell within [thresholdMs].
     */
    data class Report(
        val n: Int,
        val mae: Double,
        val bias: Double,
        val rmse: Double,
        val maxAbsError: Double,
        val withinThresholdCount: Int,
        val thresholdMs: Double
    ) {
        /** Fraction of pairs within the threshold (0.0–1.0). */
        val withinThresholdFraction: Double
            get() = if (n == 0) 0.0 else withinThresholdCount.toDouble() / n

        /** Human-readable verdict for the owner-facing docs. */
        val verdict: Verdict
            get() = when {
                mae <= THRESHOLD_GOOD -> Verdict.GOOD
                mae <= THRESHOLD_ACCEPTABLE -> Verdict.ACCEPTABLE
                else -> Verdict.POOR
            }
    }

    enum class Verdict(val label: String) {
        GOOD("Good — camera agrees with the reference; trust the snapshots."),
        ACCEPTABLE("Acceptable — noticeable error; use as a backup, not a primary source."),
        POOR("Poor — this phone's camera is not suited to PPG on your fingertip.")
    }

    /** Threshold below which we call a reading "in agreement" (RMSSD ms). */
    const val TYPICAL_THRESHOLD_MS = 8.0

    private const val THRESHOLD_GOOD = 8.0
    private const val THRESHOLD_ACCEPTABLE = 15.0
}
