package com.bios.app.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Minimal spectral-analysis surface used by [HrvAnalyzer] to compute the
 * `lf_hf_ratio` autonomic derivation. Pure functions, no Android deps —
 * lives next to the time-domain HRV math and is exercised by the same
 * unit-test suite.
 *
 * The implementation is the textbook IBI-tachogram pipeline (ESC/NASPE
 * Task Force 1996, Shaffer & Ginsberg 2017):
 *
 *   1. Build the beat-time axis t_n = cumulative sum of IBI_1..IBI_n.
 *   2. Resample the IBI series at a uniform 4 Hz via linear interpolation
 *      — the IBI series is naturally non-uniform (each sample lives at
 *      its own beat instant), and direct FFT on non-uniform data is
 *      meaningless.
 *   3. Subtract the mean (detrend) so the DC bin doesn't dominate.
 *   4. Apply a Hann window to suppress spectral leakage.
 *   5. Zero-pad to the next power of two and run a radix-2 Cooley-Tukey
 *      FFT.
 *   6. Compute the one-sided power spectral density.
 *   7. Sum power in LF (0.04–0.15 Hz) and HF (0.15–0.4 Hz) and return
 *      the ratio. A degenerate denominator (constant IBI, near-zero
 *      HF) returns 0.0 — the caller treats that as "no autonomic
 *      signal to interpret" rather than infinity.
 *
 * The minimum useful recording length is roughly 60 seconds (one full
 * LF-band cycle plus headroom). Shorter inputs return 0.0 — LF/HF over
 * a few seconds is not clinically defensible.
 */
internal object Spectral {

    /** Lower edge of the LF band (Hz), per Task Force 1996. */
    const val LF_LOW_HZ = 0.04

    /** Boundary between LF and HF bands (Hz). */
    const val LF_HIGH_HZ = 0.15

    /** Upper edge of the HF band (Hz). */
    const val HF_HIGH_HZ = 0.40

    /** Resampling rate of the IBI tachogram (Hz). 4 Hz is the standard. */
    const val RESAMPLE_HZ = 4.0

    /** Minimum recording duration for a defensible LF/HF estimate (sec). */
    const val MIN_DURATION_SEC = 60.0

    /**
     * Compute the LF/HF ratio from a list of inter-beat intervals (ms).
     * Returns 0.0 when the recording is too short for spectral analysis,
     * when the series is degenerate (constant or near-constant IBIs), or
     * when the HF band contains no power.
     */
    fun lfHfRatio(ibisMs: List<Double>): Double {
        if (ibisMs.size < 4) return 0.0

        // Build beat-time axis in seconds. The first sample lives at the
        // end of the first interval; the canonical convention places it
        // at t = IBI_1 (Task Force 1996 Fig. 3).
        val timesSec = DoubleArray(ibisMs.size)
        var cumulative = 0.0
        for (i in ibisMs.indices) {
            cumulative += ibisMs[i] / 1000.0
            timesSec[i] = cumulative
        }
        val durationSec = timesSec.last() - timesSec.first()
        if (durationSec < MIN_DURATION_SEC) return 0.0

        // Resample uniformly at RESAMPLE_HZ via linear interpolation.
        val sampleCount = (durationSec * RESAMPLE_HZ).toInt()
        if (sampleCount < 8) return 0.0
        val nfft = nextPowerOfTwo(sampleCount)
        val signal = DoubleArray(nfft)
        val tStart = timesSec.first()
        for (i in 0 until sampleCount) {
            val t = tStart + i / RESAMPLE_HZ
            signal[i] = interpolateLinear(timesSec, ibisMs, t)
        }
        // Tail samples (sampleCount..nfft-1) stay at zero — zero-padding.

        // Detrend by subtracting the mean of the populated region.
        var sum = 0.0
        for (i in 0 until sampleCount) sum += signal[i]
        val mean = sum / sampleCount
        for (i in 0 until sampleCount) signal[i] -= mean

        // Hann window over the populated region.
        var windowEnergy = 0.0
        for (i in 0 until sampleCount) {
            val w = 0.5 - 0.5 * cos(2.0 * PI * i / (sampleCount - 1).coerceAtLeast(1))
            signal[i] *= w
            windowEnergy += w * w
        }
        if (windowEnergy <= 0.0) return 0.0

        // Radix-2 FFT in place.
        val imag = DoubleArray(nfft)
        fftInPlace(signal, imag)

        // One-sided PSD (Welch-style normalization for a single segment).
        val psdScale = 1.0 / (RESAMPLE_HZ * windowEnergy)
        val df = RESAMPLE_HZ / nfft
        var lfPower = 0.0
        var hfPower = 0.0
        val halfN = nfft / 2
        for (k in 0..halfN) {
            val mag2 = signal[k] * signal[k] + imag[k] * imag[k]
            // Double everything except DC and Nyquist for the one-sided PSD.
            val onesidedScale = if (k == 0 || k == halfN) 1.0 else 2.0
            val psd = mag2 * psdScale * onesidedScale
            val f = k * df
            when {
                f >= LF_LOW_HZ && f < LF_HIGH_HZ -> lfPower += psd * df
                f >= LF_HIGH_HZ && f <= HF_HIGH_HZ -> hfPower += psd * df
            }
        }
        if (hfPower <= 0.0) return 0.0
        return lfPower / hfPower
    }

    private fun nextPowerOfTwo(n: Int): Int {
        var p = 1
        while (p < n) p = p shl 1
        return p
    }

    /**
     * Linear interpolation of values defined at [timesSec] onto a single
     * query time [t]. The series is monotonic (cumulative IBI sums), so
     * a binary search over the indexing is enough.
     */
    private fun interpolateLinear(
        timesSec: DoubleArray,
        values: List<Double>,
        t: Double
    ): Double {
        if (t <= timesSec.first()) return values.first()
        if (t >= timesSec.last()) return values.last()
        var lo = 0
        var hi = timesSec.size - 1
        while (lo < hi - 1) {
            val mid = (lo + hi) ushr 1
            if (timesSec[mid] <= t) lo = mid else hi = mid
        }
        val span = timesSec[hi] - timesSec[lo]
        if (span <= 0.0) return values[lo]
        val frac = (t - timesSec[lo]) / span
        return values[lo] + frac * (values[hi] - values[lo])
    }

    /**
     * In-place radix-2 Cooley-Tukey FFT. [real] and [imag] must have the
     * same length and that length must be a power of two.
     */
    internal fun fftInPlace(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        require(n == imag.size && n > 0 && (n and (n - 1)) == 0) {
            "FFT length must be a positive power of two; got $n"
        }
        // Bit-reversal permutation.
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
            var k = n shr 1
            while (k in 1..j) { j -= k; k = k shr 1 }
            j += k
        }
        // Butterflies.
        var size = 2
        while (size <= n) {
            val half = size shr 1
            val angleStep = -2.0 * PI / size
            val wStepR = cos(angleStep)
            val wStepI = sin(angleStep)
            var start = 0
            while (start < n) {
                var wR = 1.0
                var wI = 0.0
                for (k in 0 until half) {
                    val a = start + k
                    val b = a + half
                    val tR = wR * real[b] - wI * imag[b]
                    val tI = wR * imag[b] + wI * real[b]
                    real[b] = real[a] - tR
                    imag[b] = imag[a] - tI
                    real[a] = real[a] + tR
                    imag[a] = imag[a] + tI
                    val newR = wR * wStepR - wI * wStepI
                    wI = wR * wStepI + wI * wStepR
                    wR = newR
                }
                start += size
            }
            size = size shl 1
        }
    }
}
