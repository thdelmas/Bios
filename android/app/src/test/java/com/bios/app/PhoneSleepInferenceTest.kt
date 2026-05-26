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
        // 8h - ~1h awake ≈ 7h sleep. Tolerance bumped from ±2 min to
        // ±15 min when the Cole-Kripke 7-tap classifier landed (#244
        // Cut 1): the smoother FIR extends the AWAKE classification
        // ~3 min into each quiet neighbour at the activity boundary,
        // so a 60-min raw bout reads as ~66-74 min of AWAKE end-to-end.
        // The behaviour matches published Cole-Kripke characterisation.
        val expected = (7 * 3600).toDouble()
        assertTrue("expected ~7h, got ${duration.value}",
            kotlin.math.abs(duration.value - expected) < 900.0)
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

    // ---- #243 Cut 1: Tier-1 sensor-surface expansion ----

    @Test
    fun signal_breakdown_counts_new_signals_when_present() {
        // 5 samples: 3 with stepDelta=0 + DND on + paired BT on,
        // 2 with stepDelta=42 + DND off + no BT.
        val samples = buildList {
            repeat(3) { i ->
                add(
                    Sample(
                        timestamp = i.toLong(),
                        screenOff = true,
                        charging = true,
                        ambientLightLux = 0f,
                        accelMagnitudeVar = 0f,
                        stepDelta = 0,
                        dndEnabled = true,
                        pairedBluetoothConnected = true,
                    )
                )
            }
            repeat(2) { i ->
                add(
                    Sample(
                        timestamp = (3 + i).toLong(),
                        screenOff = true,
                        charging = true,
                        ambientLightLux = 0f,
                        accelMagnitudeVar = 0f,
                        stepDelta = 42,
                        dndEnabled = false,
                        pairedBluetoothConnected = false,
                    )
                )
            }
        }
        val breakdown = PhoneSleepInference.signalBreakdown(samples)
        assertEquals(5, breakdown.total)
        assertEquals(3, breakdown.zeroStepDelta)
        assertEquals(3, breakdown.dndOn)
        assertEquals(3, breakdown.pairedBtConnected)
        assertEquals(5, breakdown.sampledNewSignals)
    }

    @Test
    fun signal_breakdown_treats_null_new_signals_as_uncounted() {
        // No Tier-1 signals recorded — counts stay at 0.
        val samples = List(4) { i ->
            Sample(
                timestamp = i.toLong(),
                screenOff = true,
                charging = true,
                ambientLightLux = 0f,
                accelMagnitudeVar = 0f,
                // stepDelta / dndEnabled / pairedBluetoothConnected default null.
            )
        }
        val breakdown = PhoneSleepInference.signalBreakdown(samples)
        assertEquals(4, breakdown.total)
        assertEquals(0, breakdown.zeroStepDelta)
        assertEquals(0, breakdown.dndOn)
        assertEquals(0, breakdown.pairedBtConnected)
        assertEquals(0, breakdown.sampledNewSignals)
    }

    // ---- #243 Cut 2: wake-up motion sensors + stationary weave ----

    @Test
    fun signal_breakdown_counts_wake_up_motion_signals() {
        // 4 samples: 2 with sigMotion fired + stationary false, 2 with
        // sigMotion not fired + stationary true. Counts must split.
        val samples = buildList {
            repeat(2) { i ->
                add(
                    Sample(
                        timestamp = i.toLong(),
                        screenOff = true,
                        charging = true,
                        ambientLightLux = 0f,
                        accelMagnitudeVar = 0f,
                        significantMotionFired = true,
                        stationary = false,
                    )
                )
            }
            repeat(2) { i ->
                add(
                    Sample(
                        timestamp = (2 + i).toLong(),
                        screenOff = true,
                        charging = true,
                        ambientLightLux = 0f,
                        accelMagnitudeVar = 0f,
                        significantMotionFired = false,
                        stationary = true,
                    )
                )
            }
        }
        val breakdown = PhoneSleepInference.signalBreakdown(samples)
        assertEquals(4, breakdown.total)
        assertEquals(2, breakdown.sigMotionFired)
        assertEquals(2, breakdown.stationaryDetected)
        assertEquals(4, breakdown.sampledNewSignals)
    }

    @Test
    fun signal_breakdown_treats_null_wake_up_signals_as_uncounted() {
        // No wake-up sensors recorded — counts stay at 0 and the
        // denominator excludes these samples too.
        val samples = List(3) { i ->
            Sample(
                timestamp = i.toLong(),
                screenOff = true,
                charging = true,
                ambientLightLux = 0f,
                accelMagnitudeVar = 0f,
                // significantMotionFired / stationary default null.
            )
        }
        val breakdown = PhoneSleepInference.signalBreakdown(samples)
        assertEquals(3, breakdown.total)
        assertEquals(0, breakdown.sigMotionFired)
        assertEquals(0, breakdown.stationaryDetected)
        assertEquals(0, breakdown.sampledNewSignals)
    }

    @Test
    fun stationary_plus_charging_passes_isQuietSample_even_when_screen_on_and_lit() {
        // The AOD-on-nightstand failure mode the Cut 2 weave specifically
        // targets: screen reads on (AOD), room is lit (streetlight,
        // partner's lamp), accel-variance proxy might disagree — but
        // hardware-confirmed stillness + charging is strong enough.
        val s = Sample(
            timestamp = 0L,
            screenOff = false,
            charging = true,
            ambientLightLux = 80f, // lit room
            accelMagnitudeVar = 1.0f, // high enough to fail lowMotion proxy
            stationary = true,
        )
        assertTrue(PhoneSleepInference.isQuietSample(s))
    }

    @Test
    fun stationary_without_charging_does_not_pass_isQuietSample() {
        // Without the plugged-in confirmation we won't accept the
        // single stationary signal — a phone left still on a meeting
        // table for an hour must not read as sleep.
        val s = Sample(
            timestamp = 0L,
            screenOff = false,
            charging = false,
            ambientLightLux = 80f,
            accelMagnitudeVar = 1.0f,
            stationary = true,
        )
        assertFalse(PhoneSleepInference.isQuietSample(s))
    }

    @Test
    fun stationary_null_falls_back_to_the_three_way_fallback() {
        // Devices missing TYPE_STATIONARY_DETECT must keep using the
        // pre-Cut-2 charging + lowMotion + dark fallback — the stationary
        // shortcut is a strict additive path, never a regression.
        val sampleQuietByFallback = Sample(
            timestamp = 0L,
            screenOff = false,
            charging = true,
            ambientLightLux = 0f, // dark
            accelMagnitudeVar = 0f, // low motion
            stationary = null,
        )
        assertTrue(PhoneSleepInference.isQuietSample(sampleQuietByFallback))

        val sampleNotQuiet = Sample(
            timestamp = 0L,
            screenOff = false,
            charging = true,
            ambientLightLux = 200f, // lit — fails dark
            accelMagnitudeVar = 0f,
            stationary = null,
        )
        assertFalse(PhoneSleepInference.isQuietSample(sampleNotQuiet))
    }

    @Test
    fun stationary_false_does_not_force_quiet_when_fallback_would_pass() {
        // stationary = false (device is actively moving) shouldn't poison
        // an otherwise-clean dark-charging-still window. The Cut 2 weave
        // is purely additive (stationary == true opens a path); a false
        // here lets the existing fallback decide.
        val s = Sample(
            timestamp = 0L,
            screenOff = true, // screenOff short-circuit
            charging = false,
            ambientLightLux = 0f,
            accelMagnitudeVar = 0f,
            stationary = false,
        )
        assertTrue(PhoneSleepInference.isQuietSample(s))
    }

    @Test
    fun new_signal_defaults_do_not_change_inference_behaviour() {
        // 6 hours of sleepy samples with null Tier-1 fields → inference
        // still fires. Confirms the new defaults don't suppress the
        // pre-#243 firing path.
        val minute = 60_000L
        val sleepy = List(6 * 60) { i ->
            Sample(
                timestamp = i * minute,
                screenOff = true,
                charging = true,
                ambientLightLux = 0f,
                accelMagnitudeVar = 0f,
                // stepDelta / dndEnabled / pairedBluetoothConnected default null.
            )
        }
        val readings = PhoneSleepInference.infer(sleepy, sourceId = "test")
        assertTrue(readings.isNotEmpty())
    }
}
