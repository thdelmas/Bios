package com.bios.app.engine

import com.bios.app.model.AlertTier

/**
 * Tier of an alert from its [DeviationScore] and the largest single deviation.
 * WARNING is an advisory-level score with at least one metric three standard
 * deviations or more from the owner's baseline. Nothing emits URGENT.
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
