package com.bios.app

import com.bios.app.engine.CaptureQuality
import com.bios.app.engine.PpgDeviceProfile
import com.bios.app.engine.PpgDeviceProfiles
import com.bios.app.engine.PpgSignalProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Pure-JVM coverage of [PpgDeviceProfile] + [PpgDeviceProfiles]
 * (#266 Cut 2). Pins the per-device threshold registry shape, the
 * fallback contract, and the lookup case-insensitivity guarantee.
 *
 * The empty-PROFILES state is intentional at Cut 2 landing — the
 * issue spec requires thresholds to be derived from calibration-log
 * distribution data, not guessed. As specific device entries land,
 * the [unknown_device_falls_back_to_DEFAULT] test continues to pin
 * the no-regression contract; per-device entries get their own
 * pinned tests at addition time.
 */
class PpgDeviceProfileTest {

    @Test
    fun DEFAULT_preserves_pre_cut2_thresholds_exactly() {
        // No regression contract — DEFAULT must equal the eyeball-
        // tuned baselines exactly so devices not yet in PROFILES see
        // pre-Cut-2 behaviour bit-for-bit.
        assertEquals(
            CaptureQuality.MOTION_MEDIAN_JUMP_Y_DEFAULT,
            PpgDeviceProfiles.DEFAULT.motionMedianJumpY,
            1e-9,
        )
        assertEquals(
            PpgSignalProcessor.MAX_PEAK_AMPLITUDE_COV_DEFAULT,
            PpgDeviceProfiles.DEFAULT.maxPeakAmplitudeCov,
            1e-9,
        )
    }

    @Test
    fun forDevice_returns_DEFAULT_for_null_model_string() {
        // Build.MODEL can be null on emulators / oddly-configured
        // devices. The fallback chain must handle that gracefully.
        assertSame(PpgDeviceProfiles.DEFAULT, PpgDeviceProfiles.forDevice(null))
    }

    @Test
    fun forDevice_returns_DEFAULT_for_unknown_model() {
        assertSame(
            PpgDeviceProfiles.DEFAULT,
            PpgDeviceProfiles.forDevice("DEVICE_NOT_IN_REGISTRY"),
        )
    }

    @Test
    fun PpgDeviceProfile_data_class_equality_is_by_value() {
        // A data class — relied on by the test pattern that asserts
        // PROFILES["Pixel 9a"] == PpgDeviceProfile(...). Two equal-
        // value instances must be `equals` and `hashCode`-equivalent.
        val a = PpgDeviceProfile(motionMedianJumpY = 8.0, maxPeakAmplitudeCov = 0.85)
        val b = PpgDeviceProfile(motionMedianJumpY = 8.0, maxPeakAmplitudeCov = 0.85)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun classify_uses_the_per_device_threshold_not_the_global_default() {
        // 30 samples, frame-to-frame median |diff| = 7.0 (the test
        // builds an alternating-magnitude trace so consecutive diffs
        // are all 7). DEFAULT threshold = 6 → MOTION. Custom
        // profile with motionMedianJumpY = 10 → STEADY.
        val window = (0 until 60).map { i -> 100.0 + (if (i % 2 == 0) 0.0 else 7.0) }
        // Sanity-check: at default threshold this fires as MOTION.
        val atDefault = CaptureQuality.classify(window, samplingRateHz = 30.0)
        assertEquals(CaptureQuality.MOTION, atDefault)
        // With a more tolerant per-device profile, the same window
        // reads as STEADY.
        val tolerant = PpgDeviceProfile(motionMedianJumpY = 10.0, maxPeakAmplitudeCov = 0.80)
        val atTolerant = CaptureQuality.classify(window, samplingRateHz = 30.0, profile = tolerant)
        assertEquals(CaptureQuality.STEADY, atTolerant)
    }
}
