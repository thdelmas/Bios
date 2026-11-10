package com.bios.app.engine

import com.bios.app.model.AlertTier

/**
 * Pure severity classifier for [AnomalyDetector]. Extracted to a top-
 * level function so [AnomalyDetector] stays under the 500-line cap and
 * so unit tests can pin the cutoffs without spinning up the full
 * detector.
 *
 * Cutoffs are deliberately conservative: ADVISORY requires either a
 * very high combined score (> 3.0) or near-full signal agreement
 * (> 0.8). NOTICE catches moderately-high scores or majority-active
 * patterns; everything else stays at OBSERVATION.
 */
internal fun classifySeverity(
    activeSignals: Int,
    combinedScore: Double,
    totalRules: Int,
): AlertTier {
    val signalRatio = activeSignals.toDouble() / totalRules.toDouble()
    return when {
        combinedScore > 3.0 || signalRatio > 0.8 -> AlertTier.ADVISORY
        combinedScore > 2.0 || signalRatio > 0.5 -> AlertTier.NOTICE
        else -> AlertTier.OBSERVATION
    }
}

/**
 * Tier of a holistic (ML / heuristic) alert from its combined score and the
 * largest single deviation. WARNING (2026-11-07) sits between ADVISORY and
 * URGENT: an advisory-level score with at least one signal three standard
 * deviations or more from the owner's baseline.
 */
internal fun classifyHolistic(score: Float, maxAbsZ: Double): AlertTier = when {
    score > HOLISTIC_ADVISORY_SCORE && maxAbsZ >= HOLISTIC_WARNING_Z -> AlertTier.WARNING
    score > HOLISTIC_ADVISORY_SCORE -> AlertTier.ADVISORY
    score > HOLISTIC_NOTICE_SCORE -> AlertTier.NOTICE
    else -> AlertTier.OBSERVATION
}

private const val HOLISTIC_ADVISORY_SCORE = 0.85f
private const val HOLISTIC_NOTICE_SCORE = 0.7f
private const val HOLISTIC_WARNING_Z = 3.0

/**
 * Applies a pattern's optional severity floor: the emitted tier is the higher
 * of the classifier's output and the floor. Emergency vital-sign patterns
 * declare an URGENT [floor] so a single-rule fire still escalates; trend
 * patterns leave it null and pass [classified] through unchanged.
 *
 * Extracted alongside [classifySeverity] so [AnomalyDetector] and its tests
 * share one implementation.
 */
internal fun applySeverityFloor(classified: AlertTier, floor: AlertTier?): AlertTier =
    if (floor != null && floor.level > classified.level) floor else classified
