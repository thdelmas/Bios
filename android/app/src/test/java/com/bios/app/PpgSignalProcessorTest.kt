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
    fun `large amplitude variation triggers MOTION_ARTIFACT`() {
        // Sinusoid whose amplitude swings between 5 and 80 — mimics finger lift / shake.
        val n = (60.0 * fs).toInt()
        val samples = (0 until n).map { i ->
            val t = i / fs
            val envelope = 40.0 + 35.0 * sin(2 * PI * 0.1 * t)  // slow amplitude modulation
            128.0 + envelope * sin(2 * PI * 1.2 * t)            // 72 bpm
        }
        val result = PpgSignalProcessor.extract(samples, fs)
        assertFalse("should not accept motion-corrupted signal", result.accepted)
        // Either MOTION_ARTIFACT or IRREGULAR_RHYTHM is acceptable — both are
        // correct rejections for this waveform.
        assertTrue(
            "reason=${result.rejectionReason}",
            result.rejectionReason == RejectionReason.MOTION_ARTIFACT ||
                result.rejectionReason == RejectionReason.IRREGULAR_RHYTHM
        )
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
}
