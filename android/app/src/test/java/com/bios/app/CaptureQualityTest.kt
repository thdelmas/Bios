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
    fun `bright frames classify as FINGER_OFF`() {
        // Mean luminance > 200 → room light is reaching the sensor.
        val window = List(24) { 220.0 + (it % 3) }
        assertEquals(CaptureQuality.FINGER_OFF, CaptureQuality.classify(window, fs))
    }

    @Test
    fun `flat low-variance frames classify as FINGER_OFF`() {
        // Finger loosely on lens but no PPG modulation at all → no contact.
        val window = List(24) { 60.0 }
        assertEquals(CaptureQuality.FINGER_OFF, CaptureQuality.classify(window, fs))
    }

    @Test
    fun `large frame-to-frame jumps classify as MOTION`() {
        // Heart-rate PPG amplitude is ~5-20 Y units; a 40-unit step is motion.
        val window = (0 until 24).map { i ->
            60.0 + (if (i == 22) 40.0 else if (i % 3 == 0) 1.0 else 0.0)
        }
        assertEquals(CaptureQuality.MOTION, CaptureQuality.classify(window, fs))
    }

    @Test
    fun `a clean PPG-shaped signal classifies as STEADY`() {
        // ~1 Hz heart-beat (60 bpm) with 10 Y-unit amplitude on a 60 mean.
        val window = (0 until 24).map { i ->
            val t = i / fs
            60.0 + 10.0 * kotlin.math.sin(2.0 * kotlin.math.PI * 1.0 * t)
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
