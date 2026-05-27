package com.bios.app.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Zero-phase Butterworth bandpass filter used by [PpgSignalProcessor] to
 * isolate the cardiac band of the raw fingertip-PPG luminance waveform.
 * Cascaded from a 2nd-order high-pass + 2nd-order low-pass (RBJ-cookbook
 * biquad coefficients, Q = 1/√2 = Butterworth), each applied forward then
 * time-reversed for a 4th-order zero-phase response per direction.
 *
 * Extracted from [PpgSignalProcessor] purely to keep that file under the
 * 500-line project ceiling — the DSP is single-use today.
 */
internal object Bandpass {

    /**
     * Filter [samples] between [lowHz] and [highHz]. Cutoffs are in Hz (not
     * normalised) and are clamped to the Nyquist limit ([samplingHz] / 2)
     * so small recordings or unexpected sample rates can't produce
     * degenerate coefficients.
     */
    fun apply(
        samples: DoubleArray,
        lowHz: Double,
        highHz: Double,
        samplingHz: Double,
    ): DoubleArray {
        if (samples.size < 4 || samplingHz <= 0.0) return samples
        val nyquist = samplingHz / 2.0
        val fLow = lowHz.coerceIn(1e-3, nyquist * 0.99)
        val fHigh = highHz.coerceIn(fLow * 1.01, nyquist * 0.99)
        val hp = biquadForward(samples, highpassCoeffs(fLow, samplingHz))
        val bp = biquadForward(hp, lowpassCoeffs(fHigh, samplingHz))
        // Reverse, filter again, reverse back → zero-phase. Same coefficients,
        // applied in time-reversed order, cancels the forward-pass phase shift.
        val bpRev = DoubleArray(bp.size) { bp[bp.size - 1 - it] }
        val hpRev = biquadForward(bpRev, highpassCoeffs(fLow, samplingHz))
        val rev = biquadForward(hpRev, lowpassCoeffs(fHigh, samplingHz))
        return DoubleArray(rev.size) { rev[rev.size - 1 - it] }
    }

    /** Direct-form-I biquad, single forward pass. Coefficients are pre-normalised
     *  to a0 = 1; [coeffs] = [b0, b1, b2, a1, a2]. */
    private fun biquadForward(samples: DoubleArray, coeffs: DoubleArray): DoubleArray {
        val b0 = coeffs[0]; val b1 = coeffs[1]; val b2 = coeffs[2]
        val a1 = coeffs[3]; val a2 = coeffs[4]
        val out = DoubleArray(samples.size)
        var x1 = 0.0; var x2 = 0.0
        var y1 = 0.0; var y2 = 0.0
        for (i in samples.indices) {
            val x0 = samples[i]
            val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            out[i] = y0
            x2 = x1; x1 = x0
            y2 = y1; y1 = y0
        }
        return out
    }

    /** RBJ-cookbook Butterworth low-pass biquad (Q = 1/√2). Returns
     *  [b0, b1, b2, a1, a2] already normalised by a0. */
    private fun lowpassCoeffs(cutoffHz: Double, samplingHz: Double): DoubleArray {
        val w0 = 2.0 * PI * cutoffHz / samplingHz
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * (1.0 / sqrt(2.0)))
        val a0 = 1.0 + alpha
        val b0 = (1.0 - cosW0) / 2.0
        val b1 = 1.0 - cosW0
        val b2 = (1.0 - cosW0) / 2.0
        val a1 = -2.0 * cosW0
        val a2 = 1.0 - alpha
        return doubleArrayOf(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }

    /** RBJ-cookbook Butterworth high-pass biquad (Q = 1/√2). Returns
     *  [b0, b1, b2, a1, a2] already normalised by a0. */
    private fun highpassCoeffs(cutoffHz: Double, samplingHz: Double): DoubleArray {
        val w0 = 2.0 * PI * cutoffHz / samplingHz
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * (1.0 / sqrt(2.0)))
        val a0 = 1.0 + alpha
        val b0 = (1.0 + cosW0) / 2.0
        val b1 = -(1.0 + cosW0)
        val b2 = (1.0 + cosW0) / 2.0
        val a1 = -2.0 * cosW0
        val a2 = 1.0 - alpha
        return doubleArrayOf(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }
}
