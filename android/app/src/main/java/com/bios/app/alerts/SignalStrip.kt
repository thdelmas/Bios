package com.bios.app.alerts

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Where a signal's band and dot sit on a 0..1 track. Fractions, not pixels,
 * so the geometry is testable and the composable only scales.
 */
data class SignalStripGeometry(
    val bandStart: Float,
    val bandEnd: Float,
    val dot: Float,
    val domainLow: Double,
    val domainHigh: Double,
    /** Where "far" (about three standard deviations) begins, left and right of the band. */
    val farLow: Float = 0f,
    val farHigh: Float = 1f,
)

/**
 * Geometry for the alert screen's range strips (docs/specs/alert-detail.md,
 * criterion 6): the owner's usual range (baseline p5–p95) as a band and the
 * alert's 24 h average as a dot on the same track. Each strip has its own
 * scale: the domain is the band plus half a band of air on each side,
 * widened to include the value when it lies outside.
 */
object SignalStrip {

    /** Air on each side of the band, as a fraction of the band's width. */
    const val PAD_FRACTION = 0.5

    /** Band width used when p5 == p95, as a fraction of the value. */
    private const val DEGENERATE_FRACTION = 0.1

    /**
     * p5–p95 spans about ±1.645 σ, so the 3 σ line sits (3 / 1.645 − 1) / 2 ≈ 0.41
     * band widths beyond each band edge. The track is tinted by the same
     * bands the dot is colored by: inside, outside, far.
     */
    const val FAR_BAND_FRACTION = 0.412

    fun geometry(signal: MeasuredSignal): SignalStripGeometry {
        val low = min(signal.usualLow, signal.usualHigh)
        val high = max(signal.usualLow, signal.usualHigh)
        val width = (high - low).takeIf { it > 0.0 }
            ?: max(abs(signal.value) * DEGENERATE_FRACTION, 1.0)
        val pad = width * PAD_FRACTION
        val domainLow = min(low, signal.value) - pad
        val domainHigh = max(high, signal.value) + pad
        val span = domainHigh - domainLow
        fun at(x: Double): Float = ((x - domainLow) / span).toFloat().coerceIn(0f, 1f)
        return SignalStripGeometry(
            bandStart = at(low),
            bandEnd = at(high),
            dot = at(signal.value),
            domainLow = domainLow,
            domainHigh = domainHigh,
            farLow = at(low - width * FAR_BAND_FRACTION),
            farHigh = at(high + width * FAR_BAND_FRACTION),
        )
    }
}
