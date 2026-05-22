package com.bios.app

import com.bios.app.engine.PhoneSleepInference
import com.bios.app.engine.PhoneSleepInference.Sample
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.SleepStage
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the rule-based phone-only sleep inference (issue #134).
 *
 * The Android-bound collection path (PhoneSleepAdapter) is not exercised
 * here — those tests would require a SensorManager and battery service.
 * The inference itself is pure logic and fully testable.
 */
class PhoneSleepInferenceTest {

    private val sourceId = "phone-sensor-derived"
    private val minuteMs = 60_000L

    @Test
    fun `no samples produces no readings`() {
        assertTrue(PhoneSleepInference.infer(emptyList(), sourceId).isEmpty())
    }

    @Test
    fun `short screen-off stretch under threshold yields nothing`() {
        // 1h of screen-off — well below the 4h minimum window.
        val samples = trace(durationMs = 60 * minuteMs, screenOff = true, charging = true,
            lux = 0f, accelVar = 0f)
        assertTrue(PhoneSleepInference.infer(samples, sourceId).isEmpty())
    }

    @Test
    fun `8h quiet dark charging window emits a SLEEP_DURATION reading at MEDIUM confidence`() {
        val samples = trace(durationMs = 8 * 60 * minuteMs, screenOff = true, charging = true,
            lux = 0f, accelVar = 0f)
        val readings = PhoneSleepInference.infer(samples, sourceId)
        val duration = readings.firstOrNull { it.metricType == MetricType.SLEEP_DURATION.key }
        assertNotNull("Expected a SLEEP_DURATION reading", duration)
        // Sleep ~= window length when no AWAKE bouts. Allow a 1-minute
        // tolerance for the segment closing boundary.
        assertTrue(duration!!.value in (8 * 3600 - 60).toDouble()..(8 * 3600).toDouble())
        assertEquals(ConfidenceTier.MEDIUM.level, duration.confidence)
    }

    @Test
    fun `screen-on samples in a lit room are not counted as sleep`() {
        // 8h, screen on, in a lit room (desk or meeting). The fused-quiet
        // fallback rejects this because the dark-room booster fails.
        val samples = trace(durationMs = 8 * 60 * minuteMs, screenOff = false, charging = true,
            lux = 200f, accelVar = 0f)
        assertTrue(PhoneSleepInference.infer(samples, sourceId).isEmpty())
    }

    @Test
    fun `phone-in-hand in a dark room is not counted as sleep`() {
        // Owner reading / scrolling in bed: screen on, dark room, but the
        // hand-held device generates motion. Fused-quiet must reject this.
        val samples = trace(durationMs = 8 * 60 * minuteMs, screenOff = false, charging = true,
            lux = 0f, accelVar = 2f)
        assertTrue(PhoneSleepInference.infer(samples, sourceId).isEmpty())
    }

    @Test
    fun `unplugged screen-on phone is not counted as sleep`() {
        // Without the charging booster, screen-on phone in a dark still
        // room is too ambiguous (pocket, drawer, bag) to claim as sleep.
        val samples = trace(durationMs = 8 * 60 * minuteMs, screenOff = false, charging = false,
            lux = 0f, accelVar = 0f)
        assertTrue(PhoneSleepInference.infer(samples, sourceId).isEmpty())
    }

    @Test
    fun `always-on-display phone on a charged nightstand is counted as sleep`() {
        // The bug that triggered the fused-quiet fallback: phones with AOD
        // keep the display reading STATE_ON through the night even when
        // the owner is asleep. If they're stationary, the room is dark,
        // and they're charging, that's the AOD-on-nightstand signature.
        val samples = trace(durationMs = 8 * 60 * minuteMs, screenOff = false, charging = true,
            lux = 0f, accelVar = 0f)
        val readings = PhoneSleepInference.infer(samples, sourceId)
        val duration = readings.firstOrNull { it.metricType == MetricType.SLEEP_DURATION.key }
        assertNotNull("Expected the AOD-on-nightstand window to land", duration)
    }

    @Test
    fun `quiet but unplugged window downgrades confidence to LOW`() {
        // No charging confirmation, no ambient light reading — booster
        // signals don't confirm, so confidence sags to LOW.
        val samples = trace(durationMs = 8 * 60 * minuteMs, screenOff = true, charging = false,
            lux = null, accelVar = 0f)
        val readings = PhoneSleepInference.infer(samples, sourceId)
        val duration = readings.first { it.metricType == MetricType.SLEEP_DURATION.key }
        assertEquals(ConfidenceTier.LOW.level, duration.confidence)
    }

    @Test
    fun `lit room downgrades confidence to LOW even when charging`() {
        // Plugged in but the room is bright (phone face-up on a lit desk) —
        // not the dark-bedroom signature we want before claiming MEDIUM.
        val samples = trace(durationMs = 8 * 60 * minuteMs, screenOff = true, charging = true,
            lux = 200f, accelVar = 0f)
        val readings = PhoneSleepInference.infer(samples, sourceId)
        val duration = readings.first { it.metricType == MetricType.SLEEP_DURATION.key }
        assertEquals(ConfidenceTier.LOW.level, duration.confidence)
    }

    @Test
    fun `prolonged movement bout in the middle subtracts from total sleep`() {
        // 8h window, 1h of high accel variance in the middle = AWAKE.
        val samples = buildList {
            for (i in 0 until 8 * 60) {
                val ts = i * minuteMs
                val awake = i in 200..259 // ~1h awake stretch
                add(Sample(
                    timestamp = ts,
                    screenOff = true,
                    charging = true,
                    ambientLightLux = 0f,
                    accelMagnitudeVar = if (awake) 2.0f else 0f
                ))
            }
        }
        val readings = PhoneSleepInference.infer(samples, sourceId)
        val duration = readings.first { it.metricType == MetricType.SLEEP_DURATION.key }
        // 8h - ~1h awake ≈ 7h sleep. Allow ±2 min tolerance.
        val expected = (7 * 3600).toDouble()
        assertTrue("expected ~7h, got ${duration.value}",
            kotlin.math.abs(duration.value - expected) < 120.0)
    }

    @Test
    fun `brief movement bouts under five minutes are not counted as wake`() {
        // 8h, with a single 2-minute movement bout — posture shift, not wake.
        val samples = buildList {
            for (i in 0 until 8 * 60) {
                val ts = i * minuteMs
                val flicker = i in 240..241
                add(Sample(
                    timestamp = ts,
                    screenOff = true,
                    charging = true,
                    ambientLightLux = 0f,
                    accelMagnitudeVar = if (flicker) 2.0f else 0f
                ))
            }
        }
        val readings = PhoneSleepInference.infer(samples, sourceId)
        val duration = readings.first { it.metricType == MetricType.SLEEP_DURATION.key }
        // Sleep should be ~full window, brief bout absorbed.
        assertTrue(duration.value > (8 * 3600 - 120).toDouble())
    }

    @Test
    fun `output stages are limited to LIGHT and AWAKE`() {
        // Phone-only inference deliberately cannot discriminate DEEP / REM,
        // so the stage rows it produces must stay inside {LIGHT, AWAKE}.
        val samples = buildList {
            for (i in 0 until 8 * 60) {
                val ts = i * minuteMs
                val awake = i in 200..259
                add(Sample(
                    timestamp = ts,
                    screenOff = true,
                    charging = true,
                    ambientLightLux = 0f,
                    accelMagnitudeVar = if (awake) 2.0f else 0f
                ))
            }
        }
        val stages = PhoneSleepInference.infer(samples, sourceId)
            .filter { it.metricType == MetricType.SLEEP_STAGE.key }
            .map { it.value.toInt() }
            .toSet()
        assertTrue(stages.isNotEmpty())
        assertTrue(stages.all { it == SleepStage.LIGHT.value || it == SleepStage.AWAKE.value })
        assertFalse(SleepStage.DEEP.value in stages)
        assertFalse(SleepStage.REM.value in stages)
    }

    @Test
    fun `picks the longest screen-off stretch when two exist`() {
        // 2h quiet, 30m active, 8h quiet. The 8h stretch must be the one
        // that lands — picking the 2h fragment would mis-attribute the trace.
        val samples = mutableListOf<Sample>()
        var t = 0L
        repeat(2 * 60) { samples += sleepSample(t); t += minuteMs }
        repeat(30) { samples += awakeSample(t); t += minuteMs }
        repeat(8 * 60) { samples += sleepSample(t); t += minuteMs }
        val readings = PhoneSleepInference.infer(samples, sourceId)
        val duration = readings.first { it.metricType == MetricType.SLEEP_DURATION.key }
        // The 2h fragment is below the 4h minimum window, so the picked
        // stretch must be the 8h one — sleep should be ~8h, not ~2h.
        assertTrue(duration.value > (7 * 3600).toDouble())
    }

    @Test
    fun `output sourceId is propagated`() {
        val samples = trace(durationMs = 8 * 60 * minuteMs, screenOff = true, charging = true,
            lux = 0f, accelVar = 0f)
        val readings = PhoneSleepInference.infer(samples, "test-source-id-xyz")
        assertTrue(readings.isNotEmpty())
        assertTrue(readings.all { it.sourceId == "test-source-id-xyz" })
    }

    // ---- helpers ----

    private fun trace(
        durationMs: Long,
        screenOff: Boolean,
        charging: Boolean,
        lux: Float?,
        accelVar: Float,
    ): List<Sample> {
        val n = (durationMs / minuteMs).toInt()
        return List(n) { i ->
            Sample(
                timestamp = i * minuteMs,
                screenOff = screenOff,
                charging = charging,
                ambientLightLux = lux,
                accelMagnitudeVar = accelVar
            )
        }
    }

    private fun sleepSample(t: Long) = Sample(
        timestamp = t, screenOff = true, charging = true,
        ambientLightLux = 0f, accelMagnitudeVar = 0f
    )

    private fun awakeSample(t: Long) = Sample(
        timestamp = t, screenOff = false, charging = false,
        ambientLightLux = 300f, accelMagnitudeVar = 3f
    )
}
