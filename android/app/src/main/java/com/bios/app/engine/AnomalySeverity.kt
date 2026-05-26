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
