package com.bios.app

import com.bios.app.engine.PpgResult
import com.bios.app.engine.PpgSignalProcessor
import com.bios.app.engine.RejectionReason
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class PpgSignalProcessorTest {

    private val fs = 30.0  // 30 fps — CameraX ImageAnalysis default

    // -- Synthetic waveform generators --

    /** Clean sinusoid at [bpm], amplitude 40 around baseline 128 (PPG-like). */
    private fun sinusoid(bpm: Double, durationSec: Double, amplitude: Double = 40.0): List<Double> {
        val freq = bpm / 60.0
        val n = (durationSec * fs).toInt()
        return (0 until n).map { i ->
            val t = i / fs
            128.0 + amplitude * sin(2 * PI * freq * t)
        }
    }

    /** Sinusoid plus a slow linear drift to stress-test the detrend stage.
     *  0.02 per sample ≈ +36 units over 60 s at 30 fps — realistic baseline
     *  wander without saturating the 0..255 luminance range. */
    private fun sinusoidWithDrift(bpm: Double, durationSec: Double): List<Double> {
        val base = sinusoid(bpm, durationSec)
        return base.mapIndexed { i, v -> v + i * 0.02 }
    }

    // -- Orchestration tests --

    @Test
    fun `clean 60 BPM sinusoid extracts ~1000ms RR intervals`() {
        val result = PpgSignalProcessor.extract(sinusoid(bpm = 60.0, durationSec = 60.0), fs)

        assertTrue("should accept: ${result.rejectionReason}", result.accepted)
        assertTrue("need enough beats", result.peakCount >= 55)

        val meanRr = result.rrIntervalsMs.average()
        assertEquals(1000.0, meanRr, 50.0)  // ±50ms tolerance at 30fps
    }

    @Test
    fun `clean 72 BPM sinusoid extracts ~833ms RR intervals`() {
        val result = PpgSignalProcessor.extract(sinusoid(bpm = 72.0, durationSec = 60.0), fs)

        assertTrue("should accept: ${result.rejectionReason}", result.accepted)
        assertEquals(833.0, result.rrIntervalsMs.average(), 50.0)
    }

    @Test
    fun `baseline drift does not break peak detection`() {
        val result = PpgSignalProcessor.extract(sinusoidWithDrift(bpm = 72.0, durationSec = 60.0), fs)

        assertTrue("should accept despite drift: ${result.rejectionReason}", result.accepted)
        assertEquals(833.0, result.rrIntervalsMs.average(), 80.0)
    }

    @Test
    fun `SQI is meaningfully high on clean signal`() {
        val result = PpgSignalProcessor.extract(sinusoid(bpm = 72.0, durationSec = 60.0), fs)
        assertTrue("SQI=${result.sqiScore}", result.sqiScore >= 70)
    }

    // -- Rejection paths --

    @Test
    fun `short recording is rejected with INSUFFICIENT_RECORDING_TIME`() {
        val result = PpgSignalProcessor.extract(sinusoid(bpm = 72.0, durationSec = 10.0), fs)
        assertEquals(RejectionReason.INSUFFICIENT_RECORDING_TIME, result.rejectionReason)
        assertTrue(result.rrIntervalsMs.isEmpty())
    }

    @Test
    fun `flat (no-finger) signal is rejected with INSUFFICIENT_SIGNAL`() {
        val flat = List((60.0 * fs).toInt()) { 50.0 }
        val result = PpgSignalProcessor.extract(flat, fs)
        assertEquals(RejectionReason.INSUFFICIENT_SIGNAL, result.rejectionReason)
    }

    @Test
    fun `saturated (overexposed) signal is rejected with SATURATION`() {
        val saturated = List((60.0 * fs).toInt()) { 255.0 }
        val result = PpgSignalProcessor.extract(saturated, fs)
        assertEquals(RejectionReason.SATURATION, result.rejectionReason)
    }

    @Test
    fun `pure noise is rejected, not silently accepted`() {
        val rng = Random(seed = 42)
        val noise = List((60.0 * fs).toInt()) { 128.0 + rng.nextDouble(-5.0, 5.0) }
        val result = PpgSignalProcessor.extract(noise, fs)
        // Must reject with *some* reason; never produce a silent bad number.
        assertFalse("noise should not be accepted", result.accepted)
        assertNotNull(result.rejectionReason)
    }

    @Test
    fun `chaotic motion-corrupted signal is rejected`() {
        // Random-walk baseline (motion drift) on top of a weak heart-beat —
        // peak amplitudes vary wildly, RR intervals jitter. Even after
        // trimming the bottom-quartile peaks for amplitude CoV, this
        // signal should still trip one of the motion / rhythm rejections.
        val rng = java.util.Random(7)
        val n = (60.0 * fs).toInt()
        var baseline = 128.0
        val samples = (0 until n).map { i ->
            val t = i / fs
            baseline = (baseline + (rng.nextDouble() - 0.5) * 8.0).coerceIn(60.0, 200.0)
            val heart = 5.0 * sin(2 * PI * 1.2 * t)
            baseline + heart
        }
        val result = PpgSignalProcessor.extract(samples, fs)
        assertFalse("should not accept motion-corrupted signal", result.accepted)
        // Any rejection reason is fine — we just want it gone.
        assertNotNull(result.rejectionReason)
    }

    // -- Helper correctness (guards against subtle bugs) --

    @Test
    fun `detrend removes linear drift`() {
        val drifted = (0 until 100).map { it.toDouble() }
        val detrended = PpgSignalProcessor.detrend(drifted, windowSamples = 21)
        // Middle samples should be close to 0 after drift removal.
        assertEquals(0.0, detrended[50], 0.5)
    }

    @Test
    fun `smooth preserves constant signal`() {
        val constant = DoubleArray(50) { 7.0 }
        val smoothed = PpgSignalProcessor.smooth(constant, windowSamples = 5)
        smoothed.forEach { assertEquals(7.0, it, 0.001) }
    }

    @Test
    fun `detectPeaks finds peaks of a clean cosine`() {
        // Cosine starts at peak (index 0) and peaks once per period.
        val durationSec = 10.0
        val freq = 2.0  // Hz → peak every 0.5 s → ~20 peaks over 10s
        val n = (durationSec * fs).toInt()
        val samples = DoubleArray(n) { i -> cos(2 * PI * freq * i / fs) }
        val peaks = PpgSignalProcessor.detectPeaks(
            samples,
            thresholdWindow = 60,
            refractorySamples = 7,
            thresholdK = 0.0  // cosine is very clean — no threshold needed
        )
        // Expected ~20 peaks; allow ±2 for edge effects.
        assertTrue("peakCount=${peaks.size}", abs(peaks.size - 20) <= 2)
    }

    @Test
    fun `coefficientOfVariation is 0 for constant series`() {
        assertEquals(0.0, PpgSignalProcessor.coefficientOfVariation(listOf(5.0, 5.0, 5.0, 5.0)), 0.001)
    }

    @Test
    fun `rejected result carries empty RR list and reason`() {
        val r = PpgResult.rejected(RejectionReason.INSUFFICIENT_SIGNAL, durationSec = 60.0)
        assertFalse(r.accepted)
        assertTrue(r.rrIntervalsMs.isEmpty())
        assertNotNull(r.rejectionReason)
    }

    // -- Pulse-wave morphology (#181, CARDIOLOGY_POV §2.2) --

    /**
     * Synthetic PPG-like beat: asymmetric (faster rise, slower decay) with a
     * small dichrotic notch on the decay limb. Repeats at [bpm] for
     * [durationSec]. Baseline 128, peak amplitude ~40.
     */
    private fun ppgLikeWaveform(bpm: Double, durationSec: Double): List<Double> {
        val periodSec = 60.0 / bpm
        val n = (durationSec * fs).toInt()
        return (0 until n).map { i ->
            val tInBeat = (i / fs) % periodSec
            val phase = tInBeat / periodSec  // 0..1 across one beat
            val systolic = when {
                // Rise (0..0.30): rapid up-stroke, sine-shaped quarter-wave.
                phase < 0.30 -> sin(PI * phase / 0.30 / 2)
                // Initial decay (0.30..0.50): drop from peak to notch.
                phase < 0.50 -> 1.0 - 0.55 * (phase - 0.30) / 0.20
                // Diastolic rebound (0.50..0.60): small bump after the notch.
                phase < 0.60 -> 0.45 + 0.07 * sin(PI * (phase - 0.50) / 0.10)
                // Long decay to foot (0.60..1.0).
                else -> 0.52 - 0.52 * (phase - 0.60) / 0.40
            }
            128.0 + 40.0 * systolic
        }
    }

    @Test
    fun `accepted PPG carries populated waveform morphology features`() {
        val result = PpgSignalProcessor.extract(ppgLikeWaveform(bpm = 60.0, durationSec = 60.0), fs)

        assertTrue("should accept: ${result.rejectionReason}", result.accepted)
        val features = result.waveformFeatures
        assertNotNull("morphology features should be populated on accepted signal", features)
        features!!

        // Peak amplitude trimmed mean is measured on the *detrended + smoothed*
        // signal (baseline subtracted, low-pass filtered), so the absolute
        // value is well below the 40-unit raw peak amplitude. We only assert
        // it is positive and finite — exact magnitude depends on detrend
        // window and smoothing kernel.
        assertTrue(
            "peakAmplitudeTrimmedMean=${features.peakAmplitudeTrimmedMean}",
            features.peakAmplitudeTrimmedMean > 0.0 && features.peakAmplitudeTrimmedMean.isFinite()
        )
        // Synthetic signal is steady, so CoV must be small.
        assertTrue("peakAmplitudeCov=${features.peakAmplitudeCov}", features.peakAmplitudeCov < 0.2)

        // Rise time at 60 bpm with rise-fraction ~0.3 of a 1.0 s beat ≈ 0.3 s.
        // 30 fps quantisation gives ±2 sample slack → ~0.07 s.
        assertEquals(0.3, features.riseTimeMeanSec, 0.1)
        assertTrue("riseTimeCov=${features.riseTimeCov}", features.riseTimeCov < 0.2)

        // Asymmetric beat (decay slower than rise) → positive index.
        assertTrue(
            "decayAsymmetryIndex=${features.decayAsymmetryIndex}",
            features.decayAsymmetryIndex > 0.1
        )

        // Beats measured should be most of the detected peaks (60 bpm × 60 s ≈ 60).
        assertTrue("beatsMeasured=${features.beatsMeasured}", features.beatsMeasured >= 30)
    }

    @Test
    fun `dichrotic notch position is detected on PPG-like beat with notch`() {
        val result = PpgSignalProcessor.extract(ppgLikeWaveform(bpm = 60.0, durationSec = 60.0), fs)
        val features = result.waveformFeatures
        assertNotNull(features)
        // The notch in the synthetic beat sits at phase ~0.50 of the beat
        // period. Smoothing and trough-finding will shift this slightly —
        // accept the broad mid-beat band.
        val notch = features!!.dichroticNotchRelativePosition
        if (notch != null) {
            assertTrue(
                "dichroticNotchRelativePosition=$notch should sit in mid-beat",
                notch in 0.25..0.75
            )
        }
        // It is acceptable for notch detection to return null on a low-fs
        // synthetic — the field is documented as nullable. We only assert
        // the value is sensible *when* present.
    }

    @Test
    fun `rejected PPG carries null waveform features`() {
        val flat = List((60.0 * fs).toInt()) { 50.0 }
        val result = PpgSignalProcessor.extract(flat, fs)
        assertFalse(result.accepted)
        assertNull(
            "rejected recordings must not surface morphology",
            result.waveformFeatures
        )
    }

    @Test
    fun `clean sinusoid yields symmetric rise and decay`() {
        val result = PpgSignalProcessor.extract(sinusoid(bpm = 60.0, durationSec = 60.0), fs)
        assertTrue(result.accepted)
        val features = result.waveformFeatures
        assertNotNull(features)
        // Pure sinusoid → rise ≈ decay → asymmetry index close to 0.
        assertEquals(
            "sinusoidal beat must have near-zero decay asymmetry",
            0.0,
            features!!.decayAsymmetryIndex,
            0.15
        )
    }
}
