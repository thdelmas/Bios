package com.bios.app.engine

import com.bios.app.alerts.AlertDetailContent
import kotlin.math.abs

/**
 * The one score behind an alert: how many watched metrics sit beyond
 * [AlertDetailContent.DEVIATION_Z] standard deviations of the owner's own
 * baseline over the last 24 h, and how far on average. A fixed table, no
 * trained model, so every alert can be re-derived by hand from its snapshot.
 */
object DeviationScore {

    /** Score at or above which an alert is raised (three metrics beyond the cut). */
    const val THRESHOLD = 0.65f

    private const val LARGE_MEAN = 2.0f
    private const val SINGLE_LARGE_MEAN = 2.5f
    private const val QUIET_SLOPE = 0.1f
    private const val QUIET_CAP = 0.3f

    /** Score from z-scores keyed by metric; a watched metric with no entry counts as 0. */
    fun of(zScores: Map<String, Double>): Float =
        of(AlertDetailContent.WATCHED_METRICS.map { zScores[it] ?: 0.0 })

    @Suppress("MagicNumber") // the table is the definition; naming each cell would hide it
    fun of(zScores: List<Double>): Float {
        val absScores = zScores.map { abs(it).toFloat() }
        val beyond = absScores.filter { it > AlertDetailContent.DEVIATION_Z.toFloat() }
        val count = beyond.size
        val mean = if (count > 0) beyond.average().toFloat() else 0f
        return when {
            count >= 4 && mean > LARGE_MEAN -> 0.9f
            count >= 4 -> 0.8f
            count >= 3 && mean > LARGE_MEAN -> 0.75f
            count >= 3 -> THRESHOLD
            count >= 2 && mean > LARGE_MEAN -> 0.6f
            count >= 2 -> 0.4f
            count >= 1 && mean > SINGLE_LARGE_MEAN -> 0.5f
            absScores.isEmpty() -> 0f
            else -> (QUIET_SLOPE * absScores.average().toFloat()).coerceAtMost(QUIET_CAP)
        }
    }
}
