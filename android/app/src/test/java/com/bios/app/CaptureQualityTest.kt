package com.bios.app

import com.bios.app.engine.CaptureQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureQualityTest {

    private val fs = 20.0  // ASSUMED_SAMPLING_HZ used by the adapter

    @Test
    fun `too few frames classifies as WAITING`() {
        val window = listOf(50.0, 51.0, 50.5)
        assertEquals(CaptureQuality.WAITING, CaptureQuality.classify(window, fs))
    }

    @Test
    fun `empty window classifies as WAITING`() {
        assertEquals(CaptureQuality.WAITING, CaptureQuality.classify(emptyList(), fs))
    }

    @Test
    fun `under the warmup minimum still classifies as WAITING`() {
        // 20 frames at 20 fps = 1 s, below the 1.5 s warmup floor.
        val window = (0 until 20).map { i ->
            60.0 + 10.0 * kotlin.math.sin(2.0 * kotlin.math.PI * 1.0 * i / fs)
        }
        assertEquals(CaptureQuality.WAITING, CaptureQuality.classify(window, fs))
    }

    @Test
    fun `bright frames classify as FINGER_OFF`() {
        // Mean luminance > 200 → room light is reaching the sensor.
        val window = List(40) { 220.0 + (it % 3) }
        assertEquals(CaptureQuality.FINGER_OFF, CaptureQuality.classify(window, fs))
    }

    @Test
    fun `flat low-variance frames classify as FINGER_OFF`() {
        // Finger loosely on lens but no PPG modulation at all → no contact.
        val window = List(40) { 60.0 }
        assertEquals(CaptureQuality.FINGER_OFF, CaptureQuality.classify(window, fs))
    }

    @Test
    fun `sustained large jumps classify as MOTION`() {
        // Square-wave-style motion: every frame jumps ±15 Y units alternately.
        // |diff| = 15 on every adjacent pair → median |diff| = 15, well above
        // MOTION_MEDIAN_JUMP_Y (= 6). Heart-beats can't produce this pattern
        // because the diff distribution is dominated by small flat regions.
        val window = (0 until 40).map { i ->
            60.0 + (if (i % 2 == 0) 0.0 else 15.0)
        }
        assertEquals(CaptureQuality.MOTION, CaptureQuality.classify(window, fs))
    }

    @Test
    fun `a clean PPG-shaped signal classifies as STEADY`() {
        // ~1 Hz heart-beat (60 bpm) with 10 Y-unit amplitude on a 60 mean.
        val window = (0 until 40).map { i ->
            val t = i / fs
            60.0 + 10.0 * kotlin.math.sin(2.0 * kotlin.math.PI * 1.0 * t)
        }
        assertEquals(CaptureQuality.STEADY, CaptureQuality.classify(window, fs))
    }

    @Test
    fun `a strong PPG signal with sharp systolic peaks still classifies as STEADY`() {
        // Bigger-amplitude PPG (closer to a real device with strong perfusion).
        // The systolic peak frames have large frame-to-frame diffs but the
        // median diff over the full window stays small.
        val window = (0 until 40).map { i ->
            val t = i / fs
            // Asymmetric beat: fast rise, slow decay, peak ~25 Y units.
            val phase = (t * 1.0) % 1.0
            val pulse = if (phase < 0.15) phase / 0.15 else (1.0 - phase) / 0.85
            60.0 + 25.0 * pulse
        }
        assertEquals(CaptureQuality.STEADY, CaptureQuality.classify(window, fs))
    }

    @Test
    fun `a single one-frame spike on top of clean PPG is tolerated`() {
        // A momentary glitch shouldn't flip the classification — median-of-
        // diffs filters single outliers out.
        val window = (0 until 40).map { i ->
            val t = i / fs
            val base = 60.0 + 10.0 * kotlin.math.sin(2.0 * kotlin.math.PI * 1.0 * t)
            if (i == 35) base + 40.0 else base
        }
        assertEquals(CaptureQuality.STEADY, CaptureQuality.classify(window, fs))
    }

    @Test
    fun `messages are non-empty and isGood matches STEADY only`() {
        for (q in CaptureQuality.entries) {
            assert(q.message.isNotBlank()) { "$q has a blank message" }
        }
        assertEquals(true, CaptureQuality.STEADY.isGood)
        assertEquals(false, CaptureQuality.WAITING.isGood)
        assertEquals(false, CaptureQuality.MOTION.isGood)
        assertEquals(false, CaptureQuality.FINGER_OFF.isGood)
    }
}
