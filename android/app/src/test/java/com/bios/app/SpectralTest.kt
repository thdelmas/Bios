package com.bios.app

import com.bios.app.engine.Spectral
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Synthetic-signal coverage for the FFT-based LF/HF spectral derivation.
 *
 * Two design constraints anchor these tests:
 *  - Each "test" IBI series must respect the Malik 20% successive-change
 *    threshold so [HrvAnalyzer.rejectArtifacts] doesn't truncate it — we
 *    keep the perturbation amplitude well below 0.2 × mean IBI.
 *  - The frequency we want to inject (in Hz) is a property of the
 *    *time* axis. We build the IBIs against a uniform 4 Hz time axis,
 *    then convert back into an IBI series whose cumulative sums recover
 *    that time axis to within numerical noise.
 */
class SpectralTest {

    private val meanIbiMs = 800.0  // 75 bpm rest

    /**
     * Build an IBI series whose *values* oscillate at the requested
     * frequency when indexed by their actual cumulative beat time. The
     * spectral derivation rebuilds that same time axis from the IBIs,
     * so a sinusoid at frequency f here lands at frequency f in the
     * FFT.
     */
    private fun ibiSeriesWithFrequency(
        freqHz: Double,
        durationSec: Double,
        amplitudeMs: Double = 30.0
    ): List<Double> {
        val meanSec = meanIbiMs / 1000.0
        val beatCount = (durationSec / meanSec).toInt()
        val ibis = ArrayList<Double>(beatCount)
        var tSec = 0.0
        for (i in 0 until beatCount) {
            val ibi = meanIbiMs + amplitudeMs * sin(2.0 * PI * freqHz * tSec)
            ibis.add(ibi)
            tSec += ibi / 1000.0
        }
        return ibis
    }

    @Test
    fun `pure 0_1 Hz oscillation lands almost entirely in the LF band`() {
        // 0.1 Hz sits in LF (0.04–0.15 Hz); HF (0.15–0.40 Hz) should be empty.
        val ibis = ibiSeriesWithFrequency(freqHz = 0.10, durationSec = 180.0)
        val ratio = Spectral.lfHfRatio(ibis)
        assertTrue("LF/HF should be >> 1 for a pure LF signal, got $ratio", ratio > 5.0)
    }

    @Test
    fun `pure 0_25 Hz oscillation lands almost entirely in the HF band`() {
        // 0.25 Hz sits in HF; LF should be empty.
        val ibis = ibiSeriesWithFrequency(freqHz = 0.25, durationSec = 180.0)
        val ratio = Spectral.lfHfRatio(ibis)
        assertTrue("LF/HF should be << 1 for a pure HF signal, got $ratio", ratio < 0.2)
    }

    @Test
    fun `constant IBI series produces zero LF over HF`() {
        val ibis = List(360) { meanIbiMs }  // 4+ minutes of perfectly regular beats
        assertEquals(0.0, Spectral.lfHfRatio(ibis), 0.0)
    }

    @Test
    fun `recordings shorter than 60 seconds return zero`() {
        // 45 seconds at 75 bpm is ~56 beats. Below the minimum LF window.
        val ibis = List(56) { meanIbiMs + (if (it % 2 == 0) 5.0 else -5.0) }
        assertEquals(0.0, Spectral.lfHfRatio(ibis), 0.0)
    }

    @Test
    fun `equal-amplitude LF plus HF produces a finite positive ratio`() {
        // Mixed 0.10 + 0.25 Hz at equal amplitude → both bands have power.
        val meanSec = meanIbiMs / 1000.0
        val durationSec = 180.0
        val beatCount = (durationSec / meanSec).toInt()
        val ibis = ArrayList<Double>(beatCount)
        var tSec = 0.0
        for (i in 0 until beatCount) {
            val perturb = 15.0 * (sin(2.0 * PI * 0.10 * tSec) + sin(2.0 * PI * 0.25 * tSec))
            val ibi = meanIbiMs + perturb
            ibis.add(ibi)
            tSec += ibi / 1000.0
        }
        val ratio = Spectral.lfHfRatio(ibis)
        assertTrue("Mixed-band ratio should be finite and positive, got $ratio",
            ratio.isFinite() && ratio > 0.0)
        // With matching amplitudes the two integrated band powers come out
        // within ~3× of each other (Hann windowing, band-width asymmetry).
        assertTrue("Mixed-band ratio should sit roughly in [0.2, 5], got $ratio",
            ratio in 0.2..5.0)
    }

    @Test
    fun `lfHfRatio never returns NaN or infinity`() {
        // Sanity check across a few short, noisy series.
        val cases = listOf(
            List(80) { meanIbiMs },                              // constant
            List(80) { meanIbiMs + (it % 7) * 1.0 },             // small ramp
            (0 until 400).map { meanIbiMs + 20 * sin(it * 0.3) } // long & jittery
        )
        for (ibis in cases) {
            val r = Spectral.lfHfRatio(ibis)
            assertTrue("ratio must be finite for series of size ${ibis.size}, got $r",
                r.isFinite())
            assertTrue("ratio must be >= 0, got $r", r >= 0.0)
        }
    }

    // -- FFT itself --

    @Test
    fun `fft of a complex impulse produces uniform magnitude`() {
        val n = 16
        val real = DoubleArray(n).also { it[0] = 1.0 }
        val imag = DoubleArray(n)
        Spectral.fftInPlace(real, imag)
        // FFT of a unit impulse is a constant-magnitude 1 across all bins.
        for (k in 0 until n) {
            val mag = real[k] * real[k] + imag[k] * imag[k]
            assertEquals(1.0, mag, 1e-12)
        }
    }

    @Test
    fun `fft of a pure tone concentrates power at the expected bin`() {
        val n = 64
        val k0 = 5  // target bin
        val real = DoubleArray(n) { i -> cos(2.0 * PI * k0 * i / n) }
        val imag = DoubleArray(n)
        Spectral.fftInPlace(real, imag)
        // For a real cosine at bin k0, magnitude peaks at bins k0 and n - k0.
        val mag = DoubleArray(n) { real[it] * real[it] + imag[it] * imag[it] }
        val peakBin = mag.indices.maxByOrNull { mag[it] }!!
        assertTrue("Peak bin should be at k0 or n-k0, got $peakBin",
            peakBin == k0 || peakBin == n - k0)
    }
}
